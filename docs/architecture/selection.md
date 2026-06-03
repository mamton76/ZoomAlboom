# Selection

> Related: [overview](overview.md) | [coordinates](coordinates.md) | [rendering](rendering.md) | [context-menu](context-menu.md) *(proposal — will replace § 2's long-press row when it lands)* | [editor-tools](editor-tools.md) *(decided 2026-05-24 — future-state authority on gesture allocation)*

**Future-model note (2026-05-24, not yet implemented).** This doc describes today's selection behavior. [`editor-tools.md`](editor-tools.md) locks the next-generation gesture model: (a) drag-on-empty initiates rect-select directly without long-press; (b) two-finger gestures become the only navigation path in Edit mode (no single-finger pan); (c) tap dispatch becomes tool-aware (`activeTool.onTap(...)`); (d) long-press becomes the universal popup invoker across all tools, with contents derived per `editor-tools.md § 5`. Migration sequencing in `editor-tools.md § 7.3` / `§ 10` and `todo.md § 24`. This doc will be updated section-by-section as each piece lands.

Selection is the canvas's interaction focus — the set of nodes that group operations (move, resize, rotate, delete, duplicate) apply to. This doc covers what's selected (state), how the user changes it (gestures and actions), and the gesture-stack rules that make it work consistently across viewport changes.

## 1. State

`CanvasState` holds three selection-related fields:

| Field | Type | Meaning |
|-------|------|---------|
| `selectedNodeIds` | `Set<String>` | Currently selected node IDs (single-select = size 1, multi-select = size ≥ 2) |
| `groupSelectionTransform` | `Transform?` | Group rect (rigid-body handle around multi-selection). Non-null iff `selectedNodeIds.size ≥ 2`. See [coordinates § 9](coordinates.md#9-group-selection-rect--invariants) |
| `selectionRect` | `BoundingBox?` | Transient — non-null only during a rectangle-select drag |

`groupSelectionTransform` is **NOT** recomputed on every frame. It's mutated in lockstep with the nodes during move/resize/rotate, and only re-derived when selection membership changes.

## 2. Gesture Mapping

Touch-first. There are no modifier keys, so meaning is encoded in tap vs long-press, and in whether the gesture starts on a node, on empty space, or on a stack.

The table below describes **Edit mode** behavior. For non-Edit modes see [§ 7 Mode Interaction](#7-mode-interaction).

| Gesture | Result | Action dispatched |
|---------|--------|-------------------|
| Tap on a node | **Replace** — selection becomes `{node}` | `SelectNode(id)` |
| Tap on empty space | Clear selection | `DeselectAll` |
| Long-press on a single node | **Add-or-keep** — add to selection if absent; no-op if already selected | `AddNodeToSelection(id)` |
| Long-press on empty + drag | **Rect-select** — additive if selection was non-empty at drag start, replace otherwise | `SelectNodesInRect(rect, additive)` |
| Long-press on ≥2 stacked nodes | Add topmost to selection + open context menu with an inline checkbox picker (see [context-menu.md § 4](context-menu.md#4-menu-content-by-selection-type)) | `AddNodeToSelection(topmost)`; subsequent picker-row toggles dispatch `ToggleNodeSelection(id)` |
| Frame-list item tap | Replace with one node | `SelectNode(id)` |
| Double-tap anywhere | Camera reset (unrelated to selection) | — |
| Drag on a selected node body | Move the whole selection | `MoveSelection(dx, dy)` |
| Drag on a corner handle | Resize the selection rigidly around the **opposite corner** (corner-anchored) | `ResizeSelection(factor, pivotX, pivotY)` |
| Drag on the rotation handle | Rotate the selection rigidly | `RotateSelection(Δ)` |

**Rationale:**
- Tap = replace matches touch UX conventions (iOS, Figma iPad). Tap moves focus, doesn't accumulate.
- Long-press is the explicit "I want multi-select" affordance — discoverable through the long-press [context-menu popup](context-menu.md), which surfaces the resulting selection's available actions.
- Long-press is **add-or-keep**, not toggle: the removal intent moves into the [context menu's anchor-scoped items](context-menu.md#2-menu-model) (`Remove this from selection`, `Edit this only`) rather than being a hidden long-press semantic. `ToggleNodeSelection` remains in the codebase as the underlying action for those menu items; no gesture dispatches it.
- Rect-select is additive when extending an existing selection; replace from scratch when starting fresh.

## 3. Action API

`CanvasAction` selection variants (see `CanvasViewModel.kt`):

```kotlin
sealed interface CanvasAction {
    data class SelectNode(val nodeId: String)            // replace with {id}
    data class AddNodeToSelection(val nodeId: String)    // additive, idempotent (long-press dispatcher)
    data class AddNodesToSelection(val nodeIds: Set<String>) // additive union (overlap picker)
    data class ToggleNodeSelection(val nodeId: String)   // add-or-remove; reserved for future menu "Remove from selection"
    data class SelectNodesInRect(
        val worldRect: BoundingBox,
        val additive: Boolean = false,                   // union vs replace
    )
    data object DeselectAll
    // ... group ops apply to the selection
}
```

`additive` defaults to `false` for source compatibility — calls without the flag still get replace semantics.

After every selection-membership change, the ViewModel calls `recomputeGroupTransform(selectedNodeIds)`. This is the **only** call site that builds a fresh `groupSelectionTransform` — gesture handlers mutate it in place, never re-derive.

## 4. Effective Selection Transform

`CanvasViewModel.selectionTransform(): Transform?` is the single source of truth for "what should I manipulate":

- **Empty selection** → `null`
- **Single-select** → the node's transform, looked up in `_allNodes` (NOT `visibleNodes`)
- **Multi-select** → `groupSelectionTransform`

**Critical:** the lookup uses `_allNodes`, not `visibleNodes`. Selected nodes can be culled out of the viewport (panned away, zoomed out), but the user must still be able to drag handles, tap to collapse, or long-press to open the context menu on them. Anything that gates on `visibleNodes` for selection operations is wrong. See [coordinates § 10](coordinates.md#10-viewport-independent-selection-operations).

The same rule applies to:
- `isOnSelectedNode(x, y)` — iterates `_allNodes`
- `hitTestHandle(x, y)` — uses `selectionTransform()`
- `hitTestRotationHandle(x, y)` — uses `selectionTransform()`

## 5. Gesture Stack

Three modifiers stacked on the outermost canvas `Box`. Order is significant — they listen at different `PointerEventPass`es and interact through event consumption.

```
┌─ Box (CanvasScreen)
│  .nodeInteractionGestures(...)        Layer 1: Initial pass
│  .tapAndLongPressGestures(...)        Layer 2: Main pass
│  .infiniteCanvasGestures { ... }      Layer 3: Main pass
```

### Layer 1 — `nodeInteractionGestures` (Initial pass)

Active only when `selectedNodeIds` is non-empty (early-returns otherwise). All three interactive zones use **deferred-consume**: do NOT consume DOWN, poll events at Initial pass, and only consume when the pointer moves beyond `touchSlop`. If the pointer lifts (or another layer consumes) before slop is exceeded, exit without consuming so the Main pass can handle tap/long-press.

Priority order on DOWN:

1. **Resize handle hit** → deferred-consume; on slop → `dragLoop` → `onResizeDrag`.
2. **Rotation handle hit** → deferred-consume; on slop → `positionLoop` → `onRotationDragPosition`.
3. **Selected node body hit** → deferred-consume; on slop → `dragLoop` → `onDrag`.
4. **No hit** → fall through (no consumption).

The deferred-consume rule is what enables tap-on-selected (collapse multi-select) and long-press-on-selected (re-anchor the context menu on that node). It also matters for handles specifically: the handle touch radius (48 px) often extends over a node's body, so an immediate-consume on a handle hit would silently swallow taps that the user intends as "select this one node from the group."

### Layer 2 — `tapAndLongPressGestures` (Main pass)

Single coroutine combining tap, double-tap, and long-press detection. Detail in `TapAndLongPressGestureDetector.kt`. Key invariants:

- Phase 1 (long-press timeout, 400 ms): if pointer lifts during the timeout, the UP **must be consumed and recorded** (`sawUp = true`) before returning. Skipping this caused first-tap detection to defer to the next gesture.
- Long-press handler returns `Boolean` — `true` consumes the rest of the gesture via `consumeUntilAllUp()`; `false` falls through to the rect-select drag loop.
- `consumeUntilAllUp()` MUST read `allUp` from `event.changes` **before** calling `consume()`. `PointerInputChange.changedToUp()` returns `false` for consumed changes; reading the flag after consume makes the loop hang forever, which leaves `awaitEachGesture` stuck and silently swallows every subsequent gesture.

### Layer 3 — `infiniteCanvasGestures` (Main pass)

Two-or-more-finger camera pan / pinch-zoom / rotate. Single-finger events pass through unchanged.

## 6. Open Issues

- **Single-tap result has a ~300 ms delay** because Phase 3 of `tapAndLongPressGestures` waits `DOUBLE_TAP_MS` to disambiguate from double-tap. Inherent to the gesture design; could be tuned if it becomes a complaint.
- **Selection mutations are not undoable.** `SelectNode`, `AddNodeToSelection`, `AddNodesToSelection`, `ToggleNodeSelection`, `DeselectAll`, `SelectNodesInRect` do not push `CanvasCommand` entries. Only structural mutations (move, resize, rotate, add, delete, duplicate) are undoable.

## 7. Mode Interaction

The gesture stack is **selection-aware**, not directly mode-aware. `CanvasAction.SetMode(target)` clears `selectedNodeIds` (and `groupSelectionTransform`, `selectionRect`) whenever `target != Edit`. Every layer then behaves correctly without per-mode branches inside the detectors:

| Layer | Edit | View / Presentation |
|-------|------|---------------------|
| 1 — `nodeInteractionGestures` | Active on non-empty selection | Dormant — selection always empty, the modifier early-returns at the top of its `pointerInput` block |
| 2 — `tapAndLongPressGestures` | Tap → `SelectNode` / `DeselectAll`; long-press → `AddNodeToSelection` + open context-menu popup (inline overlap picker for stacked nodes); long-press-then-drag → rect-select | Tap → `FocusNode(hit.id)` if there's a hit, no-op otherwise; long-press is swallowed (returns `true`) — no overlap picker, no rect-select fallthrough |
| 3 — `infiniteCanvasGestures` | Unchanged | Unchanged — pan / pinch / rotate always work, and they cancel any in-flight focus animation |

The branch lives in `CanvasScreen.kt`'s `onTap` / `onLongPress` callbacks, not in the detector itself. This keeps the gesture detectors mode-agnostic and reusable when the `:canvas-engine` extraction lands.

**Selection chrome auto-hides in non-Edit modes** because `SelectionOverlay` and the resize / rotation handle overlays all key off `selectedNodeIds.isNotEmpty()`. No mode flag needed in those composables. The long-press context-menu popup is separately suppressed in non-Edit modes via the `state.editor.mode != Edit` guard in `CanvasScreen`.

**`FrameListBottomSheet` tap-to-focus works in both Edit and View** — the row-click handler dispatches `FocusNode(frameId)` and dismisses the sheet, regardless of mode. The TopBar toggle currently cycles Edit ↔ View; `Presentation` is reachable only via `SetMode` programmatically (reserved for a future Present surface).

See [navigation.md § Animated Frame Focus](navigation.md#animated-frame-focus) for what `FocusNode` does after dispatch.

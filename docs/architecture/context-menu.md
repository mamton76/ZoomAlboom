# Context Menu

> Related: [selection](selection.md) | [navigation](navigation.md) | [appearance](appearance.md) | [TODO § 15](../todo.md#15-context-menu)

**Status — committed (2026-05-18), pending implementation.** The shipped gesture rule is still the one described in [selection.md § 2](selection.md#2-gesture-mapping); the gesture-rule rewrite is scheduled as [todo.md § 15.4](../todo.md#154-gesture-rule-rewrite-lands-first-before-the-popover) and will update `selection.md § 2` when it lands. See [§ 7 — Migration from current toggle behavior](#7-migration-from-current-toggle-behavior) below for what changes.

The context menu is the transient popover that appears on long-press. It is distinct from `ContextualActionBar`, which is a persistent strip rendered while a selection is non-empty.

The core design choice: **long-press is a two-phase gesture**. Phase one resolves the selection (add or keep); phase two opens a popover scoped to the resulting selection plus an *anchor* — the node the user actually long-pressed. Resolving selection first means the menu always knows what its actions apply to; carrying the anchor means the menu can still offer per-object operations like "remove this from the selection" even when the selection is a group.

---

## 1. Concept

A long-press has two outcomes the user wants to express, and they tend to come together:

1. *"This is the object (or these are the objects) I want to act on."* — selection change.
2. *"Now show me what I can do with it."* — open a menu.

Today's gesture rule conflates step 1 with toggling and never reaches step 2. The proposal:

> **Long-press always opens the context menu, after selection has been resolved.**
> Drag-after-long-press still starts rect-select and does NOT open the menu (drag = "I want to keep selecting," lift = "I'm done selecting, show me my options").

This unifies a single discoverable affordance ("long-press = menu") with the existing selection semantics. The removed-by-this-proposal behavior — long-press on an already-selected node toggles it out of the selection — moves into the menu as an explicit "Remove from selection" item.

---

## 2. Menu model

```kotlin
data class ContextMenuRequest(
    val selection: Set<NodeId>,       // what most actions act on (post-resolution)
    val anchorNodeId: NodeId?,        // the long-pressed node; null for empty-space menu
    val anchorPosScreen: Offset,      // where to place the popover
)
```

- `selection` is the **post-resolution** selection — after Phase 1's add-or-keep step.
- `anchorNodeId` is the node under the finger at long-press time, even when it's already a member of `selection`.
- Menu items are split into:
  - **Selection-scoped** — operate on every node in `selection` (Align, Distribute, Create frame around selection, Delete selection, ...).
  - **Anchor-scoped** — operate on `anchorNodeId` specifically (Remove this from selection, Edit this only, ...). Shown only when `anchorNodeId != null` and `selection.size > 1` and `anchorNodeId in selection`.

The split is what answers the "how do I remove one object from a 3-object selection?" question without re-introducing the toggle gesture: that action becomes a visible, discoverable menu item rather than a hidden long-press semantics.

---

## 3. Gesture flow

Layer numbers refer to [selection.md § 5 — Gesture Stack](selection.md#5-gesture-stack).

```
DOWN
  ↓
[ Layer 1 — node interaction (selection non-empty only) ]
  · resize handle / rotation handle / selected-node-body drag
  · deferred-consume; if slop exceeded → drag loop (no menu)
  ↓ (no Layer 1 hit, or deferred without slop)
[ Layer 2 — tap & long-press detection ]
  · Phase 1 (long-press timeout, 400 ms):
      pointer lifts → it was a tap; replace selection with hit (or DeselectAll on empty)
  · Phase 2 (long-press fired):
      [a] resolve selection:
            empty-space hit:    do nothing yet
            single-node hit:    AddNodeToSelection(id)   ← new action
            stacked-node hit:   show OverlapPicker; on pick: AddNodeToSelection(picked)
            already-selected:   keep selection unchanged
      [b] wait for drag-or-lift:
            pointer moves > slop:   start rect-select drag from current position;
                                    DO NOT open menu
            pointer lifts:          dispatch OpenContextMenu(selection, anchor, posScreen)
  ↓ (two+ fingers down at any time)
[ Layer 3 — infinite canvas gestures ]
  · pan / pinch / rotate; cancels any in-flight Phase 2
```

Three invariants the implementation must keep:

1. **Selection action fires on long-press detection, not on UP.** This is what gives the haptic moment a meaning. If the user then drags into rect-select, the selection change still stands and the rect-select union grows from there.
2. **Menu opens on UP, only if no drag occurred.** Drag = "still selecting" path, no menu.
3. **`AddNodeToSelection` is idempotent.** Long-pressing an already-selected node leaves `selection` untouched. The "I want to act on just this one" intent is served by the **`Edit this only`** anchor-scoped menu item, not by the gesture.

---

## 4. Menu content by selection type

The menu is a pure function of `(selection, anchorNodeId, nodeTypes)`. Sections below are *possible actions*; the renderer will hide irrelevant ones for the concrete selection.

### 4.1 `selection.size == 0` — long-press on empty space, finger lifts

Triggered by long-press on empty space *without* dragging. (Drag = rect-select, no menu.)

- `Add photo`
- `Add frame`
- `Add text`
- `Paste`
- `Add guideline` (post §14)

### 4.2 `selection.size == 1` — single media

Anchor is the same node. No anchor section needed.

Per-concept popups (each opens a focused editor — `to_discuss.md`-decided popup direction):

- `Edit media` — replace source, intrinsic metadata
- `Edit clip shape` — RoundedRect / PerCornerRoundedRect / Ellipse (appearance.md § 12)
- `Edit alpha mask` — Image / LinearGradient / RadialGradient / Procedural (appearance.md § 12)
- `Edit overlays` — list editor for `appearance.overlays` (appearance.md § 13)
- `Edit crop` — media-specific
- `Edit color adjustments` — media-specific
- `Edit border` / `Edit shadow` / `Edit opacity`
- `Edit frame decoration` — media-specific
- `Replace media`
- `Duplicate` / `Delete`

### 4.3 `selection.size == 1` — single frame

- `Edit frame` — title, bounds, …
- `Edit frame background` — frame-specific
- `Edit clip shape` / `Edit alpha mask` / `Edit overlays` / `Edit border` / `Edit shadow` / `Edit opacity` — base-level concepts, same popups as in § 4.2 (different host node)
- `Edit title style` — frame-specific
- `Navigate to frame`
- `Edit frame contents` — opens the frame as an editing context (post-MVP)
- `Duplicate frame` / `Delete frame`

### 4.4 `selection.size >= 2` — group (any mix of media + frames)

There is **no** "Edit common appearance" umbrella popup. The per-concept popups handle multi-selection natively (see [appearance.md § 14](appearance.md#14-multi-selection-editing)). Menu items shown:

Base-level concepts (always shown — work on every selected node):

- `Edit clip shape` / `Edit alpha mask` / `Edit overlays` / `Edit border` / `Edit shadow` / `Edit opacity`

Type-specific concepts (shown only when selection is **homogeneous** in the right type):

- All-media selection → `Edit crop` / `Edit color adjustments` / `Edit frame decoration`
- All-frame selection → `Edit frame background` / `Edit title style`

Multi-only group actions:

- `Create frame around selection` — see [§ 5](#5-create-frame-around-selection-net-new-action)
- `Align` → submenu: left / center-x / right / top / middle-y / bottom
- `Distribute` → submenu: horizontally / vertically
- `Duplicate selection`
- `Delete selection`
- `Clear selection`

Anchor-scoped (shown only when `anchorNodeId in selection`):

- `Remove this from selection` — selection becomes `selection - anchorNodeId`. If that leaves one element, the menu would re-render as a single-selection menu, but the menu closes on action; the new singleton selection persists.
- `Edit this only` — selection becomes `{anchorNodeId}`; opens the single-object editor for the anchor.

---

## 5. Create frame around selection (net-new action)

Worth calling out separately because it's both the highest-value group action and the cleanest standalone slice — no dependency on the rest of the menu redesign.

Behavior:

- Compute the AABB of every node in `selection` (use `renderW / renderH`, same rule as `AlignDistribute`).
- Inflate by a configurable `framePadding` (world units; default tuned at implementation time).
- Create a new `CanvasNode.Frame` at that rect with a default title and `FrameAppearance` defaults.
- Insert into the scene graph as a sibling of the bounding contents (z-order: below the contents, so members render on top of the frame background).
- After creation, frame membership recomputes via the standard geometric containment ([frame-membership.md](frame-membership.md)) — the selected nodes become members through normal intersection, no explicit membership wiring.
- New frame becomes the selection.
- One `Compound` undo entry per invocation (depends on [TODO § 2](../todo.md#2-undoredo)).

The natural workflow this enables:

```
Add photos → arrange them → select them → "Create frame around selection"
```

This should land independent of the long-press redesign — it can hang off `ContextualActionBar` initially and migrate into the context menu when the rest lands.

---

## 6. Interaction with `ContextualActionBar`

`ContextualActionBar` stays. The two surfaces have different jobs:

| | `ContextualActionBar` | Context menu |
|---|---|---|
| When visible | Whole time `selection` is non-empty | Transient — opens on long-press, closes on outside-tap or item-tap |
| Position | Fixed (bottom or top, per layout) | At touch point |
| Triggered by | Selection becoming non-empty | Long-press gesture |
| Action set | Most-common 3–5 actions only | Full action set, with submenus |
| Reflects anchor? | No — selection only | Yes — anchor adds "Remove this / Edit this only" |

Both read the same selection state; both dispatch the same `CanvasAction` variants. The menu is the discoverable "everything I can do" surface; the bar is the always-visible shortcut for the actions the user does constantly.

---

## 7. Migration from current toggle behavior

This proposal changes the row in [selection.md § 2](selection.md#2-gesture-mapping) that says:

| Gesture | Result | Action dispatched |
|---|---|---|
| Long-press on a single node | **Toggle** — add if absent, remove if present | `ToggleNodeSelection(id)` |

To:

| Gesture | Result | Action dispatched |
|---|---|---|
| Long-press on a node | **Add (or keep) + open menu on UP** | `AddNodeToSelection(id)`, then `OpenContextMenu(...)` |

The old `ToggleNodeSelection` action stays in the codebase for now but is no longer dispatched by gestures — it remains the obvious implementation of the menu's "Remove this from selection" item (passing the anchor id).

The overlap-picker open issue ([selection.md § 6](selection.md#6-open-issues)) — "picker dispatches `SelectNode` (replace), not toggle" — resolves naturally under this proposal: the picker dispatches `AddNodeToSelection(picked)` and the menu opens scoped to the resulting multi-selection with the picked node as anchor.

When this lands, update:

- `selection.md § 2` — replace the long-press row.
- `selection.md § 6` — remove the resolved overlap-picker bullet.
- `todo.md § 15.3` — remove the "conflict with current long-press semantics" note; the proposal resolves it.

---

## 8. Open issues

- **Mask as a first-class concept distinct from crop — resolved.** `appearance.md § 12` (proposal) splits this into two composable fields: `clip: ClipShape` (geometric — RoundedRect / PerCornerRoundedRect / Ellipse) and `alphaMask: AlphaMask?` (continuous-alpha — image / linear gradient / radial gradient / procedural source). Menu item `Edit mask / crop` accordingly splits into three concept-popups once the popup direction lands (`to_discuss.md § 1.3`): `Edit clip shape`, `Edit alpha mask`, `Edit crop`. Until then, a single `Edit mask / crop` entry stays.
- **Multi-selection appearance editing — resolved.** Rules captured in [appearance.md § 14](appearance.md#14-multi-selection-editing): no "Edit common appearance" umbrella; per-concept popups handle multi-edit natively; Figma-style "Mixed" label for indeterminate fields; type-specific popups gated by homogeneous selection; preset application is type-scoped.
- **Menu dismissal interaction with rect-select.** If the menu is open and the user touches outside it, that touch should close the menu *and not* be interpreted as the start of a new rect-select drag. (Standard popover dismissal — call out so it's not skipped.)
- **Menu position when anchor is off-screen.** When the menu is requested with an anchor that lives outside the viewport (e.g. opened via keyboard shortcut on a culled selection — post-MVP), use the on-screen projection of the selection's group rect; if that's also off-screen, anchor to the viewport center.

---

## 9. Implementation order

1. **Phase 1 only — selection rule change.** Replace `ToggleNodeSelection` dispatch in Layer 2 with `AddNodeToSelection` on long-press; keep menu deferred. Verifies the gesture rewrite in isolation. Update `selection.md § 2`.
2. **Empty popover stub.** On UP after long-press, open a `Popover` at touch point. No items yet — confirms positioning and dismissal.
3. **Selection-scoped items.** Wire single-media / single-frame / group menus. `Create frame around selection` can ship here or via `ContextualActionBar` first (see § 5).
4. **Anchor-scoped items.** Add `Remove this from selection` / `Edit this only`. This is the moment the proposal becomes strictly better than the old toggle.
5. **Overlap picker → add semantics.** Closes the [selection.md § 6](selection.md#6-open-issues) open issue.
6. **Empty-space menu.** Add Photo / Add Frame / Add Text / Paste / Add Guideline.

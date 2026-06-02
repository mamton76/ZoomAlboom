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

Today's gesture rule conflates step 1 with toggling and never reaches step 2. The current behavior:

> **For a node long-press**, selection resolves on the press (topmost hit is added) and the menu opens on lift. Movement after the press is consumed by the long-press path; the menu still opens on lift.
> **For an empty-space long-press**, lifting opens the empty-space menu. If the user instead drags past touch-slop after the press, a rect-select starts from the current position and no menu opens (drag = "I want to keep selecting," lift = "show me my options").

This unifies a single discoverable affordance ("long-press = menu") with the existing selection semantics. The removed-by-this-proposal behavior — long-press on an already-selected node toggles it out of the selection — moves into the menu as an explicit "Remove from selection" item.

---

## 2. Menu model

```kotlin
data class ContextMenuRequest(
    val selection: Set<NodeId>,            // what most actions act on (post-resolution)
    val anchorNodeId: NodeId?,             // the long-pressed node; null for empty-space menu
    val anchorScreenX: Float,              // where to place the popover (screen pixels)
    val anchorScreenY: Float,
    val pickerNodes: List<CanvasNode>?,    // non-null for stacked long-press; renders the
                                           // inline checkbox picker above the menu items
)
```

- `selection` is the **post-resolution** selection — after Phase 1's add-or-keep step.
- `anchorNodeId` is the node under the finger at long-press time, even when it's already a member of `selection`. For stacked long-press it starts as the topmost hit and updates each time the user toggles a different picker row.
- `pickerNodes` is the full list of overlapping hits when long-press landed on a stack; the popup renders a checkbox row per node above the menu items, with the anchor row highlighted.
- Menu items are split into:
  - **Selection-scoped** — operate on every node in `selection` (Align, Distribute, Create frame around selection, Delete selection, ...).
  - **Anchor-scoped** — operate on `anchorNodeId` specifically (Remove this from selection, Edit this only, ...). Shown only when `anchorNodeId != null` and `selection.size > 1` and `anchorNodeId in selection`.

**Anchor visual feedback.** The anchor gets two kinds of visual treatment while the popup is open:

- **In the popup picker row** (stacked long-press only): semibold label + `secondaryContainer`-tinted background on the matching row, so the user sees which checkbox is "the anchor right now."
- **On the canvas itself**: `SelectionOverlay` draws an outer halo around the anchor node — a translucent `AccentCyan` ring offset 6 px (screen-space) outside the regular selection border, stroke 4 px. The halo follows when the anchor changes (e.g. user toggles a different picker row) and disappears when the popup closes.

The canvas-side highlight is driven by `CanvasState.contextAnchorNodeId`, mirrored from `ContextMenuRequest.anchorNodeId` via a `LaunchedEffect` in `CanvasScaffold`. The popup-side highlight reads directly from `request.anchorNodeId`.

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
            single-node hit:    AddNodeToSelection(id)
            stacked-node hit:   AddNodeToSelection(topmost); remember the full hit
                                list as picker rows for the menu (see § 4)
            already-selected:   keep selection unchanged (AddNodeToSelection is idempotent)
      [b] wait for lift (or, on empty space, drag-or-lift):
            node long-press: post-press movement is consumed (no drag escape).
                Pointer lifts → dispatch OpenContextMenu(selection, anchor,
                posScreen, pickerNodes).
            empty-space long-press:
                pointer moves > slop:   start rect-select drag from current position;
                                        DO NOT open menu
                pointer lifts:          dispatch OpenContextMenu(emptySelection, null,
                                        posScreen, null)
  ↓ (two+ fingers down at any time)
[ Layer 3 — infinite canvas gestures ]
  · pan / pinch / rotate; cancels any in-flight Phase 2
```

Three invariants the implementation must keep:

1. **Selection action fires on long-press detection, not on UP.** This is what gives the haptic moment a meaning. For an empty-space long-press that then drags into rect-select, no selection change has fired yet — the rect-select is the selection action.
2. **Menu opens on UP.** For a node long-press, post-press movement is consumed so the menu always opens on lift. For an empty-space long-press, the menu opens on lift only if the user did not drag past touch-slop (drag → rect-select instead, no menu).
3. **`AddNodeToSelection` is idempotent.** Long-pressing an already-selected node leaves `selection` untouched. The "I want to act on just this one" intent is served by the **`Edit this only`** anchor-scoped menu item, not by the gesture.

### Dismissal rules

When the popup is open, canvas gestures behave differently — discrete gestures (tap, double-tap, drag-start) dismiss the popup *without* running their normal action so users can close it without losing selection. Continuous gestures (camera pan/pinch, node drag/resize/rotate) dismiss the popup *and* still execute, because the gesture is itself the user's edit intent and there's no discrete side-effect to undo. Anchor-scoped management actions (`Remove this from selection`, picker checkbox toggle) keep the popup open so the user can continue managing the selection.

| Source (popup open) | Effect |
|---|---|
| Tap (anywhere outside the popup) | Dismisses popup. Suppresses the normal `SelectNode` / `DeselectAll`. Selection unchanged. |
| Double-tap (outside the popup) | Dismisses popup. Suppresses the normal camera reset. |
| Drag-start (rect-select path) | Dismisses popup. Suppresses the normal rect-select on the same gesture. User releases and drags again to start a fresh rect-select. |
| Camera pan / pinch / rotate (two-finger, Layer 3) | Dismisses popup on first event. Camera updates **proceed** — the user's pinch/pan/rotate of the canvas continues normally. |
| Node body drag / resize / rotation handle (Layer 1) | Dismisses popup. Interaction **proceeds** — the user's move/resize/rotate of the selection continues normally. |
| Long-press another node | Replaces the popup in-place with a new request (selection-resolution + anchor for the new target). Single gesture, no intermediate dismiss step. Selection is not lost; the new node is additively added. |
| Top-level chrome tap (top bar buttons, FAB) | Dismisses popup, then runs the chrome action. Wired via a single `dismissPopupAnd { … }` helper in `CanvasScaffold` so the rule lives in one place. The one exception is the top bar's two **frame-edit toggles** (`🔗` transform-with-contents, `🔄` rebind-after-edit) — selection-scoped gesture modifiers, not new editor commands, so they leave the popup open (the popup is still contextual to the same selection). |
| Back-press | Dismisses popup. Implemented by a `BackHandler(enabled = contextMenuRequest != null)` in `CanvasScaffold` — *not* by `Popup.dismissOnBackPress`, because the popup uses `focusable = false` and a non-focusable popup window never receives key events. |
| Menu item tap (non-anchor) | Runs the item's action; popup dismisses. |
| `Remove this from selection` | Removes the anchor from selection; **popup stays open**. If the removed node was the anchor, anchor clears (`anchorNodeId = null`) and anchor-scoped items disappear. See § 4.4. |
| Picker checkbox tap | Toggles selection membership for that node. If the toggled-off node was the anchor, anchor clears; otherwise the toggled node becomes the new anchor. Popup stays open. |

The `CanvasScreen` Composable takes `isContextMenuOpen: Boolean`, wraps it with `rememberUpdatedState` (the gesture coroutines from `pointerInput(Unit)` capture lambdas once at first composition and never restart, so a plain primitive parameter would freeze), and gates the gesture callbacks on the live value. `Popup.focusable = false` keeps long-press elsewhere working as a single gesture; the cost is that the popup can't intercept back-press itself, which is why back is handled in the scaffold instead.

---

## 4. Menu content by selection type

The menu is a pure function of `(selection, anchorNodeId, nodeTypes)`. Sections below are *possible actions*; the renderer will hide irrelevant ones for the concrete selection.

**Rendering convention (decided 2026-06-02).** MVP menus use **dividers between groups** (no section headers, no drill-down submenus). Compact action sets that share a domain (z-order, frame membership) render as a single **inline action row** — a row of icon buttons inside the menu — to keep four sibling actions in one tap without expanding the menu vertically. Two inline rows are defined today:

| Row | Layout (left → right) | Notes |
|---|---|---|
| **Z-order row** | `⏫ Bring to Front` · `↑ Bring Forward` · `↓ Send Backward` · `⏬ Send to Back` | Material order. Single-selection only today; multi-selection semantics in [z-order.md § 3](z-order.md#3-multi-selection-semantics). |
| **Frame-membership row** | `📌 Pin` · `🔓 Detach` · `🔄 Auto` | Visibility identical to today's bar. Direct dispatch for unambiguous target; opens `FrameTargetPickerDialog` when multiple frames are in the selection. |

Future growth into the § 10 category list (Transform / Appearance / Mask / Vector / Frame) is committed but deferred: once any selection type accumulates ~15+ items, the renderer adds section headers grouped by those categories. Until then, dividers + inline rows scale.

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
- `Edit decoration` — media-specific (was `Edit frame decoration` before the rename)
- `Replace media`

Group operations:

- `Duplicate`
- **Z-order row** — `⏫ ↑ ↓ ⏬` (Front / Forward / Backward / To Back)
- **Frame-membership row** — `📌 Pin · 🔓 Detach · 🔄 Auto` (visible when the media is selected alongside ≥ 1 frame; Auto shown only when an override exists for this media)
- `Delete`

### 4.3 `selection.size == 1` — single frame

Per-concept popups:

- `Edit frame` — title, bounds, …
- `Edit frame background` — frame-specific
- `Edit clip shape` / `Edit alpha mask` / `Edit overlays` / `Edit border` / `Edit shadow` / `Edit opacity` — base-level concepts, same popups as in § 4.2 (different host node)
- `Edit title style` — frame-specific
- `Edit color adjustments` / `Edit caption` — base-level concepts promoted from media (appearance.md § 1, § 12)

Navigation:

- `Navigate to frame`
- `Edit frame contents` — opens the frame as an editing context (post-MVP)

Group operations:

- `Duplicate frame`
- **Z-order row** — `⏫ ↑ ↓ ⏬`
- **Frame-membership row** — `📌 Pin · 🔓 Detach · 🔄 Auto` (visible when this frame is selected alongside ≥ 1 other node; this frame acts as the *target* for Pin/Detach against the other selected nodes — or, when ≥ 2 frames are selected, opens `FrameTargetPickerDialog` to pick which frame is the target)
- `Delete frame`

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
- **Z-order row** — `⏫ ↑ ↓ ⏬` (blocked on [todo.md § 13.5](../todo.md#135-multi-selection-z-order); row hidden until multi-selection z-order ships)
- **Frame-membership row** — `📌 Pin · 🔓 Detach · 🔄 Auto` (when applicable to the selection; opens `FrameTargetPickerDialog` for the ambiguous-target case)
- `Delete selection`
- `Clear selection`

Anchor-scoped (shown only when `anchorNodeId in selection`):

- `Remove this from selection` — selection becomes `selection - anchorNodeId`. **Popup stays open** (selection-management action; the user is likely to keep curating the selection from the same surface).
  Anchor behavior is **Option A**: the anchor clears (`anchorNodeId = null`) since the removed node *was* the anchor. The anchor halo on the canvas disappears, the anchor-scoped block of menu items (`Remove this from selection`, `Edit this only`) is hidden, and the menu re-renders as either a single-selection menu (1 remaining) or the group menu without anchor items (≥ 2 remaining). The user can pick a new anchor by tapping a picker row (stacked long-press case) or by long-pressing a new node.
  The same rule applies to the inline overlap-picker checkboxes: if the user unchecks the row that is currently the anchor, the anchor clears; otherwise the toggled-on row becomes the new anchor.
- `Edit this only` — selection becomes `{anchorNodeId}`; popup dismisses; opens the single-object editor for the anchor.

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

## 6. `ContextualActionBar` removal

**Decided 2026-06-02.** `ContextualActionBar` is removed entirely; the popup becomes the single surface for selection-scoped actions. Supersedes the older [todo.md § 5c.3](../todo.md) plan ("bar shrinks back to Delete / Duplicate / Open Properties") and the previous "two-surface" framing this section used to describe.

What was on the bar and where it lives now:

| Bar action | New home |
|---|---|
| Delete | Per-selection-type menus (§ 4.2 / 4.3 / 4.4), bottom of the list |
| Duplicate | Per-selection-type menus, group-operations block |
| Background (frame) | `Edit frame background` per-concept popup (§ 4.3) |
| Appearance (media) | `Edit appearance` per-concept popup (§ 4.2) |
| Pin / Detach / Auto | **Frame-membership inline row** (§ 4 rendering convention) |
| ToFront / Forward / Backward / ToBack | **Z-order inline row** (§ 4 rendering convention) |

**Accepted regression:** Delete loses one-tap access (was persistent on the bar; now requires long-press → tap Delete). The team accepts this — Delete isn't high-frequency in this app, and one-tap Undo from the top bar limits the cost of accidental deletes. Revisit if usage indicates otherwise.

**Implementation status:** popup item set + inline rows are not wired yet. The bar still ships today; its onClick handlers route through `dismissPopupAndAccept` (CanvasScaffold.kt) so the popup dismissal contract is already correct.

Removal lands in two slices:

1. **[todo.md § 25](../todo.md#25-editor-action-catalog)** — typed `EditorAction` catalog replaces stringly-typed `onAction(label)` dispatch. Bar and popup both migrate to consume `EditorActionCatalog.visibleByCategory(ctx)`. Foundational refactor; no UI change.
2. **[todo.md § 15.5](../todo.md#155-contextualactionbar-removal--inline-rows)** — delete the bar composable + call site; add z-order and frame-membership inline-row composables that consume the catalog by category. Pure UI work after § 25 lands.

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
5. **Overlap picker inline in the popup.** Closes the [selection.md § 6](selection.md#6-open-issues) open issue. The separate `OverlapPickerDialog` is removed; stacked-long-press now opens the context menu with a checkbox row per overlapping node above the menu items. The anchor row is highlighted (semibold + tinted background); toggling a row dispatches `ToggleNodeSelection` and updates the anchor.
6. **Empty-space menu.** Add Photo / Add Frame / Add Text / Paste / Add Guideline.

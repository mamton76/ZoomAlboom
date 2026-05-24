# Z-Order Operations

> Related: [selection](selection.md) | [rendering](rendering.md) | [data-model](data-model.md) | [undo-redo](undo-redo.md)

**Status — decided 2026-05-24.** Single-selection actions ship today (`todo.md § 13.5`); multi-selection semantics decided, implementation tracked in `todo.md § 13.5`.

Z-order operations mutate `Transform.zIndex` on one or more nodes. The four operations are `BringToFront`, `SendToBack`, `BringForward`, `SendBackward`. This doc specifies what each operation does for single and multi-selection input. Render-loop behavior (sort, paint order) lives in [rendering.md § 4](rendering.md#4-viewport-culling).

---

## 1. The four operations

| Operation | Direction | Magnitude |
|---|---|---|
| `BringToFront` | up | to the top |
| `BringForward` | up | one step |
| `SendBackward` | down | one step |
| `SendToBack` | down | to the bottom |

"Top" = highest `zIndex` (paints last, on top visually). "Step" = swap with the immediate neighbor along the z-axis.

---

## 2. Single-selection semantics (shipped)

For a single selected node `n`:

- **`BringToFront`** — `n.zIndex = max(zIndex) + 1`. No-op if `n` is already on top.
- **`SendToBack`** — `n.zIndex = min(zIndex) - 1`. No-op if `n` is already at the bottom.
- **`BringForward`** — swap `n.zIndex` with the next-higher neighbor's. No-op if already on top.
- **`SendBackward`** — swap `n.zIndex` with the next-lower neighbor's. No-op if already at the bottom.

All four produce one `CommandKind.REORDER` undo entry capturing the affected nodes (one or two, depending on the operation).

---

## 3. Multi-selection semantics

Two distinct rules. They differ because the user's mental model of "move to extreme" is well-defined for a group, while "move one step" is not.

### 3.1 `BringToFront` / `SendToBack` — block-extreme

The selection moves to the extreme **as a contiguous block, preserving its internal relative z-order**. Unselected nodes that were above/below the selection do not change their relative order among themselves.

**Trace.** `z = A(1), B(2), C(3), D(4), E(5)`, selection = `{B, D}`.

| Operation | Result |
|---|---|
| `BringToFront` | `A(1), C(2), E(3), B(4), D(5)` — selection lifted to top as a block; internal order `B < D` preserved |
| `SendToBack` | `B(1), D(2), A(3), C(4), E(5)` — selection sunk to bottom as a block; internal order preserved |

### 3.2 `BringForward` / `SendBackward` — independent-with-skip

Each selected node moves **one step** in the requested direction, **treating other selected nodes as transparent** when finding the swap target. This is Figma's behavior.

**Why "with skip" matters.** If two adjacent nodes are both selected, "Bring Forward" on each independently would just swap them with each other and accomplish nothing. Skipping over other selected nodes makes the operation always make progress (selection moves up by one step relative to the *unselected* nodes around it).

**Trace 1 — sparse selection.** `z = A(1), B(2), C(3), D(4), E(5)`, selection = `{B, D}`.

| Operation | Result | Mechanism |
|---|---|---|
| `BringForward` | `A(1), C(2), B(3), E(4), D(5)` | B → next unselected above is C(3), swap. D → next unselected above is E(5), swap. |
| `SendBackward` | `B(1), A(2), D(3), C(4), E(5)` | B → next unselected below is A(1), swap. D → next unselected below is C(3), swap. |

**Trace 2 — contiguous selection.** `z = A(1), B(2), C(3), D(4), E(5)`, selection = `{B, C}`.

| Operation | Result | Mechanism |
|---|---|---|
| `BringForward` | `A(1), D(2), B(3), C(4), E(5)` | B's next unselected above is D (C is selected, skip). C's next unselected above is E. But D is already grabbed for B's swap — both selected nodes move as a unit one step past D. Internal order `B < C` preserved. |

For a contiguous selection, the operation degenerates to "the block moves one step past the next unselected neighbor" — *without* requiring an explicit collapse-then-bump (which the rejected "block move" alternative would have needed).

### 3.3 No-op at extreme

For MVP, the operation is a no-op when there is nothing to swap with (all selected nodes are already at the front for `BringForward` / `BringToFront`, or all at the back for the inverse). Buttons are not greyed out — they dispatch and do nothing. Grey-out per-state is a follow-up.

### 3.4 Compound undo

Every multi-selection z-order command produces **one** `CommandKind.REORDER` undo entry, snapshotting all affected nodes (selected + any unselected nodes whose `zIndex` changed as a side effect of the swap chain). Matches the `DuplicateSelection` / `DeleteSelection` precedent.

---

## 4. Implementation shape

Z-order logic lives as pure functions in `core/math/ZOrder.kt`:

```kotlin
/**
 * Returns the new zIndex for every node whose value changes.
 * Empty map = no-op (selection already at the extreme / no swap possible).
 * Caller (CanvasViewModel) applies the diff and records one Compound undo.
 */
object ZOrder {
    fun bringSelectionToFront(allNodes: List<CanvasNode>, selection: Set<NodeId>): Map<NodeId, Float>
    fun sendSelectionToBack(allNodes: List<CanvasNode>, selection: Set<NodeId>): Map<NodeId, Float>
    fun bringSelectionForward(allNodes: List<CanvasNode>, selection: Set<NodeId>): Map<NodeId, Float>
    fun sendSelectionBackward(allNodes: List<CanvasNode>, selection: Set<NodeId>): Map<NodeId, Float>
}
```

`CanvasViewModel` applies the returned diff and records one `CommandKind.REORDER` undo entry. The existing single-id actions (`CanvasAction.BringToFront(nodeId)` etc.) become thin wrappers — `bringSelectionToFront(allNodes, setOf(nodeId))` — or are replaced by new `CanvasAction.BringSelectionToFront(selection)` variants. Existing tests for single-node z-order remain valid.

---

## 5. Interaction with frame membership

Z-order changes do **not** trigger frame-membership recompute. [`FrameMembershipUseCase`](frame-membership.md) computes membership from geometry (containment) plus per-frame overrides; z-order is purely visual stacking. A node's z-index can change freely without affecting which frame it belongs to.

---

## 6. Open Questions

- **Greyed-out state per-direction.** MVP says no-op when at extreme; follow-up evaluates per-button enablement (e.g. "Bring Forward" disabled when every selected node is already at the front). Adds per-state evaluation cost in the action bar; defer until UX feedback justifies it.
- **Z-order in the context menu.** Should the popup expose all four actions, or only `BringToFront` / `SendToBack`? Deferred to `to_discuss.md § 2` (bar → popup migration).
- **Layered frame interaction.** Layered frames (`FrameAppearance.overlays` non-empty) emit two paint events (`LayeredFrameSurface` at `frame.zIndex`, `LayeredFrameOverlay` past the highest member's z — see [rendering.md § 6b](rendering.md#6b-layered-frame-rendering)). Z-order operations on the frame mutate `frame.zIndex` directly; the Overlay event recomputes its sort key on the next paint loop. No special handling required. Confirm with a screenshot diff when multi-select lands.

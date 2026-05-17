# Frame Membership

> Related: [data-model](data-model.md) | [decisions](decisions.md) | [selection](selection.md) | [undo-redo](undo-redo.md)

How content nodes (photos, videos, text, stickers, widgets) relate to frames.

## The core problem

A node can be **physically inside** a frame's bounding box but **logically outside** the frame — and vice versa. Pure geometric containment cannot represent user intent: if membership is computed only from intersection, the user cannot truly "detach" a node from a frame (it re-attaches the moment it intersects).

We need a model that supports:
1. Automatic membership derived from geometry (the default).
2. Manual overrides that survive geometry changes (pin / detach).
3. "Frozen" membership after a frame edit, where the user chose to preserve the logical set across a transform.

## Decision

**Geometry is a source of *proposals*, not the source of *truth*. The source of truth is a per-frame override map; geometry fills the gaps.**

Membership is computed by:

```text
effectiveMembers(frame) =
    geometricallyInside(frame)
    minus { nodeIds with override.state = Excluded }
    plus  { nodeIds with override.state = Included }
```

Overrides live on the frame itself. No top-level binding table.

## Domain model

```kotlin
@Serializable
data class Frame(
    /* … existing fields … */
    val overrides: Map<String, FrameMembershipOverride> = emptyMap(),  // nodeId -> override
)

@Serializable
data class FrameMembershipOverride(
    val state: MembershipState,
    val origin: MembershipOrigin,
)

enum class MembershipState { Included, Excluded }

enum class MembershipOrigin {
    User,              // explicit pin / detach by user action
    BatchImport,       // node was imported into a selected frame
    Wizard,            // wizard or AI-generated content, accepted
    RebindSuppressed,  // preserved by a frame edit with rebindAfterEdit = false
}
```

Node-side:

```kotlin
sealed class CanvasNode {
    abstract val isFrameBindable: Boolean   // false for backgrounds, camera-locked, editor-only nodes
}
```

Default `isFrameBindable = true`. Album backgrounds and frame backgrounds are already separate from `CanvasNode`; widgets and decorations may opt out later.

`Frame.containsNodeIds` (currently in the data model but never wired) is **removed** in favour of computed membership.

### Why no `Geometry` origin

An entry in `overrides` is always an override. Absence of an entry means "geometry decides." Storing `Included + Geometry` for every geometric member would mean every node drag rewrites overrides on every frame the node intersects — pointless churn for no semantic gain.

### Why per-frame and not a top-level table

A top-level `List<FrameNodeBinding>` was considered. It was rejected because:
- It introduces orphans on entity delete in two directions (frame and node).
- Composite uniqueness `(frameId, nodeId)` is not enforced by a `List`.
- Every node drag potentially mutates the global list → bigger undo payloads on the hottest path.
- Hot membership queries become scans unless a parallel index is maintained.
- Per-frame `Map<String, _>` enforces uniqueness for free and cascades automatically.

If a future workflow genuinely needs cross-album bulk queries on bindings (none exists today), graduating a per-frame map to a top-level table is a mechanical refactor.

## Frame edit semantics

Frame editing offers two transient toggles in the contextual action bar:

```kotlin
data class FrameEditOptions(
    val transformContents: Boolean = true,    // move/scale/rotate members along with the frame
    val rebindAfterEdit: Boolean = true,      // recompute geometry after the edit
)
```

The four combinations:

| `transformContents` | `rebindAfterEdit` | Behaviour |
|----|----|---|
| `true`  | `true`  | Frame and its current members transform together; afterwards geometry decides membership. |
| `true`  | `false` | Frame and its current members transform together; logical membership is preserved (suppression overrides written for any geometry-vs-logical mismatch). |
| `false` | `true`  | Only the frame transforms; membership is recomputed from new geometry. |
| `false` | `false` | Only the frame transforms; logical membership is preserved (suppression overrides written). |

The hard case is `(_, false)`. After the edit:
- `dropped = beforeActual − afterPotential` → write `overrides[id] = (Included, RebindSuppressed)`.
- `newlyCaptured = afterPotential − beforeActual` → write `overrides[id] = (Excluded, RebindSuppressed)`.

Geometry recompute runs on `FinishInteraction`, not mid-gesture. Live mid-drag recompute is deferred (visual hint nice-to-have).

`FrameEditOptions` is transient per gesture, not persisted on the frame. Persisting it would create "why doesn't moving this frame do what I expect" surprises that depend on invisible state.

### Multi-frame selection

`transformContents` and `rebindAfterEdit` apply to **every** Frame in the selection, not just one:

- **At `BeginInteraction`:** the gesture set is augmented with the union of every selected frame's effective members. All move/resize/rotate gestures use this expanded set.
- **At `FinishInteraction`:** rebind suppression runs **per frame** — each edited frame independently computes its `beforeActual` / `afterPotential` and writes its own `RebindSuppressed` overrides. A single compound `CanvasCommand` captures all the changes (frame transforms + member transforms + every edited frame's overrides) atomically, so undo restores everything together.
- **Rotation pivot.** For move-with-content the same world delta applies to every node — no pivot concern. For resize the pivot comes from the gesture detector (handle position). For rotate the pivot is the **user-selection centroid** (computed from `selectedNodeIds`, not the augmented set), so members orbit with their frames rather than around a shifted point.

## Operations

Three pure-function use cases under `domain/usecase/`:

```kotlin
class FrameMembershipUseCase {
    /** Geometry ∪ Included overrides − Excluded overrides. */
    fun effectiveMembers(frame: Frame, nodes: List<CanvasNode>): Set<String>
}

class FrameOverrideUseCase {
    /** Pin / Detach — writes (state, origin) overrides for [nodeIds]. Mutually
     *  exclusive: Included replaces a prior Excluded entry and vice versa. */
    fun applyOverride(frame: Frame, nodeIds: Set<String>,
                      state: MembershipState, origin: MembershipOrigin): Frame

    /** Clear — drops override entries; membership reverts to pure geometry. */
    fun clearOverrides(frame: Frame, nodeIds: Set<String>): Frame
}

class ApplyFrameEditUseCase {
    /** Post-gesture rebind suppression. When rebindAfterEdit=false, writes
     *  RebindSuppressed overrides for the geometry-vs-logical diff. */
    fun applyFrameEdit(frameBefore: Frame, frameAfter: Frame,
                       allNodesBefore: List<CanvasNode>,
                       allNodesAfter: List<CanvasNode>,
                       options: FrameEditOptions): Frame
}
```

All three return the next `Frame` (or unchanged input on no-op). The ViewModel layer wraps each call with a `CanvasCommand` (`SET_FRAME_OVERRIDES` kind) for undo/redo.

User actions are dispatched through `CanvasAction.PinToFrame` / `DetachFromFrame` / `ClearFrameOverrides`. Each takes `(frameId, nodeIds: Set<String>)` — the target frame plus the candidates.

Invariants:
- Pin and Detach are mutually exclusive per `(frame, node)` pair. Pinning a node that's already excluded by the user clears the exclusion (and vice versa).
- Pin is **not** a no-op when the node is already geometrically included with no override — `applyOverride` writes `(Included, User)` defensively so the user's intent survives future geometry changes.
- Clear is a no-op (returns the same instance) when no entries exist for the requested ids; the ViewModel detects this and skips the command commit.

## Undo / redo

Membership changes ride existing `CanvasCommand` paired before/after lists.

| Operation | Snapshot |
|----|----|
| Pin / Detach | Frame's `overrides` before and after. One frame, small payload. |
| Frame edit `(true, _)` | Frame transform + all moved nodes' transforms + frame's `overrides` (`before` and `after`). One compound command. |
| Frame edit `(false, _)` | Frame transform + frame's `overrides`. No node transforms changed. |
| Clear suppressed overrides | Frame's `overrides` before and after. |
| Delete node | Existing `before` snapshot of the deleted node + every affected frame's `overrides` snapshot (scrubbed of the node's ID in `after`). |
| Delete frame | Existing `before` snapshot of the frame (overrides included by virtue of being a frame field). No cascade. |

Geometry recompute runs at `FinishInteraction`, so a frame edit produces exactly one command, not a stream of intermediate states.

## Edge cases

- **Node deletion.** Scrub the node ID from every frame's `overrides`. Must happen inside the same command as the node delete, so undo restores the overrides.
- **Frame deletion.** Drops its overrides with it. Members are not cascade-deleted — they fall back to being members of whichever frames still geometrically contain them.
- **Node duplication.** The new node has no overrides on any frame; it enters membership purely through geometry.
- **Node intersecting multiple frames.** Allowed; no "primary frame" concept for MVP. If "navigate to parent" arises later, define a tie-breaker then (smallest-area frame is a common heuristic).
- **Frame edit `(true, _)` and node in multiple frames.** When frame A transforms, its effective members transform too — even ones also in frame B. That's correct: the user transformed A.
- **Node member of two frames, only one is transformed.** With `transformContents=on, rebindAfterEdit=off`, frame A is moved → the node moves with A → frame B (which didn't move) no longer geometrically covers the node. Frame B writes `(Included, RebindSuppressed)` to preserve its prior logical membership. Symmetric for new captures: if B's geometry now incidentally covers some other node it didn't before, B writes `(Excluded, RebindSuppressed)` for it. Correct per the model, surprising to users — visible only via the manual-tier border on the node from both frames' perspectives.
- **Z-order during transform-with-content.** `zIndex` is absolute (world-level), not frame-relative. Preserve each member's `zIndex` through the operation.
- **Album backgrounds / camera-locked nodes.** `isFrameBindable = false`. They never enter `effectiveMembers`.

## UX

### Surfaces

- **Contextual action bar — base entries:** Delete, Duplicate, Edit.
- **Contextual action bar — single Frame selected:** adds *Background*, *Z-order* (single-node), and the *FrameEditOptionsBar* (two-checkbox row above the action bar — see below).
- **Contextual action bar — selection contains ≥1 Frame + ≥1 other node:** adds **⊕ Pin** and **⊖ Detach** (target = single selected frame, or chosen via target picker when multiple are selected).
- **Contextual action bar — same selection AND any selected frame has overrides on candidate nodes:** adds **⟲ Auto** (clears overrides; membership reverts to geometry).
- **FrameEditOptionsBar:** visible whenever the selection contains at least one frame. Two checkboxes (*Transform with content*, *Rebind*) apply to the next gesture against any selected frame.

### Dialogs

- **`FrameTargetPickerDialog`** — opens when the user fires Pin / Detach / Auto with ≥2 frames in the selection. Radio list of selected frames (in selection insertion order, leveraging `Set.plus`'s LinkedHashSet semantics), defaulting to the **largest-AABB** frame as the discoverable container. Confirm dispatches the action against the picked frame; every other selected node becomes a candidate.
- **`OverlapPickerDialog`** — opens on long-press when multiple nodes hit-test under the pointer. Multi-select with checkboxes, top-z node pre-checked by default (matches the single-hit long-press: tapping a stack gives you the visible-on-top thing). OK adds the checked set into the selection additively, consistent with the long-press additive gesture.

### Visual feedback

- **Two-tier member-border overlay** on the canvas — for every Frame in the selection, draws thin borders around its effective members:
  - **Auto tier** (lighter, thinner) — node is a member purely by geometry, no override on this frame.
  - **Manual tier** (darker, thicker) — node has an `Included` override on at least one selected frame (User, BatchImport, Wizard, or RebindSuppressed). The visual difference signals "Auto would clear this; geometry would still keep the lighter ones."
- **`FrameNameLabel`** widget renders a colour dot + the frame's name in the frame's colour. Used in `FrameListContent`, `FrameTargetPickerDialog`, and `OverlapPickerDialog` so the same frame reads identically across every chrome surface.

### Limitations

- **`(Excluded, _)` is invisible.** A node that's geometrically inside a frame but explicitly Excluded has no border (it isn't a member). It is indistinguishable from "outside the frame" — the user can tell something is "stuck" only by pressing Auto. A dashed-border variant for excluded members is a future enhancement.
- **Drag a member outside the frame's geometry:** stays a member iff pinned, otherwise drops out. No prompt.

## Migration from current model

1. **`Frame.containsNodeIds` is removed.** It's currently a serialized field but the recompute logic was never wired (see [todo §4.3](../todo.md#43-dynamic-containment)). Real albums in dev almost certainly have it empty.
2. **JSON compatibility:** `SceneGraphSerializer` already uses `ignoreUnknownKeys = true`. Old JSON with `containsNodeIds` will deserialize cleanly with the field discarded.
3. **`Frame.overrides` is additive** with a default of `emptyMap()` — old JSON loads as no overrides, geometry rules everything until the user touches frames.
4. **Behavioural migration is the real work.** Every command kind that mutates nodes or frames must be audited for `overrides` side effects, and undo/redo round-trips must be tested for each. See [MVP slices](#mvp-slices) below.

## MVP slices

Three slices, all shipped:

**Slice 1 — Effective membership read path** ✅
- `Frame.containsNodeIds` removed.
- `Frame.overrides: Map<String, FrameMembershipOverride>` added (defaulted empty).
- `CanvasNode.isFrameBindable` added (default `true`).
- `FrameMembershipUseCase.effectiveMembers` added; unit-tested.

**Slice 2 — Manual pin / detach + Auto reset** ✅
- Contextual bar entries (**⊕ Pin**, **⊖ Detach**) for `(≥1 frame + ≥1 other node)` selections.
- `FrameOverrideUseCase.applyOverride` / `clearOverrides`; unit-tested.
- **`⟲ Auto`** button + `ClearFrameOverrides` action — clears overrides; visible only when at least one selected frame has overrides on a candidate.
- `FrameTargetPickerDialog` for ≥2-frame selections; default = largest-AABB frame.
- `OverlapPickerDialog` rewritten to multi-select (checkboxes + OK), additive into the current selection.
- `FrameNameLabel` shared widget (color dot + frame name in frame's colour).
- Two-tier border overlay replaces the originally-planned member badge: auto = lighter, manual = darker. Same information, cheaper to render and read.

**Slice 3 — Frame edit options + multi-frame** ✅
- *Transform with content* and *Rebind after edit* toggles in the `FrameEditOptionsBar` (transient session state).
- `ApplyFrameEditUseCase.applyFrameEdit` implements the four-combination matrix; unit-tested including transform-with-content cases.
- Compound `CanvasCommand` on `FinishInteraction` captures frame transforms + member transforms + every edited frame's overrides atomically.
- All three gesture kinds (move / resize / rotate) honour `transformContents`. Rotation pivots around the user-selection centroid (frame center for single-frame, group center for multi-frame), so members orbit with their frames.
- Multi-frame selection: every frame in the selection participates. `pendingEditedFrameIds: Set<String>` drives per-frame rebind suppression.

## Deferred

- **Member visualisation for `(Excluded, _)`.** A dashed border (or low-saturation outline) for nodes that are geometrically inside but explicitly Excluded — closes the "stuck detached, invisible" gap.
- **Cross-album bulk operations on overrides** (e.g. "remove all wizard-created bindings album-wide"). Use case doesn't exist yet; per-frame map walk is trivial if it ever does.
- **Per-binding follow flags** (move/scale/rotate independently). When relevant, add a `FollowFlags` field on `CanvasNode`, not on the binding. (Node-intrinsic, not binding-intrinsic.)
- **Roles** (Caption, Decoration). Node-intrinsic too. Add to `CanvasNode` when presentation needs them.
- **Portals / navigation links between frames.** Separate model (`PortalLink(fromFrameId, toFrameId)`), unrelated to membership.
- **Nested frames.** Separate concern; if a hierarchy is needed, add `parentFrameId: String?` on `Frame`. Membership semantics don't change.
- **AI / wizard suggestion drafts.** Treat as transient UI state in the wizard. On accept, write `(Included, Wizard)` overrides.
- **`includeInPresentation` / `includeInExport`.** Node-level when needed; not a binding concern.
- **Live mid-drag geometry recompute.** Visual hint of membership changing in real time. Cosmetic; defer.
- **Spatial-index narrowing.** `effectiveMembers` and `applyPendingRebindSuppression` currently scan all nodes; for large albums, narrow to nodes whose AABB intersects (frame.aabb ∪ frameAfter.aabb). See `core/math/SpatialIndex.kt` and the todo entry.

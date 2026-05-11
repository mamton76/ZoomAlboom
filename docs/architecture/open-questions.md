# Open Architecture Questions

## 1. Unit system
The canvas should probably use abstract units instead of raw pixels.
Need a consistent Units -> DP mapping that respects zoom and device density.

## 2. Dynamic containment
Frame/node intersection recalculation should happen off the main thread.
Need final strategy for keeping containment references in sync.

## 3. Persistence evolution
Current plan: Room + JSON.
Future options: Protobuf or CRDT-based collaborative model.

## 4. Media validation
On project open, missing source URIs must be detected and represented as placeholders.
Need final lifecycle and caching strategy.

## 5. Edit / View / Present modes
Modes layer on top of the canvas-first contextual modes (PRD §12.6). Open:
- Should `mode` live on `CanvasState` (gesture-changing) or be split between canvas (gesture gating) and IDE (chrome visibility)?
- Default mode on album open: last-used (per `ide_workspaces`) or always `Edit`?
- Is a third **Present** mode needed for shared/published albums, or is View enough? Present would also disable frame-list / album navigation overflow.

## 6. Layers — single vs multi membership
User-defined layers (todo §13.2) initially scoped to single membership: each `CanvasNode` has at most one `userLayerId`. Open:
- Should a node be allowed in multiple user layers (tag-style)?
- If so, what's the visibility rule — visible iff *all* containing layers visible, or *any*?
- Layer ordering: do user layers have z-order, or only `Transform.zIndex`?

## 7. Guideline gesture target
Dragging a guideline introduces a fourth hit-test target (alongside resize handle / rotation handle / node body). Open:
- Add as a new gesture layer, or extend `nodeInteractionGestures` Layer 1 with a guideline-hit branch?
- Guideline hit tolerance: same screen-pixel scheme as snapping, or fixed?

## 8. Snapping performance at scale
Snapping candidate scan is per-frame during drag. Today the brute-force `ViewportCuller` works to ~2k nodes (todo §4.5). Open:
- Restrict snap candidates to the current viewport, or a small radius around the dragged rect?
- When the spatial-index upgrade (todo §4.5) lands, snap candidates should reuse it.

## 9. Portal / widget target movement semantics

When a portal, hyperlink, or widget element links to another frame/object/subtree, what should happen when the portal object itself is moved?

**Option 1 — Link-only movement**
Moving the portal moves only the portal object. The target frame/object stays at its original world position. Closest to normal hyperlink semantics — simplest to implement and understand.

**Option 2 — Portal + target subtree movement**
Moving the portal optionally moves the target frame and all objects it references. Treats the portal as a spatial handle for a deeper section. Useful for wizard-generated albums, but potentially surprising for freeform canvases.

**Option 3 — Explicit mode / command**
Default behavior is link-only. Provide an explicit command: "Move linked content with portal" or "Detach linked content from portal." The user opts in to subtree movement per operation.

This question matters most for generated overview frames (from wizards) where map markers, calendar dates, tag cloud items, or portal cards all link to deeper frames. If the user reorganizes the overview, should the deeper canvas geometry follow or stay fixed?

See also [wizards.md](../product/wizards.md) for how wizard-generated albums create portal structures.

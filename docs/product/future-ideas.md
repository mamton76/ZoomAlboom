# Future Ideas (Post-MVP)

> Related: [PRD § 10](PRD.md#10-future-scope-post-mvp) | [Vision](vision.md) | [Architecture](../architecture/overview.md) | [TODO](../todo.md)

## Navigation & Transitions
- **Cinematic transition editor** — editable camera paths between frames with duration, easing presets, waypoints, and Bezier curves. See [architecture concept](../architecture/future-features/transition-editor.md).
- **Smart tags** — tag objects with people/places/topics; clicking a tag teleports to the corresponding frame
- **Semantic jumps** — navigate across the album via tag-based graph, not just spatial proximity
- **Timeline mode** — temporal dimension for chronological navigation

## Canvas & Editing
- **Layers (full)** — type layers + user-defined layers ship as MVP-adjacent work (see [todo § 13](../todo.md#13-layers-visibility-groups)); post-MVP extensions: multi-membership, per-layer locking, per-layer effects
- **Present mode** — read-only viewing for shared/published albums (see [todo § 12.6](../todo.md#126-future))
- **Crop / masking** — mask media through internal bounding box editing
- **Text / sticker enhancements** — richer text editing, sticker library

## Media
- **Audio notes** — voice memos attached to canvas locations
- **Live photos** — animated/moving photos (Harry Potter style)

## Intelligence
- **AI-assisted organization** — auto-suggest groupings, tag recognition, layout hints

## Platform & Collaboration
- **Cloud backup and sync** — persist albums across devices
- **Real-time collaboration** — CRDT or Protobuf-based multi-user editing
- **Export** — render album to video / print / web page

## Technical Evolution
- **Unit system** — abstract canvas `Units` instead of raw pixels; `Units -> DP` formula accounting for zoom and screen density
- **Spatial index** — grid or R-tree to replace brute-force viewport culling at >2k nodes
- **Progressive image loading** — downsampling at low zoom, high-res on zoom-in

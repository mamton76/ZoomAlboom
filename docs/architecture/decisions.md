# Architecture Decisions

## 1. Infinite canvas is the primary interaction model
The product is built around spatial navigation, not page-by-page browsing.

## 2. Frames are navigation anchors, not only visual containers
Frames structure the canvas and support transitions through album space.

## 3. Canvas state is separate from IDE overlay state
This avoids unnecessary recomposition and keeps interaction logic manageable.

## 4. Shared transform layer for camera movement
Pan / zoom / rotation should be handled through a shared transform strategy rather than by re-laying out all nodes during gestures.

## 5. Persistence is split between Room and JSON
Structured metadata lives in Room; scene graph content lives in serialized JSON.

## 6. Android-first implementation
The initial architecture is optimized for Android and Jetpack Compose rather than for full cross-platform support.

## 7. Frame membership: per-frame overrides, geometry implicit
Membership is `geometry − Excluded overrides + Included overrides`, where overrides live on the frame itself (no top-level binding table). Geometry is a source of proposals, not the source of truth. See [frame-membership.md](frame-membership.md).

## 8. Per-type appearance container; shared overlay type, separate overlay fields
Visual styling is held in per-type `*Appearance` containers (`MediaAppearance` on `CanvasNode.Media`, `FrameAppearance` on `CanvasNode.Frame`) that share a sealed `NodeAppearance` base for the four cross-cutting fields (`opacity`, `cornerRadius`, `border`, `shadow`). Shared *value* types (`OverlayStyle`, `OverlaySource`, `NodeBlendMode`, `BorderStyle`, `ShadowStyle`) are defined once and reused.

`MediaAppearance.overlays: List<OverlayStyle>` and `FrameAppearance.contentOverlays: List<OverlayStyle>` use the same element type but are kept as **separate fields** because they sit at different render-pipeline positions and have different semantics: media overlays are bounded by one media's rect, content overlays are bounded by a frame's rect and composite above the frame's linked contents (requiring layered frame rendering and frame–content binding). Both lists composite in declaration order (entry `[i]` over entry `[i-1]`) so a single `List<OverlayStyle>` rendering helper serves both scopes. `NodeAppearance` deliberately does not carry a generic `overlays` field, to prevent the two from collapsing into an ambiguous one. `FrameAppearance.contentOverlays` do not mutate child nodes; they are also not the same thing as `FrameAppearance.contentEffect` (a future off-screen filter pass).

The decorative photo-frame around a single media node is `MediaAppearance.frameDecoration: MediaFrameDecoration?` — explicitly *not* a `CanvasNode.Frame`, *not* a `FrameAppearance`, *not* an `OverlayStyle`. The earlier `frameOverlay: FrameOverlay?` field is retired so that the word "frame" in code unambiguously refers to a `CanvasNode.Frame`. See [appearance.md](appearance.md).

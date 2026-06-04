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
Visual styling is held in per-type `*Appearance` containers (`MediaAppearance` on `CanvasNode.Media`, `FrameAppearance` on `CanvasNode.Frame`) that share a sealed `NodeAppearance` base for the cross-cutting fields (`opacity`, `cornerRadius`, `overlays`, `border`, `shadow`). Shared *value* types (`OverlayStyle`, `OverlaySource`, `NodeBlendMode`, `BorderStyle`, `ShadowStyle`) are defined once and reused.

`NodeAppearance.overlays: List<OverlayStyle>` is a **single unified field on the base** (since 2026-05-19), inherited by both subclasses. The renderer dispatches by node type: on a media node it paints above the photo pixels bounded by the media rect; on a frame node it paints above the frame's combined contents output (background + members + their own per-media overlays), clipped to the frame rect — which requires layered frame rendering and frame–content binding (`feature/canvas/view/FramePaintEvents.kt` + `FrameRendererPhased`, using `FrameMembershipUseCase.effectiveMembers`). The list composites in declaration order (entry `[i]` over entry `[i-1]`); both scopes share `DrawScope.drawOverlayStack` in `OverlayRenderer.kt`. Frame overlays do not mutate child nodes; they are not the same thing as `FrameAppearance.contentEffect` (a future off-screen filter pass). The pre-2026-05-19 design had separate `MediaAppearance.overlays` / `FrameAppearance.contentOverlays` fields; `SceneGraphSerializer` migrates legacy JSON on read.

The decorative photo-frame around a single media node is `MediaAppearance.frameDecoration: MediaFrameDecoration?` — explicitly *not* a `CanvasNode.Frame`, *not* a `FrameAppearance`, *not* an `OverlayStyle`. The earlier `frameOverlay: FrameOverlay?` field is retired so that the word "frame" in code unambiguously refers to a `CanvasNode.Frame`. See [appearance.md](appearance.md).

## 9. Frame chrome is a separate hint layer, not part of appearance
Frame chrome (outline / corner ticks / glow / label tab drawn on a frame's edge) is **editor-and-viewer hinting**, not album content. It is mode-dependent, may be hidden, and is never exported — distinct from `FrameAppearance` which is always visible and serialized as album data. Chrome paint is restricted to the frame's edge / outside / a small label tab; it never paints inside the frame's content rect. Resolution is pick-one from a closed `FrameChromeStyle` enum via a pure resolver (`(frame, mode, selection, currentFrameId, profile, overrides) → FrameChromeStyle`) using most-specific-target-wins (`CURRENT > SELECTED > ALL`) with most-recent-pushed as tiebreaker; the mode default is the implicit lowest-priority entry on the same stack. Per-mode defaults live in `AlbumPresentationProfile.frameChrome` (serialized); transient overrides live in `CanvasState.editor.chromeOverrides` (not serialized). See [frame-chrome.md](frame-chrome.md). Decided 2026-05-23, implementation tracked in [todo.md § 23](../todo.md#23-frame-chrome).

## 10. Cloud sync is local-first; remote binding is separate from the album
Albums always exist locally with a stable `AlbumId` and a `(headRevisionId, parentRevisionId)` versioning boundary minted at each `FinishInteraction` commit. Cloud connection is modeled as an optional, per-provider `RemoteBinding` keyed by `AlbumId` — explicitly **not** a `storageMode: Local | GoogleDrive` flag on `Album`. Sync is automatic snapshot upload on stable commits + open-time remote head check that gates Edit mode; conflicts preserve the local branch as a separate conflict-copy album (`"<name> — local conflict copy"`) and restore the primary from the remote head — never overwrite, never auto-merge. Conflict detection uses **revision lineage**, never timestamps. The architecture is constrained to remain compatible with future end-to-end encryption: remote stores opaque blobs only; sync must not depend on the provider inspecting or merging plaintext (which is why conflict-copy preservation is chosen over auto-merge). Decided 2026-06-03, implementation deferred. See [cloud-sync.md](cloud-sync.md), implementation slices in [todo.md § 26](../todo.md#26-cloud-sync-deferred).

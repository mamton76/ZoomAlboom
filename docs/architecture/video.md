# Video on the Canvas (MVP)

> Related: [data-model.md § MediaType](data-model.md#mediatype) | [rendering.md § 4b LOD](rendering.md#4b-level-of-detail-resolution-lod) | [media-appearance.md](media-appearance.md) | [editor-tools.md](editor-tools.md) | [PRD](../product/PRD.md) | [todo.md § 27](../todo.md#27-video-mvp)

**Status — first implementation shipped 2026-06-20** (design decided 2026-06-17, settling `to_discuss.md § 13`). The MVP video-node slice is implemented: video nodes can be placed on the canvas, poster frames are rendered through `coil-video`, playback is handled by a bounded ExoPlayer pool, and playback is triggered through uniform double-tap / the Edit context menu. This doc is the source of truth for how video objects live on the infinite canvas; remaining work + slice detail live in [todo.md § 27](../todo.md#27-video-mvp). (See § 8 for the file map.)

The product goal is **not** a video editor. It is: *let video objects appear as elegant, playable "living media" on the infinite canvas* — the "living album" / Harry-Potter-newspaper direction. A video behaves like an image node for all transform/selection purposes; the only additions are playback and a poster.

---

## 1. Scope

**First slice (required):**
- Place a video on the canvas the same way as an image.
- Render a poster frame when not playing, with a subtle play-icon overlay.
- View / Present mode: tap a video → play / pause. Play icon fades while playing, reappears on pause.
- No accidental playback in Edit mode.
- Video transform behaves exactly like an image node (move / resize / rotate / select) and stays compatible with camera transforms.
- Reasonable performance while panning / zooming.

**Out of scope (MVP):** full video editor, trimming, audio editing, custom poster frame, loop / mute / start-position controls, inline mini-controls, autoplay-on-frame-entry, pause-on-leaving-frame, album-level `AlbumVideoDefaults`, the full Media Library (see [data-model.md](data-model.md) + `to_discuss.md § 14`).

---

## 2. Model bridge — no migration

`CanvasNode.Media` already carries everything the MVP needs:

```kotlin
data class Media(
    ...
    val mediaRefId: String,                       // today: a raw source URI
    val mediaType: MediaType = MediaType.IMAGE,   // VIDEO already in the enum
    ...
)
```

`MediaType.VIDEO` already exists; `mediaRefId` is currently a raw URI string handed straight to Coil. The MVP **activates** the `VIDEO` path and keeps `mediaRefId` a raw URI — **no `MediaAsset` indirection, no migration, no new model field.** This is the minimal bridge that still points toward the future Media Library (`to_discuss.md § 14`): when `MediaAsset` lands, `mediaRefId` becomes the asset id without changing the node shape.

A node is a video iff `mediaType == MediaType.VIDEO`. Image vs. video is the only branch.

**URI handling.** Because `mediaRefId` is a raw ref, playback must not assume it is a filesystem path. It can be a `content://` URI (Android media picker), a `file://` URI, an `http(s)://` URL, or a bare path (today the import flow copies picked media into app storage and stores its absolute path). Conversion goes through `mediaRefToUri` (`feature/canvas/playback/MediaUri.kt`): refs that already carry a scheme are passed through `Uri.parse` untouched; bare paths use `Uri.fromFile`. This avoids corrupting a `content://` ref into `file://content:/…` — the trap of blindly wrapping the ref in `Uri.fromFile(File(ref))`.

---

## 3. Poster / thumbnail — zero storage

The poster frame is extracted lazily by Coil's `coil-video` decoder (pointed at the video URI with a frame-millis parameter) and cached like any other image. There is **no** generated-on-import step, **no** stored poster, and **no** new model field. Custom poster-frame selection is deferred.

**Implementation note (updated 2026-06-20).** The poster does **not** render through the shared image renderer (`FullMediaRenderer`). It is loaded as an explicit `ARGB_8888` software bitmap via `rememberVideoPosterBitmap` (mirroring the alpha-mask loader) and drawn through `VideoPosterSurface` (see § 6). The shared image path could not be reused: a `coil-video` frame composited in `FullMediaRenderer`'s single-layer offscreen masks its cut-away region as **opaque black** (a plain image is fine). Rendering the poster through the same surface chrome as the live player — and as an ARGB bitmap — is what makes masked / styled posters correct.

---

## 4. Playback affordance — uniform double-tap

**Updated 2026-06-19 (supersedes the original single-tap-in-View design).** Playback is a **uniform double-tap** across modes; single-tap keeps each mode's default so it never fights selection / focus.

| Mode | Single-tap | Double-tap on a video | Other play affordance |
|---|---|---|---|
| **View / Present** | Focus the node (camera fit). | Play / pause. | — |
| **Edit (Selection)** | Select (unchanged). | Play / pause. | Context-menu `Play / Pause`. |
| **Edit (Eraser / CropEdit)** | Tool default. | Tool default (no play). | — |

- **Why double-tap, not single-tap-in-View:** a single mental model across modes ("double-tap a video to play"), and single-tap stays free for the mode's primary action. Implemented via `DoubleTapRoute.PlayPauseVideo` (router branches on a `hitIsVideo` flag); the Edit context-menu item (`PlayVideoAction` → `EditorActionEffect.ToggleVideoPlayback`) is a discoverable alternative.
- **No accidental playback:** single-tap never plays; double-tap on a *non-video* keeps the mode default (View = reset camera, Edit = no-op). Specialized Edit tools (Eraser, CropEdit) keep their own double-tap so playback can't interrupt them.
- **Play / pause is pause-*in-place*, not stop.** Double-tapping a playing video pauses it (`playWhenReady = false`) keeping its position + frozen frame; the next double-tap resumes from there. A video never *stops* via gesture — it leaves the active set only by pool eviction (off-screen / lost the recency race) or node delete. Pause is tracked in `VideoPlaybackController.pausedNodeIds` (so it survives `reconcile`), and a play badge is drawn over the paused frame so it reads as paused rather than a still.
- The poster's play-icon badge remains the static affordance (drawn inside the mask so it doesn't paint over masked-out regions).

---

## 5. Concurrency — bounded player pool

Playback is **simultaneous** (multiple short atmospheric clips can play at once), implemented via a **bounded player pool** — because hardware `MediaCodec` decoders are capped per device (often ~4–8, sometimes lower), so "simultaneous" can never mean unbounded.

- **Candidates** are bounded first by LOD: only `RenderDetail.Full` videos are playback candidates.
- A pool of *K* players attaches to the *K* most-relevant playing videos. *K* is derived from a **device decoder-capability probe**, clamped to a safe ceiling.
- **Eviction policy** decides which playing videos hold a player when demand exceeds *K* (e.g. off-screen / least-recently-started evicted first).
- **Poster fallback**: a video that wants to play but can't get a pooled player shows its (animated-or-frozen) poster instead of failing.

This is a deliberate expansion past "keep the first slice small," chosen as the truest fit for the living-album direction. Frame-level playback policy (autoplay on entry, pause on leave) remains a later concern.

---

## 6. Playback host & rendering (implemented 2026-06-20)

At `RenderDetail.Full` a video renders through a dedicated Compose surface, **not** the shared image renderer. Both the live and the static cases share one structure, **`VideoSurfaceChrome`** (`feature/canvas/playback/`):

- **Outer layer** — the node's world translate / rotate / opacity (`clip = false` so the shadow can extend past the node rect).
- **Inner layer** — clipped to the rounded node rect and, when an alpha mask is present, composited **offscreen** (`CompositingStrategy.Offscreen`) via `drawWithContent`: base content, then overlays, then the mask's `BlendMode.DstIn`. Border + shadow stay outside the mask.
- Keeping the rotation on the **outer** layer and the mask's offscreen on an **inner, clipped, un-rotated** layer is load-bearing. The shared image renderer's single-layer offscreen (rotation *on* the offscreen layer) composited a masked video frame's cut-away region as **opaque black**; the two-layer split fixes it.

Two users of the chrome:

- **`VideoPlayerSurface`** (live) — a bare **`TextureView`** (not `PlayerView`) bound via `player.setVideoTextureView`, set `isOpaque = false` so opacity / mask actually show through. A `SurfaceView` would ignore container alpha / clip / offscreen compositing. The view is non-clickable / non-focusable so pan / zoom / selection gestures still reach the Compose routers.
- **`VideoPosterSurface`** (static) — draws the `ARGB_8888` poster bitmap (§ 3) + the play badge.

**Mounting / z-order.** Both surfaces are emitted **inline in the paint loop**, at the node's z-order position — playing → `VideoPlayerSurface`, else → `VideoPosterSurface`. The poster is pure Compose; the live player is an `AndroidView`, but because it hosts a **`TextureView`** (an in-hierarchy view, not a separate `SurfaceView` window layer) Compose interleaves it with the drawn nodes by composition order, so a playing video keeps its z-order too (verified on-device 2026-06-20). (A separate pool-reconcile `LaunchedEffect` only decides *which* nodes hold a player.) Videos below `Full` render a placeholder via the normal renderer.

The inline emission is gated by the `PLAYER_SURFACE_INLINE` constant in `CanvasScreen.kt` (default `true`). Setting it `false` falls back to mounting the live player surfaces **after** the paint loop — they then draw above z-order, but it's a stable layout, kept as an escape hatch in case a device layers the inline `TextureView` against transformed `graphicsLayer` content inconsistently.

**Live appearance.** Because poster and player share the chrome, every `MediaAppearance` op that renders on the poster also applies to the playing frame — opacity, corner radius, border, shadow, crop (Fit/Fill/Stretch/Manual), overlays, opening (rectangular), decorations (Above/Below, Stretch/NineSlice), content mask — so styling never snaps off during playback. `colorAdjustments` / `caption` are excluded (they render nowhere yet). Tracked in [todo.md § 27.9](../todo.md#279-live-appearance-on-playing-video-decided-2026-06-19).

**State.** Playback state + the player pool live in a `CanvasScaffold`-level holder (`VideoPlaybackController`) keyed by `nodeId` — never in domain models, per the editor-state rule that UI-surface / transient state stays out of `CanvasState` ([editor-tools.md § 7.1](editor-tools.md#71-state)). A video node carries no `isPlaying` field.

---

## 7. Dependencies (first slice)

| Dependency | Purpose |
|---|---|
| Media3 ExoPlayer | Playback. |
| `coil-video` | Lazy poster-frame extraction. |

Today only Coil 3 image (`coil-compose 3.4.0`) is wired — both are new.

---

## 8. Implementation status

**Implemented + verified on-device, 2026-06-20.** Slices A–E (deps, poster, single-player → bounded pool, gestures, tests) plus § 27.9 live appearance. Code lives in `feature/canvas/playback/` (`VideoPlaybackController`, `VideoDecoderProbe`, `VideoSurfaceChrome` / `VideoPlayerSurface` / `VideoPosterSurface`) with wiring in `CanvasScreen` / `CanvasScaffold` and the picker/import path in `CanvasViewModel`. Slice detail + remaining polish in [todo.md § 27](../todo.md#27-video-mvp).

Out of scope (unchanged from § 1): loop / mute / start-position / custom poster / inline controls / autoplay-on-frame-entry / pause-on-leave / `AlbumVideoDefaults` / full Media Library.

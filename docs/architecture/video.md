# Video on the Canvas (MVP)

> Related: [data-model.md § MediaType](data-model.md#mediatype) | [rendering.md § 4b LOD](rendering.md#4b-level-of-detail-resolution-lod) | [media-appearance.md](media-appearance.md) | [editor-tools.md](editor-tools.md) | [PRD](../product/PRD.md) | [todo.md § 27](../todo.md#27-video-mvp)

**Status — design decided 2026-06-17 (settles `to_discuss.md § 13`); implementation pending.** This doc is the source of truth for how video objects live on the infinite canvas. It captures the resolved design; the implementation plan + slices live in [todo.md § 27](../todo.md#27-video-mvp).

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

---

## 3. Poster / thumbnail — zero storage

The poster is **the existing Coil path**, pointed at the video URI with a frame-millis parameter, decoded by Coil's `coil-video` decoder. There is **no** generated-on-import step, **no** stored poster, and **no** new model field. Extraction is lazy, on first render, cached by Coil like any other image. Custom poster-frame selection is deferred.

---

## 4. Edit vs. View playback

| Mode | Tap behavior | Play affordance |
|---|---|---|
| **View / Present** | Tap anywhere on the video → play / pause. | The poster play-icon overlay (fades while playing). |
| **Edit** | Tap **selects** (unchanged). Playback must not fight selection. | A small **play button drawn on the selected node's poster**. |

**Edit-mode play button rules.** The button is a **node-local** hit-target: it sits *inside* the node, yields to transform handles, and must **not** extend selection or start a marquee. It is the same play-icon affordance already required by the poster, made tappable on the selected node only.

No accidental playback in Edit: outside the explicit play button, Edit taps/drags do exactly what Selection does today.

---

## 5. Concurrency — bounded player pool

Playback is **simultaneous** (multiple short atmospheric clips can play at once), implemented via a **bounded player pool** — because hardware `MediaCodec` decoders are capped per device (often ~4–8, sometimes lower), so "simultaneous" can never mean unbounded.

- **Candidates** are bounded first by LOD: only `RenderDetail.Full` videos are playback candidates.
- A pool of *K* players attaches to the *K* most-relevant playing videos. *K* is derived from a **device decoder-capability probe**, clamped to a safe ceiling.
- **Eviction policy** decides which playing videos hold a player when demand exceeds *K* (e.g. off-screen / least-recently-started evicted first).
- **Poster fallback**: a video that wants to play but can't get a pooled player shows its (animated-or-frozen) poster instead of failing.

This is a deliberate expansion past "keep the first slice small," chosen as the truest fit for the living-album direction. Frame-level playback policy (autoplay on entry, pause on leave) remains a later concern.

---

## 6. Playback host

- The player surface is an **`AndroidView` hosting a Media3 `ExoPlayer`**, mounted **only** at `RenderDetail.Full` for pool-assigned, currently-playing nodes. Every other video renders its poster via the Coil path of [media-appearance.md § Rendering Pipeline](media-appearance.md#rendering-pipeline-per-media-node).
- **Playback state and the player pool live in a `CanvasScaffold`-level holder keyed by `nodeId`** — never in domain models. This follows the editor-state rule that UI-surface / transient state stays out of `CanvasState` ([editor-tools.md § 7.1](editor-tools.md#71-state)); a video node carries no "isPlaying" field.
- The host must not fight gesture routing: the player surface participates in the canvas transform but defers pan/zoom/selection gestures to the existing routers.

---

## 7. Dependencies (first slice)

| Dependency | Purpose |
|---|---|
| Media3 ExoPlayer | Playback. |
| `coil-video` | Lazy poster-frame extraction. |

Today only Coil 3 image (`coil-compose 3.4.0`) is wired — both are new.

---

## 8. Implementation status

**No code yet.** Design decided 2026-06-17. Next deliverable is the implementation plan; slices tracked in [todo.md § 27](../todo.md#27-video-mvp).

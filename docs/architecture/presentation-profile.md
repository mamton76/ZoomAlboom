# Presentation Form Factor

> Related: [data-model](data-model.md) | [coordinates](coordinates.md) | [navigation](navigation.md) | [TODO § 22](../todo.md#22-presentation-form-factor)

The infinite canvas remains infinite. **Presentation profile** describes the *intended* screen shape for viewing/presenting the album — it shapes new-frame defaults, View-mode camera transforms, and editor overlays. It does **not** constrain or resize the canvas itself.

## 1. Concept

An album declares one or more "presentation profiles" describing the target screen geometry. The primary profile drives:

1. **Default aspect ratio** of new frames (frames may still be free-ratio on explicit user choice).
2. **View / Present mode camera fit** — when navigating to a frame, the camera transform is computed from `frame bounds`, `actual device viewport`, and the selected `fit mode`.
3. **Editor overlays** — target aspect ratio, safe area, current device preview, optional target-profile preview.

What the profile does **not** do (MVP):

- It does not reflow or rearrange the canvas for different screens.
- It does not crop frame content.
- It does not lock a frame's resize behavior to the album ratio.

## 2. Domain Model

Lives in `domain/model/` alongside the scene graph types.

```kotlin
@Serializable
data class AlbumPresentationProfile(
    val aspectRatio: AspectRatio,
    val orientation: Orientation,
    val defaultFitMode: FrameFitMode = FrameFitMode.CONTAIN,
    val defaultOutsideMode: OutsideFrameMode = OutsideFrameMode.BLURRED_BACKDROP,
    val safeAreaInset: Float = 0.1f, // fractional inset on the shorter axis
)

@Serializable
sealed class AspectRatio {
    @Serializable object R_16_9 : AspectRatio()
    @Serializable object R_9_16 : AspectRatio()
    @Serializable object R_4_3 : AspectRatio()
    @Serializable object R_3_4 : AspectRatio()
    @Serializable object Square : AspectRatio()
    @Serializable object Free : AspectRatio()
    @Serializable data class Custom(val w: Int, val h: Int) : AspectRatio()
}

@Serializable enum class Orientation { Landscape, Portrait }

@Serializable enum class FrameFitMode {
    CONTAIN, // fit whole frame into viewport; min(scaleX, scaleY); MVP default
    COVER,   // fill viewport; max(scaleX, scaleY); may crop frame content
    STRETCH, // independent X/Y scale; ignores aspect ratio (not MVP, but cheap to add)
}

@Serializable enum class OutsideFrameMode {
    ALBUM_BACKGROUND,  // fall back to AlbumBackground (cheapest)
    BLURRED_BACKDROP,  // blurred sample (post-MVP for full fidelity; see § 6)
    SOLID_FILL,        // single dark/desaturated color
}
```

Per-frame override (post-MVP, see § 7):

```kotlin
@Serializable
data class FramePresentationOverride(
    val aspectRatio: AspectRatio? = null,
    val fitMode: FrameFitMode? = null,
    val outsideMode: OutsideFrameMode? = null,
)
```

Effective fit mode for a frame: `frame.presentation?.fitMode ?: album.profile.defaultFitMode`.

## 3. Persistence

`AlbumPresentationProfile` lives in the **scene graph JSON root** alongside `albumBackground` (§1.3 + §19 work).

```jsonc
{
  "albumId": 123,
  "camera": { ... },
  "background": { ... },
  "profile": {
    "aspectRatio": "R_16_9",
    "orientation": "Landscape",
    "defaultFitMode": "CONTAIN",
    "defaultOutsideMode": "BLURRED_BACKDROP",
    "safeAreaInset": 0.1
  },
  "nodes": [ ... ]
}
```

- `profile` is **nullable** — older albums and new defaults both work without it.
- Profile changes do **not** mutate existing nodes (no reflow). They affect *future* frame creations and the View-mode camera fit.
- Profile is album content, not IDE state — so it goes in the scene JSON, not `ide_workspaces`.

## 4. Integration With `Transform.toCamera()`

`core/math/TransformUtils.kt::Transform.toCamera()` already implements CONTAIN with a hardcoded `fillFraction = 0.9f`:

```kotlin
val scale = minOf(screenW * fillFraction / renderW, screenH * fillFraction / renderH)
```

For presentation work, parameterize by fit mode + safe-area inset:

```kotlin
fun Transform.toCamera(
    screenW: Float,
    screenH: Float,
    fitMode: FrameFitMode = FrameFitMode.CONTAIN,
    safeAreaInset: Float = 0.1f,
): Camera {
    val fill = 1f - safeAreaInset * 2f
    val sx = screenW * fill / renderW
    val sy = screenH * fill / renderH
    val scale = when (fitMode) {
        FrameFitMode.CONTAIN -> minOf(sx, sy)
        FrameFitMode.COVER   -> maxOf(sx, sy)
        FrameFitMode.STRETCH -> sx // X-driven; Y handled separately if/when used
    }
    return Camera(
        cx = screenW / 2f - cx * scale,
        cy = screenH / 2f - cy * scale,
        scale = scale,
        rotation = rotation,
    )
}
```

Removing the hardcoded `0.9f` is project-wide — every existing caller needs to pass (or default) the profile-derived value. Small but touches several files.

## 5. Frame Creation Defaults

`CanvasNodeFactory.Frame` currently creates frames at `(screenW × 0.8) × (screenH × 0.8)` — independent of any album profile. New rule:

1. Compute the *budget rect* `(screenW × 0.8, screenH × 0.8)` as today.
2. Fit the album's `aspectRatio` rect inside the budget (preserving ratio), producing `(targetRenderW, targetRenderH)`.
3. Apply the same `1/camera.scale` rebase trick so `w/h` stay camera-independent (see [data-model.md § Transform "Creation conventions"](data-model.md#transform)).

`AspectRatio.Free` skips step 2 and uses the budget rect directly — preserves the current "free frame" behavior for users who explicitly opt out.

## 6. Outside-Frame Behavior (Letterbox Region)

When `fitMode = CONTAIN`, the frame fits inside the viewport with leftover bars. `OutsideFrameMode` decides what fills the bars in View/Present mode.

Cost ordering (cheapest → priciest):

| Mode | What we render | MVP? |
|------|----------------|------|
| `ALBUM_BACKGROUND` | Existing `AlbumBackground` keeps showing outside the frame's clipped region | ✅ |
| `SOLID_FILL` | Single color (configurable; default dark) | ✅ |
| `BLURRED_BACKDROP` | Frame content sampled, blurred, scaled to fill viewport | Post-MVP |

**Recommendation:** MVP ships `ALBUM_BACKGROUND` (zero extra work — letterbox bars just show whatever the album background draws) and `SOLID_FILL`. `BLURRED_BACKDROP` is the cinema-letterbox effect and needs an offscreen pass or `RenderEffect.createBlurEffect` (API 31+); defer until the rest is in place.

## 7. Editor Overlays

A new render layer drawn **inside** the camera `graphicsLayer` (world-locked, zooms with the canvas). Strokes scaled by `1/camera.scale` like guidelines (see [TODO § 14.3](../todo.md#14-guidelines--snapping)).

| Overlay | Source | Toggle |
|---------|--------|--------|
| Target aspect ratio rect | profile.aspectRatio rendered around the *currently focused* frame (or canvas center if none) | TopBar |
| Safe area | inset rect inside the target rect, based on `safeAreaInset` | TopBar |
| Current device viewport | rect representing the editor's actual canvas size at current zoom | TopBar |
| Target profile preview | a hypothetical device with the profile's aspect ratio (fixed pixel diagonal, e.g. 6") | TopBar |

Visible in **Edit mode only**. Not visible in View/Present mode (that's what the actual viewport is for).

## 8. Mode Interaction

| Mode | Profile usage |
|------|--------------|
| Edit | Drives new-frame defaults; renders overlays when toggled. Does not constrain gestures. |
| View | `FocusNode` uses `Transform.toCamera(viewport, effectiveFitMode, profile.safeAreaInset)` |
| Present (post-MVP) | Same as View; may additionally lock orientation or hide chrome |

View mode (§12) is the primary consumer. Until presentation profile lands, View-mode focus calls effectively use `CONTAIN @ 0.9` implicitly — adding the profile is a behaviour-preserving generalisation.

## 9. Open Questions

- **Frame aspect lock on resize.** Decision 2 says new frames default to album ratio; decision 3 says frames stay free-ratio. Resize gestures therefore **don't** lock to the ratio — the album ratio is only the *initial* shape. Confirm before implementation.
- **Multiple profiles.** Decision 1 mentions a *primary* profile, implying secondary profiles may exist. MVP stores one profile (the primary). Multi-profile storage and switching is post-MVP.
- **Profile change → existing frames.** When the user changes album profile, existing frames are *not* touched. (No reflow per MVP scope.) Confirm.
- **Where the "current device" comes from.** `LocalConfiguration` provides the editor's canvas pixel size — that's our "current device viewport." Whether to also show physical inches / DPI is a UX call.
- **Settings entry point.** No "Album Settings" surface exists today. Options: TopBar menu, a new bottom sheet, or extend `PanelConfigDialog`. Probably a dedicated sheet since it's album content, not panel/IDE state.

## 10. Implementation Order

Depends on §1.3 (scene graph root wrapper) — must land first.

1. Add `AlbumPresentationProfile` + enums in domain; thread into `SceneGraph` root with default fallback.
2. Parameterize `Transform.toCamera()` by `FrameFitMode` + `safeAreaInset`; remove hardcoded `0.9f`.
3. Profile-aware `CanvasNodeFactory.Frame`.
4. Editor overlay renderer (read-only — no editing UI yet).
5. Settings UI (sheet or dialog) to pick profile.
6. View mode (§12) consumes profile for `FocusNode`.
7. Per-frame override (`FramePresentationOverride`) — post-MVP.
8. `BLURRED_BACKDROP` rendering — post-MVP.

## 11. Out of Scope (Post-MVP / Never)

- Adaptive layouts that rearrange nodes for different screens.
- Multiple independent layouts per frame.
- Smart AI recomposition.
- Responsive behaviour beyond camera-fit at view time.

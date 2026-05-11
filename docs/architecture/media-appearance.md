# Media Appearance System

> Related: [data-model.md](data-model.md) | [overview.md](overview.md) | [todo.md §20](../todo.md#20-media-appearance-non-destructive-editing) | [PRD §8.7](../product/PRD.md#87-non-destructive-media-appearance)

Non-destructive visual styling for `CanvasNode.Media`. The original source file is never modified.

**Core formula:**
```
source media asset  +  MediaAppearance  =  rendered media object on canvas
```

The same source photo can appear multiple times with different visual styles (normal, sepia, Polaroid frame, school album border, etc.) because the appearance recipe lives on the canvas node, not on the file.

---

## MediaAppearance

```kotlin
@Serializable
data class MediaAppearance(
    val opacity: Float = 1f,
    val cornerRadius: Float = 0f,
    val crop: CropSettings = CropSettings(),
    val border: BorderStyle? = null,
    val shadow: ShadowStyle? = null,
    val colorAdjustments: MediaColorAdjustments? = null,
    val overlays: List<MediaOverlay> = emptyList(),
    val frameOverlay: FrameOverlay? = null,
    val caption: CaptionStyle? = null,
)
```

Stored as a nullable field on `CanvasNode.Media`. `null` = default rendering (no appearance applied). `ignoreUnknownKeys` handles old nodes that predate the field.

---

## Crop

```kotlin
@Serializable
data class CropSettings(
    val mode: CropMode = CropMode.Fit,
    val offsetX: Float = 0f,   // manual pan offset within bounding box (Manual mode)
    val offsetY: Float = 0f,
    val zoom: Float = 1f,      // manual zoom within bounding box (Manual mode)
    val focalX: Float = 0.5f,  // focal point for auto-crop, 0..1 relative to source
    val focalY: Float = 0.5f,
)

@Serializable
enum class CropMode {
    Fit,      // whole image visible inside bounding box; empty space allowed
    Fill,     // fills entire bounding box; parts of image may be cropped; respects focal point
    Manual,   // user pans and zooms the image inside the bounding box
    Stretch,  // fills bounds without preserving aspect ratio
}
```

---

## Border & Shadow

```kotlin
@Serializable
data class BorderStyle(
    val color: String,       // hex
    val widthPx: Float,
    val opacity: Float = 1f,
)

@Serializable
data class ShadowStyle(
    val color: String = "#000000",
    val opacity: Float = 0.5f,
    val offsetX: Float = 4f,
    val offsetY: Float = 4f,
    val blurRadius: Float = 8f,
)
```

---

## Color Adjustments

Parametric color grading applied before overlays.

```kotlin
@Serializable
data class MediaColorAdjustments(
    val brightness: Float = 0f,   // -1..1
    val contrast: Float = 0f,
    val saturation: Float = 0f,
    val temperature: Float = 0f,
    val tint: Float = 0f,
    val exposure: Float = 0f,
    val highlights: Float = 0f,
    val shadows: Float = 0f,
    val blur: Float = 0f,
    val sharpen: Float = 0f,
    val vignette: Float = 0f,
)
```

For MVP, raster filter overlays (see below) are the simpler approach. Parametric color adjustments can be added incrementally.

---

## Raster Overlays

Semi-transparent PNG/WebP layers applied on top of the source image. Each overlay has its own blend mode and opacity.

```kotlin
@Serializable
data class MediaOverlay(
    val id: String,
    val kind: OverlayKind,
    val assetUri: String,
    val opacity: Float = 1f,
    val blendMode: OverlayBlendMode = OverlayBlendMode.Normal,
    val fitMode: OverlayFitMode = OverlayFitMode.Cover,
    val rotation: Float = 0f,
    val scale: Float = 1f,
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
    val isEnabled: Boolean = true,
)

@Serializable
enum class OverlayKind { Texture, Filter, Frame, LightLeak, Dust, Scratches, Vignette, Decoration }

@Serializable
enum class OverlayBlendMode { Normal, Multiply, Screen, Overlay, SoftLight, Darken, Lighten }

@Serializable
enum class OverlayFitMode { Cover, Fit, Stretch, Tile }
```

**Blend mode use cases:**
- Paper texture → `Multiply`
- Light leak → `Screen`
- Vintage color wash → `SoftLight`
- Dust / scratches → `Normal` or `Screen`

---

## Frame Overlay

Decorative photo frame rendered on top of all other layers. Two rendering modes.

```kotlin
@Serializable
data class FrameOverlay(
    val assetUri: String,
    val opacity: Float = 1f,
    val mode: FrameRenderMode = FrameRenderMode.Stretch,
    // Nine-slice insets — ignored in Stretch mode
    val sliceLeft: Float = 0f,
    val sliceTop: Float = 0f,
    val sliceRight: Float = 0f,
    val sliceBottom: Float = 0f,
    // Usable content area inside the frame decoration
    // e.g. Polaroid has extra space at bottom for caption
    val contentInsetLeft: Float = 0f,
    val contentInsetTop: Float = 0f,
    val contentInsetRight: Float = 0f,
    val contentInsetBottom: Float = 0f,
)

@Serializable
enum class FrameRenderMode {
    Stretch,    // frame PNG stretched over entire object — simple, fine for textures/vignettes
    NineSlice,  // corners unscaled; edges scaled one axis only — required for real photo frames
}
```

**Nine-slice layout:**
```
corner | top-edge  | corner
left   |  content  | right
corner | bot-edge  | corner
```
Corners are never distorted. Edges stretch along one axis only. Center (content area) is transparent or filled by the source image.

---

## Rendering Pipeline

Per media node, in order:

1. Decode source asset, apply `CropSettings` (mode + focal point)
2. Apply `MediaColorAdjustments` if non-null
3. Draw `MediaOverlay` list in order — each with its `blendMode` and `opacity`
4. Draw `FrameOverlay` (Stretch or NineSlice) on top of overlays
5. Apply `cornerRadius`, `border`, `shadow`, overall `opacity`
6. Draw `CaptionStyle` if present

**LOD:** At `Stub` or `Preview` detail levels, skip overlays and frame rendering — show the cropped source only. Full pipeline runs at `Full` detail.

---

## Style Presets

A complete `MediaAppearance` recipe can be saved as a named preset and applied to other objects.

```kotlin
@Serializable
data class MediaStylePreset(
    val id: String,
    val name: String,
    val appearance: MediaAppearance,
)
```

Example preset names: `old_family_photo`, `polaroid`, `school_album`, `travel_postcard`, `recipe_clean`.

Presets stored per-album (scene graph) or globally (app-level preferences). Copy/paste appearance works on the `MediaAppearance` value directly — no preset required.

---

## Rendered Derivatives

Users can flatten the current appearance into a new image file:

```
source photo + MediaAppearance = new PNG/JPEG/WebP asset
```

The original file is unchanged. The generated file is registered in `media_library` with:
- `origin = RENDERED_DERIVATIVE`
- `sourceAssetId` — id of the original asset
- `recipeHash` — hash of the `MediaAppearance` used

Stored in `filesDir/media/<albumId>/rendered/`.

**Canvas commands:**
- `CreateRenderedCopyOnCanvas` — new node alongside the original, references the derivative
- `ReplaceWithRenderedImage` — replaces node's `mediaRefId` with the derivative id (undoable; preserves transform/zIndex/tags)
- `SaveToDeviceGallery` — exports rendered image to system gallery

---

## MVP Scope

**Required:** opacity, crop (Fit/Fill/Manual), cornerRadius, border, shadow, raster overlays (PNG/WebP) with opacity + blend mode, FrameOverlay (Stretch mode), copy/paste appearance, save as preset, save rendered derivative.

**Nice to have in MVP:** NineSlice frame rendering, basic color adjustments, CreateRenderedCopyOnCanvas, ReplaceWithRenderedImage, ResetAppearance.

**Post-MVP:** AI auto-enhance, background removal, animated overlays, batch preset application, advanced masks, caption styling.

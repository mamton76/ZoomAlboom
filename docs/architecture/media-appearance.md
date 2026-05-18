# Media Appearance

> Related: [appearance.md](appearance.md) (shared types) | [data-model.md](data-model.md) | [overview.md](overview.md) | [todo.md §20](../todo.md#20-appearance-system-non-destructive-styling) | [PRD §8.7](../product/PRD.md#87-non-destructive-media-appearance)

Non-destructive visual styling for `CanvasNode.Media`. The original source file is never modified.

**Core formula:**
```
source media asset  +  MediaAppearance  =  rendered media object on canvas
```

The same source photo can appear multiple times with different visual styles (normal, sepia, Polaroid frame, school-album border, etc.) because the appearance recipe lives on the canvas node, not on the file.

`MediaAppearance` is one of the two concrete `NodeAppearance` subclasses (alongside `FrameAppearance`). The shared base class, the shared `OverlayStyle` type, and the rule for *why media overlay and frame content overlay are different fields* live in [appearance.md](appearance.md). This doc covers the media-specific surface.

---

## MediaAppearance

```kotlin
@Serializable
@SerialName("MediaAppearance")
data class MediaAppearance(
    override val opacity: Float = 1f,
    override val cornerRadius: Float = 0f,
    override val border: BorderStyle? = null,
    override val shadow: ShadowStyle? = null,

    val crop: CropSettings = CropSettings(),
    val colorAdjustments: MediaColorAdjustments? = null,

    // Object-level overlays. Bounded by this media node only.
    // Ordered list: entry [i] composites above entry [i-1].
    // Element type is the shared OverlayStyle — see appearance.md §7.1.
    val overlays: List<OverlayStyle> = emptyList(),

    // Decorative photo frame *around this single media* (Polaroid, nine-slice).
    // NOT a CanvasNode.Frame; NOT a FrameAppearance — see "Media frame decoration" below.
    val frameDecoration: MediaFrameDecoration? = null,

    val caption: CaptionStyle? = null,
) : NodeAppearance()
```

Stored as a nullable field on `CanvasNode.Media`. `null` = default rendering (no appearance applied). `ignoreUnknownKeys` handles old nodes that predate the field.

`opacity`, `cornerRadius`, `border`, `shadow` are inherited from `NodeAppearance`. `BorderStyle` and `ShadowStyle` are defined in [appearance.md § 7.3](appearance.md#73-borderstyle--shadowstyle).

---

## Object-level overlays

`MediaAppearance.overlays: List<OverlayStyle>` are the **object-level** overlays (per [appearance.md § 2](appearance.md#2-mediaappearance--object-level-styling)). They draw above this media's pixels, bounded by this media's rect, and transform with this media. They do not affect any other node, and they do not depend on frame containment.

The list is **ordered**: entry `[i]` composites above entry `[i-1]`. An empty list (the default) means no overlays. Each entry carries its own `blendMode` and `opacity`. Typical "vintage photo" looks stack a baked texture, a light leak with `Screen`, and a vignette in sequence; modelling overlays as a list keeps that natural without a future schema migration.

For the available `OverlaySource` variants (Solid / Texture / Procedural) and the `NodeBlendMode` enum, see [appearance.md § 7](appearance.md#7-shared-value-types).

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

`CropSettings` is media-specific (it operates on the source pixels of one media node) and stays on `MediaAppearance` rather than the shared base.

---

## Color Adjustments

Parametric color grading applied to the media pixels before the overlay composites on top.

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

For MVP, a single texture/filter entry in `overlays` is the simpler approach. Parametric color adjustments can be added incrementally — the field is nullable.

---

## Media frame decoration

`MediaAppearance.frameDecoration: MediaFrameDecoration?` is the decorative *picture-frame* drawn around a single media node — a Polaroid border, an old-album mat, a wooden frame asset. It belongs to one media node and renders on top of all other media layers.

> ⚠ **Name disambiguation.** "Media frame decoration" here means the decorative photo-frame around *one media object*. It is **not** a `CanvasNode.Frame` and **not** a `FrameAppearance.contentOverlays` entry. The three concepts:
>
> | Field | Owner | Meaning |
> |---|---|---|
> | `MediaAppearance.frameDecoration` | one `CanvasNode.Media` | Decorative picture-frame asset around this single photo (Polaroid, mat, wooden frame). |
> | `FrameAppearance` | one `CanvasNode.Frame` | Styling for a navigation/container frame (its background, contentOverlays, border, title). |
> | `FrameAppearance.contentOverlays` | one `CanvasNode.Frame` | Overlays above the frame's linked contents, clipped to the frame. See [appearance.md § 3](appearance.md#3-frameappearance--containercontent-level-styling). |
>
> The previous name `frameOverlay: FrameOverlay?` is retired in favour of `frameDecoration: MediaFrameDecoration?` to finish disambiguating "frame" — see [data-model.md § Migration Notes](data-model.md#migration-notes).

```kotlin
@Serializable
data class MediaFrameDecoration(
    val assetUri: String,
    val opacity: Float = 1f,
    val mode: MediaFrameDecorationMode = MediaFrameDecorationMode.Stretch,
    // Nine-slice insets — ignored in Stretch mode
    val sliceLeft: Float = 0f,
    val sliceTop: Float = 0f,
    val sliceRight: Float = 0f,
    val sliceBottom: Float = 0f,
    // Usable content area inside the decoration
    // e.g. Polaroid has extra space at bottom for caption
    val contentInsetLeft: Float = 0f,
    val contentInsetTop: Float = 0f,
    val contentInsetRight: Float = 0f,
    val contentInsetBottom: Float = 0f,
)

@Serializable
enum class MediaFrameDecorationMode {
    Stretch,    // PNG stretched over entire object — simple, fine for textures/vignettes
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

## Rendering Pipeline (per media node)

In order:

1. Decode source asset, apply `CropSettings` (mode + focal point).
2. Apply `colorAdjustments` if non-null.
3. Draw each entry of `overlays` in list order — each `OverlayStyle` with its own `OverlaySource`, `blendMode`, and `opacity` per [appearance.md § 7.1](appearance.md#71-overlaystyle). Entry `[i]` composites above entry `[i-1]`.
4. Draw `frameDecoration` (Stretch or NineSlice) on top of overlays.
5. Apply `cornerRadius`, `border`, `shadow`, overall `opacity` (all inherited from `NodeAppearance`).
6. Draw `caption` if present.

**LOD:** At `Stub` or `Preview` detail levels, skip overlay and frame-decoration rendering — show the cropped source only. Full pipeline runs at `Full` detail. At intermediate levels, the renderer may also draw only the first overlay entry (or only entries with non-`Normal` blend that visibly change tone).

This pipeline is **self-contained inside `MediaRenderer`** — it does not need to know about other nodes or about frame containment. That's the operational distinction from `FrameAppearance.contentOverlays`, which need the frame's linked contents to draw correctly (see [appearance.md § 6](appearance.md#6-render-pipeline-implication)).

---

## Style Presets

A complete `MediaAppearance` recipe can be saved as a named preset and applied to other media nodes.

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

Frame presets (saved `FrameAppearance` recipes) are a parallel future feature; the preset mechanism is intentionally per-appearance-type because the editable surfaces differ.

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

## Implementation status

**Landed (model + renderer + editor):**
- `MediaAppearance` data class + `appearance: MediaAppearance?` on `CanvasNode.Media`.
- All shared types: `BorderStyle`, `ShadowStyle`, `OverlayStyle`, `OverlaySource` (Solid / Texture / Procedural), all 7 `NodeBlendMode` values.
- All media-specific value types: `CropSettings`+`CropMode`, `MediaColorAdjustments`, `MediaFrameDecoration`+`MediaFrameDecorationMode`, `CaptionStyle`, `MediaStylePreset`.
- `FullMediaRenderer` paints: surface opacity, cornerRadius (rounded clip), shadow, cropped source (`CropMode` → `ContentScale`), overlay stack, border. Texture overlays load through `rememberOverlayTextureBitmaps` (Coil `SingletonImageLoader.execute` with `allowHardware(false)`, keyed on the unique `textureRefId` set).
- `MediaAppearanceBottomSheet` covers every field: opacity / cornerRadius / crop (mode + focal / manual) / color adjustments / border / shadow / overlays (shared `OverlayListEditor`) / frame decoration / caption. Reached from a `✦ Appearance` entry in `ContextualActionBar` when a single Media is selected. Backed by `CanvasAction.SetMediaAppearance` + `CommandKind.SET_MEDIA_APPEARANCE`; undoable like any other snapshot command.

**Model + editor land, renderer pending:**
- `MediaColorAdjustments` rendering — needs a `ColorMatrix` or shader pass. (Editor sliders persist values.)
- `MediaFrameDecoration` rendering (Stretch + NineSlice asset draw, `contentInsets`). (Editor takes an asset URI + mode + opacity.)
- `CaptionStyle` rendering. (Editor takes text + font + color + show toggle.)
- `CropMode.Manual` pan/zoom (falls back to `Crop` for now; editor sliders persist values).
- LOD-aware overlay drop-out (today: Full = everything, Simplified+ = placeholders).

**No code yet:**
- `frameDecoration` asset picker (current UI is an asset-URI text field rather than a visual browser).
- In-canvas manual-crop handle (the sliders work; gesture-based pan/zoom inside the rect is post-MVP).
- `MediaStylePreset` storage and the `SaveAsPreset` / `ApplyPreset` / `CopyAppearance` / `PasteAppearance` / `ResetAppearance` canvas actions.
- Rendered derivatives (`SaveRenderedDerivative`, `CreateRenderedCopyOnCanvas`, `ReplaceWithRenderedImage`, `SaveToDeviceGallery`).
- AI auto-enhance, background removal, animated overlays, batch preset application, advanced masks.

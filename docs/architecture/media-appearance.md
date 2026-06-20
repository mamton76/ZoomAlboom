# Media Appearance

> Related: [appearance.md](appearance.md) (shared types) | [data-model.md](data-model.md) | [overview.md](overview.md) | [todo.md §20](../todo.md#20-appearance-system-non-destructive-styling) | [PRD §8.7](../product/PRD.md#87-non-destructive-media-appearance)

Non-destructive visual styling for `CanvasNode.Media`. The original source file is never modified.

**Core formula:**
```
source media asset  +  MediaAppearance  =  rendered media object on canvas
```

The same source photo can appear multiple times with different visual styles (normal, sepia, Polaroid frame, school-album border, etc.) because the appearance recipe lives on the canvas node, not on the file.

`MediaAppearance` is one of the two concrete `NodeAppearance` subclasses (alongside `FrameAppearance`). The shared base class, the shared `OverlayStyle` type, and the rule for *why media overlay and frame content overlay are different fields* live in [appearance.md](appearance.md). This doc covers the media-specific surface.

> **Proposed evolution.** The inherited `cornerRadius: Float` will be replaced by `clip: ClipShape` + `alphaMask: AlphaMask?`. See [appearance.md § 12](appearance.md#12-proposed-evolution--clip--alphamask). Not yet implemented.

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

> ⚠ **Name disambiguation.** "Media frame decoration" here means the decorative photo-frame around *one media object*. It is **not** a `CanvasNode.Frame` and **not** an entry in `FrameAppearance.overlays`. The three concepts:
>
> | Field | Owner | Meaning |
> |---|---|---|
> | `MediaAppearance.frameDecoration` | one `CanvasNode.Media` | Decorative picture-frame asset around this single photo (Polaroid, mat, wooden frame). |
> | `FrameAppearance` | one `CanvasNode.Frame` | Styling for a navigation/container frame (its background, overlays, border, title). |
> | `FrameAppearance.overlays` (inherited from base) | one `CanvasNode.Frame` | Overlays above the frame's combined contents output, clipped to the frame. See [appearance.md § 3](appearance.md#3-frameappearance--containercontent-level-styling). |
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
    // The frame's opening (fractions 0..1 of the node edge). The media is drawn
    // ONLY inside this rect, then the decoration PNG draws over the full rect on
    // top — so the photo/video never leaks past the frame. All-zero = no crop.
    val contentInsetLeft: Float = 0f,
    val contentInsetTop: Float = 0f,
    val contentInsetRight: Float = 0f,
    val contentInsetBottom: Float = 0f,
    // Arbitrary (non-rectangular) opening — planned, not yet consumed. When set
    // it OVERRIDES the rectangular contentInset* (oval / arch / torn paper);
    // null = use the rectangular insets above.
    val openingMaskUri: String? = null,
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

1. Decode source asset, apply `CropSettings` (mode + focal point). When `frameDecoration` defines a non-zero opening (`contentInset*`), the source is scaled into and clipped to that **opening rect** rather than the full node rect, so it can't leak past the frame.
2. Apply `colorAdjustments` if non-null.
3. Draw each entry of `overlays` in list order — each `OverlayStyle` with its own `OverlaySource`, `blendMode`, and `opacity` per [appearance.md § 7.1](appearance.md#71-overlaystyle). Entry `[i]` composites above entry `[i-1]`. Overlays are clipped to the same opening rect as the source.
4. Draw `frameDecoration` (Stretch or NineSlice) over the **full** node rect, on top of the opening-clipped media. Composited **after** the `alphaMask` layer (steps 1–3 are masked; the decoration is not) so the frame is never cut by the node's alpha mask.
5. Apply `cornerRadius`, `border`, `shadow`, overall `opacity` (all inherited from `NodeAppearance`).
6. Draw `caption` if present.

> **Opening crop** is rectangular today (`contentInset*`). The planned `openingMaskUri` will override it for arbitrary shapes (oval / arch / torn paper): the media is masked by that asset's white/alpha area instead of a rectangle. Same render hook (`MediaFrameDecoration.openingRect` in `CanvasRenderer`) — the mask is swapped in when present.

**LOD:** At `Stub` or `Preview` detail levels, skip overlay and frame-decoration rendering — show the cropped source only. Full pipeline runs at `Full` detail. At intermediate levels, the renderer may also draw only the first overlay entry (or only entries with non-`Normal` blend that visibly change tone).

This pipeline is **self-contained inside `MediaRenderer`** — it does not need to know about other nodes or about frame containment. That's the operational distinction from frame-level overlays, which need the frame's linked contents to draw correctly (see [appearance.md § 6](appearance.md#6-render-pipeline-implication)).

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
- `FullMediaRenderer` paints: surface opacity, cornerRadius (rounded clip), shadow, cropped source (`CropMode` → `ContentScale`), overlay stack, frame decoration, border. Texture overlays load through `rememberOverlayTextureBitmaps` (Coil `SingletonImageLoader.execute` with `allowHardware(false)`, keyed on the unique `textureRefId` set).
- `MediaFrameDecorationRenderer` paints `frameDecoration` on top of the overlay stack (step 4): `Stretch` scales the asset to the rect; `NineSlice` splits both source and dest by the `slice*` fractions (0..1 of the asset edge), drawing corners unscaled relative to each other and stretching the four edges along one axis so one asset fits any media aspect ratio. The decoration bitmap loads through `rememberDecorationBitmap` (single-asset analogue of `rememberOverlayTextureBitmaps`, ARGB_8888 to preserve transparency).
- `MediaAppearanceBottomSheet` covers every field: opacity / cornerRadius / crop (mode + focal / manual) / color adjustments / border / shadow / overlays (shared `OverlayListEditor`) / frame decoration / caption. Reached from the long-press context-menu popup's `✦ Edit appearance` entry (dispatched via `EditorActionCatalog.EditMediaAppearanceAction` — see [context-menu.md](context-menu.md)) when a single Media is selected. Backed by `CanvasAction.SetMediaAppearance` + `CommandKind.SET_MEDIA_APPEARANCE`; undoable like any other snapshot command.

**Model + editor land, renderer pending:**
- `MediaColorAdjustments` rendering — needs a `ColorMatrix` or shader pass. (Editor sliders persist values.)
- `CaptionStyle` rendering. (Editor takes text + font + color + show toggle.)
- `MediaFrameDecoration.openingMaskUri` — modelled + persisted, not yet consumed. Arbitrary-shape opening that will override the rectangular `contentInset*` crop. (Rectangular opening crop landed; see below.)
- LOD-aware overlay drop-out (today: Full = everything, Simplified+ = placeholders).

**Landed 2026-06-20 (frame decoration):**
- `MediaFrameDecorationEditor` asset picker — SAF `OpenDocument` (`image/*`) with thumbnail + Pick/Replace/Clear, mirroring the mask/overlay pickers (replaced the asset-URI text field). Slice border (NineSlice) and Opening insets are **integer-percent text fields** (`PercentField`, 0–49%). Each of the Slice and Opening sections has its own **"Edit each edge separately"** checkbox toggling between symmetric input (one slice field; Horizontal/Vertical opening fields) and asymmetric input (four `PercentField`s). Each checkbox defaults to that section's actual symmetry in the initial decoration (`isSliceAsymmetric` / `isOpeningAsymmetric`), so opening an asymmetric frame (e.g. Polaroid) doesn't silently flatten it.
- `MediaFrameDecorationRenderer` — `Stretch` + `NineSlice` asset draw; `rememberDecorationBitmap` for the (transparent) asset. Applied by **both** the still-media path (`FullMediaRenderer`) and video (`VideoSurfaceChrome`, live + poster).
- **Opening crop** — `contentInset*` is consumed: the media is scaled into + clipped to the opening rect (`MediaFrameDecoration.openingRect`), the decoration draws over the full rect on top, composited **after** the `alphaMask` layer so the frame isn't cut by the mask. Video clips (does not rescale) its content to the opening — documented limitation. Arbitrary openings (`openingMaskUri`) reuse the same hook later.

**Landed 2026-06-07 (CropEdit slice — see [editor-tools.md § 4.8](editor-tools.md#48-cropedit) and [todo.md § 20.8](../todo.md#208-cropedit-slice--manual-renderer--in-canvas-handles)):**
- `CropMode.Manual` rendering — `FullMediaRenderer.drawCroppedBitmap` reads `crop.{offsetX, offsetY, zoom}` directly; composes with rounded corners, `alphaMask`, `overlays`, border, and shadow per the pipeline above. `zoom = 1` is the Fill scale.
- In-canvas crop handle — `EditorTool.CropEdit` ships with four corner + four edge handles, drag-inside-rect pans the source under the viewport, two-finger pinch zooms source around the centroid, topbar slider provides slider-driven zoom, Cancel reverts the session. Model A — no source-space `cropRect` field; `CropSettings` is unchanged.
- Selection-tool resize on Manual-crop media scales `crop.offsetX/Y` by the same `factor` so the visible source content stays identical (same content, scaled with the rect). Distinct from CropEdit's resize, which holds the source in world coords.

**No code yet:**
- `MediaStylePreset` storage and the `SaveAsPreset` / `ApplyPreset` / `CopyAppearance` / `PasteAppearance` / `ResetAppearance` canvas actions.
- Rendered derivatives (`SaveRenderedDerivative`, `CreateRenderedCopyOnCanvas`, `ReplaceWithRenderedImage`, `SaveToDeviceGallery`).
- AI auto-enhance, background removal, animated overlays, batch preset application, advanced masks.

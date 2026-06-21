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

    // contentMask (arbitrary-shape clip of the CONTENT only) is on NodeAppearance base.
    val crop: CropSettings = CropSettings(),
    val colorAdjustments: MediaColorAdjustments? = null,

    // Object-level overlays. Bounded by this media node only.
    // Ordered list: entry [i] composites above entry [i-1].
    // Element type is the shared OverlayStyle — see appearance.md §7.1.
    val overlays: List<OverlayStyle> = emptyList(),

    // Rectangular content-area slot — the media is resized into it; decorations
    // fill the margin. Distinct from crop (which fits media WITHIN this area).
    val opening: MediaOpening? = null,

    // Ordered stack of decorative visual layers *around this single media*
    // (frame, tape, bow, sticker, label, torn/burnt edge…). NOT CanvasNode.Frames;
    // carry no clip of their own — see "Media decorations" below.
    val decorations: List<MediaDecoration> = emptyList(),

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

## Media opening + decorations

The content model (2026-06-21 refactor): **one** rectangular `opening` (content-area slot) + **one** `contentMask` (arbitrary-shape clip of the content) on the appearance, and a **stack** of pure-visual `decorations`. Decorations carry no clip of their own.

> ⚠ **Name disambiguation.** A `MediaDecoration` is a visual layer around *one media object*. It is **not** a `CanvasNode.Frame` and **not** an entry in `FrameAppearance.overlays`:
>
> | Field | Owner | Meaning |
> |---|---|---|
> | `MediaAppearance.decorations` | one `CanvasNode.Media` | Stack of decorative visual layers (frame, tape, sticker, label, torn/burnt edge…). |
> | `FrameAppearance` | one `CanvasNode.Frame` | Styling for a navigation/container frame (its background, overlays, border, title). |
> | `FrameAppearance.overlays` | one `CanvasNode.Frame` | Overlays above the frame's combined contents, clipped to the frame. See [appearance.md § 3](appearance.md#3-frameappearance--containercontent-level-styling). |

```kotlin
@Serializable
data class MediaDecoration(            // a pure visual layer; carries no clip
    val id: String,                    // stable — reorder + future item-level overrides
    val assetUri: String,
    val opacity: Float = 1f,
    val mode: MediaDecorationMode = MediaDecorationMode.Stretch,
    val placement: DecorationPlacement = DecorationPlacement.Above,  // Above | Below media
    val sliceLeft: Float = 0f,         // nine-slice insets (fractions 0..1) — Stretch ignores
    val sliceTop: Float = 0f,
    val sliceRight: Float = 0f,
    val sliceBottom: Float = 0f,
)

@Serializable enum class MediaDecorationMode { Stretch, NineSlice }
@Serializable enum class DecorationPlacement { Above, Below }

@Serializable
data class MediaOpening(               // rectangular content-area slot (resize); no mask
    val insetLeft: Float = 0f,
    val insetTop: Float = 0f,
    val insetRight: Float = 0f,
    val insetBottom: Float = 0f,
)
```

**Nine-slice layout** (per decoration): corners drawn uniform-scaled (keeping aspect), the four edges stretched one axis only, centre stretched both. Corners never distort.

---

## Rendering Pipeline (per media node)

Five stages — node rect → `opening` (resize) → `crop` (fit within) → `contentMask` (shape) → `decorations` (layers). In order:

1. Decode source, apply `CropSettings` (mode + focal point), fitted into the `opening` rect (rectangular resize) when one is set — so the media fills the content-area slot, leaving the margin for decorations.
2. Apply `colorAdjustments` if non-null.
3. Draw `overlays` in list order (each `OverlayStyle` with its source / blend / opacity, clipped to the opening rect).
4. **Content mask** — `contentMask` DstIn over the content (media + overlays) in the offscreen layer. Clips the content only.
5. **Decorations** — `Below`-placement layers under the content, `Above` over it. Above layers composite **after** the mask, so they're never cut. Render order overall: `Below` → content(opening+crop+contentMask) → overlays → `Above` → border. *(Slice-0 limitation: a `Below` layer is also cut when a `contentMask` is present — content-scoped masking is a follow-up.)*
6. Apply `cornerRadius`, `border`, `shadow`, overall `opacity` (inherited from `NodeAppearance`). Draw `caption` if present.

> **Opening vs. crop vs. contentMask:** `opening` = *where/how big* the content area is (rectangular resize slot); `crop` = *how* the media fits inside it; `contentMask` = arbitrary-shape clip of the content. Arbitrary opening shapes go through `contentMask` — there is no opening-specific mask.

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
- All media-specific value types: `CropSettings`+`CropMode`, `MediaColorAdjustments`, `MediaDecoration`+`MediaDecorationMode`+`DecorationPlacement`, `MediaOpening`, `CaptionStyle`, `MediaStylePreset`.
- `FullMediaRenderer` paints: surface opacity, cornerRadius (rounded clip), shadow, Below decorations, cropped source (`CropMode` → `ContentScale`, fitted into `opening`), overlay stack, `contentMask` (DstIn), Above decorations, border. Texture overlays load through `rememberOverlayTextureBitmaps`.
- `MediaDecorationRenderer` paints each `MediaDecoration` (`drawDecoration`): `Stretch` scales the asset to the rect; `NineSlice` splits source + dest by the `slice*` fractions, drawing corners uniform-scaled (keeping aspect) and stretching the four edges one axis only so one asset fits any media aspect ratio. Bitmaps load through `rememberDecorationBitmaps` (per-stack, ARGB_8888 to preserve transparency).
- Per-concept editors cover every field: opacity / cornerRadius / crop / color adjustments / content mask / border / shadow / overlays / **opening** / **decorations** / caption — each a `ConceptEditorSheet` opened from a long-press context-menu action (see [context-menu.md](context-menu.md)). Backed by `CanvasAction.SetMediaAppearance`; undoable like any other snapshot command.

**Model + editor land, renderer pending:**
- `MediaColorAdjustments` rendering — needs a `ColorMatrix` or shader pass. (Editor sliders persist values.)
- `CaptionStyle` rendering. (Editor takes text + font + color + show toggle.)
- LOD-aware overlay drop-out (today: Full = everything, Simplified+ = placeholders).

**Landed 2026-06-21 — content-model refactor (Slice 0; graduated from `to_discuss.md § 19`):**

Reshaped the appearance model so presets (`media-presets.md`) and a decoration *stack* aren't blocked. (Supersedes the 2026-06-20 single-`frameDecoration` + `openingMaskUri` shipment — those names are gone.)
- `NodeAppearance.alphaMask` → **`contentMask`** (clips the object's *content*, not the whole node — decorations are independent; **no whole-node mask**). Field-only rename; the value type `AlphaMask` + `AlphaMaskRenderer` / `AlphaMaskEditor` keep their names.
- `MediaAppearance.frameDecoration: MediaFrameDecoration?` → **`decorations: List<MediaDecoration>`** (pure visual layers — frame / tape / sticker / label / torn-paper / ticket; each `id` + `assetUri` + `opacity` + `mode` + `slice*` + `placement: Above|Below`).
- New **`opening: MediaOpening?`** (rectangular insets only). **`openingMaskUri` removed** — arbitrary opening shapes go through `contentMask`.
- `MediaDecorationRenderer` (renamed from `MediaFrameDecorationRenderer`): `drawDecoration` per layer + `rememberDecorationBitmaps` (stack). `FullMediaRenderer` + `VideoSurfaceChrome` draw Below → content (opening + crop + contentMask) → overlays → Above → border. Video rescales into the opening (`contentTargetRect`).
- Editor: `DecorationListEditor` (add/remove/reorder + per-item `MediaDecorationEditor` with placement toggle) + `MediaOpeningEditor`; context menu actions **Content mask / Opening / Decorations**.
- **No `@SerialName`, no migration** (no important existing projects): old `alphaMask` / `frameDecoration` / `openingMaskUri` JSON keys are ignored on load (`ignoreUnknownKeys`), new fields default.
- **Slice-0 limitation:** a `Below` decoration is also cut when a `contentMask` is present (content-scoped masking — drawing Below outside the mask layer — is a follow-up). `Above` is never cut.
- **Deferred:** item-level stack overrides (local additive layer / hide-show / reorder / per-layer opacity vs. an inherited preset stack) — feasible later because decorations carry stable `id`s; a local additive layer is a distinct override kind from a scalar override (`media-presets.md § 6`).

**Landed 2026-06-07 (CropEdit slice — see [editor-tools.md § 4.8](editor-tools.md#48-cropedit) and [todo.md § 20.8](../todo.md#208-cropedit-slice--manual-renderer--in-canvas-handles)):**
- `CropMode.Manual` rendering — `FullMediaRenderer.drawCroppedBitmap` reads `crop.{offsetX, offsetY, zoom}` directly; composes with rounded corners, `contentMask`, `overlays`, border, and shadow per the pipeline above. `zoom = 1` is the Fill scale.
- In-canvas crop handle — `EditorTool.CropEdit` ships with four corner + four edge handles, drag-inside-rect pans the source under the viewport, two-finger pinch zooms source around the centroid, topbar slider provides slider-driven zoom, Cancel reverts the session. Model A — no source-space `cropRect` field; `CropSettings` is unchanged.
- Selection-tool resize on Manual-crop media scales `crop.offsetX/Y` by the same `factor` so the visible source content stays identical (same content, scaled with the rect). Distinct from CropEdit's resize, which holds the source in world coords.

**No code yet:**
- `MediaStylePreset` storage and the `SaveAsPreset` / `ApplyPreset` / `CopyAppearance` / `PasteAppearance` / `ResetAppearance` canvas actions.
- Rendered derivatives (`SaveRenderedDerivative`, `CreateRenderedCopyOnCanvas`, `ReplaceWithRenderedImage`, `SaveToDeviceGallery`).
- AI auto-enhance, background removal, animated overlays, batch preset application, advanced masks.

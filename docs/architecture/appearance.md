# Node Appearance System

> Related: [data-model.md](data-model.md) | [media-appearance.md](media-appearance.md) | [background.md](background.md) | [rendering.md](rendering.md) | [frame-membership.md](frame-membership.md)

The appearance system holds non-destructive visual styling for any `CanvasNode`. Each node variant owns a typed appearance container; the base class carries the cross-cutting properties that have the same meaning for every kind of node (opacity, corner radius, border, shadow, overlays). Shared *value* types (overlay sources, borders, shadows, blend modes) are defined once and reused.

Each subclass extends the base with its own type-specific fields: `MediaAppearance` adds crop / color adjustments / frame decoration / caption; `FrameAppearance` adds background / title style / content effect. The renderer dispatches by node type, so the base-level fields can be uniform (one field name) while the rendering pipeline differs per type — e.g. `overlays` paint above a media's pixels for `CanvasNode.Media` and above the frame's combined children for `CanvasNode.Frame`.

---

## 1. The base — `NodeAppearance`

```kotlin
@Serializable
sealed class NodeAppearance {
    abstract val opacity: Float                          // 0..1, applied to the node's own rendered output
    abstract val cornerRadius: Float                     // world units; affects border / clipping
    abstract val overlays: List<OverlayStyle>            // ordered; entry [i] composites above entry [i-1]
    abstract val border: BorderStyle?
    abstract val shadow: ShadowStyle?
}
```

`NodeAppearance` carries the cross-cutting properties: opacity of the node's own rendered output, corner rounding of the node's rectangle, an ordered list of overlays painted above the node's output (clipped to the node's rect), border drawn on the node's rectangle, drop shadow cast by the node's rectangle.

The `overlays` semantic is uniform but the renderer's pipeline position differs per node type:

- On a `CanvasNode.Media`, `overlays` paint above the cropped photo pixels, bounded by the media rect — object-level layering.
- On a `CanvasNode.Frame`, `overlays` paint above the frame's *combined* output (background + every member node, each with its own per-media overlays), clipped to the frame rect — container/content-level layering. Requires the layered frame renderer; see [§ 6](#6-render-pipeline-implication).

The renderer dispatches by node type — the data model is one field. See [§ 13 — Design history](#13-design-history--overlay-unification) for the prior two-field design.

> **Proposed evolution.** `cornerRadius: Float` will be replaced by `clip: ClipShape` + `alphaMask: AlphaMask?`. See [§ 12](#12-proposed-evolution--clip--alphamask). Not yet implemented; the rest of this doc describes shipped state.

---

## 2. `MediaAppearance` — object-level styling

```kotlin
@Serializable
@SerialName("MediaAppearance")
data class MediaAppearance(
    override val opacity: Float = 1f,
    override val cornerRadius: Float = 0f,
    // Object-level overlays inherited from NodeAppearance — above this media's
    // pixels, bounded by the media rect. Ordered list, declaration-order
    // compositing. Empty = no overlays. See §7.1 for the OverlayStyle type.
    override val overlays: List<OverlayStyle> = emptyList(),
    override val border: BorderStyle? = null,
    override val shadow: ShadowStyle? = null,

    // Layout of the source pixels inside the node's bounding rect.
    val fitMode: MediaFitMode = MediaFitMode.Cover,
    val crop: CropRect? = null,
    val objectPosition: ObjectPosition = ObjectPosition.Center,

    // Color transform applied to the media itself (sepia, brightness, etc.).
    val visualFilter: VisualFilter? = null,

    // Parametric color grading (brightness/contrast/saturation/...).
    val adjustments: ImageAdjustments = ImageAdjustments(),
) : NodeAppearance()
```

Stored on `CanvasNode.Media` as a nullable field (`null` = default rendering). See [media-appearance.md](media-appearance.md) for crop, fit, filter, derived-image, and preset detail.

**`overlays` on a media node — object-level overlays.**

| Property | Value |
|---|---|
| Owner | A single `CanvasNode.Media` |
| Bounds | The media node's own rect |
| Transforms with | The media node (pan / scale / rotate together) |
| Knows about other nodes? | No |
| Depends on frame containment? | No |
| Renders inside | `MediaRenderer` (single-pass) |
| Ordering | Ordered list: entry `[i]` composites above entry `[i-1]`. Empty list = no overlays. |

Use cases: a single light leak on one photo; a vignette on one photo; stacked looks such as paper grain + light leak + vignette on the same photo (each layer drawn in declaration order with its own blend mode).

---

## 3. `FrameAppearance` — container/content-level styling

```kotlin
@Serializable
@SerialName("FrameAppearance")
data class FrameAppearance(
    override val opacity: Float = 1f,
    override val cornerRadius: Float = 0f,
    // Container/content-level overlays inherited from NodeAppearance — above the
    // frame's combined output (background + members), clipped to frame bounds.
    // Ordered list, declaration-order compositing. Empty = no overlays.
    override val overlays: List<OverlayStyle> = emptyList(),
    override val border: BorderStyle? = null,
    override val shadow: ShadowStyle? = null,

    // Background drawn behind the frame's linked contents.
    val background: BackgroundData? = null,

    // Future: true off-screen effect applied to rendered frame contents
    // (sepia / blur / grayscale of *everything inside* this frame). Not MVP.
    val contentEffect: FrameContentEffect? = null,

    val titleStyle: FrameTitleStyle? = null,
) : NodeAppearance()
```

Stored on `CanvasNode.Frame` as a nullable field (`null` = default rendering). `background` migrates from the current top-level `Frame.background` — see [data-model.md § Migration Notes](data-model.md#migration-notes) and [background.md](background.md).

**`overlays` on a frame node — container/content-level overlays.**

| Property | Value |
|---|---|
| Owner | A single `CanvasNode.Frame` |
| Bounds | The frame's rect (clipped) |
| Transforms with | The frame |
| Affects | Every node bound to this frame (members, see [frame-membership.md](frame-membership.md)) |
| Mutates child nodes? | **No.** Children keep their own appearance untouched. |
| Depends on frame–content binding? | Yes — only meaningful for nodes that are members of this frame |
| Renders inside | Layered frame renderer (see [§6 — Render pipeline](#6-render-pipeline-implication)) |
| Ordering | Ordered list: entry `[i]` composites above entry `[i-1]`. Empty list = no overlays. |

Use cases: subtle old-paper tint over every photo inside a frame; translucent watercolor wash; dark-glass dimming over the frame's contents; uniform paper grain over a whole scrapbook page; stacks like grain + tint + vignette over a single frame's contents.

**Frame `overlays` are not `contentEffect`.** `overlays` are extra layers composited *above* the rendered contents — children render exactly as they would standalone, then the overlays draw on top in list order. `contentEffect` (future) is an off-screen pass that re-renders the contents through a filter (sepia, blur, grayscale of everything inside). Both leave child node data unchanged; only the rendered frame output differs.

---

## 4. Overlay semantics — one field, two render-pipeline positions

`overlays` is a single field on `NodeAppearance` (see § 1), but the renderer treats it differently per node type because "above this node's own rendered output" means different things for a leaf and a container:

- **Media node.** The "rendered output" is the cropped photo pixels. Overlays paint above those pixels, bounded by the media rect. Composited inside the single-pass `MediaRenderer`. Depends only on the media node itself.
- **Frame node.** The "rendered output" is the frame's *combined* surface — background + every member node + each member's own per-media overlays. Frame overlays paint above that combined surface, clipped to the frame rect. Composited inside the layered frame renderer (§ 6). Depends on the frame's effective member set (see [frame-membership.md](frame-membership.md)).

The renderer dispatches by node type, so the data model can carry a single uniform field while the rendering code chooses the right pipeline position. The shared `OverlayStyle` element type (§ 7.1) and the shared `drawOverlayStack` helper keep the per-type renderers thin.

---

## 5. Future appearance variants

When a new `*Appearance` subtype lands (e.g. a future `WidgetAppearance`), it inherits `overlays` from `NodeAppearance` with the natural semantic — *above this node's own rendered output*. The base-level fields are the right hook for surface-level overlays on any node type.

If a variant ever needs an *additional* overlay surface with different semantics (e.g. a payload-vs-chrome distinction on a widget), that surface should be a new type-specific field on the variant, not a renamed base field. The renderer's per-type dispatch keeps multiple overlay surfaces unambiguous as long as each lives on its own typed field.

---

## 6. Render pipeline implication

A frame with any `overlays` (or, in the future, a `contentEffect`) can no longer be rendered as a single ordinary `CanvasNode` pass — its contents must be composited between two of its own layers. Conceptually:

```
1. Album background
     (camera- or world-locked; see background.md)

2. Frame background layer
     frame.appearance.background

3. Linked frame contents
     every node bound to this frame, in z-order
     (each child still draws its own MediaAppearance, including its overlays)

4. Frame overlay layer
     frame.appearance.overlays, drawn in list order
     clipped to frame bounds, each entry composited above the previous one

5. Frame decoration layer
     frame.appearance.border, frame.appearance.titleStyle,
     selection handles / editor overlays (Edit mode only)
```

`contentEffect`, when added later, sits between (3) and (4) as an off-screen pass that re-renders the contents through a filter before the overlays composite on top.

The simple per-node `CanvasNodeRenderer` pass used today handles step (2) trivially (it's the existing `Frame.background` render), and step (5) trivially (border + label), but not (3) + (4) together: the renderer needs to know which children belong to the frame and must draw them between the frame's two own layers. This is what makes frame overlays strictly more than a node-local effect.

**Status — what's wired today.** The layered renderer ships. `CanvasScreen` walks `FramePaintEvent`s built from the visible-nodes set: every layered frame paints its background on the Surface phase at `frame.zIndex`, members draw in z-order, then the overlay phase paints `overlays + border` just past `max(memberZ, frame.zIndex)`. Membership uses [frame-membership.md](frame-membership.md). Plain frames (no `overlays`) still paint in a single pass.

`FrameContentEffect` is the one piece still field-only: the sealed class has no variants yet and the off-screen filter pass is post-MVP. `contentEffect` deserializes and round-trips but renders as a no-op until that slice lands.

---

## 7. Shared value types

### 7.1 OverlayStyle

```kotlin
@Serializable
data class OverlayStyle(
    val source: OverlaySource,
    val opacity: Float = 0.2f,
    val blendMode: NodeBlendMode = NodeBlendMode.Normal,
)

@Serializable
sealed class OverlaySource {
    @Serializable @SerialName("SolidColor")
    data class SolidColor(val color: String) : OverlaySource()

    @Serializable @SerialName("Texture")
    data class Texture(
        val textureRefId: String,
        val tile: TileData = TileData(),
    ) : OverlaySource()

    @Serializable @SerialName("Procedural")
    data class Procedural(
        val pattern: ProceduralPattern,
        val fillColor: String? = null,
    ) : OverlaySource()
}
```

`OverlayStyle` is the element type carried by `NodeAppearance.overlays` (inherited by both `MediaAppearance` and `FrameAppearance`). The shape mirrors `BackgroundData` (Solid / Texture / Procedural) on purpose: paper grain that works as a background also works as an overlay, just composited differently. `TileData` and `ProceduralPattern` are the same types as in [background.md](background.md).

`overlays` is a `List<OverlayStyle>` with **declaration-order compositing** — entry `[i]` draws on top of entry `[i-1]`. An empty list is the default ("no overlays"). The renderer iterates the list inside the active scope (media rect for media nodes, frame-clip rect for frame nodes) and applies each entry's `blendMode` and `opacity`. The same `drawOverlayStack` helper serves both scopes.

### 7.2 NodeBlendMode

```kotlin
@Serializable
enum class NodeBlendMode {
    Normal,
    Multiply,
    Screen,
    Overlay,
    SoftLight,
    Darken,
    Lighten,
}
```

The model lists every mode we expect to need. The renderer is free to ship `Normal` (and optionally `Multiply`) first; later modes light up as the renderer learns them. Unknown values during deserialization should not crash — `ignoreUnknownKeys` handles it.

### 7.3 BorderStyle / ShadowStyle

```kotlin
@Serializable
data class BorderStyle(
    val color: String,        // hex
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

Reused by both `MediaAppearance` and `FrameAppearance` unchanged.

---

## 8. Terminology — pick one word per concept

These phrases mean different things. Use them consistently in code, comments, and UI labels.

| Term | Where it lives | What it draws |
|------|----------------|---------------|
| **Frame background** | `FrameAppearance.background` | Layer *behind* the frame's linked contents. Solid / texture / procedural / gradient / watercolor. |
| **Overlays (on a media node)** | `MediaAppearance.overlays: List<OverlayStyle>` (inherited from base) | Layers *above* one media node only. Bounded by the media rect. Ordered list — entry `[i]` over entry `[i-1]`. Examples: light leak, vignette, scratches, paper grain on a single photo (and stacks of those). |
| **Overlays (on a frame node)** | `FrameAppearance.overlays: List<OverlayStyle>` (inherited from base) | Layers *above* the frame's combined output (background + members), clipped to the frame. Ordered list — entry `[i]` over entry `[i-1]`. Examples: subtle old-paper tint over every photo inside a frame, translucent watercolor wash, dark glass, or stacks of those. |
| **Frame contentEffect** *(future)* | `FrameAppearance.contentEffect` | True off-screen pass applied to the *rendered* frame contents (sepia/blur/grayscale of everything inside). Not MVP. Distinct from frame overlays, which only composite new layers on top. |
| **Media frame decoration** | `MediaAppearance.frameDecoration: MediaFrameDecoration?` | Decorative picture-frame asset around a single media node (Polaroid border, mat, wooden frame, nine-slice). NOT a `CanvasNode.Frame`, NOT a `FrameAppearance`. See [media-appearance.md](media-appearance.md#media-frame-decoration). |

---

## 9. Persistence

`MediaAppearance` and `FrameAppearance` are nullable fields on their owning `CanvasNode` variant. `null` = default rendering, omitted from JSON. `ignoreUnknownKeys` lets old scene graphs deserialize cleanly while the model is extended.

Polymorphism uses kotlinx-serialization's standard `@SerialName` discriminator. `OverlaySource` and `ProceduralPattern` are sealed classes with discriminator names that match the on-disk wire format (`"Solid"`, `"Texture"`, `"Procedural"`, etc.) — same convention as [background.md § Notes](background.md#domain-types).

---

## 10. Implementation status

What this slice landed:

- Shared types: `NodeAppearance` (with unified `overlays: List<OverlayStyle>` on base), `OverlayStyle`, `OverlaySource` (Solid / Texture / Procedural), `NodeBlendMode` (all 7 values mapped to Compose `BlendMode`), `BorderStyle`, `ShadowStyle`.
- `FrameAppearance` and `MediaAppearance` data classes with their per-type fields; nullable `appearance` field on `CanvasNode.Frame` and `CanvasNode.Media`.
- Scene-graph serializer migrations: lifts legacy top-level `Frame.background` into `appearance.background` on read; lifts legacy `FrameAppearance.contentOverlays` into the unified `overlays` field.
- Media renderer paints `overlays`, `border`, `shadow`, `cornerRadius`, surface `opacity`, and `crop.mode` (Fit / Fill / Stretch).
- Layered frame renderer paints background → members → `overlays` → border, driven by `FrameMembershipUseCase.effectiveMembers`.
- `DrawScope.drawOverlayStack` helper shared between the two scopes.

Still pending (each has its own todo entry under [§ 20](../todo.md#20-appearance-system-non-destructive-styling)):

- Renderer for `MediaAppearance.frameDecoration` (NineSlice asset draw) and `MediaAppearance.caption` (editor UI persists both today).
- Renderer for `MediaColorAdjustments` — `Compose ColorMatrix` or shader; editor sliders persist values for it.
- `CropMode.Manual` pan/zoom inside bounds — editor sliders persist values; renderer falls back to Fill.
- Variants and rendering for `FrameContentEffect` (sepia / blur / grayscale off-screen pass).
- In-canvas crop handle (the manual-mode sliders live in the sheet; gesture-based pan/zoom inside the rect is post-MVP).
- `frameDecoration` asset picker (current editor is asset-URI text field + mode dropdown).
- Style-preset library (`MediaStylePreset` storage, copy/paste appearance, save-as-preset action).
- LOD-aware overlay drop-out at intermediate zoom.

---

## 11. Short rule

> `NodeAppearance.overlays` is a single `List<OverlayStyle>` field on the base.
> On a media node it paints above the photo pixels.
> On a frame node it paints above the frame's combined contents, clipped to the frame.
> The renderer dispatches by node type — the data model is uniform.

---

## 12. Proposed evolution — `clip` + `alphaMask`

> **Status — proposal.** Not yet implemented. Replaces `NodeAppearance.cornerRadius: Float`. Migration is a one-shot read-time lift in `SceneGraphSerializer`; no runtime feature flag.

### 12.1 Why two fields, not one

`cornerRadius` answers a single question: "give the node's rectangle rounded corners." A general visibility model needs to answer two questions, and they have different costs:

1. **What shape is the rendered output clipped to?** (Binary on/off per pixel — `clipRect` / `clipPath`. Cheap.)
2. **What continuous alpha is applied within that shape?** (0..1 per pixel — requires an offscreen compositing layer with `BlendMode.DstIn`. Expensive.)

A unified field would conflate the cheap geometric case (picking `Ellipse` for a circular photo) with the expensive continuous case (adding a vignette mask) — easy to confuse, easy to accidentally upgrade rendering cost. Keeping them as separate fields means the geometric path never pays for an offscreen layer, and the alpha path is an explicit second knob that composes on top: clip first, then alpha mask operates within the clipped region.

### 12.2 Model

```kotlin
@Serializable
sealed class NodeAppearance {
    abstract val opacity: Float
    abstract val clip: ClipShape           // default = RoundedRect(0); supersedes cornerRadius
    abstract val alphaMask: AlphaMask?     // null = no alpha mask (default)
    abstract val border: BorderStyle?
    abstract val shadow: ShadowStyle?
}

@Serializable
sealed class ClipShape {
    @Serializable @SerialName("RoundedRect")
    data class RoundedRect(val cornerRadius: Float = 0f) : ClipShape()

    @Serializable @SerialName("PerCornerRoundedRect")
    data class PerCornerRoundedRect(
        val topLeft: Float = 0f,
        val topRight: Float = 0f,
        val bottomRight: Float = 0f,
        val bottomLeft: Float = 0f,
    ) : ClipShape()

    @Serializable @SerialName("Ellipse")
    data object Ellipse : ClipShape()
}

@Serializable
data class AlphaMask(
    val source: AlphaMaskSource,
    val invert: Boolean = false,           // flip "white = opaque" ↔ "white = transparent"
)

@Serializable
sealed class AlphaMaskSource {
    @Serializable @SerialName("Image")
    data class Image(
        val maskRefId: String,                              // FK to media_library — reuses existing table
        val channel: MaskChannel = MaskChannel.Luminance,   // Luminance: BW PNG; Alpha: transparent PNG
        val fitMode: MaskFitMode = MaskFitMode.Stretch,
    ) : AlphaMaskSource()

    @Serializable @SerialName("LinearGradient")
    data class LinearGradient(
        val angleDeg: Float = 0f,                           // 0 = left→right, 90 = top→bottom
        val stops: List<GradientStop>,
    ) : AlphaMaskSource()

    @Serializable @SerialName("RadialGradient")
    data class RadialGradient(
        val centerX: Float = 0.5f,                          // 0..1 relative to bounds
        val centerY: Float = 0.5f,
        val radiusX: Float = 0.5f,                          // 0..1 relative to bounds.width
        val radiusY: Float = 0.5f,                          // 0..1 relative to bounds.height
        val stops: List<GradientStop>,
    ) : AlphaMaskSource()

    @Serializable @SerialName("Procedural")
    data class Procedural(val pattern: ProceduralPattern) : AlphaMaskSource()
}

@Serializable
data class GradientStop(
    val position: Float,    // 0..1
    val alpha: Float,       // 0..1 (the mask value at this stop)
)

@Serializable enum class MaskChannel { Luminance, Alpha }
@Serializable enum class MaskFitMode { Stretch, Fit, Fill }
```

`AlphaMaskSource` deliberately mirrors `OverlaySource` (see [§ 7.1](#71-overlaystyle)). The shape — sealed `source` discriminator with Image / Gradient / Procedural variants — is the third place in the codebase using this pattern (`BackgroundData`, `OverlaySource`, `AlphaMaskSource`). `ProceduralPattern` is the **same** sealed type already used by overlays and backgrounds (see [background.md § Procedural Patterns](background.md#procedural-patterns)) — the mask renderer extracts luminance from the rendered pattern. No new procedural type.

`MaskChannel` is meaningful only for `Image` sources. Gradients specify alpha directly via `GradientStop`; procedural patterns produce grayscale that the renderer reads as alpha.

### 12.3 MVP variants and cost

| Combination | Renderer | Cost |
|---|---|---|
| `clip = RoundedRect(0)`, no alphaMask | `clipRect` | Free — same as today |
| `clip = RoundedRect(r > 0)`, no alphaMask | `clipPath(addRoundRect)` | Cheap — same as today |
| `clip = PerCornerRoundedRect(...)`, no alphaMask | `clipPath(addRoundRect)` with per-corner ctor | Cheap |
| `clip = Ellipse`, no alphaMask | `clipPath(addOval)` | Cheap |
| Any clip + `alphaMask.LinearGradient` / `RadialGradient` | Offscreen layer + `Brush.*Gradient` + `BlendMode.DstIn` | One layer per node, no asset load |
| Any clip + `alphaMask.Image` | Offscreen layer + bitmap (Coil) + `BlendMode.DstIn` | One layer + asset load |
| Any clip + `alphaMask.Procedural` | Offscreen layer + procedural pattern render + `BlendMode.DstIn` | One layer, no asset load |

The fast path (no alphaMask) is unchanged from today. Offscreen-layer cost is the floor for any non-binary alpha and only paid when `alphaMask != null`.

### 12.4 Renderer

`CanvasRenderer.kt:465` `withRoundedClip` becomes `withClipAndMask`:

```kotlin
private inline fun DrawScope.withClipAndMask(
    bounds: Rect, clip: ClipShape, alphaMask: AlphaMask?,
    maskBitmap: ImageBitmap?,          // pre-resolved by the renderer for Image sources
    block: DrawScope.() -> Unit,
) {
    val drawClipped: DrawScope.() -> Unit = {
        when (clip) {
            is RoundedRect if clip.cornerRadius == 0f -> clipRect(bounds) { block() }
            is RoundedRect              -> clipPath(roundRectPath(bounds, clip.cornerRadius)) { block() }
            is PerCornerRoundedRect     -> clipPath(perCornerRoundRectPath(bounds, clip)) { block() }
            Ellipse                     -> clipPath(ovalPath(bounds)) { block() }
        }
    }
    if (alphaMask == null) {
        drawClipped()
    } else {
        // CompositingStrategy.Offscreen forces a layer so DstIn operates on the just-drawn node,
        // not the canvas behind it.
        drawIntoOffscreenLayer(bounds) {
            drawClipped()
            drawAlphaMask(alphaMask, bounds, maskBitmap)
        }
    }
}

private fun DrawScope.drawAlphaMask(mask: AlphaMask, bounds: Rect, bmp: ImageBitmap?) {
    val brushOrBitmap = when (val s = mask.source) {
        is Image -> /* draw bmp with optional luminance-to-alpha ColorFilter and BlendMode.DstIn */
        is LinearGradient -> Brush.linearGradient(s.stops.toColorStops(mask.invert),
                                                  start = startFromAngle(s.angleDeg, bounds),
                                                  end   = endFromAngle(s.angleDeg, bounds))
        is RadialGradient -> Brush.radialGradient(s.stops.toColorStops(mask.invert),
                                                  center = Offset(s.centerX * bounds.width + bounds.left,
                                                                  s.centerY * bounds.height + bounds.top),
                                                  radius = (s.radiusX * bounds.width).coerceAtLeast(1f))
        is Procedural    -> proceduralBrush(s.pattern, bounds)
    }
    /* drawRect(brush = ..., blendMode = DstIn, ...) */
}
```

Elliptical radial gradient (`radiusX ≠ radiusY`) wraps the brush draw in `scale(1f, radiusY / radiusX)` to stretch the circular brush into an ellipse along Y. Minor extra step.

`GradientStop.alpha` maps to `Color.White.copy(alpha = stop.alpha)` when building the brush — the DstIn blend uses the color's alpha channel.

### 12.5 Knock-ons

- **Border** — today strokes a rounded rect. With non-rect clips, the border follows the clip path (`drawPath(path, style = Stroke(...))`). Cheap. For nodes with `alphaMask != null`, the border still strokes the **clip** outline (not the mask silhouette) — image masks don't have a vector outline to stroke.
- **Shadow** — built from the clip path's outline, blurred via `BlurMaskFilter`. For image/procedural alpha masks, the shadow uses the clip rect (no silhouette extraction in MVP). Users can disable shadow when it looks wrong; silhouette shadows are post-MVP.
- **Hit-test** — stays AABB. Tapping a pixel that the mask hides should still select the node. Mask is purely visual.
- **LOD** — at `Preview` / `Simplified` tiers, drop the offscreen layer entirely and render the unmasked node. Alpha mask only at `Full` tier. Same dropout pattern as overlays.
- **Mask asset storage** — `Image.maskRefId` is the same FK type as `Media.mediaRefId`. Reuses `media_library`; no new table.
- **Mask asset loading** — Coil through `SingletonImageLoader.execute(request)` with `allowHardware(false)`, keyed on `maskRefId`. Same path as overlay textures and frame-decoration assets.
- **`MediaAppearance.crop` is unaffected.** Crop selects source pixels; clip + alphaMask shape the output. They compose: crop first, clip second, alphaMask third.

### 12.6 Migration

Legacy JSON `{ "cornerRadius": 12.0, ... }` lifts on read in `SceneGraphSerializer` to `{ "clip": { "type": "RoundedRect", "cornerRadius": 12.0 }, ... }`. Same pattern as the existing `Frame.background → appearance.background` migration described in [data-model.md § Migration Notes](data-model.md#migration-notes).

After migration, `cornerRadius: Float` is removed from `NodeAppearance`, `MediaAppearance`, `FrameAppearance`, and the renderer. No deprecated alias.

### 12.7 Editor UX

Lines up with the proposed per-concept popup direction (see [to_discuss.md § 1](../to_discuss.md#1-tablet-vs-phone-editor-split)):

- `Edit clip shape` popup — shape picker (RoundedRect / PerCornerRoundedRect / Ellipse) with conditional sub-fields (uniform radius vs. four per-corner radii vs. nothing).
- `Edit alpha mask` popup — source picker (Image / LinearGradient / RadialGradient / Procedural) with per-source sub-editors:
  - `Image` → asset thumbnail picker from media library, channel toggle, fit-mode dropdown.
  - `LinearGradient` → angle dial, stops list (add / remove / drag positions, alpha sliders).
  - `RadialGradient` → center XY sliders, radiusX/radiusY sliders, stops list.
  - `Procedural` → reuse `ProceduralPatternEditor.kt` (already exists for backgrounds/overlays).
  - `invert` checkbox at the top level.

Both popups use the shared content composable pattern (`feature/<name>/ui/content/`), wrappable as panel section (tablet) or popup (phone).

### 12.8 Implementation order

1. **Model + serializer migration.** Add `ClipShape`, `AlphaMask`, `AlphaMaskSource`, `GradientStop`, `MaskChannel`, `MaskFitMode` to `domain/model/`. Replace `cornerRadius` on the three appearance classes. Add migration lift in `SceneGraphSerializer`. Behavior-preserving — existing albums look identical.
2. **Geometric clip variants.** `Ellipse` and `PerCornerRoundedRect` rendering (cheap clip-path calls). Editor: shape picker. No alpha mask yet.
3. **Gradient alpha masks.** `LinearGradient` and `RadialGradient` — offscreen layer + brush draw with DstIn. Editor: gradient stops editor. The cheap procedurals: no asset loading.
4. **Image alpha mask.** Coil-loaded bitmap + DstIn. Editor: asset picker, channel toggle.
5. **Procedural alpha mask.** Reuse `ProceduralPattern` + `ProceduralPatternEditor.kt`.
6. **Border / shadow path-aware rendering.** Stroke the clip path instead of a hardcoded rounded rect.
7. **LOD dropout.** Skip offscreen layer below `Full` tier.

---

## 13. Design history — overlay unification

> **Shipped 2026-05-19** ([commit `d17efcb`](#)). Previous design had two separate fields: `MediaAppearance.overlays` (object-level) and `FrameAppearance.contentOverlays` (container-level). Both have been unified onto `NodeAppearance.overlays` (see § 1). `SceneGraphSerializer` reads legacy `contentOverlays` JSON on a frame and lifts it into the unified field; no other migration is needed. Section preserved for cross-reference stability; the rationale lives in § 4.

---

## 14. Multi-selection editing

> **Status — committed (2026-05-19), pending implementation.** Captures the rules for what happens when per-concept appearance popups operate on a multi-node selection. Builds on the per-concept popup direction (`to_discuss.md` resolved, `context-menu.md`) and the unified-overlays design (§ 1).

### 14.1 No "Edit common appearance" mega-popup

The per-concept popup decomposition makes a single multi-edit umbrella popup unnecessary. Each per-concept popup handles multi-selection natively by operating on its own field across every relevant node in the selection. The context menu lists the per-concept items directly (`Edit clip shape`, `Edit alpha mask`, `Edit border`, `Edit shadow`, `Edit overlay`, …) — there is no separate "Edit common appearance" entry.

### 14.2 Indeterminate values within a homogeneous-by-concept selection

When a field's value differs across the selected nodes (e.g. clip cornerRadius is 12 on A, 0 on B, 20 on C), the editor shows a **Figma-style "Mixed" label**:

- Numeric fields display `Mixed` as the text value.
- Sliders show no thumb at a value (or a desaturated thumb at the median position purely for layout — value text reads `Mixed`).
- Dropdowns / pickers show `Mixed` as the selected entry.
- Editing the field commits the new value to **every** node in the selection — on the next read, the field reads as homogeneous. This is the destructive-unify behavior; the user pays for it explicitly by acting on a `Mixed` field.

No confirmation dialog. Standard Figma / Sketch convention.

### 14.3 Type-applicable popups

Per-concept popups split into two categories based on which `NodeAppearance` subtype they edit:

| Concept | Field lives on | Shown in menu when |
|---|---|---|
| **Opacity / Clip / Alpha mask / Border / Shadow / Overlays** | `NodeAppearance` base | Any selection (homogeneous or mixed). Edits the base field on every selected node uniformly. |
| **Crop / Color adjustments / Frame decoration / Caption** | `MediaAppearance` only | Selection is **homogeneous all-media** |
| **Background / Title style / Content effect** | `FrameAppearance` only | Selection is **homogeneous all-frame** |

Type-specific popups are hidden from the context menu when the selection contains the wrong type or is mixed. There is no "tabs" or "intersection" view — the menu shows you what's actually editable for *this* selection.

### 14.4 Overlays on a mixed selection

`overlays` lives on `NodeAppearance` base (§ 1), so this case is trivial: "Add overlay" appends a new `OverlayStyle` to **every** selected node's `appearance.overlays` list. The renderer paints each entry in the right pipeline position per node type — for media nodes, above the photo pixels; for frame nodes, above the frame contents. The user gets the visually-uniform layer they asked for; the per-type render-pipeline semantics still apply.

### 14.5 Preset application

Presets (post-MVP, `MediaStylePreset` shipping first; `FrameStylePreset` later) are inherently type-scoped — a preset captures a full appearance of a specific type. On a multi-selection:

- **`MediaStylePreset.apply`** → applies to every selected **media** node. Frames and other types in the selection are skipped.
- **`FrameStylePreset.apply`** (post-MVP) → applies to every selected **frame** node. Other types skipped.
- The action UI labels the operation honestly: *"Apply Sepia preset to 3 of 5 selected nodes (2 skipped — wrong type)."*
- Cross-type preset application is not a thing. There is no "universal appearance preset."

### 14.6 Action dispatch and undo

Every per-concept popup session, regardless of selection size, produces **one `Compound` undo entry** per popup session (per the popup design points). Internally:

- Open popup → open `commandSessionId`.
- Each control change → dispatch a live `CanvasAction` that operates on the full selection. Action is internally a fan-out: per-node mutation × N selected nodes.
- Close popup → finalize one `Compound` entry covering every change in the session, across every node.

Undo replays the whole compound as a single user-perceived operation.

### 14.7 Open

- **Frame decoration on multi-selected media.** `MediaAppearance.frameDecoration` is currently a single value (one decoration per node). "Apply this decoration to all 5 selected photos" works trivially. No issue here.
- **`MediaAppearance.crop` on multi-selected media.** Crop is per-source-aspect — the same `focalX/focalY` makes sense across nodes, but `offsetX/offsetY/zoom` in `CropMode.Manual` may not. Mark per-field as `Mixed` if they differ; let user unify on edit (same rule as § 14.2). Probably fine.

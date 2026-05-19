# Node Appearance System

> Related: [data-model.md](data-model.md) | [media-appearance.md](media-appearance.md) | [background.md](background.md) | [rendering.md](rendering.md) | [frame-membership.md](frame-membership.md)

The appearance system holds non-destructive visual styling for any `CanvasNode`. Each node variant owns a typed appearance container; the base class carries only the cross-cutting properties that have the same meaning for every kind of node. Shared *value* types (overlay sources, borders, shadows, blend modes) are defined once and reused.

The system is deliberately split per node type. Media and frames look similar at a glance — both can carry a "border", both can carry an "overlay" — but the same word means different things on a leaf media node and on a container frame. Putting the two under one ambiguous field would force the renderer and the UI to special-case at every step. Keeping the fields apart, with one shared *type*, gives us reuse where it's correct and clarity where it isn't.

---

## 1. The base — `NodeAppearance`

```kotlin
@Serializable
sealed class NodeAppearance {
    abstract val opacity: Float        // 0..1, applied to the node's own rendered output
    abstract val cornerRadius: Float   // world units; affects border / clipping
    abstract val border: BorderStyle?
    abstract val shadow: ShadowStyle?
}
```

`NodeAppearance` carries only the four properties that mean the same thing on every node: opacity of the node's own surface, corner rounding of the node's rectangle, border drawn on the node's rectangle, drop shadow cast by the node's rectangle.

> **Proposed evolution.** Two changes pending implementation:
> - `cornerRadius: Float` → `clip: ClipShape` + `alphaMask: AlphaMask?` ([§ 12](#12-proposed-evolution--clip--alphamask)).
> - `MediaAppearance.overlays` and `FrameAppearance.contentOverlays` collapse into a single `overlays: List<OverlayStyle>` on `NodeAppearance` base ([§ 13](#13-proposed-evolution--unified-overlays-on-the-base)). Supersedes §§ 4–5.
>
> The model in §§ 1–11 below describes shipped state.

`NodeAppearance` **intentionally does not** have a generic `overlay` field. See [§5 — Why no generic overlay](#5-why-no-generic-overlay-on-the-base).

---

## 2. `MediaAppearance` — object-level styling

```kotlin
@Serializable
@SerialName("MediaAppearance")
data class MediaAppearance(
    override val opacity: Float = 1f,
    override val cornerRadius: Float = 0f,
    override val border: BorderStyle? = null,
    override val shadow: ShadowStyle? = null,

    // Layout of the source pixels inside the node's bounding rect.
    val fitMode: MediaFitMode = MediaFitMode.Cover,
    val crop: CropRect? = null,
    val objectPosition: ObjectPosition = ObjectPosition.Center,

    // Color transform applied to the media itself (sepia, brightness, etc.).
    val visualFilter: VisualFilter? = null,

    // Object-level overlays above this specific media only — bounded by the media rect.
    // Ordered list: drawn in order, later entries composite on top of earlier ones.
    // Empty list = no overlays. Each entry uses the shared OverlayStyle type (§7.1).
    val overlays: List<OverlayStyle> = emptyList(),

    // Parametric color grading (brightness/contrast/saturation/...).
    val adjustments: ImageAdjustments = ImageAdjustments(),
) : NodeAppearance()
```

Stored on `CanvasNode.Media` as a nullable field (`null` = default rendering). See [media-appearance.md](media-appearance.md) for crop, fit, filter, derived-image, and preset detail.

**`MediaAppearance.overlays` semantics — object-level overlays.**

| Property | Value |
|---|---|
| Owner | A single `CanvasNode.Media` |
| Bounds | The media node's own rect |
| Transforms with | The media node (pan / scale / rotate together) |
| Knows about other nodes? | No |
| Depends on frame containment? | No |
| Renders inside | `MediaRenderer` |
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
    override val border: BorderStyle? = null,
    override val shadow: ShadowStyle? = null,

    // Background drawn behind the frame's linked contents.
    val background: BackgroundData? = null,

    // Overlays drawn above the frame's linked contents, clipped to frame bounds.
    // Ordered list: drawn in order, later entries composite on top of earlier ones.
    // Empty list = no content overlays.
    // Intentionally NOT named just `overlays` — see §4.
    val contentOverlays: List<OverlayStyle> = emptyList(),

    // Future: true off-screen effect applied to rendered frame contents
    // (sepia / blur / grayscale of *everything inside* this frame). Not MVP.
    val contentEffect: FrameContentEffect? = null,

    val titleStyle: FrameTitleStyle? = null,
) : NodeAppearance()
```

Stored on `CanvasNode.Frame` as a nullable field (`null` = default rendering). `background` migrates from the current top-level `Frame.background` — see [data-model.md § Migration Notes](data-model.md#migration-notes) and [background.md](background.md).

**`FrameAppearance.contentOverlays` semantics — container/content-level overlays.**

| Property | Value |
|---|---|
| Owner | A single `CanvasNode.Frame` |
| Bounds | The frame's rect (clipped) |
| Transforms with | The frame |
| Affects | Every node bound to this frame (members, see [frame-membership.md](frame-membership.md)) |
| Mutates child nodes? | **No.** Children keep their own appearance untouched. |
| Depends on frame–content binding? | Yes — only meaningful for nodes that are members of this frame |
| Renders inside | Layered frame renderer (see [§6 — Render pipeline](#6-render-pipeline-implication)) |
| Ordering | Ordered list: entry `[i]` composites above entry `[i-1]`. Empty list = no content overlays. |

Use cases: subtle old-paper tint over every photo inside a frame; translucent watercolor wash; dark-glass dimming over the frame's contents; uniform paper grain over a whole scrapbook page; stacks like grain + tint + vignette over a single frame's contents.

**`contentOverlays` are not `contentEffect`.** `contentOverlays` are extra layers composited *above* the rendered contents — children render exactly as they would standalone, then the overlays draw on top in list order. `contentEffect` (future) is an off-screen pass that re-renders the contents through a filter (sepia, blur, grayscale of everything inside). Both leave child node data unchanged; only the rendered frame output differs.

---

## 4. Why media `overlays` and frame `contentOverlays` are separate fields

> **Note (2026-05-19):** This section describes the **shipped** rationale. The committed direction is to unify the two into a single `overlays: List<OverlayStyle>` on `NodeAppearance` base. See [§ 13](#13-proposed-evolution--unified-overlays-on-the-base). The reasoning below remains accurate for current code; it will be superseded when the migration ships.

They share the same element type (`OverlayStyle`) and the same shader-level concept ("draw this layer with this blend mode"), but they sit at different points in the render pipeline and answer different questions.

> `MediaAppearance.overlays` are object-level overlays.
> `FrameAppearance.contentOverlays` are container/content-level overlays.

A single `NodeAppearance.overlays` field would be ambiguous for frames:

- For a leaf media node, an overlay obviously means *above this media object*.
- For a frame, the *useful* overlay is *above the frame's contents, clipped to frame bounds* — i.e. a layer in a multi-layer compositing pipeline that needs the frame's member set to exist before it can mean anything.

The two operations are different in:

- **Bounds.** Media-overlay bounds = the media rect. Content-overlay bounds = the frame rect, clipped, after children draw.
- **Render-pipeline position.** Media overlays sit inside the single-pass media renderer. Content overlays sit between *frame contents* and *frame decoration* in a layered frame render — see §6.
- **Dependencies.** Media overlays need only the media node. Content overlays need the frame's effective members (frame–content binding).
- **What they composite with.** Media overlays composite with the cropped source pixels. Content overlays composite with whatever the children happened to render, including their own per-media overlays.

Keeping the two fields apart in the model keeps the renderer's job unambiguous and avoids needing a "is this a media or a frame" branch inside an overlay-handling step. The list-vs-list parallel is intentional: both fields support stacking with identical ordering semantics, so the rendering helper for `List<OverlayStyle>` is shared even though the two outer fields aren't merged.

---

## 5. Why no generic overlay(s) on the base

> **Note (2026-05-19):** Same as § 4 — describes shipped rationale only. The committed direction puts `overlays` on the base; see [§ 13](#13-proposed-evolution--unified-overlays-on-the-base) for the unified design.

`NodeAppearance` deliberately does not declare an abstract `overlay` or `overlays`. The proper hook lives on each concrete subclass:

- `MediaAppearance.overlays: List<OverlayStyle>`
- `FrameAppearance.contentOverlays: List<OverlayStyle>`

A future widget / text / shape variant may want its own *surface* overlays (on the widget's own rendered rectangle, no container semantics). When that need arises, the right field is a *new* field on that variant — for example `WidgetAppearance.surfaceOverlays: List<OverlayStyle>` — defined explicitly as "overlays on this node's own rendered surface". Surface overlays would never replace `FrameAppearance.contentOverlays`, because the frame overlays have container semantics that a per-node surface overlay can't express.

If, after multiple variants have shipped, the same surface-overlay code appears in every subclass, then `NodeAppearance.surfaceOverlays` can be lifted into the base with a precise definition. Until then, keeping the base minimal prevents a generic field from quietly turning into "do whatever the renderer feels like."

---

## 6. Render pipeline implication

A frame with any `contentOverlays` (or, in the future, a `contentEffect`) can no longer be rendered as a single ordinary `CanvasNode` pass — its contents must be composited between two of its own layers. Conceptually:

```
1. Album background
     (camera- or world-locked; see background.md)

2. Frame background layer
     frame.appearance.background

3. Linked frame contents
     every node bound to this frame, in z-order
     (each child still draws its own MediaAppearance, including its MediaAppearance.overlays)

4. Frame content overlay layer
     frame.appearance.contentOverlays, drawn in list order
     clipped to frame bounds, each entry composited above the previous one

5. Frame decoration layer
     frame.appearance.border, frame.appearance.titleStyle,
     selection handles / editor overlays (Edit mode only)
```

`contentEffect`, when added later, sits between (3) and (4) as an off-screen pass that re-renders the contents through a filter before the overlays composite on top.

The simple per-node `CanvasNodeRenderer` pass used today handles step (2) trivially (it's the existing `Frame.background` render), and step (5) trivially (border + label), but not (3) + (4) together: the renderer needs to know which children belong to the frame and must draw them between the frame's two own layers. This is what makes `contentOverlays` strictly more than a node-local effect.

**Status — what's wired today.** The layered renderer ships with this slice. `CanvasScreen` walks `FramePaintEvent`s built from the visible-nodes set: every layered frame paints its background on the Surface phase at `frame.zIndex`, members draw in z-order, then the overlay phase paints `contentOverlays + border` just past `max(memberZ, frame.zIndex)`. Membership uses [frame-membership.md](frame-membership.md). Plain frames (no `contentOverlays`) still paint in a single pass.

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

`OverlayStyle` is the element type reused by both `MediaAppearance.overlays` and `FrameAppearance.contentOverlays`. The shape mirrors `BackgroundData` (Solid / Texture / Procedural) on purpose: paper grain that works as a background also works as an overlay, just composited differently. `TileData` and `ProceduralPattern` are the same types as in [background.md](background.md).

Both overlay fields are `List<OverlayStyle>` with **declaration-order compositing** — entry `[i]` draws on top of entry `[i-1]`. An empty list is the default ("no overlays"). The renderer iterates the list inside its scope (media rect for media overlays, frame-clip rect for content overlays) and applies each entry's `blendMode` and `opacity`. Reusing the same element type means one rendering helper for `List<OverlayStyle>` serves both scopes.

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

These five phrases mean five different things. Use them consistently in code, comments, and UI labels.

| Term | Where it lives | What it draws |
|------|----------------|---------------|
| **Frame background** | `FrameAppearance.background` | Layer *behind* the frame's linked contents. Solid / texture / procedural / gradient / watercolor. |
| **Media overlays** | `MediaAppearance.overlays: List<OverlayStyle>` | Layers *above* one media node only. Bounded by the media rect. Ordered list — entry `[i]` over entry `[i-1]`. Examples: light leak, vignette, scratches, paper grain on a single photo (and stacks of those). |
| **Frame contentOverlays** | `FrameAppearance.contentOverlays: List<OverlayStyle>` | Layers *above* the frame's linked contents, clipped to the frame. Ordered list — entry `[i]` over entry `[i-1]`. Examples: subtle old-paper tint over every photo inside a frame, translucent watercolor wash, dark glass, or stacks of those. |
| **Frame contentEffect** *(future)* | `FrameAppearance.contentEffect` | True off-screen pass applied to the *rendered* frame contents (sepia/blur/grayscale of everything inside). Not MVP. Distinct from `contentOverlays`, which only composite new layers on top. |
| **Media frame decoration** | `MediaAppearance.frameDecoration: MediaFrameDecoration?` | Decorative picture-frame asset around a single media node (Polaroid border, mat, wooden frame, nine-slice). NOT a `CanvasNode.Frame`, NOT a `FrameAppearance`. See [media-appearance.md](media-appearance.md#media-frame-decoration). |

---

## 9. Persistence

`MediaAppearance` and `FrameAppearance` are nullable fields on their owning `CanvasNode` variant. `null` = default rendering, omitted from JSON. `ignoreUnknownKeys` lets old scene graphs deserialize cleanly while the model is extended.

Polymorphism uses kotlinx-serialization's standard `@SerialName` discriminator. `OverlaySource` and `ProceduralPattern` are sealed classes with discriminator names that match the on-disk wire format (`"Solid"`, `"Texture"`, `"Procedural"`, etc.) — same convention as [background.md § Notes](background.md#domain-types).

---

## 10. Implementation status

What this slice landed:

- Shared types: `NodeAppearance`, `OverlayStyle`, `OverlaySource` (Solid / Texture / Procedural), `NodeBlendMode` (all 7 values mapped to Compose `BlendMode`), `BorderStyle`, `ShadowStyle`.
- `FrameAppearance` and `MediaAppearance` data classes with their per-type fields; nullable `appearance` field on `CanvasNode.Frame` and `CanvasNode.Media`.
- Scene-graph serializer migration that lifts legacy top-level `Frame.background` into `appearance.background` on read.
- Media renderer paints `overlays`, `border`, `shadow`, `cornerRadius`, surface `opacity`, and `crop.mode` (Fit / Fill / Stretch).
- Layered frame renderer paints background → members → `contentOverlays` → border, driven by `FrameMembershipUseCase.effectiveMembers`.
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

> `MediaAppearance.overlays` are object-level overlays.
> `FrameAppearance.contentOverlays` are container/content-level overlays.
> Both are `List<OverlayStyle>` with declaration-order compositing.
> They share the element type. They are not the same field.

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

## 13. Proposed evolution — unified `overlays` on the base

> **Status — committed (2026-05-19), pending implementation.** Replaces the separate `MediaAppearance.overlays` and `FrameAppearance.contentOverlays` fields with a single `overlays: List<OverlayStyle>` on `NodeAppearance` base. Supersedes the rationale in §§ 4–5 (which described the shipped two-field design).

### 13.1 The unified semantic

> **`overlays` paints above whatever the node renders, clipped to the node's rect.**

For a media node, "whatever the node renders" is the cropped photo pixels — so `overlays` paints above those, bounded by the media rect (today's `MediaAppearance.overlays` behavior).

For a frame node, "whatever the node renders" is the frame's *complete* output: background + every member node + each member's own per-media overlays, composed. `overlays` paints above all of that, bounded by the frame rect (today's `FrameAppearance.contentOverlays` behavior).

The semantic is uniform: **above the node's own rendered output**. The renderer's pipeline position differs per node type, but the data-model field is one.

### 13.2 Model

```kotlin
@Serializable
sealed class NodeAppearance {
    abstract val opacity: Float
    abstract val cornerRadius: Float                     // pending replacement — see § 12
    abstract val overlays: List<OverlayStyle>            // unified; default emptyList()
    abstract val border: BorderStyle?
    abstract val shadow: ShadowStyle?
}

@Serializable
@SerialName("MediaAppearance")
data class MediaAppearance(
    override val opacity: Float = 1f,
    override val cornerRadius: Float = 0f,
    override val overlays: List<OverlayStyle> = emptyList(),    // inherited; no per-type rename
    override val border: BorderStyle? = null,
    override val shadow: ShadowStyle? = null,

    val crop: CropSettings = CropSettings(),
    val visualFilter: VisualFilter? = null,
    val adjustments: ImageAdjustments = ImageAdjustments(),
    // ... media-specific only fields stay
) : NodeAppearance()

@Serializable
@SerialName("FrameAppearance")
data class FrameAppearance(
    override val opacity: Float = 1f,
    override val cornerRadius: Float = 0f,
    override val overlays: List<OverlayStyle> = emptyList(),    // formerly contentOverlays
    override val border: BorderStyle? = null,
    override val shadow: ShadowStyle? = null,

    val background: BackgroundData? = null,
    val contentEffect: FrameContentEffect? = null,
    val titleStyle: FrameTitleStyle? = null,
) : NodeAppearance()
```

No new value types. `OverlayStyle`, `OverlaySource`, `NodeBlendMode` are unchanged (§ 7).

### 13.3 Render pipeline position (unchanged per-type behavior)

Field is uniform; renderer dispatches by node type, same as today.

**Media node** — single-pass `FullMediaRenderer`:
```
crop source → adjustments → overlays (in list order) → border (per § 12 clip path)
```

**Frame node** — layered renderer (`buildFramePaintEvents`):
```
1. background (FrameAppearance.background)
2. members in z-order — each child draws its own MediaAppearance.overlays
3. overlays (in list order, clipped to frame rect)        ← was contentOverlays
4. border, titleStyle
```

The unification is naming/data only. No rendering behavior changes.

### 13.4 Migration

`SceneGraphSerializer` reads legacy JSON:

```jsonc
// Legacy FrameAppearance (pre-unification)
{ "type": "FrameAppearance", "contentOverlays": [ ... ], ... }
// New
{ "type": "FrameAppearance", "overlays": [ ... ], ... }
```

Migration: on read of a `FrameAppearance`, if `contentOverlays` is present, lift it into `overlays`. `MediaAppearance.overlays` keeps its name and needs no migration.

Same one-shot read-time-lift pattern as `Frame.background → appearance.background` and the planned `cornerRadius → clip` (§ 12.6). No deprecated alias; old field name disappears from the data model.

### 13.5 Why this works

The shipped rationale (§§ 4–5) argued separation kept the renderer's pipeline position visible in the data model. After consideration: the renderer dispatches by node type anyway (`FullMediaRenderer` vs. layered frame renderer), and the dispatch is unambiguous from the node type alone — the field name doesn't add information the renderer needs. Meanwhile the **uniformity** wins:

- **One concept** to teach: "overlays paint above the node's output."
- **One field name** in the multi-edit popup ("Add overlay" appends to `appearance.overlays` regardless of type).
- **No special-case** in editor UI for type-specific overlay-field lookup.
- **Future variants** (e.g. widget appearance) automatically inherit `overlays` with the natural semantic ("above this widget's own surface").

The renderer-pipeline-position argument is preserved by the **per-type rendering code** — `FullMediaRenderer` and the layered frame renderer each know where overlays sit in their pipeline. The data model doesn't need to encode the distinction.

### 13.6 Implementation order

1. **Model rename.** Move `overlays` to `NodeAppearance` base abstract. Remove `FrameAppearance.contentOverlays`. Both subclasses override the inherited `overlays` field.
2. **Serializer migration.** Read-time lift of legacy `contentOverlays` → `overlays`.
3. **Renderer rename.** Layered frame renderer reads `appearance.overlays` instead of `appearance.contentOverlays`. `buildFramePaintEvents`, `FullFrameRenderer`, `drawOverlayStack` call sites.
4. **Editor rename.** `MediaAppearanceBottomSheet` and `FrameAppearanceBottomSheet` already use a shared `OverlayListEditor` — just update the field reference. No new UI work.
5. **Doc cleanup.** Collapse §§ 4–5 into a single short note pointing at this section. Update § 6, § 8, § 10, § 11 to use the single `overlays` field name throughout.

Behavior-preserving end-to-end. Existing albums look identical after migration.

---

## 14. Multi-selection editing

> **Status — committed (2026-05-19), pending implementation.** Captures the rules for what happens when per-concept appearance popups operate on a multi-node selection. Builds on the per-concept popup direction (`to_discuss.md` resolved, `context-menu.md`) and the unified-overlays decision (§ 13).

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

With overlays unified onto the base (§ 13), this case becomes trivial: "Add overlay" appends a new `OverlayStyle` to **every** selected node's `appearance.overlays` list. The renderer paints each entry in the right pipeline position per node type — for media nodes, above the photo pixels; for frame nodes, above the frame contents. The user gets the visually-uniform layer they asked for; the per-type render-pipeline semantics still apply.

Before § 13 unification, this case required either "add to two different fields" or "disallow." After unification, it's one field, one append.

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

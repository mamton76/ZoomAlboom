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

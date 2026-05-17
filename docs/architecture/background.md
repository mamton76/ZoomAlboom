# Album & Frame Backgrounds

> Related: [data-model.md](data-model.md) | [appearance.md](appearance.md) | [overview.md](overview.md) | [todo.md §19](../todo.md#19-album-and-frame-backgrounds) | [PRD §8.6](../product/PRD.md#86-visual-atmosphere)

Backgrounds are **not** `CanvasNode` objects. They are render-layer style properties stored alongside (not inside) the nodes list in the scene graph.

A background source is described by a **`BackgroundData`** sealed family — one of three variants:

1. **Solid** — a single hex color.
2. **Texture** — a Coil-loadable URI (eventually a `media_library` reference) plus tiling parameters.
3. **Procedural** — a `ProceduralPattern` whose parameters describe how to *draw* the pattern. See [Procedural Patterns](#procedural-patterns) below.

Same `BackgroundData` type is used at two scopes:

- **`AlbumBackground = BackgroundData + AnchorMode`** — the album-level background. The anchor decides whether the source is screen-fixed (`CameraLocked`) or canvas-fixed (`WorldLocked`).
- **`FrameAppearance.background: BackgroundData?`** — per-frame backgrounds, nested inside the frame's `FrameAppearance` container ([appearance.md § 3](appearance.md#3-frameappearance--containercontent-level-styling)). No anchor field: a frame *is* its own anchor (background moves/scales/rotates with the frame). The same `BackgroundData` payload that previously sat directly on `Frame.background` now sits at `frame.appearance.background` — see [data-model.md § Migration Notes](data-model.md#migration-notes).

---

## Domain Types

```kotlin
@Serializable
enum class TileMode { None, Stretch, Cover, Contain, Repeat }

@Serializable
enum class AnchorMode {
    CameraLocked,  // fixed to viewport/screen — does not move with canvas pan/zoom/rotation
    WorldLocked,   // anchored in world coordinates — moves and scales with the camera
    // FrameLocked — future: clip the album background to a specific frame and transform with it
}

@Serializable
data class TileData(
    val tileMode: TileMode = TileMode.None,
    val tileOriginX: Float = 0f,
    val tileOriginY: Float = 0f,
    val tileWidth: Float = 200f,
    val tileHeight: Float = 200f,
)

@Serializable
sealed class BackgroundData {
    abstract val opacity: Float

    @Serializable @SerialName("Solid")
    data class SolidBackgroundData(
        val color: String = "#000000",
        override val opacity: Float = 1f,
    ) : BackgroundData()

    @Serializable @SerialName("Texture")
    data class TextureBackgroundData(
        val textureRefId: String,
        val tile: TileData = TileData(),
        override val opacity: Float = 1f,
    ) : BackgroundData()

    @Serializable @SerialName("Procedural")
    data class ProceduralBackgroundData(
        val pattern: ProceduralPattern,
        // Optional solid fill drawn under the pattern. Useful for patterns
        // with gaps (Grid / DotGrid / RuledPaper / GraphPaper / Gradient /
        // Grain / Noise). `null` = no fill — whatever's behind the layer
        // shows through. Alpha-aware via 8-char hex.
        val fillColor: String? = null,
        override val opacity: Float = 1f,
    ) : BackgroundData()
}

@Serializable
data class AlbumBackground(
    val data: BackgroundData,
    val anchorMode: AnchorMode = AnchorMode.CameraLocked,
)
```

Notes:

- `ProceduralBackgroundData` does **not** carry a `TileData`. Each `ProceduralPattern` variant (`Grid.cellSize`, `DotGrid.spacing`, `RuledPaper.lineSpacing`, etc.) owns its own tiling/positioning. An outer `TileData` would be redundant — what does `tileMode=Stretch` mean for a `Grid`?
- `ProceduralBackgroundData.fillColor` is an optional solid color drawn under the pattern. Patterns with gaps (Grid lines, dots, gradient transitions, sparse noise) can use it instead of inheriting whatever's behind the layer. `Watercolor` overrides it (it draws its own full-rect `baseColor` wash).
- Per-source `opacity` lives on each `BackgroundData` variant rather than on `AlbumBackground`, so the same opacity follows the source even when reusing it elsewhere.
- A `FrameAppearance.background = BackgroundData?` of `null` (or a `null` `Frame.appearance` entirely) means "no background" (transparent frame).
- An `AlbumBackground? = null` on the scene graph means "no album background" (canvas-default backdrop only).
- Class names carry the `*BackgroundData` suffix in code; the `@SerialName` discriminators are the short forms (`"Solid"` / `"Texture"` / `"Procedural"`) — that's the on-disk wire format and is what to use when reading or writing scene JSON by hand.

---

## Rendering Order

1. **Camera-locked album background** — drawn outside the camera `graphicsLayer` (screen-fixed, no transform applied)
2. **World-locked album background** — drawn inside the camera `graphicsLayer`, before all nodes
3. **Frame backgrounds** — `frame.appearance.background`, drawn behind the frame's linked contents, clipped to frame bounds
4. **Canvas nodes** by `zIndex`, including each frame's linked contents
5. **Frame content overlays** — `frame.appearance.contentOverlays` (ordered list), drawn above the frame's linked contents, clipped to frame bounds; entry `[i]` composites over entry `[i-1]` (future layered renderer — see [appearance.md § 6](appearance.md#6-render-pipeline-implication) and [rendering.md § 6b](rendering.md#6b-layered-frame-rendering))
6. **Frame decoration** — `frame.appearance.border`, title, selection handles
7. Selection overlays, guidelines, snapping indicators
8. IDE UI overlay

Steps 3 and 5 are the two halves of the layered frame render: background goes under the contents, `contentOverlays` go over them in list order. With today's single-pass `CanvasNodeRenderer`, step 3 is implemented and step 5 is persisted-but-not-painted; the renderer slice that closes the gap is tracked in todo.

---

## World-Locked vs Camera-Locked

| Mode | Where rendered | Moves with pan? | Scales with zoom? |
|------|---------------|-----------------|-------------------|
| `CameraLocked` | Outside `graphicsLayer` Box | No | No |
| `WorldLocked` | Inside `graphicsLayer` Box | Yes | Yes |

**Camera-locked** is simpler: draw behind the canvas layer at screen size. Good for solid colors and simple textures that should feel like paper under the canvas.

**World-locked** is drawn inside the `graphicsLayer` transform, so it moves and scales exactly as canvas content does. Required for grid patterns, dot grids, or textures that need to feel like the physical album surface — zooming in reveals more texture detail.

---

## Shared Draw Dispatch

`AlbumBackgroundRenderer.kt` exposes one private function used by both album scopes and the frame renderer:

```kotlin
internal fun DrawScope.drawBackgroundData(
    data: BackgroundData,
    left: Float, top: Float, right: Float, bottom: Float,        // viewport — what to rasterize
    textureBitmap: ImageBitmap? = null,
    anchorLeft: Float = left, anchorTop: Float = top,            // pattern anchor — see "Procedural anchor split"
    anchorRight: Float = right, anchorBottom: Float = bottom,
)
```

- For `SolidBackgroundData`, fills the rect with the parsed color × `opacity`.
- For `TextureBackgroundData`, draws via the [Texture pipeline](#texture-pipeline) — `drawImage` for None/Stretch/Cover/Contain (Cover/Contain are aspect-preserving), native-canvas `BitmapShader` for Repeat.
- For `ProceduralBackgroundData`, fills the viewport with `fillColor` (if non-null), then delegates to `drawProceduralPattern` in `ProceduralBackgroundRenderer.kt` with both rects.

The `anchor*` params default to the viewport, which is correct for CameraLocked album + Frame backgrounds. `WorldLockedAlbumBackground` overrides with a fixed world rect — see [Procedural anchor split](#procedural-anchor-split). Tileable patterns (Grid family) ignore it; the four fill-rect patterns (Gradient / Watercolor / Grain / Noise) use it for positioning. `SolidBackgroundData` / `TextureBackgroundData` ignore it.

The Composable layer is responsible for resolving the texture bitmap. A helper `rememberBackgroundBitmap(data)` runs Coil's `ImageLoader.execute(...)` inside a `LaunchedEffect(refId)` and returns the decoded `ImageBitmap` (or `null` while loading / on error). Loading is keyed on `textureRefId` so the bitmap survives recomposition and is shared across renderers (album camera-locked, album world-locked, frame).

---

## Texture pipeline

```
None / Stretch:
    drawImage(bitmap, dstOffset = (left, top), dstSize = (w, h), alpha = opacity)
    (Stretch fills the rect ignoring aspect; None currently aliases Stretch —
    "native-pixel-size at tileOrigin" semantics are a separate ticket.)

Cover:
    scale  = max(rectW/bw, rectH/bh)        # fills both axes
    drawn  = (bw * scale, bh * scale)        # one axis overflows
    dst    = rect.topLeft + (rect.size - drawn) / 2   # centered
    drawImage(bitmap, dstOffset = dst, dstSize = drawn, alpha = opacity)

Contain:
    scale  = min(rectW/bw, rectH/bh)        # fits inside both axes
    drawn  = (bw * scale, bh * scale)        # one axis letterboxes
    dst    = rect.topLeft + (rect.size - drawn) / 2   # centered
    drawImage(bitmap, dstOffset = dst, dstSize = drawn, alpha = opacity)

Repeat:
    shader  = BitmapShader(bitmap, REPEAT, REPEAT)
    shader.localMatrix = scale(tileWidth/bw, tileHeight/bh) ∘ translate(tileOriginX, tileOriginY)
    paint   = Paint(shader, alpha = opacity, isFilterBitmap = true, isDither = true)
    drawIntoCanvas { it.nativeCanvas.drawRect(rect, paint) }   // native, not ShaderBrush
```

The Repeat path uses the native `Canvas.drawRect(rect, paint)` rather than
Compose's `ShaderBrush` wrapper. The wrapper has produced silently-transparent
output for pre-built shaders when applied inside a `graphicsLayer`; the native
path is the most reliable way to use `BitmapShader`.

The shader's `localMatrix` maps one bitmap pixel at `(0, 0)` to world (or screen) coordinate `(tileOriginX, tileOriginY)`, and one bitmap copy spans `tileWidth × tileHeight` of the destination space. The shader extends infinitely in both axes; the destination rect bounds what's rasterised.

`tileOriginX/Y` controls the phase of the grid — shifting origin moves the pattern without changing tile size.

### Cost

Tile rendering is a **single `drawRect` call regardless of how many tiles fit**. Zoom-out cost is constant — the GPU evaluates the shader once per visible pixel, not once per tile. No tile-count cap is needed; the previous CPU-side `for` loop is gone.

This matters because at extreme zoom-out (`cameraScale ≈ 0.01`) the visible world rect can be 200k × 200k units. With 200 × 200 tiles a naive loop would issue ~1M `painter.draw` calls per frame and freeze the app.

---

## Procedural Patterns

A procedural pattern stores **parameters only**, never pixels. The renderer draws each variant from those parameters at every frame, so a tiny scene-graph file can describe an arbitrarily large background.

```kotlin
@Serializable
sealed class ProceduralPattern {
    @Serializable @SerialName("Grid")        data class Grid(...) : ProceduralPattern()
    @Serializable @SerialName("DotGrid")     data class DotGrid(...) : ProceduralPattern()
    @Serializable @SerialName("RuledPaper")  data class RuledPaper(...) : ProceduralPattern()
    @Serializable @SerialName("GraphPaper")  data class GraphPaper(...) : ProceduralPattern()
    @Serializable @SerialName("PaperGrain")  data class PaperGrain(...) : ProceduralPattern()
    @Serializable @SerialName("Noise")       data class Noise(...) : ProceduralPattern()
    @Serializable @SerialName("Gradient")    data class Gradient(...) : ProceduralPattern()
    @Serializable @SerialName("Watercolor")  data class Watercolor(...) : ProceduralPattern()
}
```

### Length-parameter semantics

Each pattern carries lengths (`cellSize`, `lineWidth`, `spacing`, `dotRadius`, `splotchRadius`, …). The renderer interprets them in whatever coordinate space it is currently drawing into:

- Album background, `CameraLocked` → **screen pixels**. The pattern feels printed on the screen; cells/lines stay the same size on screen regardless of pan/zoom.
- Album background, `WorldLocked` → **world units**. The pattern feels stamped on the canvas surface; cells/lines move with pan and scale with zoom.
- Frame background → **world units**, frame-local. Cells/lines rotate and scale with the frame.

### Determinism

Seeded patterns (`PaperGrain`, `Noise`, `Watercolor`) sample `kotlin.random.Random(seed)`. Same seed + same parameters → same pattern every render. Persisting the seed in the scene graph keeps the canvas visually stable across reloads.

### Procedural anchor split

`drawProceduralPattern` takes **two** rects: a **viewport** (what to rasterize) and an **anchor** (the pattern's fixed coordinate frame). For tileable patterns (`Grid` / `DotGrid` / `RuledPaper` / `GraphPaper`) the anchor is ignored — their `floor((axisLow − originX) / step)` math already anchors lines to a world-fixed `originX/Y` field on the pattern itself. For the four fill-rect patterns (`Gradient` / `Watercolor` / `PaperGrain` / `Noise`) the anchor is what they use to position content:

- `Gradient` — derives `start` / `end` (Linear) and `center` / `radius` (Radial) from the anchor center + extent. The `drawRect` still uses the viewport so we only rasterize visible pixels.
- `Watercolor` — splotch positions are random within the anchor; viewport-cull each draw with `padR = splotchRadius × 1.4`. Base wash fills the viewport.
- `PaperGrain` / `Noise` — dot count is `density × anchor.area / 100` (capped at 4000); positions are random within the anchor; viewport-cull each draw. RNG advances `target` times regardless of culling so positions stay deterministic per seed.

Where the anchor comes from per scope:

| Scope | Viewport | Anchor |
|-------|----------|--------|
| Album, `CameraLocked` | Screen rect | Same as viewport |
| Album, `WorldLocked` | Visible world rect (from `cameraViewport`) | Hardcoded `(-2500..+2500)` world units (`AlbumBackgroundRenderer.kt :: PROCEDURAL_WORLD_ANCHOR_HALF`) — TODO §19.10: wire to `AlbumPresentationProfile` |
| Frame | Frame-local rect | Same as viewport |

The split is what stops Gradient/Watercolor/Grain/Noise from "sliding with the camera" in `WorldLocked` (they used to recompute geometry from the moving viewport every frame, which made them effectively camera-locked).

### Cost / density caps

The renderer (`ProceduralBackgroundRenderer.kt`) clamps line/dot grids to ≤500 lines per axis and grain/noise to ≤4000 dots per frame. Past those thresholds the pattern would be too dense to read and would burn the GPU; the renderer skips rather than draws.

### What lives where

- Pattern data → `domain/model/ProceduralPattern.kt`
- Background data → `domain/model/Background.kt` (`BackgroundData`, `AlbumBackground`, `TileData`, `AnchorMode`, `TileMode`)
- Album background rendering → `feature/canvas/view/AlbumBackgroundRenderer.kt` (`CameraLockedAlbumBackground`, `WorldLockedAlbumBackground`, `drawBackgroundData`, `rememberBackgroundBitmap`)
- Procedural rendering → `feature/canvas/view/ProceduralBackgroundRenderer.kt` (`DrawScope.drawProceduralPattern`)
- Frame background rendering → `feature/canvas/view/CanvasRenderer.kt` (`drawFrameBackground` in `FullFrameRenderer` / `SimplifiedFrameRenderer`)
- UI editor → `feature/ide_ui/ui/sheets/BackgroundEditor.kt` (shared between album and frame sheets), plus `ProceduralPatternEditor.kt` for per-pattern controls.

---

## Persistence

`albumBackground` lives in the scene graph **root object**:

```json
{
  "albumId": 123,
  "camera": { "cx": 0, "cy": 0, "scale": 1.0, "rotation": 0 },
  "background": {
    "data": {
      "type": "Texture",
      "textureRefId": "content://…",
      "tile": {
        "tileMode": "Repeat",
        "tileOriginX": 0,
        "tileOriginY": 0,
        "tileWidth": 300,
        "tileHeight": 300
      },
      "opacity": 0.4
    },
    "anchorMode": "WorldLocked"
  },
  "nodes": [ ... ]
}
```

Per the appearance system, the frame's background lives at `frame.appearance.background: BackgroundData?`. `null` (or a `null` `frame.appearance` entirely) means transparent. When present it serializes inline using the same `type` discriminator (`Solid` / `Texture` / `Procedural`). Today's on-disk layout still stores `Frame.background` directly on the frame node; the migration to `frame.appearance.background` is tracked in [data-model.md § Migration Notes](data-model.md#migration-notes) and [todo.md § 20](../todo.md#20-appearance-system-non-destructive-styling).

---

## MVP Scope

**Required:**
- `BackgroundData.Solid` with `CameraLocked` and `WorldLocked` album anchoring
- `BackgroundData.Texture` (any tile mode) album background
- `BackgroundData` on frames (Solid / Texture / Procedural — all three)
- `BackgroundData.Procedural` for album backgrounds with the 8 listed pattern variants
- Scene graph root wrapper (§1.3) to store `background` alongside `nodes`

**Post-MVP:**
- `AnchorMode.FrameLocked` — clip album background to a specific frame and transform with it
- Animated backgrounds
- `TileMode.None` aspect-preserving "native pixel size at tileOrigin" semantics (currently aliased to Stretch)
- Per-pattern preview swatches in the editor
- Better noise (Perlin / simplex) and watercolor (mask-based soft stamps)

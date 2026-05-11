# Album & Frame Backgrounds

> Related: [data-model.md](data-model.md) | [overview.md](overview.md) | [todo.md §19](../todo.md#19-album-and-frame-backgrounds) | [PRD §8.6](../product/PRD.md#86-visual-atmosphere)

Backgrounds are **not** `CanvasNode` objects. They are render-layer style properties stored alongside (not inside) the nodes list in the scene graph.

---

## Domain Types

```kotlin
@Serializable
enum class BackgroundType { None, SolidColor, Texture }

@Serializable
enum class TileMode { None, Stretch, Cover, Contain, Repeat, RepeatX, RepeatY }

@Serializable
enum class AnchorMode {
    CameraLocked,  // fixed to viewport/screen — does not move with canvas pan/zoom/rotation
    WorldLocked,   // anchored in world coordinates — moves and scales with the camera
    // FrameLocked — future: local to a specific frame, clipped to frame bounds
}

@Serializable
data class AlbumBackground(
    val type: BackgroundType = BackgroundType.None,
    val color: String = "#000000",           // hex color (used when type == SolidColor)
    val textureRefId: String? = null,        // FK to media_library (used when type == Texture)
    val opacity: Float = 1f,
    val tileMode: TileMode = TileMode.None,
    val anchorMode: AnchorMode = AnchorMode.CameraLocked,
    val tileOriginX: Float = 0f,            // world X of the tile grid anchor
    val tileOriginY: Float = 0f,            // world Y of the tile grid anchor
    val tileWidth: Float = 200f,            // tile width in world units
    val tileHeight: Float = 200f,           // tile height in world units
)

@Serializable
data class FrameBackground(
    val color: String? = null,  // null = transparent / no fill
    val opacity: Float = 1f,
    // texture support planned post-MVP
)
```

---

## Rendering Order

1. **Camera-locked album background** — drawn outside the camera `graphicsLayer` (screen-fixed, no transform applied)
2. **World-locked album background** — drawn inside the camera `graphicsLayer`, before all nodes
3. **Frame backgrounds** — drawn inside each frame's own rendering, clipped to frame bounds
4. **Canvas nodes** by `zIndex`
5. Selection overlays, guidelines, snapping indicators
6. IDE UI overlay

---

## World-Locked vs Camera-Locked

| Mode | Where rendered | Moves with pan? | Scales with zoom? |
|------|---------------|-----------------|-------------------|
| `CameraLocked` | Outside `graphicsLayer` Box | No | No |
| `WorldLocked` | Inside `graphicsLayer` Box | Yes | Yes |

**Camera-locked** is simpler: draw behind the canvas layer at screen size. Good for solid colors and simple textures that should feel like paper under the canvas.

**World-locked** is drawn inside the `graphicsLayer` transform, so it moves and scales exactly as canvas content does. Required for grid patterns, dot grids, or textures that need to feel like the physical album surface — zooming in reveals more texture detail.

---

## Tile Algorithm (world-locked Repeat/RepeatX/RepeatY)

Find the first tile column/row whose right edge reaches the visible world left edge:

```
firstCol = floor((worldLeft - tileOriginX) / tileWidth)
firstRow = floor((worldTop  - tileOriginY) / tileHeight)
```

Then loop until past `worldRight` / `worldBottom`. Draw each tile with:
- `drawImage` for texture tiles
- `drawRect` for solid color tiles

`tileOriginX/Y` control the phase of the grid — shifting origin moves the pattern without changing tile size. This lets the user anchor the pattern to a meaningful world point (e.g., world origin or a frame center).

---

## Persistence

`albumBackground` lives in the scene graph **root object** (requires §1.3 JSON wrapper — implemented together):

```json
{
  "albumId": 123,
  "camera": { "cx": 0, "cy": 0, "scale": 1.0, "rotation": 0 },
  "background": {
    "type": "Texture",
    "textureRefId": "media_paper_grain",
    "opacity": 0.4,
    "anchorMode": "WorldLocked",
    "tileMode": "Repeat",
    "tileOriginX": 0,
    "tileOriginY": 0,
    "tileWidth": 300,
    "tileHeight": 300
  },
  "nodes": [ ... ]
}
```

`FrameBackground` is a nullable field on `CanvasNode.Frame` in the nodes array. `null` = no background (transparent frame).

---

## MVP Scope

**Required:**
- `AlbumBackground`: `SolidColor` + `CameraLocked` (static paper color behind infinite canvas)
- `AlbumBackground`: `SolidColor` + `WorldLocked` (scrolling solid tint)
- `FrameBackground`: solid color fill
- Scene graph root wrapper (§1.3) to store `background` alongside `nodes`

**Nice to have in MVP:**
- `Texture` type with `WorldLocked` tiling (dot grid, crosshatch, linen)
- `RepeatX`/`RepeatY` tile modes for stripe patterns

**Post-MVP:**
- `FrameLocked` anchor mode (texture clipped to frame bounds)
- Animated backgrounds
- Gradient backgrounds
- Per-frame texture backgrounds
- Background editor UI (opacity slider, tile size controls, pattern picker)

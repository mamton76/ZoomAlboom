# Data Model

> This document describes the **target** data model. See [Migration Notes](#migration-notes) at the bottom for differences from the current implementation.
>
> Related: [overview](overview.md) | [rendering](rendering.md) | [modules & DI](modules.md) | [TODO](../todo.md)

Three independent storage layers:

1. **Metadata & UI State** — relational (Room)
2. **Canvas Scene Graph** — document (JSON)
3. **Operation History / Undo-Redo** — append-only (JSON, planned)

## Domain Models

### Transform

```kotlin
@Serializable
data class Transform(
    val cx: Float = 0f,       // center X in world space
    val cy: Float = 0f,       // center Y in world space
    val w: Float = 100f,      // base width in world units
    val h: Float = 100f,      // base height in world units
    val scale: Float = 1f,    // user-applied scale multiplier (pinch-to-resize)
    val rotation: Float = 0f, // degrees
    val zIndex: Float = 0f,   // draw order
) {
    val renderW: Float get() = w * scale   // rendered width in world units
    val renderH: Float get() = h * scale   // rendered height in world units
}
```

**Coordinate contract:**
- `cx`/`cy` = center of the node in world space (not top-left corner).
- `w`/`h` = base world-unit dimensions. **Not** native pixel size, **not** immutable intrinsic dimensions, **not** final rendered size. They may be rebased later (see Rebasing below) as long as the visual `renderW × renderH` is preserved.
- `scale` = current multiplier over `w/h`. Resize gestures mutate `scale`, **not** `w/h`.
- **Render invariant:** `renderW × renderH = w*scale × h*scale`. Every consumer (rendering, AABB, hit-test, viewport culling, selection bounds, group rect math) MUST read `renderW/renderH`, never raw `w/h`.
- **Do NOT conflate `Transform.scale` with `Camera.scale`** — they have different semantics (see Camera below).

**Creation conventions (`CanvasNodeFactory`):**
Unified across node types: `transform.scale = 1 / camera.scale` at creation, `w/h = targetRender * camera.scale`.

| Node | `targetRenderW × targetRenderH` (world units) |
|------|------------------------------------------------|
| `Frame` | `(screenWidth/camera.scale × 0.8) × (screenHeight/camera.scale × 0.8)` |
| `Media` | aspect-preserving fit of `imageWidth × imageHeight` into 80% of viewport |

The `targetRender` formulas inherently contain a `1/camera.scale` factor (the world-unit viewport shrinks as we zoom in). Putting `1/camera.scale` into `scale` and multiplying `w/h` by `camera.scale` cancels that factor out of `w/h` — so **`w/h` are camera-independent**, representing the canonical render size at `scale = 1`. Two frames created at different zoom levels end up with identical `w/h` (e.g. `screenW × 0.8`).

Visual: `renderW = w * scale = targetRender` regardless of split. Pleasant default: at `camera.scale = 1`, `scale = 1`.

Both factories also set `VisibilityPolicy(referenceScale = camera.scale)` so LOD knows the zoom at which the node is "meant to be viewed."

**Rebasing (future capability):**
Two operations should be supported when grouping, frame transforms, or LOD stabilization needs a renormalized transform — neither changes the visual:
```
normalizeTransform():     // bake scale into w/h
  w = renderW; h = renderH; scale = 1

rebaseScale(newScale):    // change scale, recompute w/h to preserve render
  w = renderW / newScale
  h = renderH / newScale
  scale = newScale
```
Use cases: rebase against camera scale or a parent frame's scale, clean up extreme `scale` values after long resize sessions, post-group transformations, or LOD-stable comparisons.

### Camera

`Camera` lives in `core/math/Camera.kt`:

```kotlin
data class Camera(
    val cx: Float = 0f,       // graphicsLayer translationX (screen-pixel units)
    val cy: Float = 0f,       // graphicsLayer translationY (screen-pixel units)
    val scale: Float = 1f,    // zoom factor: 1.0 = 100%, 2.0 = zoomed in 2×
    val rotation: Float = 0f, // canvas rotation in degrees
) {
    companion object {
        const val MIN_SCALE = 0.00005f
        const val MAX_SCALE = 10000f
    }
}
```

**Camera.cx/cy semantics:** `cx`/`cy` are the `translationX`/`Y` values applied to the Compose `graphicsLayer`. Screen position of world point `(wx, wy)` = `(wx*scale + cx, wy*scale + cy)`. To center world point `(wx, wy)` at screen center: `cx = screenWidth/2 - wx*scale`.

**Camera ↔ Transform conversion** — `Transform.toCamera()` in `core/math/TransformUtils.kt`:
```kotlin
fun Transform.toCamera(screenWidth: Float, screenHeight: Float, fillFraction: Float = 0.9f): Camera {
    val scale = minOf(screenWidth * fillFraction / renderW, screenHeight * fillFraction / renderH)
    return Camera(
        cx = screenWidth / 2f - cx * scale,
        cy = screenHeight / 2f - cy * scale,
        scale = scale,
        rotation = rotation,
    )
}
```

### CanvasNode (sealed)

| Variant | Extra fields | Purpose |
|---------|-------------|---------|
| `Frame` | `label`, `color` (hex string), `containsNodeIds` (dynamically calculated) | Navigation area / logical grouping |
| `Media` | `mediaRefId` (FK to `media_library`), `mediaType`, `tags`, `intrinsicPixelWidth/Height` | Any media asset (image, video, text; future: audio, sticker, animated photo, vector shape) |

`Media.intrinsicPixelWidth/Height` are the source's native pixel dimensions (after EXIF rotation). They are **media metadata**, not layout — kept separate from `Transform.w/h` per the scaling contract above. LOD uses them to compute `sourcePxPerScreenPx = intrinsicPixelWidth / (renderW * camera.scale)` for downsampling decisions. `0` = unknown (LOD falls back to render-size-only). When `media_library` ships, intrinsic dims will also live there as the source of truth; the field on `Media` is a per-instance cache.

Both share `id: String`, `transform: Transform`, `visibilityPolicy: VisibilityPolicy?`. Both are `@Serializable`.

### VisibilityPolicy & RenderDetail

Controls how a node appears at different zoom levels. Stored per-node (`visibilityPolicy` field, nullable — falls back to type-based defaults in `LodResolver`).

```kotlin
@Serializable
data class VisibilityPolicy(
    val referenceScale: Float,       // camera zoom at which node is "meant to be viewed"
    val minRelativeZoom: Float,      // below → belowRangeMode
    val maxRelativeZoom: Float,      // above → aboveRangeMode
    val belowRangeMode: RenderDetail,
    val aboveRangeMode: RenderDetail,
)

@Serializable
enum class RenderDetail { Hidden, Stub, Preview, Full, Simplified }
```

LOD resolution is performed by `LodResolver` (`core/math/LodResolver.kt`) — see [rendering.md §4b](rendering.md#4b-level-of-detail-resolution-lod) for the algorithm.

### MediaType

enum class MediaType {
    Image,          // raster photo/image
    Video,          // video clip
    Text,           // text block

    // future
    Audio,          // audio clip
    Sticker,        // static sticker/illustration
    AnimatedPhoto,  // Live Photo / animated GIF
    VectorShape,    // SVG or vector primitive
}


`Image, Video, Text are MVP variants. The rest are planned post-MVP extensions.

### AlbumMeta

```kotlin
data class AlbumMeta(
    val id: Long,
    val name: String,
    val updatedAt: Long,
    val thumbnailUri: String?,
)
```

### AlbumData

Pairs metadata with its scene graph: `AlbumMeta` + `List<CanvasNode>`.

## Persistence

### Room (SQLite)

**Database:** `AppDatabase` (`zoom_album.db`)

#### Table `albums`

| Column | Type | Description |
|--------|------|-------------|
| `id` | Long (PK) | Auto-generated |
| `name` | String | Album name |
| `updatedAt` | Long | Epoch millis, used for sorting on home screen |
| `thumbnailUri` | String? | URI of album thumbnail |

#### Table `ide_workspaces` (UI State)

Isolates panel state from the canvas. Panel collapse/expand writes here without touching the scene graph JSON.

| Column | Type | Description |
|--------|------|-------------|
| `album_id` | Long (PK/FK) | Link to album |
| `activeTheme` | String | Selected color theme (e.g. "DarkCyan") |
| `panelsState` | String (JSON) | Serialized panel state: docked/floating, collapsed/expanded, x/y for floating |

#### Table `media_library` (Project Media Files)

Registry of all media used in an album. Allows finding all usages of a single file.

| Column | Type | Description |
|--------|------|-------------|
| `id` | String (PK) | Hash or UUID of the file |
| `album_id` | Long (FK) | Binding to album |
| `sourceUri` | String | Content URI (local) or URL (network) |
| `mediaType` | Enum | IMAGE, VIDEO, TEXT (current); AUDIO, STICKER, ANIMATED_PHOTO, VECTOR_SHAPE (future) |
| `status` | Enum | AVAILABLE, MISSING (validated at startup) |

### JSON Scene Graph

**Location:** `filesDir/scene_{albumId}.json`

> **Current implementation:** `SceneGraphSerializer` emits a bare `List<CanvasNode>` (flat JSON array), not the wrapped format below. The root object with `albumId` and `viewport` is the **target** format — see todo §1.3.

```json
{
  "albumId": 123,
  "viewport": { "cx": 0, "cy": 0, "scale": 1.0 },
  "nodes": [
    {
      "id": "node_1",
      "type": "MEDIA",
      "mediaRefId": "media_abc",
      "transform": { "cx": 300, "cy": 350, "w": 400, "h": 300, "scale": 1.0, "rotation": 0, "zIndex": 1 },
      "tags": ["vacation", "2024"]
    },
    {
      "id": "frame_1",
      "type": "FRAME",
      "color": "#FF5555",
      "transform": { "cx": 450, "cy": 450, "w": 800, "h": 600, "scale": 1.0, "rotation": 0, "zIndex": 0 },
      "containsNodeIds": ["node_1"]
    }
  ]
}
```

Key points:
- `viewport` saves last camera position (restored on album open)
- `mediaRefId` links to `media_library` table instead of storing URI directly
- `containsNodeIds` on frames is dynamically calculated during node movement
- Serialized via kotlinx-serialization (`prettyPrint`, `ignoreUnknownKeys`)

**Components:** `FileStorageHelper` (raw file I/O) + `SceneGraphSerializer` (encode/decode) + `MediaRepositoryImpl` (abstraction).

## Undo/Redo Model (Snapshot-Based)

`Deque<UndoEntry>`, capped at ~50-100 entries. Persisted to `filesDir/history_{albumId}.json` on each mutation, loaded on album open.

```kotlin
data class CanvasCommand(
    val nodeId: String,
    val before: CanvasNode?,  // null = node didn't exist (this was an add)
    val after: CanvasNode?,   // null = node was removed (this was a delete)
)

sealed interface UndoEntry {
    data class Single(val command: CanvasCommand) : UndoEntry
    data class Compound(val commands: List<CanvasCommand>) : UndoEntry
}
```

Snapshot-based: captures full node state before/after, so any mutation (transform, property edit, add, delete) is covered without new command types.

**Grouping:**
- **Drag/resize gestures** — snapshot on gesture start, commit one `Single` command on gesture end. No intermediate commands.
- **Multi-node operations** (e.g. delete frame + unparent children) — wrapped in `Compound` and undo/redo atomically.

**Mechanics:**
- `undo()` — pop from undo stack, restore `before` state for each command, push entry to redo stack.
- `redo()` — pop from redo stack, apply `after` state, push to undo stack.
- Any new command clears the redo stack.

## Repository Interfaces

```
domain/repository/
├── ProjectRepository      # observeAlbums(), createAlbum(), deleteAlbum()
├── MediaRepository        # loadSceneGraph(albumId), saveSceneGraph(albumId, nodes)
└── MediaLibraryRepository # register/query/validate media files (planned)
```

Implementations in `data/repository/`, bound via Hilt `@Binds`.

## Migration Notes

| Area | Current | Target |
|------|---------|--------|
| `albums.createdAt` | exists (not yet removed) | to be removed |
| `albums.thumbnailPath` | `thumbnailPath: String?` (not yet renamed) | rename to `thumbnailUri` |
| `Transform.width/height` | `width`, `height` | renamed to `w`, `h` ✓ |
| `Transform.x/y` | top-left corner | renamed to `cx/cy` (center-based) ✓ |
| `Transform.w/h` | normalized (short side = 1) | actual world-unit size ✓ |
| `Transform.zIndex` | separate field on `CanvasNode` | moved into `Transform` ✓ |
| `Camera` | in `CanvasViewModel` | moved to `core/math/Camera.kt` ✓ |
| `CanvasNode.Media.uri` | direct URI string | `mediaRefId` FK to `media_library` |
| `Frame.childIds` | static list | `containsNodeIds` (dynamic) |
| `Frame.color` | Long (ARGB) | hex string |
| `CanvasNode.tags` | not present | added |
| `ide_workspaces` table | not present | new table |
| `media_library` table | not present | new table |
| Scene graph `viewport` | not saved | saved in JSON root |
| Undo/Redo | not present | Snapshot-based `UndoEntry` deque, persisted to `history_{albumId}.json` |

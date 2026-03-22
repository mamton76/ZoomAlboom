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
- `w`/`h` = actual world-unit base dimensions (not normalized).
- `scale` = user-applied multiplier (default 1.0); used for pinch-to-resize on a node.
- Render size = `renderW × renderH = w*scale × h*scale`.
- **Do NOT conflate `Transform.scale` with `Camera.scale`** — they have different semantics (see Camera below).

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
| `Media` | `mediaRefId` (FK to `media_library`), `mediaType`, `tags` | Any media asset (image, video, text; future: audio, sticker, animated photo, vector shape) |

Both share `id: String`, `transform: Transform`. Both are `@Serializable`.

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

## Undo/Redo Model (Command Pattern)

In-memory `Deque<CanvasCommand>`, persisted to `filesDir/history_{albumId}.json` during autosave. Each action is atomic.

```kotlin
@Serializable
sealed interface CanvasCommand {
    val targetNodeId: String

    data class Move(
        override val targetNodeId: String,
        val oldTransform: Transform,
        val newTransform: Transform,
    ) : CanvasCommand

    data class AddNode(
        override val targetNodeId: String,
        val node: CanvasNode,
    ) : CanvasCommand

    data class RemoveNode(
        override val targetNodeId: String,
        val node: CanvasNode,
    ) : CanvasCommand

    // undo() applies oldTransform / removes node / re-adds node
    // redo() applies newTransform / adds node / removes node
}
```

**Mechanics:**
- `undo()` — pop from undo stack, apply reverse, push to redo stack.
- `redo()` — pop from redo stack, apply forward, push to undo stack.
- Any new command clears the redo stack.
- History file (`history_{albumId}.json`) written on autosave, loaded on album open.

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
| Undo/Redo | not present | `CanvasCommand` deque + `history_{albumId}.json` |

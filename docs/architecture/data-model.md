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
    val x: Float = 0f,        // world X
    val y: Float = 0f,        // world Y
    val w: Float = 100f,      // unscaled width
    val h: Float = 100f,      // unscaled height
    val scale: Float = 1f,    // uniform scale
    val rotation: Float = 0f, // degrees
    val zIndex: Float = 0f,   // draw order
)
```

### CanvasNode (sealed)

| Variant | Extra fields | Purpose |
|---------|-------------|---------|
| `Frame` | `label`, `color` (hex string), `containsNodeIds` (dynamically calculated) | Navigation area / logical grouping |
| `Media` | `mediaRefId` (FK to `media_library`), `mediaType`, `tags` | Image/video asset |

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
| `mediaType` | Enum | IMAGE, VIDEO, AUDIO |
| `status` | Enum | AVAILABLE, MISSING (validated at startup) |

### JSON Scene Graph

**Location:** `filesDir/scene_{albumId}.json`

```json
{
  "albumId": 123,
  "viewport": { "x": 0, "y": 0, "scale": 1.0 },
  "nodes": [
    {
      "id": "node_1",
      "type": "MEDIA",
      "mediaRefId": "media_abc",
      "transform": { "x": 100, "y": 200, "w": 400, "h": 300, "scale": 1.0, "rotation": 0, "zIndex": 1 },
      "tags": ["vacation", "2024"]
    },
    {
      "id": "frame_1",
      "type": "FRAME",
      "color": "#FF5555",
      "transform": { "x": 50, "y": 150, "w": 800, "h": 600, "scale": 1.0, "rotation": 0, "zIndex": 0 },
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
| `albums.createdAt` | exists | removed (not needed) |
| `albums.thumbnailPath` | String? | renamed to `thumbnailUri` |
| `Transform.width/height` | `width`, `height` | renamed to `w`, `h` |
| `Transform.zIndex` | separate field on `CanvasNode` | moved into `Transform` |
| `CanvasNode.Media.uri` | direct URI string | `mediaRefId` FK to `media_library` |
| `Frame.childIds` | static list | `containsNodeIds` (dynamic) |
| `Frame.color` | Long (ARGB) | hex string |
| `CanvasNode.tags` | not present | added |
| `ide_workspaces` table | not present | new table |
| `media_library` table | not present | new table |
| Scene graph `viewport` | not saved | saved in JSON root |
| Undo/Redo | not present | `CanvasCommand` deque + `history_{albumId}.json` |

# Data Model

## Domain Models

### Transform

Position, size, scale, and rotation of any canvas object.

```kotlin
@Serializable
data class Transform(
    val x: Float = 0f,        // world X
    val y: Float = 0f,        // world Y
    val width: Float = 100f,  // unscaled width
    val height: Float = 100f, // unscaled height
    val scale: Float = 1f,    // uniform scale
    val rotation: Float = 0f, // degrees
)
```

### CanvasNode (sealed)

Scene graph node. Two variants:

| Variant | Extra fields | Purpose |
|---------|-------------|---------|
| `Frame` | `label`, `color` (ARGB Long), `childIds` | Navigation area / logical grouping |
| `Media` | `uri`, `mediaType` (`IMAGE` / `VIDEO`) | Image or video asset |

Both share `id: String`, `transform: Transform`, `zIndex: Float`. Both are `@Serializable`.

### AlbumMeta

```kotlin
data class AlbumMeta(
    val id: Long,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long,
    val thumbnailPath: String?,
)
```

### AlbumData

Pairs metadata with its scene graph: `AlbumMeta` + `List<CanvasNode>`.

## Persistence

### Room — Album Metadata

**Database:** `AppDatabase` (version 1, `zoom_album.db`)

**Table:** `albums`

| Column | Type | Notes |
|--------|------|-------|
| `id` | `Long` | auto-generated PK |
| `name` | `String` | |
| `createdAt` | `Long` | epoch millis |
| `updatedAt` | `Long` | epoch millis |
| `thumbnailPath` | `String?` | nullable |

**DAO:** `AlbumDao`
- `observeAll(): Flow<List<AlbumEntity>>` — ordered by `updatedAt DESC`
- `insert(album): Long`
- `deleteById(id: Long)`

### JSON Files — Scene Graphs

**Location:** `context.filesDir/scene_$albumId.json`

**Format:** `List<CanvasNode>` serialized via kotlinx-serialization with `prettyPrint = true` and `ignoreUnknownKeys = true`.

**Components:**
- `FileStorageHelper` — raw file read/write.
- `SceneGraphSerializer` — encode/decode `List<CanvasNode>`.
- `MediaRepositoryImpl` — combines both behind the `MediaRepository` interface.

## Repository Interfaces

```
domain/repository/
├── ProjectRepository    # observeAlbums(), createAlbum(), deleteAlbum()
└── MediaRepository      # loadSceneGraph(albumId), saveSceneGraph(albumId, nodes)
```

Implementations live in `data/repository/` and are bound via Hilt's `@Binds`.
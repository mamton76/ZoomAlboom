# ZoomAlboom Architecture Overview

> **Source of truth** for the project's architecture. Other docs in this folder expand on individual subsystems.
>
> Related: [PRD](../product/PRD.md) | [Vision](../product/vision.md) | [Future Ideas](../product/future-ideas.md) | [TODO](../todo.md)

## High-Level Summary

ZoomAlboom is a single-module Android app for creating and navigating infinite-canvas photo/video albums. Users place media and navigation frames on a zoomable, pannable, rotatable canvas, then browse albums through an IDE-style overlay UI.

## Layers (Clean Architecture)

```
com.mamton.zoomalbum/
├── app/          # Entry point, Hilt setup, navigation
├── core/         # Shared: design system, pure math, MVI contracts
├── domain/       # Models, repository interfaces, use cases
├── data/         # Room database (albums) + file serialization (scene graphs)
└── feature/      # canvas/, ide_ui/, projects_home/
```

**Dependency rule:** `feature` -> `domain` <- `data`; `core` is shared by all.

### app/
- `ZoomAlbumApp` (`@HiltAndroidApp`) — application entry point.
- `MainActivity` (`@AndroidEntryPoint`) — single Activity composing `CanvasScreen` (background) + `IdeOverlayScreen` (foreground) in a `Box`.
- `AppNavigation` — Jetpack Navigation routes (`PROJECTS_HOME`, `CANVAS/{albumId}`). Currently defined but not yet wired into `MainActivity`.
- `AppModule` — Hilt DI: `DatabaseModule` (Room singleton, DAO), `RepositoryModule` (binds impls).

### core/
- **`mvi/MviContract.kt`** — marker interfaces: `State`, `Intent`, `Effect`.
- **`math/TransformUtils.kt`** — pure functions: `rotateVector`, `toBoundingBox`, `cameraViewport`.
- **`math/SpatialIndex.kt`** — `ViewportCuller`: brute-force AABB visible-node filter.
- **`math/BoundingBox.kt`** — axis-aligned bounding box with `intersects()`.
- **`designsystem/`** — `Color.kt` (dark-first palette), `Theme.kt` (Material 3 `ZoomAlbumTheme`).

### domain/
- **Models:** `Transform`, `CanvasNode` (sealed: `Frame` | `Media`), `AlbumMeta`, `AlbumData`, `MediaType`.
- **Repository interfaces:** `ProjectRepository` (albums CRUD), `MediaRepository` (scene graph load/save).
- **Use cases:** `CalculateViewportIntersectionsUseCase`, `SaveSceneGraphUseCase`.

### data/
- **Room:** `AppDatabase` (v1), `AlbumEntity`, `AlbumDao` (observe, insert, delete).
- **File I/O:** `FileStorageHelper` (reads/writes `scene_$albumId.json`), `SceneGraphSerializer` (kotlinx-serialization JSON).
- **Repo impls:** `ProjectRepositoryImpl`, `MediaRepositoryImpl`.

### feature/
| Feature | Responsibility |
|---------|---------------|
| `canvas/` | Infinite canvas: gestures, camera, node rendering, viewport culling |
| `ide_ui/` | IDE overlay: docked/floating panel system, panel state management |
| `projects_home/` | Album list screen (stub) |

Each feature owns its ViewModel(s) and Compose UI. ViewModels are `@HiltViewModel`.

See [conventions.md](conventions.md) for code style, file placement rules, and "how to add" recipes.

## State Management — MVI

Every feature follows the MVI pattern:

1. **State** — immutable data class exposed as `StateFlow<T>`.
2. **Intent / Action** — sealed interface describing user events.
3. **ViewModel** — owns `MutableStateFlow`, processes intents, emits new state.
4. **Composable** — observes state via `collectAsStateWithLifecycle()`, dispatches intents.

Canvas and IDE have **separate ViewModels** to prevent cross-recomposition.

## Canvas Rendering Pipeline

See [rendering.md](rendering.md) for full detail.

1. `InfiniteCanvasGestureDetector` emits `(centroid, pan, zoom, rotation)`.
2. `CanvasViewModel.onGesture()` updates `Camera` state (centroid-anchored zoom/rotation).
3. `ViewportCuller` filters visible nodes on `Dispatchers.Default`.
4. `CanvasScreen` applies **one `graphicsLayer`** (translate + scale + rotate, origin `(0,0)`) to an inner `Box`.
5. `CanvasNodeRenderer` draws each visible node at its world-coordinate position.

**Key insight:** all pan/zoom/rotation is a single GPU transform — child nodes never recompose during gestures.

## Data Persistence

See [data-model.md](data-model.md) for schema details.

| Data | Storage | Format | Status |
|------|---------|--------|--------|
| Album metadata | Room (`albums` table) | SQL | implemented |
| IDE workspace state | Room (`ide_workspaces` table) | SQL + JSON blob | planned |
| Media registry | Room (`media_library` table) | SQL | planned |
| Scene graphs | `filesDir/scene_{albumId}.json` | JSON (kotlinx-serialization) | implemented (format changing) |
| Undo/Redo history | `filesDir/history_{albumId}.json` | JSON (command pattern) | planned |

Canvas mutations go through `CanvasCommand` (sealed interface: `Move`, `AddNode`, `RemoveNode`). Commands are stored in a `Deque` in memory and autosaved to disk. See [data-model.md § Undo/Redo](data-model.md#undoredo-model-command-pattern).

## IDE Overlay

See [rendering.md](rendering.md) § IDE Overlay.

Six docked slots (`LeftTop`, `LeftBottom`, `RightTop`, `RightBottom`, `Top`, `Bottom`) plus floating panels. Each slot supports multiple panels via tab switching. Floating panels auto-dock when dragged near edges.

## Navigation

See [navigation.md](navigation.md).

Three navigation levels:

1. **App-level** — Jetpack Navigation between screens (`PROJECTS_HOME` -> `CANVAS/{albumId}`). Not yet wired into `MainActivity`.
2. **In-album camera** — pan / zoom / rotate via gestures. Continuous, user-driven.
3. **Frame transitions** — animated camera interpolation (linear/bezier) to focus on a frame. Discrete, intent-driven.

## Tech Stack

| Component | Technology |
|-----------|------------|
| Language | Kotlin 2.2.10 |
| Min / Target SDK | 24 / 36 |
| UI | Jetpack Compose + Canvas API + `Modifier.graphicsLayer` |
| DI | Hilt |
| DB | Room |
| Serialization | kotlinx-serialization (JSON) |
| Images | Coil 3 (deferred) |
| Async | Coroutines + Flow |
| Navigation | Jetpack Navigation Compose |

## Dependencies

Managed via version catalog (`gradle/libs.versions.toml`). Compose libraries via BOM.

| Dependency | Version |
|------------|---------|
| Compose BOM | 2026.02.01 |
| Hilt | 2.59.1 |
| Room | 2.8.4 |
| Coil | 3.4.0 |
| kotlinx-serialization | 1.9.0 |
| Navigation Compose | 2.9.7 |
| Lifecycle | 2.10.0 |
| Coroutines | 1.10.1 |
| KSP | 2.2.10-2.0.2 |
| AGP | 9.0.1 |

**Gotchas:** No `material-icons-core` in deps (use Unicode chars). `TabRow` deprecated in this BOM — use `PrimaryTabRow` with `@OptIn(ExperimentalMaterial3Api::class)`.

## Key Architectural Decisions

1. **Frames are navigation anchors, not only visual containers** — they structure the canvas and support transitions through album space.
2. **Single `graphicsLayer`** for all camera transforms — GPU-accelerated, zero recomposition.
3. **Separate Canvas / IDE ViewModels** — isolated recomposition scopes.
4. **Dual persistence** — Room for queryable metadata, JSON files for flexible scene graphs.
5. **Brute-force viewport culling** — sufficient now; planned grid/R-tree upgrade for >2k nodes.
6. **Dark-first design system** — `CanvasDark`, `PanelBackground`, `AccentCyan`.

## Performance Principles

- Single shared transform layer (`graphicsLayer`) — avoid per-node recomposition during gestures
- Heavy geometry and containment work runs off main thread (`Dispatchers.Default`)
- Progressive image loading / downsampling at low zoom to prevent OOM
- Viewport culling — only render nodes visible in the current camera AABB

## Open Questions & Future Direction

See [project-memory.md](../product/project-memory.md) for the full decisions log.

- **Unit System:** Canvas should use abstract `Units` instead of raw pixels. Needs a reliable `Units -> DP` formula accounting for zoom and screen density.
- **Dynamic Containment:** Frame `containsNodeIds` must be recalculated on `Dispatchers.Default` when nodes move — avoid blocking the main thread.
- **Persistence Evolution:** Current SQLite + JSON is local-only. Future consideration: CRDT or Protobuf for real-time cloud collaboration.
- **Media Validation:** On album open, check `media_library` source URIs and substitute placeholders for missing files.

Planned features (post-MVP): smart tags, layers, audio/live photos, crop. See [future-ideas.md](../product/future-ideas.md).

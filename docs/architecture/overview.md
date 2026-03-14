# ZoomAlboom Architecture Overview

> **Source of truth** for the project's architecture. Other docs in this folder expand on individual subsystems.

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

| Data | Storage | Format |
|------|---------|--------|
| Album metadata | Room (`albums` table) | SQL |
| Scene graphs | `filesDir/scene_$albumId.json` | JSON (kotlinx-serialization) |

## IDE Overlay

See [rendering.md](rendering.md) § IDE Overlay.

Six docked slots (`LeftTop`, `LeftBottom`, `RightTop`, `RightBottom`, `Top`, `Bottom`) plus floating panels. Each slot supports multiple panels via tab switching. Floating panels auto-dock when dragged near edges.

## Navigation

See [navigation.md](navigation.md).

Routes: `PROJECTS_HOME` (album list) and `CANVAS/{albumId}` (canvas editor). Not yet fully integrated — `MainActivity` currently composes canvas directly.

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

## Key Architectural Decisions

1. **Single `graphicsLayer`** for all camera transforms — GPU-accelerated, zero recomposition.
2. **Separate Canvas / IDE ViewModels** — isolated recomposition scopes.
3. **Dual persistence** — Room for queryable metadata, JSON files for flexible scene graphs.
4. **Brute-force viewport culling** — sufficient now; planned grid/R-tree upgrade for >2k nodes.
5. **Dark-first design system** — `CanvasDark`, `PanelBackground`, `AccentCyan`.
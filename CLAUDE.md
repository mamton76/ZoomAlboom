# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Commands

```bash
./gradlew assembleDebug              # Build debug APK
./gradlew test                       # Run unit tests
./gradlew testDebugUnitTest          # Run a specific test class: add --tests "com.mamton.zoomalboom.ExampleUnitTest"
./gradlew connectedAndroidTest       # Run instrumented tests on connected device/emulator
./gradlew clean                      # Clean build artifacts
```

## Architecture

> **Full docs:** [`docs/architecture/overview.md`](docs/architecture/overview.md) — source of truth
> See also: [data-model](docs/architecture/data-model.md) | [modules & DI](docs/architecture/modules.md) | [rendering](docs/architecture/rendering.md) | [navigation](docs/architecture/navigation.md)

ZoomAlboom is a single-module Android app organized in Clean Architecture layers:

```
com.mamton.zoomalbum/
├── app/          # Entry point, Hilt setup, navigation
├── core/         # Shared: design system, pure math utils, MVI contracts
├── domain/       # Models, repository interfaces, use cases
├── data/         # Room database (albums) + file serialization (scene graphs)
└── feature/      # canvas/, ide_ui/, projects_home/
```

### Canvas Rendering

The infinite canvas (`feature/canvas/`) uses a single `graphicsLayer` transformation on a Box composable — all pan/zoom/rotation is handled as GPU transforms rather than recomposing individual nodes. `InfiniteCanvasGestureDetector` extracts centroid, pan, zoom, and rotation deltas and forwards them to `CanvasViewModel`, which maintains the camera state. `CanvasNodeRenderer` (`feature/canvas/view/`) draws individual `CanvasNode`s (sealed class: `Frame` | `Media`) after viewport culling via `ViewportCuller` (`core/math/SpatialIndex.kt`). Camera translation math lives in `TransformUtils` (`core/math/`) — rotation of vectors uses `TransformUtils.rotateVector`.

### State Management

MVI pattern throughout: `MviContract.kt` defines the `State`/`Intent`/`Effect` interfaces. ViewModels expose `StateFlow<State>` and process intents. The canvas and IDE overlay have separate ViewModels (`CanvasViewModel`, `IdeViewModel`) to prevent cross-recomposition.

### Data Persistence

- **Albums metadata** → Room database (`AppDatabase`, `AlbumDao`, `AlbumEntity`)
- **Scene graphs** (the list of `CanvasNode`s per album) → JSON files via `SceneGraphSerializer` + `FileStorageHelper`, abstracted behind `MediaRepository`

### Key Domain Models

- `Transform` — position (offset), size, scale, rotation for any canvas object
- `CanvasNode` — sealed class: `Frame` (navigation area) | `Media` (image/video/note)
- `AlbumData` — pairs `AlbumMeta` with its `List<CanvasNode>` scene graph
- `IdeState` — panel layout state for the IDE overlay (lives in `feature/ide_ui/viewmodel/`, not `domain/`)

## Tech Stack

- **Language:** Kotlin 2.2.10, **Min SDK:** 24, **Target SDK:** 36
- **UI:** Jetpack Compose + Canvas API + `Modifier.graphicsLayer`
- **DI:** Hilt, **DB:** Room, **Serialization:** kotlinx-serialization, **Images:** Coil 3
- **Async:** Coroutines + Flow
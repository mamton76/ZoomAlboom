# Modules & Dependency Injection

> Related: [overview](overview.md) | [data-model](data-model.md) | [rendering](rendering.md)

## Single-Module Structure

ZoomAlboom is a single Gradle module (`app`). Packages enforce the layer boundaries:

```
com.mamton.zoomalbum/
├── app/            # Entry point, DI, navigation
├── core/           # Shared utilities (no Android framework deps in math/)
├── domain/         # Pure Kotlin — models, interfaces, use cases
├── data/           # Android — Room, file I/O, repo implementations
└── feature/        # Compose UI + ViewModels
    ├── canvas/
    ├── ide_ui/
    └── projects_home/
```

**Dependency direction:** `feature` -> `domain` <- `data`. `core` is shared. `app` wires everything.

## Hilt Setup

### Entry Points

| Class | Annotation |
|-------|-----------|
| `ZoomAlbumApp` | `@HiltAndroidApp` |
| `MainActivity` | `@AndroidEntryPoint` |
| `CanvasViewModel` | `@HiltViewModel` |
| `IdeViewModel` | `@HiltViewModel` |
| `ProjectsViewModel` | `@HiltViewModel` |

### Modules (in `app/di/AppModule.kt`)

**DatabaseModule** (`@InstallIn(SingletonComponent)`, `object`):
- `provideDatabase()` — Room `AppDatabase` singleton.
- `provideAlbumDao()` — `AlbumDao` from database.

**RepositoryModule** (`@InstallIn(SingletonComponent)`, `abstract class`):
- `bindProjectRepository()` — `ProjectRepositoryImpl` -> `ProjectRepository`.
- `bindMediaRepository()` — `MediaRepositoryImpl` -> `MediaRepository`.

### Injection in ViewModels

ViewModels use constructor injection:

```kotlin
@HiltViewModel
class CanvasViewModel @Inject constructor() : ViewModel()
```

`FileStorageHelper` and `SceneGraphSerializer` are `@Inject constructor` classes — Hilt provides them automatically.

## Feature Isolation

Each feature package owns:
- **ViewModel(s)** — processes intents, holds state.
- **UI composables** — observes state, dispatches actions.
- **Feature-specific models** (e.g., `IdeState.kt` lives in `feature/ide_ui/viewmodel/`, not `domain/`).

Cross-feature communication goes through shared domain models (`CanvasNode`, `AlbumData`).
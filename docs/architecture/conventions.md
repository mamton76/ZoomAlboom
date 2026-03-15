# Code Conventions

> Related: [overview](overview.md) | [modules & DI](modules.md)

## File & Package Naming

- Files: `PascalCase.kt` (e.g. `CanvasViewModel.kt`, `TransformUtils.kt`)
- Feature packages: lowercase with underscores (e.g. `ide_ui`, `projects_home`)
- Classes/sealed classes: PascalCase
- Functions: camelCase
- Constants: `SCREAMING_SNAKE_CASE` in companion objects
- Data class properties: camelCase

## Where to Put New Files

| Adding... | Location | Example |
|-----------|----------|---------|
| Domain model | `domain/model/` | `CanvasCommand.kt` |
| Repository interface | `domain/repository/` | `MediaLibraryRepository.kt` |
| Use case | `domain/usecase/` | `SaveSceneGraphUseCase.kt` |
| Room entity | `data/local/room/` | `MediaLibraryEntity.kt` |
| Room DAO | `data/local/room/` | `MediaLibraryDao.kt` |
| File I/O helper | `data/local/file/` | `HistoryStorageHelper.kt` |
| Repository impl | `data/repository/` | `MediaLibraryRepositoryImpl.kt` |
| New feature screen | `feature/<name>/ui/` | `feature/export/ui/ExportScreen.kt` |
| Feature ViewModel | `feature/<name>/viewmodel/` | `feature/export/viewmodel/ExportViewModel.kt` |
| Feature-specific state | `feature/<name>/viewmodel/` | `IdeState.kt` (not in `domain/`) |
| Shared math util | `core/math/` | `SpatialIndex.kt` |
| Design tokens | `core/designsystem/` | `Color.kt` |
| Hilt module | `app/di/` | `AppModule.kt` |
| Navigation route | `app/navigation/` | `AppNavigation.kt` |

## Feature Structure Template

```
feature/<name>/
├── ui/           # Composable screens and components
├── viewmodel/    # ViewModel + State/Action definitions
├── gestures/     # Gesture handling (if needed)
├── view/         # Rendering logic (if needed)
└── layout/       # Custom layout logic (if needed)
```

## How To: Add a New Panel

1. Create content composable in `feature/ide_ui/ui/panels/MyPanel.kt`
2. Add variant to `PanelTab` enum in `feature/ide_ui/viewmodel/IdeState.kt`:
   ```kotlin
   enum class PanelTab(val label: String) {
       MediaLibrary("Media Library"),
       FrameList("Frame List"),
       MyPanel("My Panel"),       // add here
   }
   ```
3. Add content rendering in `DockedPanel.kt` `InnerTabContent()` composable
4. If it should appear by default, add to the default `IdeUiState.panels` list in `IdeState.kt`

## How To: Add a New Room Table

1. Create entity in `data/local/room/MyEntity.kt` with `@Entity`
2. Create DAO in `data/local/room/MyDao.kt` with `@Dao`
3. Add to `AppDatabase`: add entity to `@Database(entities = [...])`, add abstract DAO getter
4. Increment database version, add migration in `AppModule.kt`
5. Expose DAO via `DatabaseModule` `@Provides` in `app/di/AppModule.kt`
6. Create domain interface in `domain/repository/MyRepository.kt`
7. Create impl in `data/repository/MyRepositoryImpl.kt` with `@Inject constructor`
8. Bind in `RepositoryModule` with `@Binds`

## How To: Add a New ViewModel

1. Create in `feature/<name>/viewmodel/MyViewModel.kt`
2. Follow MVI pattern:
   ```kotlin
   @Immutable
   data class MyState(...) : State

   sealed interface MyAction : Intent {
       data class DoSomething(...) : MyAction
   }

   @HiltViewModel
   class MyViewModel @Inject constructor(
       // inject repos here
   ) : ViewModel() {
       private val _state = MutableStateFlow(MyState())
       val state: StateFlow<MyState> = _state.asStateFlow()

       fun onAction(action: MyAction) { ... }
   }
   ```
3. Use in Compose:
   ```kotlin
   @Composable
   fun MyScreen(viewModel: MyViewModel = hiltViewModel()) {
       val state by viewModel.state.collectAsStateWithLifecycle()
   }
   ```

## How To: Add a New CanvasNode Variant

1. Add data class inside `CanvasNode` sealed class in `domain/model/CanvasNode.kt`
2. Add `@Serializable` annotation
3. Override abstract properties (`id`, `transform`)
4. Update `CanvasNodeRenderer` in `feature/canvas/view/CanvasRenderer.kt` with `when` branch
5. Update `SceneGraphSerializer` if JSON format changes
6. Update `ViewportCuller` if bounding box logic differs

## Compose Patterns

**Modifier chains** — one modifier per line when multiple:
```kotlin
Box(
    modifier = Modifier
        .fillMaxSize()
        .background(CanvasDark)
        .pointerInput(Unit) { ... },
)
```

**State collection** — always use lifecycle-aware:
```kotlin
val state by viewModel.state.collectAsStateWithLifecycle()
```

**ViewModel injection** — default parameter:
```kotlin
@Composable
fun MyScreen(viewModel: MyViewModel = hiltViewModel()) { ... }
```

## Sealed Class Style

```kotlin
@Serializable
sealed class Parent {
    abstract val id: String
    // abstract properties first

    @Serializable
    data class VariantA(...) : Parent()

    @Serializable
    data class VariantB(...) : Parent()
}
```

For intents/actions — sealed interface:
```kotlin
sealed interface MyAction : Intent {
    data class Foo(...) : MyAction
    data class Bar(...) : MyAction
}
```

## Import Ordering

1. `android.*` / `androidx.*`
2. Third-party (`dagger.*`, `javax.*`, `kotlinx.*`)
3. Project (`com.mamton.zoomalbum.*`)
4. `kotlin.*`
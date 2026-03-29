# TODO — ZoomAlboom

Gap between current implementation and target architecture.

> Sources: [data-model](architecture/data-model.md) | [PRD](product/PRD.md) | [project-memory](product/project-memory.md) | [future-ideas](product/future-ideas.md)

## Legend
- `[ ]` — not started
- `[~]` — partially done / stub exists
- `[x]` — done

---

## 1. Data Model Migration

### 1.1 Transform & CanvasNode refactor
- [x] Rename `Transform.width/height` to `w/h`
- [x] Move `zIndex` from `CanvasNode` into `Transform`
- [x] `Frame.color`: change from `Long` (ARGB) to hex `String`
- [x] `Frame.childIds` -> `containsNodeIds` (dynamic calculation)
- [x] `Media.uri` -> `mediaRefId` (FK to `media_library`)
- [x] Add `tags: List<String>` to `CanvasNode.Media`
- [x] Add `MediaType.AUDIO` variant
- [x] `Transform.x/y` → `cx/cy` (center-based world coords, not top-left)
- [x] `Transform.w/h` → actual world-unit size (not normalized aspect ratio)
- [x] `Transform.renderW`/`renderH` computed properties added
- [x] `Camera` moved to `core/math/Camera.kt` (was in `CanvasViewModel`)
- [x] `Camera.x/y` → `cx/cy` (graphicsLayer translation values)
- [x] `Transform.toCamera()` conversion helper added in `TransformUtils.kt`
- [x] `CanvasNodeFactory` simplified (no rotateVector compensation needed with center-based coords)
- [x] `CanvasViewModel.allNodes` → `MutableStateFlow` (fixes P5 race condition)
- [x] `CanvasViewModel.frames: StateFlow<List<Frame>>` exposed reactively
- [~] Update `SceneGraphSerializer` for new JSON structure (serializer exists, but emits flat `List<CanvasNode>` — needs root wrapper; JSON field names now cx/cy)

### 1.2 AlbumMeta / albums table
- [ ] Remove `createdAt` field (still present in `AlbumMeta` + `AlbumEntity`)
- [ ] Rename `thumbnailPath` to `thumbnailUri` (still `thumbnailPath` in code)

### 1.3 Scene graph JSON format
- [ ] Wrap nodes in root object with `albumId` and `viewport` (currently serializes bare list)
- [ ] Save/restore last camera position on album open/close

### 1.4 New Room tables
- [ ] `ide_workspaces` — persist panel state per album (`album_id`, `activeTheme`, `panelsState` JSON)
- [ ] `media_library` — media registry (`id`, `album_id`, `sourceUri`, `mediaType`, `status`)
- [ ] Room migration (v1 -> v2)
- [ ] `IdeWorkspaceEntity` + `IdeWorkspaceDao`
- [ ] `MediaLibraryEntity` + `MediaLibraryDao`

### 1.5 New repositories
- [ ] `MediaLibraryRepository` interface (domain)
- [ ] `MediaLibraryRepositoryImpl` (data)
- [ ] `IdeWorkspaceRepository` interface (domain)
- [ ] `IdeWorkspaceRepositoryImpl` (data)
- [ ] Bind new repos in `AppModule`

---

## 2. Undo/Redo (Command Pattern)

- [ ] `CanvasCommand` sealed interface (`Move`, `AddNode`, `RemoveNode`)
- [ ] `CommandHistory` class — undo/redo `Deque` management
- [ ] Integrate into `CanvasViewModel` (all mutations go through commands)
- [ ] Autosave history to `filesDir/history_{albumId}.json`
- [ ] Load history on album open
- [ ] Undo/redo UI buttons in TopBar

---

## 3. Navigation

- [x] Routes defined (`PROJECTS_HOME`, `CANVAS/{albumId}`) with `Routes.canvas()` helper
- [x] Wire `AppNavigation` NavHost into `MainActivity`
- [x] Pass `albumId` to `CanvasViewModel` via `SavedStateHandle`
- [x] Back navigation from canvas to album list

---

## 4. Canvas — Core MVP

### 4.0 Album loading
- [x] Load scene graph from `MediaRepository` on album open
- [x] Save scene graph on ViewModel cleared (album exit)

### 4.1 Media rendering
- [x] `CanvasNode.Frame` rendering via `graphicsLayer` + `drawBehind` (no Compose Constraints limits)
- [ ] `CanvasNode.Media` rendering with Coil 3 (currently stub / placeholder only)
- [ ] Downsampling at low zoom levels (OOM prevention)
- [ ] High-res loading on zoom-in
- [ ] Missing media placeholder (when `status == MISSING`)

### 4.2 Node interaction
- [ ] Node selection (tap to select, bounding box highlight)
- [ ] Node drag/move (update `Transform`, record `CanvasCommand.Move`)
- [ ] Node resize
- [ ] Multi-select

### 4.3 Dynamic containment
- [ ] Calculate `containsNodeIds` on node move (AABB intersection with frames)
- [ ] Run on `Dispatchers.Default` to avoid blocking main thread

### 4.4 Media validation
- [ ] On album open, iterate `media_library` and check `sourceUri` availability
- [ ] Mark missing files as `status = MISSING`
- [ ] Show placeholder for missing media on canvas

### 4.5 Viewport culling upgrade
- [x] Brute-force AABB (`ViewportCuller` in `core/math/SpatialIndex.kt`)
- [ ] Spatial index (grid or R-tree) for >2k nodes

### 4.6 Level-of-Detail (LOD)
- [x] `VisibilityPolicy` data class + `RenderDetail` enum (`domain/model/VisibilityPolicy.kt`)
- [x] `visibilityPolicy` field on `CanvasNode` (optional, per-node override)
- [x] `LodResolver` object (`core/math/LodResolver.kt`) — screen-size cull + semantic zoom filtering
- [x] Default policies for Frame and Media node types
- [x] Debug logging in `LodResolver` (tag: `LodResolver`)
- [ ] Wire `LodResolver` into rendering pipeline (skip/downgrade nodes based on `RenderDetail`)
- [ ] Persist `visibilityPolicy` in scene graph JSON

---

## 5. UI Architecture — Canvas-First Refactor

- [x] Refactor `IdeViewModel` default state: all panels hidden by default (empty panels list)
- [x] `CanvasScaffold` — M3 Scaffold with TopBar + FAB wrapping canvas + IDE overlay
- [x] `CanvasTopBar` — back button, album name, frame list + panel config actions
- [x] FAB [+] with `AddContentBottomSheet` (content type picker: Photo, Video, Text, Sticker, Frame)
- [x] `MediaLibraryBottomSheet` — stub (placeholder for future media browser)
- [x] `ContextualActionBar` — stub with AnimatedVisibility (awaits node selection from §4.2)
- [x] `PanelConfigDialog` — toggle panels on/off, reset to defaults
- [x] `IdeState.PanelConfig` model + `TogglePanelEnabled` / `ResetPanelConfig` actions
- [x] `FrameListBottomSheet` — accessible from TopBar, shows visible frames with delete
- [x] `CanvasTopBar` — HUD info: visible/total nodes, camera zoom/rotation/xy
- [x] `CanvasNodeFactory` — creates frames with viewport-proportional w/h, zoom-derived scale, rotation-aligned placement
- [x] `CanvasViewModel.addNode()` / `removeNode()` — generic node add/remove
- [x] Reusable content composables in `ide_ui/ui/content/` (shared between panels and sheets)

---

## 6. IDE Overlay

### 6.0 Panel infrastructure (done)
- [x] `IdeOverlayScreen` — 6-slot docked layout + floating layer
- [x] `DockedPanel` — collapsible header, inner tab bar, animated content area
- [x] `FloatingPanel` — draggable title bar, resizable corner handle, auto-dock at 80dp threshold
- [x] `PanelSlot` — tab bar for multi-panel slots, delegates to `DockedPanel`
- [x] `IdeViewModel` — full `IdeAction` processing (select tab, toggle expand/visibility, move, resize, dock, bring-to-front, select slot active panel)

### 6.1 Panel content (opt-in panel mode)
- [~] `MediaLibraryPanel` — stub text only (default users use bottom sheet from §5 instead)
- [x] `FrameListPanel` — delegates to `FrameListContent` (frame list with delete, shared with bottom sheet)
- [ ] Wire `MediaLibraryPanel` to `media_library` data
- [x] Wire `FrameListBottomSheet` to all frames via `CanvasViewModel.frames` (reactive StateFlow, not filtered to visible)
- [~] Wire `FrameListPanel` (docked panel) to `CanvasViewModel.frames` — still passes empty list
- [ ] Add media to canvas from library panel

### 6.2 Panel persistence
- [ ] Save panel state to `ide_workspaces` on change
- [ ] Restore panel state on album open

### 6.3 Dynamic themes
- [ ] Theme switching per album (`activeTheme` in `ide_workspaces`)
- [ ] Reactive theme adaptation based on album content (PRD § 3)

---

## 7. Projects Home

- [x] `AlbumListScreen` — LazyColumn with album cards, empty state, loading indicator
- [x] `ProjectsViewModel` — wired to `ProjectRepository`, MVI with `ProjectsAction`
- [x] Create album flow (FAB + name dialog)
- [x] Delete album flow (delete button on each card)
- [ ] Album thumbnails

---

## 8. Unit System (Research)

- [ ] Define abstract `Units` for canvas coordinates
- [ ] Formula: `Units -> DP` accounting for zoom (scale) and screen density
- [ ] Migrate node positioning from raw px/dp to `Units`

---

## 9. Infrastructure Done (Reference)

Items completed — kept here to track what exists.

- [x] Clean Architecture packages: `app/`, `core/`, `domain/`, `data/`, `feature/`
- [x] Hilt DI: `AppModule` (DatabaseModule + RepositoryModule)
- [x] Room v1: `AppDatabase`, `AlbumEntity`, `AlbumDao`
- [x] File I/O: `FileStorageHelper` (scene_{albumId}.json)
- [x] Scene graph serialization: `SceneGraphSerializer` (kotlinx-serialization)
- [x] Repositories: `ProjectRepository` + `MediaRepository` (interfaces + impls)
- [x] Use cases: `CalculateViewportIntersectionsUseCase`, `SaveSceneGraphUseCase`
- [x] MVI contracts: `State`, `Intent`, `Effect` marker interfaces
- [x] Math utilities: `TransformUtils`, `BoundingBox`, `ViewportCuller`
- [x] Design system: dark-first palette, Material 3 `ZoomAlbumTheme`
- [x] Canvas rendering: single `graphicsLayer` transform, centroid-anchored zoom/rotation
- [x] Gesture detection: `InfiniteCanvasGestureDetector` (pan + pinch-zoom + rotation)
- [x] Viewport culling: brute-force AABB on `Dispatchers.Default`
- [x] HUD info moved to `CanvasTopBar` (visible/total nodes, camera zoom/rotation/xy)

---

## 10. Future (Post-MVP)

See [future-ideas.md](product/future-ideas.md) for the full list.

- [ ] Smart tags — tap tag to teleport to frame
- [ ] Layers — global show/hide groups
- [ ] Audio / Live Photos support
- [ ] Crop — media masking via bounding box editing
- [ ] Cloud sync — CRDT or Protobuf for real-time collaboration
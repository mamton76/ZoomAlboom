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
- [ ] Update `SceneGraphSerializer` for new JSON structure

### 1.2 AlbumMeta / albums table
- [ ] Remove `createdAt` field
- [ ] Rename `thumbnailPath` to `thumbnailUri`

### 1.3 Scene graph JSON format
- [ ] Wrap nodes in root object with `albumId` and `viewport`
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
- [ ] Undo/redo UI buttons in IDE overlay

---

## 3. Navigation

- [~] Routes defined (`PROJECTS_HOME`, `CANVAS/{albumId}`) — not wired into `MainActivity`
- [ ] Wire `AppNavigation` NavHost into `MainActivity`
- [ ] Pass `albumId` to `CanvasViewModel` (load album on navigate)
- [ ] Back navigation from canvas to album list

---

## 4. Canvas — Core MVP

### 4.1 Media rendering
- [ ] `CanvasNode.Media` rendering with Coil 3
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
- [~] Brute-force AABB (current)
- [ ] Spatial index (grid or R-tree) for >2k nodes

---

## 5. UI Architecture — Canvas-First Refactor

- [ ] Refactor `IdeViewModel` default state: all panels hidden by default
- [ ] Implement FAB [+] with content type picker bottom sheet
- [ ] Implement media library as bottom sheet (currently: docked panel stub)
- [ ] Implement contextual action bar for selected canvas node
- [ ] Design and implement Panel Configuration UI (menu-accessible)
- [ ] Update `IdeState` to support panel config persistence
- [ ] Move frame list to menu/swipe-accessible surface

---

## 6. IDE Overlay

### 6.1 Panel content (opt-in panel mode)
- [~] `MediaLibraryPanel` — stub (default users use bottom sheet from §5 instead)
- [~] `FrameListPanel` — stub (default users access via menu/swipe from §5 instead)
- [ ] Wire `MediaLibraryPanel` to `media_library` data
- [ ] Wire `FrameListPanel` to scene graph frames
- [ ] Add media to canvas from library panel

### 6.2 Panel persistence
- [ ] Save panel state to `ide_workspaces` on change
- [ ] Restore panel state on album open

### 6.3 Dynamic themes
- [ ] Theme switching per album (`activeTheme` in `ide_workspaces`)
- [ ] Reactive theme adaptation based on album content (PRD § 3)

---

## 7. Projects Home

- [~] `AlbumListScreen` — stub
- [~] `ProjectsViewModel` — stub, not wired to repos
- [ ] Wire `ProjectsViewModel` to `ProjectRepository`
- [ ] Create album flow
- [ ] Delete album flow (with confirmation)
- [ ] Album thumbnails

---

## 8. Unit System (Research)

- [ ] Define abstract `Units` for canvas coordinates
- [ ] Formula: `Units -> DP` accounting for zoom (scale) and screen density
- [ ] Migrate node positioning from raw px/dp to `Units`

---

## 9. Future (Post-MVP)

See [future-ideas.md](product/future-ideas.md) for the full list.

- [ ] Smart tags — tap tag to teleport to frame
- [ ] Layers — global show/hide groups
- [ ] Audio / Live Photos support
- [ ] Crop — media masking via bounding box editing
- [ ] Cloud sync — CRDT or Protobuf for real-time collaboration

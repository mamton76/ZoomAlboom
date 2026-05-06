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

## 2. Undo/Redo

Snapshot-based: each command captures `before`/`after` node state (not a sealed class per mutation type). Automatically covers any current or future mutation without new command classes.

### 2.1 Core model
- [ ] `CanvasCommand` data class — `nodeId`, `before: CanvasNode?` (null = add), `after: CanvasNode?` (null = delete)
- [ ] `UndoEntry` sealed interface — `Single(command)` / `Compound(commands: List)` for atomic multi-node operations
- [ ] `CommandHistory` class — undo/redo `Deque<UndoEntry>`, capped at ~50-100 entries

### 2.2 Gesture grouping
- [ ] `onGestureStart` → snapshot node state as `before`
- [ ] Gesture updates apply transforms directly (no intermediate commands)
- [ ] `onGestureEnd` → create one `CanvasCommand(before=snapshot, after=currentState)`, push to history
- [ ] Multi-node operations (e.g. delete frame + unparent children) → `Compound` entry

### 2.3 Persistence
- [ ] `@Serializable` on `CanvasCommand` and `UndoEntry`
- [ ] Autosave history to `filesDir/history_{albumId}.json` on each mutation
- [ ] Load history on album open, cap at ~50-100 entries

### 2.4 Integration
- [ ] Integrate into `CanvasViewModel` — all mutations go through commands
- [ ] Undo/redo UI buttons in TopBar

> **Timing:** implement after §4.2 (node interaction) — undo is most useful once move/resize/delete exists.

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
- [x] `CanvasNode.Media` rendering with Coil 3 (currently stub / placeholder only)
- [x] `Media.intrinsicPixelWidth/Height` captured at creation (LOD source-px metadata)
- [ ] Downsampling at low zoom levels (OOM prevention) — use `intrinsicPixelWidth / (renderW * camera.scale)`
- [ ] High-res loading on zoom-in
- [ ] Missing media placeholder (when `status == MISSING`)

### 4.2 Node interaction
- [x] Node selection (tap to select, cyan border + corner handles + rotation handle)
- [x] Node drag/move (update `Transform` via `CanvasAction.MoveSelection`)
- [x] Node resize (corner handle drag, proportional)
- [x] Node rotation (rotation handle + two-finger on selected node)
- [x] Multi-select (rectangle selection via long-press+drag, group move/resize/rotate)
- [x] Overlap picker (long-press on overlapping nodes shows selection dialog)
- [x] Contextual action bar (Delete, Duplicate wired; Edit stub)
- [x] Selection debug panel (shows node transform details)
- [x] Deselect on camera gesture (pan/zoom clears selection)
- [ ] Undo integration (record `CanvasCommand` on move/resize/rotate — depends on §2)

### 4.3 Dynamic containment
- [ ] Calculate `containsNodeIds` on node move (AABB intersection with frames)
- [ ] Run on `Dispatchers.Default` to avoid blocking main thread

### 4.4 Media validation
- [ ] On album open, iterate `media_library` and check `sourceUri` availability
- [ ] Mark missing files as `status = MISSING`
- [ ] Show placeholder for missing media on canvas

### 4.7 Media adding & editing
- [x] Photo picker integration (Android photo picker / `ACTION_PICK`)
- [x] Copy picked photo to app-private storage (`filesDir/media/<albumId>/`) for persistence across restarts
- [x] Create `CanvasNode.Media` from picked photo (viewport-centered, aspect-ratio-preserving, EXIF-corrected)
- [x] Wire "Photo" action in FAB / AddContentBottomSheet to photo picker flow
- [x] Basic media editing: move, resize, delete (via unified node interaction — same as frames)
- [ ] Add media from device gallery (file manager / Files app, not just photo picker)
- [ ] Media property editing (label/tags via contextual UI, depends on §5 Object selected mode)
- [ ] Video picker + thumbnail extraction for video nodes
- [ ] Text node creation (inline text input → `CanvasNode.Media` with `MediaType.TEXT`)

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

## 5b. Radial FAB (Quarter-Circle)

Replaces current FAB [+] + BottomSheet. Planned immediately after photo node adding/editing (§4.1 Media).

- [ ] `RadialFab` composable — bottom-right corner, quarter-circle arc with 4 sectors
- [ ] 3 customizable sectors (user-pinned media types, default: Photo, Frame, Text)
- [ ] 1 fixed ".." sector → opens full `AddContentBottomSheet` with all media types
- [ ] Fan-out / collapse arc animation (staggered per sector)
- [ ] Hit-testing via arc path geometry (`pointerInput` + angle calculation)
- [ ] Long-press to enter "edit sectors" mode (drag media types in/out)
- [ ] Persist pinned sector config (per-user or per-album in `ide_workspaces`)
- [ ] Remove old `FloatingActionButton` + rewire content creation flow

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

## 10. Canvas Engine Extraction (`:canvas-engine` module)

Extract the canvas rendering / navigation / interaction system as a reusable library for AI Diary integration (Timeline, Milestone, Map visualization modes).

### Phase 1 — Extract as Gradle module within this repo
- [ ] Create `:canvas-engine` module with its own `build.gradle.kts`
- [ ] Move `core/math/` → engine module: `Camera`, `TransformUtils`, `BoundingBox`, `ViewportCuller`, `LodResolver`, `ResizeHandle`
- [ ] Define generic `CanvasNode` interface in engine (replace sealed `Frame | Media` with open contract: `id`, `transform`, `visibilityPolicy`)
- [ ] Move gesture detectors → engine: `NodeInteractionGestureDetector`, `TapAndLongPressGestureDetector`, `InfiniteCanvasGestureDetector`
- [ ] Move rendering pipeline → engine: `CanvasScreen` (parameterized node renderer), `SelectionOverlay`, `SelectionDebugPanel`
- [ ] Move selection/interaction state → engine: `CanvasAction` (generic), `CanvasState`, action dispatch logic
- [ ] Keep `CanvasViewModel` in app — wraps engine state with app-specific persistence (Room, scene graph serialization)
- [ ] ZoomAlboom's `Frame` and `Media` implement the engine's `CanvasNode` interface

### Phase 2 — Stabilize API
- [ ] Define engine's public API surface (node interface, gesture callbacks, rendering slots)
- [ ] Abstract away app-specific callbacks (delete/duplicate become generic action slots)
- [ ] Engine exposes `@Composable CanvasEngine(...)` entry point with configuration lambdas

### Phase 3 — Standalone library
- [ ] Promote to separate repo / published artifact
- [ ] AI Diary depends on `canvas-engine` artifact
- [ ] ZoomAlboom depends on same artifact

### What stays in ZoomAlboom (not extracted)
- Frame / Media domain models (implement engine's generic interface)
- Album persistence (Room, scene graph JSON, FileStorageHelper)
- IDE panel system (CanvasScaffold, IdeOverlayScreen, panels)
- Project management (AlbumListScreen, ProjectsViewModel)
- Design system (colors, theme — or extract separately)

---

## 12. Canvas Interaction Modes (Edit / View)

Two global modes that change gesture meaning. Layered on top of the existing canvas-first contextual modes (Navigate / Add content / Object selected, see [PRD § 12.6](product/PRD.md#126-canvas-first-chrome)) — modes gate *which* contextual modes are reachable.

### 12.1 Model
- [ ] `enum class CanvasInteractionMode { Edit, View }` in `domain/model/`
- [ ] `mode: CanvasInteractionMode` on `CanvasState` (default `Edit`)
- [ ] `CanvasAction.SetMode(mode)`
- [ ] Entering View clears selection

### 12.2 View mode behavior
- [ ] Tap on any node → `FocusNode(id)` — animated camera fit using `Transform.toCamera()`
- [ ] Long-press / rect-select drag → no-op
- [ ] Pan / pinch-zoom / rotate → unchanged (always active)
- [ ] No selection overlays, no handles, no contextual action bar, no toolbars

### 12.3 Gesture stack changes
See [selection.md § 5](architecture/selection.md#5-gesture-stack):
- [ ] Layer 1 (`nodeInteractionGestures`): early-return when `mode != Edit`
- [ ] Layer 2 (`tapAndLongPressGestures`): branch on mode (Edit dispatches existing actions; View dispatches `FocusNode` / no-ops)
- [ ] Layer 3 (`infiniteCanvasGestures`): unchanged

### 12.4 UI
- [ ] Toggle in `CanvasTopBar` (Edit ⇄ View) — explicit button
- [ ] Edit-only chrome (toolbar, action bar, layer popover) hidden in View

### 12.5 Persistence
- [ ] `mode` saved to `ide_workspaces` (UI state)

### 12.6 Future
- A "Present" mode for shared/published albums (read-only, no album-list overflow). Out of MVP — see [open-questions.md § 5](architecture/open-questions.md).

---

## 13. Layers (Visibility Groups)

Two-tier model: **type layers** (fixed, derived from node class) + **user-defined layers** (variable, explicit membership). A node is visible iff both layer flags are on.

### 13.1 Type layers (MVP)
- [ ] `data class TypeLayerVisibility(media: Boolean = true, frames: Boolean = true, guidelines: Boolean = true)`
- [ ] `typeLayerVisibility: TypeLayerVisibility` on `CanvasState`
- [ ] `CanvasAction.ToggleTypeLayer(LayerKind)`
- [ ] Renderer + hit-test gate on type-layer visibility
- [ ] Fixed draw order: Media → Frames → Guidelines (bottom → top)

### 13.2 User-defined layers (post-§13.1)
- [ ] `data class UserLayer(id: String, name: String, visible: Boolean = true)`
- [ ] `userLayers: List<UserLayer>` on `CanvasState`
- [ ] `userLayerId: String?` on `CanvasNode` (single-membership; nullable)
- [ ] CRUD actions: `CreateUserLayer`, `RenameUserLayer`, `DeleteUserLayer`, `ToggleUserLayerVisibility`, `AssignSelectionToLayer`
- [ ] Visibility = AND of type layer + user layer

### 13.3 UI
- [ ] Layer popover from `CanvasTopBar` — type layers + user layers with checkbox
- [ ] Visible in Edit mode only

### 13.4 Persistence
- [ ] Type layer visibility → `ide_workspaces` (UI state)
- [ ] User layer identity (id, name, ordering) → scene JSON `editor` block (album content)
- [ ] User layer visibility flags → `ide_workspaces` keyed by layer id (toggle doesn't dirty scene)
- [ ] `CanvasNode.userLayerId` → scene JSON node field

> Open: should user-defined layers allow multi-membership (tag-style)? See [open-questions.md § 6](architecture/open-questions.md).

---

## 14. Guidelines + Snapping

Guidelines are **editor metadata, not `CanvasNode`**. They belong to the Guidelines type layer (§13.1).

### 14.1 Guideline model
- [ ] `data class Guideline(id: String, orientation: Vertical|Horizontal, position: Float, isLocked: Boolean = false)`
- [ ] Position in world units. Vertical: `x = position`. Horizontal: `y = position`.
- [ ] `guidelines: List<Guideline>` on `CanvasState`

### 14.2 Guideline CRUD
- [ ] `CreateGuideline`, `MoveGuideline`, `DeleteGuideline`, `LockGuideline`
- [ ] Drag a guideline = `MoveGuideline` (new gesture target — extends gesture stack)

### 14.3 Guideline rendering
- [ ] Drawn inside the camera `graphicsLayer` (world-anchored)
- [ ] Stroke width = `2dp / camera.scale` (constant on screen)
- [ ] Visible only when Guidelines type layer is visible AND mode == Edit

### 14.4 Snapping
- [ ] `snappingEnabled: Boolean` on `CanvasState`
- [ ] `activeSnapLines: List<SnapLine>` on `CanvasState` (transient, populated during drag/resize)
- [ ] Pure `core/math/Snapping.kt`
- [ ] Tolerance in **screen pixels** (default ~8 px), converted via `screenDeltaToWorld(tolerancePx, 0)`
- [ ] Snap targets: guidelines, non-selected node edges, non-selected node centers, frame edges
- [ ] Dragged candidates: rect edges + center
- [ ] Don't snap to nodes inside the moving selection
- [ ] Apply in ViewModel reducer for `MoveSelection` first, then `ResizeSelection`

### 14.5 Persistence
- [ ] `guidelines` → scene JSON `editor` block (album content)
- [ ] `snappingEnabled` → `ide_workspaces` (UI state)

> **Order:** §14.1–14.3 (guidelines without snap) ships before §14.4 (snapping).

---

## 15. Context Menu

Distinct from `ContextualActionBar` — the action bar is persistent on selection; the context menu is transient on long-press.

### 15.1 Edit mode
- [ ] Long-press on a node → context menu popover at touch point
- [ ] Initial actions: Delete, Duplicate, Edit, Move to Layer (post §13.2), Bring Forward / Send Backward
- [ ] Long-press on empty space → context menu with Add Photo / Add Frame / Add Text / Paste / Add Guideline

### 15.2 View mode
- [ ] Long-press on media → viewer menu (Open / Share / Info)
- [ ] Long-press on frame → frame menu (Focus / Open as Album View)

### 15.3 Conflict with current long-press semantics
- Today long-press = toggle selection or rect-select start ([selection.md § 2](architecture/selection.md#2-gesture-mapping)). Context menu replaces single-node long-press; rect-select start still fires on empty space + drag.

---

## 16. Multi-Photo Import + Auto Grid Placement

Extends [§4.7 Media adding & editing](#47-media-adding--editing) (single-photo picker is done).

### 16.1 Multi-select picker
- [ ] Switch to `PickMultipleVisualMedia` Activity Result contract
- [ ] Cap (~50; varies by device — confirm)
- [ ] Copy each picked file to `filesDir/media/<albumId>/`

### 16.2 Pure grid placement
- [ ] `core/math/GridPlacement.kt` — pure function, not a use case class
- [ ] `fun placeInGrid(targetBounds: BoundingBox, items: List<AspectRatio>, padding: Float, gap: Float): List<Transform>`
- [ ] Algorithm: `columns = ceil(sqrt(n))`, fit each item in its cell preserving aspect ratio, center within cell
- [ ] Returns transforms in world units; caller wraps with `1/camera.scale` per `CanvasNodeFactory` contract

### 16.3 Target resolution
- [ ] If exactly one selected node is a `Frame` → use frame AABB
- [ ] Else → use viewport via `TransformUtils.cameraViewport`
- [ ] Rotated frame: AABB only (rotated-local layout deferred)

### 16.4 Action wiring
- [ ] `CanvasAction.AddMediaBatch(uris: List<Uri>)`, `SelectNodesByIds(ids: List<String>)`
- [ ] After add, select the new batch
- [ ] One `Compound` undo entry per batch (depends on §2)

---

## 17. Group Align / Distribute

### 17.1 Pure functions
- [ ] `core/math/AlignDistribute.kt`
- [ ] `alignLeft / alignCenter / alignRight / alignTop / alignMiddle / alignBottom(transforms): List<Transform>`
- [ ] `distributeHorizontally / distributeVertically(transforms): List<Transform>`
- [ ] Use `renderW / renderH` (not raw `w/h`); AABB for rotated nodes (MVP)
- [ ] Preserve `scale`, `rotation`, `w`, `h` — only `cx/cy` change

### 17.2 Action wiring
- [ ] `CanvasAction.AlignSelection(edge: AlignEdge)`, `DistributeSelection(axis: Axis)`
- [ ] Align requires 2+ selected; Distribute requires 3+
- [ ] One `Compound` undo entry per command
- [ ] `groupSelectionTransform` re-anchors at end (membership unchanged, bounds change)

### 17.3 UI
- [ ] Layout toolbar in `CanvasTopBar` (Edit mode only)
- [ ] Buttons disabled when selection size insufficient

---

## 18. Future (Post-MVP)

See [future-ideas.md](product/future-ideas.md) for the full list.

- [ ] Smart tags — tap tag to teleport to frame
- [ ] User-defined layers full feature parity (multi-membership? layer locking? per-layer effects?)
- [ ] Present mode (read-only viewing for shared albums)
- [ ] Audio / Live Photos support
- [ ] Crop — media masking via bounding box editing
- [ ] Cloud sync — CRDT or Protobuf for real-time collaboration
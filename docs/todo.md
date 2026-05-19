# TODO — ZoomAlboom

Gap between current implementation and target architecture.

> Sources: [data-model](architecture/data-model.md) | [PRD](product/PRD.md) | [project-memory](product/project-memory.md) | [future-ideas](product/future-ideas.md)

## Legend
- `[ ]` — not started
- `[~]` — partially done / stub exists
- `[x]` — done

---

## Recommended next implementation order

Work from foundation toward features. Do not jump to widgets, export, or appearance editor before the scene graph and media foundation are stable.

1. **Scene graph root wrapper** (§1.3) — `albumId`, `camera`, `nodes`, `profile` (§22), `albumBackground` (§19), editor metadata (§13/§14).
2. **Save/restore camera position** (§1.3) — on album open/close.
3. **Minimal media library** (§1.4–1.5) — `media_library` table: id, album_id, sourceUri, mediaType, status, intrinsic dimensions.
4. **Media validation + missing placeholder** (§4.4) — check `sourceUri` on album open, show placeholder for `MISSING`.
5. **Frame membership** (§4.3) — computed from geometry + `Frame.overrides`; recompute on `Dispatchers.Default`. See [frame-membership.md](architecture/frame-membership.md).
6. **Edit / View mode split** (§12) — gates gesture routing; unblocks View-mode navigation.
7. **Multi-photo import + auto grid placement** (§16).
8. **Group align / distribute** (§17).
9. **Guidelines** (§14.1–14.3) — without snapping first.
10. **Snapping** (§14.4).
11. **Basic widget infrastructure** (§21.1) — `CanvasNode.Widget`, then `Portal` and `FrameNavigator` (§21.2).

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
- [x] Add `MediaType.TEXT`, `STICKER`, `VECTOR_SHAPE` variants (MVP scope)
- [x] `Transform.x/y` → `cx/cy` (center-based world coords, not top-left)
- [x] `Transform.w/h` → actual world-unit size (not normalized aspect ratio)
- [x] `Transform.renderW`/`renderH` computed properties added
- [x] `Camera` moved to `core/math/Camera.kt` (was in `CanvasViewModel`)
- [x] `Camera.x/y` → `cx/cy` (graphicsLayer translation values)
- [x] `Transform.toCamera()` conversion helper added in `TransformUtils.kt`
- [x] `CanvasNodeFactory` simplified (no rotateVector compensation needed with center-based coords)
- [x] `CanvasViewModel.allNodes` → `MutableStateFlow` (fixes P5 race condition)
- [x] `CanvasViewModel.frames: StateFlow<List<Frame>>` exposed reactively
- [x] Update `SceneGraphSerializer` for new JSON structure (wraps in `SceneGraph` root; migration fallback for old bare-list format)

### 1.2 AlbumMeta / albums table
- [ ] Remove `createdAt` field (still present in `AlbumMeta` + `AlbumEntity`)
- [ ] Rename `thumbnailPath` to `thumbnailUri` (still `thumbnailPath` in code)

### 1.3 Scene graph JSON format
- [x] Wrap nodes in root object with `albumId` and `camera` (migration fallback for legacy bare-list format)
- [x] Save/restore last camera position on album open/close

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
- [x] `CanvasCommand` — snapshot-based `before`/`after: List<CanvasNode>?` (list-shape unifies single/multi ops)
- [x] `CommandKind` — `@Serializable` enum: ADD, REMOVE, DELETE, DUPLICATE, MOVE, RESIZE, ROTATE
- [x] `InteractionKind` — gesture-side enum MOVE/RESIZE/ROTATE, maps to `CommandKind` at commit
- [x] `CommandHistory` — two `ArrayDeque<CanvasCommand>`, capped at 50; `push/undo/redo/snapshot/restore`
- [x] `HistorySnapshot` — serializable wrapper for persistence

### 2.2 Gesture grouping
- [x] `BeginInteraction(kind)` action → snapshots selected nodes as `before`
- [x] Gesture updates (MoveSelection/ResizeSelection/RotateSelection) mutate state directly
- [x] `FinishInteraction` → commits one `CanvasCommand`; skips push if `before == after` (no-op guard)
- [x] Second finger during node gesture cancels node interaction and hands off to canvas layer

### 2.3 Persistence
- [x] `CanvasCommand` and `HistorySnapshot` are `@Serializable`
- [x] `HistorySerializer` — serialize/deserialize `HistorySnapshot` (mirrors `SceneGraphSerializer`)
- [x] History saved to `filesDir/history_{albumId}.json` on `ViewModel.onCleared()`
- [x] History loaded on album open; **not** process-death-safe (save is onCleared-only)

### 2.4 Integration
- [x] All mutations in `CanvasViewModel` push `CanvasCommand`: addNode, removeNode, DeleteSelection, DuplicateSelection, BeginInteraction/FinishInteraction
- [x] Undo/redo TopBar buttons (↶ ↷); enabled state driven by `canUndo`/`canRedo` flows

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
- [x] Node rotation (rotation handle drag)
- [x] Multi-select (rectangle selection via long-press+drag, group move/resize/rotate)
- [x] Overlap picker (long-press on overlapping nodes shows selection dialog)
- [x] Contextual action bar (Delete, Duplicate wired; Edit stub)
- [x] Selection debug panel (shows node transform details)
- [x] Deselect on camera gesture (pan/zoom clears selection)
- [x] Undo integration (snapshot-based `CanvasCommand` on move/resize/rotate/add/delete/duplicate — §2 done)

### 4.3 Frame membership
> See [architecture/frame-membership.md](architecture/frame-membership.md).
- [x] Slice 1: replace `Frame.containsNodeIds` with computed `effectiveMembers`; add `Frame.overrides`; add `CanvasNode.isFrameBindable`
- [x] Slice 2: *Pin* / *Detach* / *Auto* contextual bar entries; multi-frame target picker; multi-select overlap picker; two-tier border overlay (supersedes the originally-planned text badge); `FrameNameLabel` widget
- [x] Slice 3: *Transform with content* / *Rebind after edit* toggles + `applyFrameEdit` use case; move / resize / rotate all honour transformContents; multi-frame selection participates
- [ ] Excluded-membership visualisation: dashed border (or similar) for nodes that are geometrically inside but explicitly Excluded — closes the "stuck detached, invisible" gap
- [ ] Scrub `overrides` on node delete (cascade inside the existing delete command)
- [ ] Hygiene pass on album load: drop orphan overrides (target node missing or `isFrameBindable=false`)
- [ ] Run geometry recompute on `Dispatchers.Default` (currently runs on main; cheap for MVP album sizes)
- [ ] Spatial-index narrowing for `FrameMembershipUseCase.effectiveMembers` and `applyPendingRebindSuppression`: only consider nodes whose AABB intersects (frame.aabb ∪ frameAfter.aabb) instead of scanning all nodes. Use `core/math/SpatialIndex.kt`. Cheap to ignore at MVP album sizes; matters at thousands of nodes.

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
- [ ] Sticker picker + place sticker on canvas (`MediaType.STICKER`)
- [ ] Vector shape placement (`MediaType.VECTOR_SHAPE`)

### 4.5 Viewport culling upgrade
- [x] Brute-force AABB (`ViewportCuller` in `core/math/SpatialIndex.kt`)
- [ ] Spatial index (grid or R-tree) for >2k nodes

### 4.6 Level-of-Detail (LOD)
- [x] `VisibilityPolicy` data class + `RenderDetail` enum (`domain/model/VisibilityPolicy.kt`)
- [x] `visibilityPolicy` field on `CanvasNode` (optional, per-node override)
- [x] `LodResolver` object (`core/math/LodResolver.kt`) — screen-size cull + semantic zoom filtering
- [x] Default policies for Frame and Media node types
- [x] Debug logging in `LodResolver` (tag: `LodResolver`)
- [x] Wire `LodResolver` into rendering pipeline (skip/downgrade nodes based on `RenderDetail`)
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

## 5c. Object Properties Panel (planned)

A dedicated surface for editing properties of a selected node. Today these controls live in the Contextual Action Bar (Delete / Duplicate / Edit) plus ad-hoc bottom sheets (Frame Background — §19.6). The action bar is the wrong long-term home for these — too cramped to hold label, color, background, opacity, transform readouts, tags, layer assignment, appearance preset, per-frame presentation override, etc.

> **Status note (2026-05-19):** This section predates the per-concept popup decision. The phone half (`ObjectPropertiesBottomSheet`) is obsolete — phone now uses context-menu popups (`context-menu.md`) as the primary properties surface, with each concept (border, shadow, clip, alpha mask, etc.) opening its own popup. The tablet half (`ObjectPropertiesPanel`) remains the deferred tablet enhancement; see [§ 5d](#5d-tablet-properties-panel-deferred). Per-type sections below still apply — they become the panel's stacked content composables when the panel lands.

### 5c.1 Surface
- [ ] `ObjectPropertiesPanel` — docked panel slot (right side, fits IDE panel system §6) for tablets/landscape — **deferred**, see § 5d
- [ ] ~~`ObjectPropertiesBottomSheet` — compact alternative for phone/portrait~~ — **obsolete**, replaced by context-menu popups (`context-menu.md`)
- [ ] Either surface is hidden when selection is empty or in View/Presentation mode

### 5c.2 Per-type sections (drive by `CanvasNode` variant)
- [ ] **Frame** — label, border color, background (solid color), fit-mode override (§22.8), opacity
- [ ] **Media** — label, tags, MediaAppearance (§20) preset, opacity
- [ ] **Widget** (§21) — per-widget-type config form
- [ ] Header row: node type + id + transform readout (cx, cy, w, h, scale, rotation)

### 5c.3 Action Bar simplification
- [ ] Once 5c.1 ships, the ContextualActionBar shrinks back to Delete / Duplicate / Open Properties. Move the Frame Background button (§19.6) into the panel and delete it from the bar.

### 5c.4 Dependencies
- §13 layers (for "Move to Layer" control)
- §15 context menu (long-press → "Properties" entry)
- §20 MediaAppearance (Media section needs the appearance UI to be defined)

---

## 5d. Tablet Properties Panel (deferred)

> **Status:** Placeholder. Decided 2026-05-19 — MVP ships popup-first for both phone and tablet (`to_discuss.md` resolved direction). Tablet docked panels are explicitly post-MVP. This entry exists so the deferred tablet work is visible in the backlog and doesn't become invisible.

The tablet enhancement reuses the per-concept content composables shipped under §15 + §20.6 + popups. The panel is a wrapper that stacks them vertically; nothing in the panel is new content.

### 5d.1 Surface
- [ ] `ObjectPropertiesPanel` docked panel — right-side slot, integrates with IDE panel system (§6)
- [ ] Visible when selection is non-empty AND a tablet-class window size is detected
- [ ] Aggregates the per-concept content composables (`BorderEditorContent`, `ShadowEditorContent`, `ClipShapeEditorContent`, `AlphaMaskEditorContent`, `OverlayListEditorContent`, `ColorAdjustmentsEditorContent`, `CropEditorContent`, etc.) stacked vertically with collapsible sections
- [ ] Hidden in View / Presentation modes

### 5d.2 Behavior
- [ ] Panel-mode editors share state with their popup-mode counterparts — opening a popup from the context menu while the panel is open should not cause divergent edits
- [ ] Tablet still supports popups (e.g., from context menu) — panel is additive, not replacement
- [ ] Compound undo grouping applies to panel sessions the same way as popup sessions

### 5d.3 Open
- [ ] `WindowSizeClass` threshold for "tablet-class" — `Expanded` width? Or based on shortest dimension?
- [ ] Default-on or default-off on tablet?
- [ ] Coexistence with the existing `IdeUiState.panels` model (Media Library, Frame List) — same panel system or parallel slot?
- [ ] Reconciliation with §5c (Object Properties Panel planned) — likely §5d supersedes §5c's surface design once it lands.

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
- [x] File I/O: `FileStorageHelper` (scene_{albumId}.json, history_{albumId}.json)
- [x] Undo/redo: `CommandHistory`, `HistorySerializer`, `HistorySnapshot`, `CanvasCommand`, `CommandKind`, `InteractionKind`
- [x] Scene graph serialization: `SceneGraphSerializer` (kotlinx-serialization)
- [x] Repositories: `ProjectRepository` + `MediaRepository` + `HistoryRepository` (interfaces + impls)
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
- [x] `enum class CanvasInteractionMode { Edit, View }` in `domain/model/`
- [x] `mode: CanvasInteractionMode` on `CanvasState` (default `Edit`)
- [x] `CanvasAction.SetMode(mode)`
- [x] Entering View clears selection (+ groupSelectionTransform, selectionRect)

### 12.1a Camera focus animation (infrastructure)
- [x] `CameraAnimation` data class — transient runtime state on `CanvasState`
- [x] `CameraInterpolation.interpolate(from, to, t, easing)` — shortest-path angular lerp
- [x] `CameraInterpolation.resolveTransition(preset, profileEasing, from, to)` — auto-duration + preset multiplier
- [x] `EasingType.apply(t)` extension (LINEAR / EASE_IN / EASE_OUT / EASE_IN_OUT)
- [x] `CanvasAction.FocusNode(nodeId)` action — looks up node, runs animation
- [x] ViewModel coroutine ticks camera state at ~60Hz; cancels on any `onGesture`

### 12.2 View mode behavior
- [x] Tap on any node → `FocusNode(id)` — animated camera fit using `Transform.toCamera()`
- [x] Long-press / rect-select drag → no-op (long-press consumed; no rect-select drag fallthrough)
- [x] Pan / pinch-zoom / rotate → unchanged (always active)
- [x] No selection overlays, no handles, no contextual action bar, no toolbars (selection cleared on mode entry; chrome auto-hides)

### 12.3 Gesture stack changes
See [selection.md § 5](architecture/selection.md#5-gesture-stack):
- [x] Layer 1 (`nodeInteractionGestures`): early-returns on empty selection — selection is cleared whenever `mode != Edit`, so the layer is de-facto disabled in View/Presentation
- [x] Layer 2 (`tapAndLongPressGestures`): branches on mode in `CanvasScreen` (Edit dispatches existing actions; View/Presentation dispatches `FocusNode` and swallows long-press)
- [x] Layer 3 (`infiniteCanvasGestures`): unchanged

### 12.4 UI
- [x] Toggle in `CanvasTopBar` (Edit ⇄ View) — text button; cycles Edit ↔ View, Presentation reachable programmatically
- [x] Edit-only chrome (toolbar, action bar, layer popover) hidden in View (auto via empty selection)
- [x] FrameList row tap dispatches `FocusNode` + dismisses sheet (works in both Edit and View)

### 12.5 Persistence
- [ ] `mode` saved to `ide_workspaces` (UI state)

### 12.6 Future
- A "Present" mode for shared/published albums (read-only, no album-list overflow). Out of MVP — see [open-questions.md § 5](architecture/open-questions.md). Enum value (`Presentation`) reserved on `CanvasInteractionMode`; UI surface deferred.

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

### 13.5 Z-order actions (BringToFront / SendToBack / BringForward / SendBackward)

Single shared `Transform.zIndex: Float` space across all canvas nodes (Frame / Media / Widget). Until now, render order was implicit insertion order; only hit-testing used `zIndex`. The render order is now sorted by `zIndex` (ascending — lowest first, so highest ends on top), so reorder actions take effect visually.

- [x] `recalculateVisibleNodes` sorts the result by `Transform.zIndex` (`CanvasViewModel.kt`) — render correctness now depends only on `zIndex`, not on `_allNodes` insertion order.
- [x] `CanvasAction.BringToFront(nodeId)` — `newZ = max + 1`. No-op if already on top.
- [x] `CanvasAction.SendToBack(nodeId)` — `newZ = min - 1`. No-op if already at bottom.
- [x] `CanvasAction.BringForward(nodeId)` — swaps `zIndex` with the next-higher neighbor. No-op if already on top.
- [x] `CanvasAction.SendBackward(nodeId)` — swaps `zIndex` with the next-lower neighbor. No-op if already at bottom.
- [x] All four routed through `applyZIndexReorder(nodeId, ZReorder)`; undoable via `CommandKind.REORDER` (snapshot of the one or two affected nodes).
- [x] `ContextualActionBar` exposes the four actions (icons `⤒ ▲ ▼ ⤓`) when exactly one node is selected.
- [ ] Multi-select reorder semantics (move the group as a block? individually?). Single-only for MVP.
- [ ] Move into the Object Properties Panel (§5c) once that exists; current `ContextualActionBar` placement is interim.

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

Distinct from `ContextualActionBar` — the action bar is persistent on selection; the context menu is transient on long-press. Full design in [context-menu.md](architecture/context-menu.md) (committed 2026-05-18; pending implementation).

### 15.1 Edit mode
- [ ] Long-press on a node → context menu popover at touch point
- [ ] Menu model: `(selection, anchorNodeId, anchorPosScreen)`. Selection-scoped items + anchor-scoped items per [context-menu.md § 2](architecture/context-menu.md#2-menu-model)
- [ ] Single-media menu: Edit media / Edit appearance / Edit mask / crop (split into clip + alpha mask + crop when §1.3 popup direction lands) / Edit overlay / Replace media / Duplicate / Delete
- [ ] Single-frame menu: Edit frame / Edit frame appearance / Navigate to frame / Edit frame contents (post-MVP) / Duplicate frame / Delete frame
- [ ] Group menu (selection ≥ 2): Edit common appearance / Create frame around selection / Align / Distribute / Duplicate / Delete / Clear selection
- [ ] Anchor-scoped items (selection ≥ 2, anchor in selection): Remove this from selection / Edit this only
- [ ] Long-press on empty space → context menu with Add Photo / Add Frame / Add Text / Paste / Add Guideline

### 15.2 View mode
- [ ] Long-press on media → viewer menu (Open / Share / Info)
- [ ] Long-press on frame → frame menu (Focus / Open as Album View)

### 15.3 Conflict with current long-press semantics — resolved
- Today long-press = toggle selection ([selection.md § 2](architecture/selection.md#2-gesture-mapping)). Decision: long-press becomes **add-or-keep + open menu on UP**; the "remove this from selection" intent moves into the menu's anchor-scoped items. See §15.4.

### 15.4 Gesture rule rewrite (lands first, before the popover)
Pure-gesture slice that verifies the rule shift in isolation. Behavior-preserving for empty-selection users; replaces "long-press = toggle off" with "long-press = add-or-keep" for non-empty selection.

- [ ] `CanvasAction.AddNodeToSelection(nodeId: String)` — idempotent; `selection - {nodeId}` if absent, no-op if already in. Replaces `ToggleNodeSelection` as the long-press dispatcher.
- [ ] `ToggleNodeSelection` stays in the codebase as the implementation of the future menu's "Remove this from selection" item; no gesture dispatches it.
- [ ] `tapAndLongPressGestures` Layer 2 (`feature/canvas/gestures/`) — long-press handler dispatches `AddNodeToSelection(hit.id)` on Phase 1; UP after no drag dispatches `OpenContextMenu(...)` (stubbed initially)
- [ ] Overlap picker switches from `SelectNode` (replace) to `AddNodeToSelection` (add). Closes [selection.md § 6](architecture/selection.md#6-open-issues) open issue.
- [ ] Update `selection.md § 2` long-press row to **Add-or-keep**; remove `ToggleNodeSelection` from the dispatcher list.
- [ ] Update `selection.md § 6` to remove the resolved overlap-picker bullet.

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

## 18. Create Frame Around Selection

See [context-menu.md § 5](architecture/context-menu.md#5-create-frame-around-selection-net-new-action). Independent of the rest of the context-menu redesign — can ship on `ContextualActionBar` first.

### 18.1 Pure function
- [ ] `core/math/FrameAroundSelection.kt` — pure function, not a use case class
- [ ] `fun frameAroundSelection(transforms: List<Transform>, padding: Float): Transform` — AABB of `renderW / renderH` (rotated nodes use AABB; same rule as `AlignDistribute`), inflated by `padding`

### 18.2 Action wiring
- [ ] `CanvasAction.CreateFrameAroundSelection(padding: Float)`
- [ ] Requires 1+ selected node (1 selected = padded frame around single node; useful for "frame this photo")
- [ ] Build `CanvasNode.Frame` at the computed rect with default title + default `FrameAppearance`
- [ ] Z-order: insert below the contents so members render above the frame background
- [ ] Members attach via geometric containment (no explicit wiring) — see [frame-membership.md](architecture/frame-membership.md)
- [ ] After creation, selection becomes the new frame
- [ ] One `Compound` undo entry per command (depends on §2)

### 18.3 UI
- [ ] Button in `ContextualActionBar` (visible when selection size ≥ 1)
- [ ] Migrate into context menu when §15 lands (group-actions section)

### 18.4 Open
- [ ] `framePadding` default — world-unit constant or derived from selection bounds?

---

## 19. Album and Frame Backgrounds

Backgrounds are **not** `CanvasNode` objects — they are render-layer style properties stored in the scene graph root (album background) or on `CanvasNode.Frame` (frame background). Requires §1.3 (JSON root wrapper) to be implemented together.

See [data-model.md § Backgrounds](architecture/data-model.md#backgrounds-backgrounddata-albumbackground) for types and rendering order.

### 19.1 Domain model
- [x] `BackgroundData` sealed { `SolidBackgroundData` | `TextureBackgroundData` | `Procedural` } in `domain/model/Background.kt` (was a flat `AlbumBackground` with a `BackgroundType` discriminator + nullable per-variant fields — replaced in §19.6½)
- [x] `TileData` composite (`tileMode`, `tileOriginX/Y`, `tileWidth/Height`) — used only by `TextureBackgroundData`; procedural patterns own their own positioning
- [x] `TileMode` enum: `None`, `Stretch`, `Cover`, `Contain`, `Repeat`
- [x] `AnchorMode` enum: `CameraLocked`, `WorldLocked` (FrameLocked post-MVP)
- [x] `AlbumBackground(data: BackgroundData, anchorMode: AnchorMode)` — album-level wrapper that adds an anchor
- [x] `Frame.background: BackgroundData?` directly — frames are implicitly their own anchor (no `FrameBackground` wrapper)

### 19.2 Scene graph (requires §1.3 first)
- [x] `SceneGraph` root wrapper: `albumId`, `camera`, `nodes`, `background`
- [x] Migration reader: try root object, fall back to bare `List<CanvasNode>` for old files
- [x] `SceneGraphSerializer` updated to encode/decode root wrapper (handled by `ignoreUnknownKeys` + nullable defaults)
- [x] `albumBackground` added to `CanvasState`
- [x] `CanvasAction.SetAlbumBackground(background: AlbumBackground?)`
- [x] `CanvasAction.SetFrameBackground(nodeId, background)`

### 19.3 Rendering
- [x] `AlbumBackgroundRenderer` composable (split into `CameraLockedAlbumBackground` + `WorldLockedAlbumBackground`)
  - Camera-locked: drawn outside the camera `graphicsLayer` Box — screen-fixed, no transform
  - World-locked solid color: inside the `graphicsLayer` Box before nodes, sized to visible world rect from camera state
  - World-locked tiled texture: shader brush — `BitmapShader(REPEAT, REPEAT)` with `localMatrix` scaling one bitmap copy to `tileWidth × tileHeight` and translating to `tileOriginX/Y`; single `drawRect` covers the visible world rect. Constant cost regardless of zoom level (GPU evaluates the shader once per visible pixel, not once per tile)
- [x] BitmapShader-based texture tiling (was a per-tile `for` loop; replaced after the 1M-tiles-at-extreme-zoom hang)
- [x] Frame background rendering inside `CanvasNodeRenderer` for Frame, drawn before frame border
- [x] Frame background clipped to frame bounds (via `drawRoundRect` shape)

### 19.4 MVP scope
- [x] Album: solid color + texture/image + procedural pattern; camera-locked + world-locked; all tile modes. Stretch fills the rect ignoring aspect, Cover fills both axes preserving aspect (overflows / crops the other), Contain fits inside preserving aspect (letterboxes the other). None currently still aliases Stretch — proper "native pixel size at tileOrigin" semantics tracked separately.
- [x] Frame: full `BackgroundData?` (solid / texture / procedural) + opacity. Came for free with the §19.6½ refactor.
- [x] Undo for background changes — `CommandKind.SET_ALBUM_BACKGROUND` + `SET_FRAME_BACKGROUND`; `CanvasCommand.albumBackgroundChange` for album-level snapshots

### 19.6 UI
- [x] Shared `BackgroundEditor` composable in `feature/ide_ui/ui/content/BackgroundEditorContent.kt` — source radio (None / Solid / Texture / Procedural), per-source controls, opacity slider. Used by both album and frame sheets.
- [x] Album Settings bottom sheet (TopBar palette button) — wraps `BackgroundEditor` and adds the anchor toggle (CameraLocked / WorldLocked)
- [x] Frame Background bottom sheet (ContextualActionBar `▣` button when one Frame selected) — wraps `BackgroundEditor` with no anchor toggle (frame is its own anchor)
- [x] Inline HSV/RGB color picker (no external dep) — SV square + hue slider + alpha slider + hex field + preset swatches
- [x] Tile-size slider shows unit suffix ("screen px" for CameraLocked album, "world units" for WorldLocked album and Frame) so users know what number they're adjusting
- [ ] Texture URI persistence — `takePersistableUriPermission` works for picker-issued URIs; for filesystem URIs we still rely on `media_library` (§1.4/§1.5) to own and validate. Acceptable as interim path.

### 19.6½ Sealed-class refactor
Replace the flat-struct `AlbumBackground` (discriminator enum + nullable fields) with a sealed-class family. See [background.md § Domain Types](architecture/background.md#domain-types).

- [x] `BackgroundData` sealed { `Solid` | `Texture` | `Procedural` } in `domain/model/Background.kt`
- [x] `TileData` composite extracted (used by `Texture`; procedural patterns own their own positioning)
- [x] `AlbumBackground` becomes `data class AlbumBackground(data, anchorMode)` — no more `type` enum, no nullable per-variant fields
- [x] Drop `FrameBackground` — `Frame.background: BackgroundData?` directly. Frames immediately get texture + procedural support.
- [x] `BackgroundType` enum removed. `BackgroundSourceChoice` (UI-only) replaces it in the editor.
- [x] Shared draw entry point: `DrawScope.drawBackgroundData(data, rect, texturePainter)` reused by album (camera/world) and frame renderers.
- [x] Shared UI: `BackgroundEditor` composable used by both `AlbumSettingsBottomSheet` (with anchor toggle) and `FrameBackgroundBottomSheet`.
- No on-disk migration needed — no albums-with-background exist yet on disk.

### 19.7 Procedural patterns
Third album-background source: parameters, not files. Anchored same way as solid/texture (camera-locked, world-locked, frame-locked future). See [background.md § Procedural Patterns](architecture/background.md#procedural-patterns).

- [x] `BackgroundData.Procedural(pattern, opacity)` sealed-class variant (replaces the original plan for a `BackgroundType.Procedural` enum value)
- [x] `ProceduralPattern` sealed class with 8 `@Serializable @SerialName` variants
- [x] Procedural patterns reachable from both album and frame backgrounds via `AlbumBackground.data: BackgroundData` and `Frame.background: BackgroundData?` — no dedicated `procedural` field
- [x] `DrawScope.drawProceduralPattern` in `ProceduralBackgroundRenderer.kt`
- [x] Wire into `CameraLockedAlbumBackground` (screen rect) and `WorldLockedAlbumBackground` (visible world rect)
- [x] Album Settings: "Pattern" radio + pattern-type dropdown + per-pattern editor (`ProceduralPatternEditor.kt`)
- [x] Undoable via existing `SetAlbumBackground` snapshot — no command-kind change needed
- [x] Density caps (≤500 lines/axis, ≤4000 noise dots, ≤100 splotches) — skip rather than burn GPU
- [x] Multi-stop gradients — `Gradient.stops: List<GradientStop>` (position 0..1 + hex color, alpha-aware). Renderer sorts defensively and feeds `colors` + `positions` to `android.graphics.LinearGradient` / `RadialGradient`. Editor MVP: read-only preview strip + vertical list with locked edge stops at 0 / 1, position slider + `ColorPicker` + Delete per intermediate stop, `+ Add stop` button that picks the midpoint of the largest gap and interpolates the color.
- [x] Optional solid fill behind any procedural pattern — `ProceduralBackgroundData.fillColor: String?`. Renderer draws the rect with the fill first, then the pattern on top. Editor: Checkbox "Solid fill behind pattern" + `ColorPicker`. Useful for Grid/DotGrid/Gradient/Grain/Noise — gives the pattern a chosen base instead of inheriting whatever's behind the layer. Watercolor's own `baseColor` overrides since it draws its own wash.
- [ ] `AnchorMode.FrameLocked` — clip pattern to a specific frame's bounds, move/scale/rotate with it. Post-MVP.
- [ ] Cover/Contain aspect handling for texture patterns (currently Stretch). Not strictly procedural; tracked here for parity.
- [ ] Per-pattern preview swatch in the type dropdown (currently text labels only).
- [ ] Better noise — Perlin/value noise instead of seeded random dots. Current rendering is a deterministic dot field, recognizable but not "noisy" in the simplex sense.
- [ ] Better watercolor — soft-edge stamps via masked gradients (current implementation stacks 3 concentric translucent circles per splotch).
- [ ] Gradient post-MVP polish: draggable stop handles on the preview strip, allow free first/last positions (currently locked to 0 and 1), gradient presets, easing modes between stops.

### 19.8 Tech debt — Compose-native texture tiling

Current production solution for repeated texture backgrounds uses Android `BitmapShader` drawn through `drawIntoCanvas { nativeCanvas.drawRect(...) }` (see `AlbumBackgroundRenderer.kt :: drawTiledShader` and `memory/background_shader_gotchas.md`). It works and should remain the default for now.

Later, investigate a Compose-native implementation based on a custom `ShaderBrush` / `ImageShader`, **only if there is a concrete reason**:

- we need tighter integration with Compose clipping / compositing / blend modes;
- we add texture previews / swatches and want reusable brush objects;
- we add animated / tinted / masked texture effects;
- the nativeCanvas path shows issues inside camera / frame graphics layers;
- we want to reduce Android-specific drawing code or prepare for Compose Multiplatform.

**Important:** Do not reintroduce the old broken path where a pre-built Android `BitmapShader` is wrapped directly into `ShaderBrush(shader)`. If we try this again, the shader must be created **inside** `ShaderBrush.createShader(size)`, preferably using Compose `ImageShader(image, tileModeX, tileModeY)`.

When the investigation does happen, the work is roughly:

- [ ] Subclass `ShaderBrush`; create the shader **inside** `createShader(size)` so Compose owns its lifecycle (the prior bug was passing a pre-built shader to `ShaderBrush(shader)`).
- [ ] Prefer `ImageShader(ImageBitmap, tileModeX, tileModeY)` over raw Android `BitmapShader` if behavior matches.
- [ ] Verify rendering inside the camera `graphicsLayer` (the original failure scope).
- [ ] Verify CameraLocked, WorldLocked, and Frame backgrounds.
- [ ] Verify alpha, clipping, tile origin, and tile size.
- [ ] Keep the nativeCanvas approach if the Compose path isn't clearly better or reliable.

Not an MVP blocker. Do not keep two production backends unless there is a real reason.

### 19.10 Tech debt — Procedural pattern anchor wired to album extent

Fill-rect procedural patterns (Gradient / Watercolor / PaperGrain / Noise) currently use a hardcoded `(-2500..+2500)` world anchor in `WorldLockedAlbumBackground` (`AlbumBackgroundRenderer.kt :: PROCEDURAL_WORLD_ANCHOR_HALF`). The pattern exists inside that rect; outside it Gradient shows the end-color and dot patterns show nothing.

When album-extent / `AlbumPresentationProfile` lands:

- [ ] Replace the hardcoded constant with a value derived from `AlbumPresentationProfile` (or the nodes' world bounding box when no profile is set).
- [ ] Optionally expose the anchor as a per-pattern field (`originX/Y/width/height`) so a user can position a gradient over a specific region of the canvas.
- [ ] Decide what happens outside the anchor — current behavior is "Gradient end-color / dot-patterns blank". Possible alternatives: tile the anchor (so the pattern repeats), or extend `CLAMP`-style.

Tileable patterns (Grid / DotGrid / RuledPaper / GraphPaper) are not affected — they're already world-anchored via their own `originX/Y` fields.

### 19.9 Frame background tile-origin semantics

Frame backgrounds are drawn in frame-local coordinates centered on `(0, 0)` (consistent with how every other frame property works). Stored `tileOriginX/Y` is interpreted as **offset from the frame's top-left** (the user-intuitive convention); the renderer translates it into centered frame-local coordinates before passing to `drawBackgroundData`.

- [x] `CanvasRenderer.kt :: drawFrameBackground` — copies the `TileData` with `tileOriginX = -renderW/2 + stored.tileOriginX` (same for Y) before drawing. Only visible in Repeat mode; non-Repeat ignores `tileOriginX/Y`.
- Album backgrounds (CameraLocked / WorldLocked) are unaffected — their origin is already in screen-px / world-units, which has no center/top-left ambiguity.
- [ ] Procedural pattern `originX/Y` fields (Grid / DotGrid / RuledPaper / GraphPaper) inside frames have the same "starts at center" issue. Follow-up: apply the same translation to procedural patterns with origin fields when used on frame backgrounds.

### 19.5 Post-MVP
- [x] Frame texture backgrounds (tiled or stretched image fill) — done via the §19.6½ refactor: frames take a full `BackgroundData?`, not just a color
- [x] Background editing UI (color picker, texture picker, tile controls) — done in §19.6 (Album Settings + Frame Background bottom sheets)
- [ ] `AnchorMode.FrameLocked` — background (texture or procedural) anchored to a specific frame, transformed with it. Also tracked in §19.7 for the procedural angle.
- [ ] User layer backgrounds

---

## 20. Appearance System (Non-Destructive Styling)

Shared non-destructive styling for canvas nodes. Each variant owns its own `*Appearance` container under a sealed `NodeAppearance` base; shared value types (`OverlayStyle`, `OverlaySource`, `NodeBlendMode`, `BorderStyle`, `ShadowStyle`) are defined once and reused. The original source files are never modified.

**Formulae:**
- `source media + MediaAppearance = rendered media object on canvas`
- `frame rect + FrameAppearance + linked contents = rendered frame on canvas`

See [appearance.md](architecture/appearance.md) for the shared model and the render-pipeline contract. Shipped state has `MediaAppearance.overlays` and `FrameAppearance.contentOverlays` as separate `List<OverlayStyle>` fields; the **committed direction** is to unify these onto `NodeAppearance` base as a single `overlays` field — see [§ 20.7](#207-overlay-field-unification-mediaappearanceoverlays--frameappearancecontentoverlays--baseoverlays) and [appearance.md § 13](architecture/appearance.md#13-proposed-evolution--unified-overlays-on-the-base).  
See [media-appearance.md](architecture/media-appearance.md) for media-specific surface.  
See [PRD § 8.7](product/PRD.md#87-non-destructive-media-appearance) and [PRD § 11.8](product/PRD.md#118-media-appearance-non-destructive-editing) for product requirements.

### 20.1 Domain model — shared base + value types
- [x] `NodeAppearance` sealed base — `opacity`, `cornerRadius`, `border: BorderStyle?`, `shadow: ShadowStyle?`. No generic `overlays` on the base.
- [x] `OverlayStyle` — `source: OverlaySource`, `opacity`, `blendMode: NodeBlendMode`
- [x] `OverlaySource` sealed — `SolidColor`, `Texture(textureRefId, tile)`, `Procedural(pattern, fillColor?)` (mirrors `BackgroundData` shape; `ProceduralPattern` and `TileData` reused from §19)
- [x] `NodeBlendMode` enum (`Normal`, `Multiply`, `Screen`, `Overlay`, `SoftLight`, `Darken`, `Lighten`) — all seven mapped to `androidx.compose.ui.graphics.BlendMode` in `OverlayRenderer.kt`
- [x] `BorderStyle`, `ShadowStyle`
- [x] All types `@Serializable`; polymorphism via `@SerialName`; `ignoreUnknownKeys` handles old nodes

### 20.1a Domain model — `MediaAppearance`
- [x] `MediaAppearance : NodeAppearance` — `crop`, `colorAdjustments`, `overlays: List<OverlayStyle> = emptyList()` (ordered; entry `[i]` over entry `[i-1]`; replaces prior `overlays: List<MediaOverlay>` + `OverlayKind`/`OverlayBlendMode`), `frameDecoration: MediaFrameDecoration?` (decorative photo-frame around one media — *not* a `CanvasNode.Frame`), `caption`
- [x] `CropSettings` + `CropMode` enum (Fit, Fill, Manual, Stretch) with focal point
- [x] `MediaColorAdjustments` — brightness, contrast, saturation, temperature, tint, exposure, highlights, shadows, blur, sharpen, vignette
- [x] `MediaFrameDecoration` (renamed from `FrameOverlay`) — assetUri, opacity, mode, slice insets, content insets
- [x] `MediaFrameDecorationMode` enum (renamed from `FrameRenderMode`) — `Stretch`, `NineSlice`
- [x] `MediaStylePreset` — id, name, appearance (saved recipe). Storage/wiring (§20.3) still pending.
- [x] `appearance: MediaAppearance?` on `CanvasNode.Media`

### 20.1b Domain model — `FrameAppearance`
- [x] `FrameAppearance : NodeAppearance` — `background: BackgroundData?` (migrated from `Frame.background`), `contentOverlays: List<OverlayStyle> = emptyList()` (ordered; entry `[i]` over entry `[i-1]`), `titleStyle: FrameTitleStyle?`, `contentEffect: FrameContentEffect?` (sealed stub; no variants yet — see §20.2)
- [x] `appearance: FrameAppearance?` on `CanvasNode.Frame` — replaces direct `Frame.background` field; legacy JSON migration in `SceneGraphSerializer` lifts top-level `background` into `appearance.background` on read.
- [x] `FrameTitleStyle` — typography for the frame label (data shape only; title rendering pending)
- [ ] `FrameContentEffect` variants — sealed-class stub exists; concrete variants (Sepia / Grayscale / Blur) plus the off-screen pass rendering are post-MVP.

### 20.2 Rendering pipeline
- [x] **Media rendering** (`FullMediaRenderer`): shadow → cropped source (`CropMode` → `ContentScale`) → `overlays` (in list order, clipped to a rounded rect when `cornerRadius > 0`) → border → surface opacity via `graphicsLayer.alpha`. `colorAdjustments`, `frameDecoration`, `caption` persist but render as no-ops for now.
- [x] **Layered frame rendering**: paint loop in `CanvasScreen` walks `FramePaintEvent`s built by `buildFramePaintEvents` — Surface (shadow + background) at `frame.zIndex`, members in their own z-order, Overlay (contentOverlays + border) at `max(memberZ, frameZ) + epsilon`. Plain frames (no `contentOverlays`) still paint single-pass. See [rendering.md § 6b](architecture/rendering.md#6b-layered-frame-rendering).
- [x] **Shared overlay-stack helper**: `DrawScope.drawOverlayStack(overlays, left, top, right, bottom, textureBitmaps)` in `OverlayRenderer.kt`. Used by both `FullMediaRenderer` and `FullFrameRenderer` (Overlay phase).
- [x] `CropMode.Fit` — letterbox within bounding box (`ContentScale.Fit`).
- [x] `CropMode.Fill` — crop to fill bounding box (`ContentScale.Crop`; respects focal point is deferred until the manual-crop renderer lands).
- [ ] `CropMode.Manual` — pan/zoom inside bounding box (user-controlled). Currently falls back to `ContentScale.Crop`.
- [x] `CropMode.Stretch` — stretch to bounds ignoring aspect ratio (`ContentScale.FillBounds`).
- [x] `OverlayStyle` rendering — Solid / Texture / Procedural source with `NodeBlendMode` via `saveLayer(Paint(blendMode, alpha))`. Texture overlays load through `rememberOverlayTextureBitmaps` (Coil `SingletonImageLoader.execute` with `allowHardware(false)`), keyed on the set of `textureRefId`s in the list.
- [ ] Nine-slice decoration rendering — draw 9 regions independently (corners unscaled, edges scaled one axis).
- [ ] `MediaFrameDecoration.contentInsets` — defines usable content area (e.g. Polaroid caption space).
- [ ] LOD: skip overlay/filter rendering below a threshold zoom (stub view only); at intermediate zoom the renderer may draw only the first overlay entry or only entries with non-`Normal` blend; contentOverlays also drop at low LOD. Today: Full = everything, Simplified = no contentOverlays, Stub/Preview = stub paint only.
- [ ] `FrameContentEffect` rendering — off-screen pass (`GraphicsLayer.record` / `ColorMatrix`); post-MVP.

### 20.3 Style presets
- [ ] `MediaStylePreset` storage — per-album (scene graph) and/or global (app prefs)
- [ ] `CanvasAction.SaveAppearanceAsPreset(name: String)`
- [ ] `CanvasAction.ApplyPreset(presetId: String)` — applies to current selection
- [ ] `CanvasAction.CopyAppearance` / `PasteAppearance` — clipboard for `MediaAppearance` value
- [ ] `CanvasAction.ResetAppearance` — sets appearance to null (default rendering)
- [ ] All appearance mutations are undoable via existing snapshot undo

### 20.4 Rendered derivatives
- [ ] `CanvasAction.SaveRenderedDerivative` — flatten source + appearance into a new image file
- [ ] Output settings: format (PNG/JPEG/WebP), quality, resolution strategy (source res or display res ×2)
- [ ] Store result in `filesDir/media/<albumId>/rendered/`
- [ ] Register in `media_library` with `origin = RENDERED_DERIVATIVE`, `sourceAssetId`, `recipeHash`
- [ ] `CanvasAction.CreateRenderedCopyOnCanvas` — new node next to original, references derivative
- [ ] `CanvasAction.ReplaceWithRenderedImage` — replace node's mediaRefId with derivative id (undoable; preserve transform/zIndex/tags)
- [ ] `SaveToDeviceGallery` — export rendered image to system gallery

### 20.5 UI
- [x] Appearance panel / properties sheet for selected media node (`MediaAppearanceBottomSheet`), wired to a `✦ Appearance` entry in the contextual action bar.
- [x] `overlays` list editor — reorder (↑/↓), remove, per-entry source picker (Solid / Texture / Procedural), opacity slider, blend mode picker. Shared between media `overlays` and frame `contentOverlays`.
- [x] Frame appearance panel — `background` editor + `contentOverlays` list editor + opacity / cornerRadius / border / shadow sections (`FrameAppearanceBottomSheet`).
- [x] `BorderStyleEditor` + `ShadowStyleEditor` — reused by both sheets.
- [x] Crop mode selector (Fit / Fill / Manual / Stretch) + focal-point sliders (Fill) / manual offset+zoom sliders (Manual).
- [ ] `frameDecoration` picker (browse built-in + user assets — current UI is asset-URI text field + mode dropdown only).
- [ ] Color adjustments — sliders exist (`ColorAdjustmentsEditor`) but the renderer doesn't apply them yet; sliders persist values for the future renderer.
- [ ] Manual crop handle in canvas (the slider UI exists; in-canvas pan/zoom gesture handle is post-MVP).
- [ ] Context menu actions: Copy Appearance, Paste Appearance, Save as Preset, Reset Appearance, Save Edited Image.

### Implementation priority
MVP-adjacent: shared `NodeAppearance` base + `OverlayStyle` value type; `MediaAppearance` with opacity, crop (Fit/Fill/Manual), cornerRadius, border, shadow, `overlays: List<OverlayStyle>` (1–2 entries common), `frameDecoration` (Stretch), copy/paste appearance, save as preset, ResetAppearance; `FrameAppearance` with migrated `background` field plus an empty default `contentOverlays` list (model only; layered rendering can land later).
Post-MVP: layered frame renderer (`FrameAppearance.contentOverlays`), additional `NodeBlendMode` values, NineSlice decoration, parametric color adjustments, rendered derivatives, `FrameContentEffect`, animated overlays.

### 20.6 Clip + alpha mask (replaces `cornerRadius`)

See [appearance.md § 12](architecture/appearance.md#12-proposed-evolution--clip--alphamask). Status: proposal. Replaces `NodeAppearance.cornerRadius: Float` with two composable fields — `clip: ClipShape` (geometric) and `alphaMask: AlphaMask?` (continuous).

#### 20.6.1 Domain model
- [ ] `ClipShape` sealed — `RoundedRect(cornerRadius)`, `PerCornerRoundedRect(tl, tr, br, bl)`, `Ellipse`
- [ ] `AlphaMask(source, invert)` data class
- [ ] `AlphaMaskSource` sealed — `Image(maskRefId, channel, fitMode)`, `LinearGradient(angleDeg, stops)`, `RadialGradient(centerX, centerY, radiusX, radiusY, stops)`, `Procedural(pattern: ProceduralPattern)` — `ProceduralPattern` reused from §19
- [ ] `GradientStop(position, alpha)`, `MaskChannel { Luminance, Alpha }`, `MaskFitMode { Stretch, Fit, Fill }` enums
- [ ] Replace `cornerRadius: Float` on `NodeAppearance` / `MediaAppearance` / `FrameAppearance` with `clip: ClipShape` (default `RoundedRect(0)`) + `alphaMask: AlphaMask?` (default `null`)
- [ ] `SceneGraphSerializer` migration: read-time lift of legacy `cornerRadius` into `clip = RoundedRect(value)`

#### 20.6.2 Renderer
- [ ] Rename `withRoundedClip` → `withClipAndMask` in `CanvasRenderer.kt`
- [ ] `ClipShape.RoundedRect(0)` → fast path `clipRect` (free)
- [ ] `ClipShape.RoundedRect(r > 0)` → `clipPath(addRoundRect)` (today's behavior)
- [ ] `ClipShape.PerCornerRoundedRect` → `clipPath(addRoundRect)` with per-corner ctor
- [ ] `ClipShape.Ellipse` → `clipPath(addOval)`
- [ ] `AlphaMask` path: `CompositingStrategy.Offscreen` layer + `BlendMode.DstIn` + brush/bitmap source
- [ ] `AlphaMaskSource.LinearGradient` → `Brush.linearGradient` with stops mapped to `Color.White.copy(alpha = stop.alpha)`
- [ ] `AlphaMaskSource.RadialGradient` → `Brush.radialGradient`; elliptical case (`radiusX != radiusY`) via `scale` transform
- [ ] `AlphaMaskSource.Image` → Coil-loaded bitmap, optional luminance-to-alpha `ColorFilter` for `MaskChannel.Luminance`
- [ ] `AlphaMaskSource.Procedural` → reuse `drawProceduralPattern` from §19; luminance read as alpha
- [ ] `mask.invert` applied uniformly (color filter for image; stop reversal for gradients)
- [ ] Border / shadow stroke the clip path (not a hardcoded rounded rect); image-masked nodes use clip rect for shadow
- [ ] LOD: skip offscreen layer below `Full` tier

#### 20.6.3 Editor UI (depends on §1.3 popup direction)
- [ ] `ClipShapeEditor` content composable — shape picker with conditional sub-fields
- [ ] `AlphaMaskEditor` content composable — source picker with per-source sub-editors
- [ ] Image source picker: thumbnail browser from `media_library`, channel toggle, fit-mode dropdown
- [ ] Gradient source picker: angle dial (linear) / center+radii (radial), stops list editor
- [ ] Procedural source picker: reuse `ProceduralPatternEditor.kt` from §19
- [ ] Wire into `MediaAppearanceBottomSheet` (replaces uniform-radius slider) AND popup wrappers (per §1.3)

#### 20.6.4 Action wiring
- [ ] `CanvasAction.SetClip(clip: ClipShape)` — selection-scoped
- [ ] `CanvasAction.SetAlphaMask(mask: AlphaMask?)` — selection-scoped
- [ ] Compound undo entry per popup session (open `commandSessionId` on popup open, finalize on close)

### 20.7 Overlay field unification (`MediaAppearance.overlays` + `FrameAppearance.contentOverlays` → `base.overlays`)

See [appearance.md § 13](architecture/appearance.md#13-proposed-evolution--unified-overlays-on-the-base). Status: committed 2026-05-19, pending implementation. Behavior-preserving rename.

#### 20.7.1 Domain model
- [ ] Move `overlays: List<OverlayStyle>` to `NodeAppearance` base abstract; default `emptyList()`
- [ ] Remove `MediaAppearance.overlays` declaration (keep override on the subclass)
- [ ] Remove `FrameAppearance.contentOverlays` field; override `overlays` on the subclass

#### 20.7.2 Serializer migration
- [ ] `SceneGraphSerializer` read-time lift: when reading a `FrameAppearance`, if `contentOverlays` is present in the JSON, populate `overlays` instead
- [ ] `MediaAppearance.overlays` already has the right name — no JSON change needed
- [ ] Write path emits `overlays` for both subtypes; `contentOverlays` is removed from the wire format

#### 20.7.3 Renderer rename
- [ ] `buildFramePaintEvents` (`feature/canvas/view/FramePaintEvents.kt`) — read `appearance.overlays` for the layered-frame check (was `appearance.contentOverlays`)
- [ ] `FullFrameRenderer` overlay pass — read `appearance.overlays`
- [ ] `FullMediaRenderer` — no change (field name unchanged for media)
- [ ] `drawOverlayStack` call sites — no change (helper takes a `List<OverlayStyle>` regardless of which field it came from)

#### 20.7.4 Editor rename
- [ ] `FrameAppearanceBottomSheet` — `OverlayListEditor` now binds to `appearance.overlays` (was `appearance.contentOverlays`)
- [ ] `MediaAppearanceBottomSheet` — no change
- [ ] `CanvasAction.SetFrameAppearance` / `SetMediaAppearance` payloads — already carry the whole `FrameAppearance` / `MediaAppearance`, so payload shape changes only through the model rename
- [ ] If popup-based per-concept editors (§5d / context-menu) land first, the unified field naturally fits the unified "Edit overlays" popup

#### 20.7.5 Doc cleanup (after code lands)
- [ ] Collapse `appearance.md §§ 4–5` into a one-line historical note
- [ ] Update `appearance.md § 6` (render pipeline), § 8 (terminology), § 10 (impl status), § 11 (short rule) to use the single `overlays` name throughout
- [ ] Update `rendering.md § 6b` (`buildFramePaintEvents` description), `background.md § 5` (frame-overlays bullet), `data-model.md § NodeAppearance` to use the unified field
- [ ] Remove the "proposed evolution" banner on `appearance.md` once shipped

### Post-MVP
- [ ] `FrameAppearance.contentEffect` — off-screen filter pass (sepia / blur / grayscale of rendered frame contents)
- [ ] AI auto-enhance, background removal, old photo restoration, B&W colorization
- [ ] Animated overlays (Live Photo / Harry Potter newspaper style)
- [ ] Batch preset application across selection or entire album
- [ ] Batch rendering
- [ ] Silhouette-aware shadow for image/procedural alpha masks (extract outline from mask alpha)
- [ ] Vector polygon / SVG path masks (additional `AlphaMaskSource` or `ClipShape` variants)
- [ ] Caption styling (`CaptionStyle` field)

---

## 21. Widget System

Canvas-native smart objects with data binding and navigation. Widgets are `CanvasNode.Widget` entries — they participate in selection, drag, resize, LOD, and viewport culling like any other node, but also render structured album data and provide clickable navigation targets.

See [data-model.md § Widget System](architecture/data-model.md#widget-system) for domain types.  
See [PRD § 8.8](product/PRD.md#88-widget-system) and [PRD § 11.9](product/PRD.md#119-widget-system) for product requirements.

**This is a post-MVP milestone.** MVP infrastructure first, then individual widget types incrementally.

### 21.1 Infrastructure (prerequisite for all widgets)
- [ ] `CanvasNode.Widget` sealed variant — `widgetType`, `config: WidgetConfig`, `dataSource: WidgetDataSource`, `links: List<WidgetLink>`
- [ ] `WidgetType` enum (see data-model.md for full list)
- [ ] `WidgetDataSource` sealed class — AlbumNodes, AlbumTags, AlbumDates, AlbumPlaces, StaticConfig
- [ ] `WidgetLink` + `NavigationTarget` sealed class — ToFrame, ToNode, ToAlbum, ToFilteredView, ToExternalUri
- [ ] `WidgetConfig` per-type sealed class; serialized as JSON blob with type discriminator
- [ ] `CanvasWidgetRenderer<TConfig>` interface — `Render(widget, config, renderDetail, onNavigate)`
- [ ] Widget renderer registry — maps `WidgetType` to its `CanvasWidgetRenderer`
- [ ] Widget hit-test: distinguish outer bounds (node selection) from inner element clicks (navigation)
- [ ] Navigation dispatch from widget element click in View/Present mode → camera transition to target
- [ ] LOD support: Stub = placeholder rectangle; Preview = simplified; Full = interactive
- [ ] `CanvasNode.Widget` serialization + `ignoreUnknownKeys` for forward compat
- [ ] Widget undo: add/remove widget uses existing snapshot undo; config changes are undoable

### 21.2 Core navigation widgets (MVP widget set)
- [ ] **Portal** — clickable canvas object linking to any `NavigationTarget`; simplest widget type
- [ ] **Frame Navigator** — canvas-native table of contents showing frame hierarchy; click → camera jump
- [ ] **Tag Cloud** — visual tag frequency map; click tag → filtered view or frame
- [ ] **Highlights / Media Gallery** — curated photo grid/strip; click item → source frame
- [ ] **Calendar** — month/year grid with album dates highlighted; click day/month → frame
- [ ] **Map** — place markers from album data; click marker → frame; optional route lines

### 21.3 People & relationships widgets
- [ ] **People** — person avatars with photo count; click → person frame
- [ ] **Family Tree** — genealogy graph with relationship lines; click person → frame

### 21.4 Travel album widgets
- [ ] **Route** — trip route with waypoints; click segment/city → frame
- [ ] **Places / Cities List** — structured list with date range + photo count per place
- [ ] **Trip Calendar** — travel-specific calendar; shows where family was each day
- [ ] **Travel Highlights** — best photo per city / day

### 21.5 Family / child / school album widgets
- [ ] **Milestone Timeline** — key events on a chronological axis; click → frame
- [ ] **Growth Timeline** — age-based view (pregnancy → birth → years → school)
- [ ] **Milestones Card Grid** — grid of milestone cards (first steps, first day, etc.)
- [ ] **Classmates / Teachers** — people widget specialized for school years
- [ ] **Drawings / Crafts Gallery** — media gallery filtered by art/craft tag
- [ ] **Certificates / Achievements** — achievement cards with date

### 21.6 Cookbook widgets
- [ ] **Recipe Index** — searchable list; filter by person/ingredient/season/category; click → recipe frame
- [ ] **Recipe Card** — structured single-recipe widget (title, ingredients, steps, photos)
- [ ] **Recipe Steps** — step-by-step cooking widget with photos; designed for cook mode
- [ ] **Ingredients** — ingredient → recipe list; click ingredient → recipes
- [ ] **Seasons** — recipes organized by season/holiday; click → frame/filter
- [ ] **Meal Calendar** — calendar specialized for food history
- [ ] **Map of Tastes** — map widget with recipe-origin regions; click → recipe cluster
- [ ] **Recipe People** — recipes by family member / author
- [ ] **Incomplete Recipes** — workflow widget: drafts missing photos/ingredients/steps

### 21.7 AI Diary integration widgets
- [ ] **Period Summary** — AI-generated day/week/month/year summary; click → filtered frame
- [ ] **Memory Resurfacing** — "1 year ago / on this day" strip; click → source frame
- [ ] **Recent Entries** — latest diary entries or edited album notes
- [ ] **Needs Review** — AI-generated items awaiting user confirmation
- [ ] **Statistics** — compact summary card (entries, photos, places, people counts)

### 21.8 Educational / project widgets
- [ ] **Concept Map** — concepts + dependency links; click concept → detail frame
- [ ] **Process Timeline** — project stages with before/after and media evidence
- [ ] **Checklist** — interactive checklist; items can link to frames
- [ ] **Before / After** — side-by-side or slider comparison widget
- [ ] **Asset Strip** — media strip filtered by frame/person/date/tag

### 21.9 Wizard integration
- [ ] Album wizard can auto-generate an overview frame populated with widgets linked to nested frames
- [ ] Travel wizard: main frame = Map + Trip Calendar + Highlights + Tag Cloud + Cities List + AI Summary; nested frames per city/day
- [ ] Cookbook wizard: overview = Family Table + Recipe Index + Ingredients + Seasons + People; nested frames per recipe
- [ ] Child/school wizard: overview = Growth Timeline + Milestones + People + Calendar + Highlights; nested frames per year/event

### Post-MVP
- [ ] AI auto-layout (wizard places widgets and frames from imported data automatically)
- [ ] Timer / Cook mode widget (interactive, hands-free kitchen use)
- [ ] Animated widget transitions
- [ ] Batch widget data refresh when album content changes
- [ ] Widget marketplace / sharing presets

---

## 22. Presentation Form Factor

Album-level "intended screen shape" for viewing/presenting. The infinite canvas stays infinite — the profile only shapes new-frame defaults, View-mode camera fit, and editor overlays.

See [architecture/presentation-profile.md](architecture/presentation-profile.md) for the full design, data model, and open questions.

**Depends on §1.3** (scene graph root wrapper) — the profile lives in the JSON root alongside `albumBackground`.

### 22.1 Domain model
- [x] `AlbumPresentationProfile` — aspectRatio, orientation, defaultFitMode, defaultOutsideMode, safeAreaInset, defaultTransitionPreset, defaultEasing
- [x] `AspectRatio` sealed class — R_16_9, R_9_16, R_4_3, R_3_4, Square, Free, Custom(w, h)
- [x] `Orientation` enum — Landscape, Portrait
- [x] `FrameFitMode` enum — CONTAIN (MVP default), COVER, STRETCH
- [x] `OutsideFrameMode` enum — ALBUM_BACKGROUND, SOLID_FILL, BLURRED_BACKDROP (last is post-MVP)
- [x] `EasingType` enum — LINEAR, EASE_IN, EASE_OUT, EASE_IN_OUT (shared with future transition editor)
- [x] `TransitionPreset` enum — CALM, SOFT (MVP default), FAST, LINEAR, CUSTOM (last is post-MVP)
- [x] All `@Serializable`

### 22.2 Scene graph integration
- [x] Add `profile: AlbumPresentationProfile?` field to `SceneGraph` root (nullable for back-compat)
- [x] `SceneGraphSerializer` reads/writes profile (`ignoreUnknownKeys` covers old files)

### 22.3 Camera math
- [x] Parameterize `Transform.toCamera()` by `FrameFitMode` + `safeAreaInset`
- [x] Remove hardcoded `fillFraction = 0.9f`; default to profile or `0.1f` safe area
- [x] CONTAIN uses `min(sx, sy)`; COVER uses `max(sx, sy)`; STRETCH is X-driven
- [x] Update all callers to thread profile (or default) through (no production callers yet; tests updated)

### 22.4 Frame creation defaults
- [x] `CanvasNodeFactory.Frame` fits album aspect ratio inside the current viewport budget
- [x] `AspectRatio.Free` (and `profile == null`) skips the ratio fit (preserves current behavior)
- [x] Preserve `1/camera.scale` rebase trick — `w/h` stay camera-independent
- [x] `AspectRatio.numericRatio()` helper in `AlbumPresentationProfile.kt`

### 22.5 Editor overlays
- [ ] `PresentationOverlayRenderer` composable — drawn inside camera `graphicsLayer`, world-locked, strokes scaled by `1/camera.scale`
- [ ] Target aspect ratio rect (around focused frame or canvas center)
- [ ] Safe area inset rect
- [ ] Current device viewport indicator
- [ ] Target profile preview (hypothetical device at fixed pixel diagonal)
- [ ] TopBar toggle (visible in Edit mode only)

### 22.6 Settings UI
- [ ] Album settings entry point — likely a dedicated bottom sheet (no "Album Settings" surface exists today)
- [ ] Profile picker — aspect ratio + orientation + fit mode + outside mode + safe area inset
- [ ] `CanvasAction.SetAlbumPresentationProfile(profile)`
- [ ] Profile changes do not mutate existing frames (no reflow)

### 22.7 View mode consumption (depends on §12)
- [ ] `FocusNode` uses `Transform.toCamera(viewport, effectiveFitMode, profile.safeAreaInset)`
- [ ] Render `OutsideFrameMode` for the letterbox region in CONTAIN fit

### 22.8 Per-frame override (post-MVP)
- [ ] `FramePresentationOverride` — nullable per-field overrides on `CanvasNode.Frame`
- [ ] Effective fit/outside/ratio = override ?? album default
- [ ] UI for per-frame override (frame properties sheet, depends on §5 Object selected mode)

### 22.9 Post-MVP rendering
- [ ] `OutsideFrameMode.BLURRED_BACKDROP` — sample + blur (RenderEffect API 31+ or offscreen pass)

### Out of scope
- Adaptive layouts that rearrange nodes per screen.
- Multiple independent layouts per frame.
- Smart AI recomposition.
- Multiple profiles per album (only the primary is stored for MVP).

---

## 18. Future (Post-MVP)

See [future-ideas.md](product/future-ideas.md) for the full list.

- [ ] Smart tags — tap tag to teleport to frame
- [ ] User-defined layers full feature parity (multi-membership? layer locking? per-layer effects?)
- [ ] Present mode (read-only viewing for shared albums)
- [ ] Audio / Live Photos support
- [ ] Crop — media masking via bounding box editing
- [ ] Cloud sync — CRDT or Protobuf for real-time collaboration
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

Items 1–6 in the previous ordering are shipped: scene-graph root wrapper, save/restore camera, frame membership, and the Edit / View mode split with tool-aware gesture routing (`§ 12`, `editor-tools.md`, `selection.md`). What's actually next:

1. **Minimal media library** (§1.4–1.5) — `media_library` table: id, album_id, sourceUri, mediaType, status, intrinsic dimensions.
2. **Media validation + missing placeholder** (§4.4) — check `sourceUri` on album open, show placeholder for `MISSING`.
3. **Multi-photo import + auto grid placement** (§16).
4. **Group align / distribute** (§17).
5. **Guidelines** (§14.1–14.3) — without snapping first.
6. **Snapping** (§14.4).
7. **Other media types** (§4.7) — video / text / sticker / vector shape: pickers, schema, render.
8. **Panel / workspace persistence** — save panel layout + last-open album state across restarts.
9. **Basic widget infrastructure** (§21.1) — `CanvasNode.Widget`, then `Portal` and `FrameNavigator` (§21.2).

View-mode single-finger pan shipped 2026-06-05 (§24.5) — see `editor-tools.md § 7.4` and `selection.md § 5`.

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
- [x] Multi-select — rectangle selection via direct drag-on-empty in `Edit + Selection` (screen-space marquee, axis-aligned under camera rotation); group move/resize/rotate
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

> **Status note (2026-06-03, supersedes 2026-05-19 note):** This section predates the logical-surfaces decision (`editor-surfaces.md`). Phone *and* tablet both use the long-press popup + per-concept editor popups as the baseline `SelectionActionSurface` + `ConceptEditorSurface`. There is no separate "phone properties surface" or "tablet properties surface" — the baseline is the same. The `ObjectPropertiesPanel` placeholder below (§ 5d) is one possible future *alternative placement* for `ConceptEditorSurface` content, not a separate MVP architecture. Per-type sections in this entry remain useful as future content-composable specs.

### 5c.1 Surface
- [ ] `ObjectPropertiesPanel` — docked panel slot (right side, fits IDE panel system §6) for tablets/landscape — **deferred**, see § 5d
- [ ] ~~`ObjectPropertiesBottomSheet` — compact alternative for phone/portrait~~ — **obsolete**, replaced by context-menu popups (`context-menu.md`)
- [ ] Either surface is hidden when selection is empty or in View/Presentation mode

### 5c.2 Per-type sections (drive by `CanvasNode` variant)
- [ ] **Frame** — label, border color, background (solid color), fit-mode override (§22.8), opacity
- [ ] **Media** — label, tags, MediaAppearance (§20) preset, opacity
- [ ] **Widget** (§21) — per-widget-type config form
- [ ] Header row: node type + id + transform readout (cx, cy, w, h, scale, rotation)

### 5c.3 Action Bar removal (supersedes the older "shrinks back to…" plan)

> **Decided 2026-06-02.** `ContextualActionBar` is removed entirely (see [context-menu.md § 6](architecture/context-menu.md#6-contextualactionbar-removal)). All actions migrate into the long-press context menu. The bar shrinks-to-zero rather than shrinks-back-to-Delete/Duplicate.

Tracked under [§ 15.5](#155-contextualactionbar-removal--inline-rows) below — bar deletion + the inline z-order row + the inline frame-membership row land together.

### 5c.4 Dependencies
- §13 layers (for "Move to Layer" control)
- §15 context menu (long-press → "Properties" entry)
- §20 MediaAppearance (Media section needs the appearance UI to be defined)

---

## 5d. Tablet Properties Panel (deferred wide-screen placement)

> **Status updated 2026-06-03:** Per [`editor-surfaces.md`](architecture/editor-surfaces.md), there is **no separate tablet editor architecture**. Tablet uses the same baseline layout as phone — global top bar + `ToolControlBar` + canvas + long-press popup + concept-editor popups. This § 5d placeholder is demoted to a **deferred future placement option** for `ConceptEditorSurface` content in wide-screen / configurable-workspace scenarios. The content composables it would host (`BorderEditorContent`, `ShadowEditorContent`, …) are already designed surface-agnostic and ship under their popup hosts first — the panel is an *additional* rendering host, not a parallel surface.

When (and only when) wide-screen workspace mode is built, this placeholder becomes the docked-inspector implementation. Nothing about the editor model changes when that happens.

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

Multi-selection semantics **decided 2026-05-24**; see [architecture/z-order.md](architecture/z-order.md) for the full rule set. Short form: `BringToFront` / `SendToBack` use block-extreme (selection lifted/sunk as a block, internal order preserved); `BringForward` / `SendBackward` use independent-with-skip (each selected node moves one step, treating other selected nodes as transparent — Figma-aligned).

- [x] `recalculateVisibleNodes` sorts the result by `Transform.zIndex` (`CanvasViewModel.kt`) — render correctness now depends only on `zIndex`, not on `_allNodes` insertion order.
- [x] `CanvasAction.BringToFront(nodeId)` — `newZ = max + 1`. No-op if already on top.
- [x] `CanvasAction.SendToBack(nodeId)` — `newZ = min - 1`. No-op if already at bottom.
- [x] `CanvasAction.BringForward(nodeId)` — swaps `zIndex` with the next-higher neighbor. No-op if already on top.
- [x] `CanvasAction.SendBackward(nodeId)` — swaps `zIndex` with the next-lower neighbor. No-op if already at bottom.
- [x] All four routed through `applyZIndexReorder(nodeId, ZReorder)`; undoable via `CommandKind.REORDER` (snapshot of the one or two affected nodes).
- [x] Four actions surfaced when exactly one node is selected. Shipped first on `ContextualActionBar` with icons `⤒ ▲ ▼ ⤓`; migrated 2026-06-02 to the inline z-order row in the long-press popup when the bar was removed (§ 15.5). Visibility predicates now live on `BringToFrontAction` / `BringForwardAction` / `SendBackwardAction` / `SendToBackAction` in `EditorActionCatalog`.

#### Multi-selection (decided, not yet implemented)

- [ ] Pure functions in `core/math/ZOrder.kt`: `bringSelectionToFront`, `sendSelectionToBack`, `bringSelectionForward`, `sendSelectionBackward`. Each returns `Map<NodeId, Float>` of zIndex changes; empty map = no-op.
- [ ] Block-extreme rule for `BringToFront` / `SendToBack` — selection moved to extreme as a contiguous block, internal relative order preserved. (see `z-order.md § 3.1`)
- [ ] Independent-with-skip rule for `BringForward` / `SendBackward` — each selected node swaps with its next *unselected* neighbor in the requested direction. (see `z-order.md § 3.2`)
- [ ] Refactor existing single-id `CanvasAction.BringToFront(nodeId)` etc. to use the new pure functions internally (or replace with `BringSelectionToFront(selection)` and let the single-id path pass `setOf(nodeId)`).
- [ ] One `CommandKind.REORDER` Compound undo per multi-selection command (snapshot all nodes whose zIndex changed — selected + side-effect-affected unselected).
- [ ] Frame membership recompute: **not** required (z-order doesn't affect geometry). Confirm in the implementation PR.
- [ ] Z-order popup row ungated for multi-selection — drop the `selectedNodeIds.size == 1` constraint on `BringToFrontAction` / `BringForwardAction` / `SendBackwardAction` / `SendToBackAction` in `EditorActionCatalog`. (The bar is gone; the inline z-order row in the popup is the current host — see `context-menu.md § 4`.)
- [ ] No-op-at-extreme acceptable for MVP; popup row entries stay enabled even when no movement is possible. Grey-out per-state is a follow-up (see `z-order.md § 6`).
- [ ] Unit tests for each pure function covering: sparse selection, contiguous selection, full selection, at-extreme no-op, single-node degenerate case, layered-frame interaction.

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

The transient popover that appears on long-press. **Baseline rendering of the `SelectionActionSurface`** (see [editor-surfaces.md](architecture/editor-surfaces.md)) and the single host for selection-scoped actions today — the earlier persistent `ContextualActionBar` strip was removed in § 15.5 (shipped 2026-06-02). Full design in [context-menu.md](architecture/context-menu.md).

### 15.1 Edit mode
- [x] Long-press on a node → context menu popover at touch point (`ContextMenuPopup` in `feature/canvas/view/ContextMenu.kt`, hosted in `CanvasScaffold`). Popup is non-focusable (transparent to touches outside its surface) so a new long-press on another node immediately replaces the popup; tap / drag / double-tap dismiss via `onCanvasGesture` callback.
- [x] Menu model: `(selection, anchorNodeId, anchorScreenX, anchorScreenY, pickerNodes)`. Selection-scoped items + anchor-scoped items per [context-menu.md § 2](architecture/context-menu.md#2-menu-model). Stacked-long-press carries the full hit list in `pickerNodes`; the popup renders a checkbox picker row per node above the menu items, with the anchor row highlighted (semibold + tinted background). Toggling a row dispatches `ToggleNodeSelection` and updates the anchor to that node.
- [x] Single-media menu: `Edit appearance` (opens `MediaAppearanceBottomSheet`), `Duplicate`, `Delete`. Items with no underlying action yet (Edit media, Replace media, separate clip/mask/crop popups) omitted — wired when their actions/popups ship.
- [x] Single-frame menu: `Edit frame appearance` (opens `FrameAppearanceBottomSheet`), `Navigate to frame` (`FocusNode`), `Duplicate`, `Delete`. `Edit frame contents` omitted (post-MVP).
- [x] Group menu (selection ≥ 2): `Duplicate selection`, `Delete selection`, `Clear selection`. `Create frame around selection` deferred until §18 ships; `Align` / `Distribute` deferred until §17 ships.
- [x] Anchor-scoped items (selection ≥ 2, anchor in selection): `Remove this from selection` (dispatches `ToggleNodeSelection(anchorId)` — `ToggleNodeSelection` is no longer dispatched by gestures but is wired here per §15.4), `Edit this only` (`SelectNode(anchorId)`).
- [x] Long-press on empty space → context menu with `Add…` (single entry opens the existing `AddContentBottomSheet`). Splitting into per-type items (Add Photo / Add Frame / Add Text / Paste / Add Guideline) and direct dispatches is a follow-up; Text/Paste/Guideline don't have underlying infrastructure yet.

### 15.2 View mode
- [ ] Long-press on media → viewer menu (Open / Share / Info). Deferred — no underlying viewer/share/info infrastructure yet. View-mode long-press is currently swallowed; no context menu opens.
- [ ] Long-press on frame → frame menu (Focus / Open as Album View). Deferred — `Focus` is redundant with View-mode tap; `Open as Album View` has no infrastructure.

### 15.3 Conflict with current long-press semantics — resolved
- Today long-press = toggle selection ([selection.md § 2](architecture/selection.md#2-gesture-mapping)). Decision: long-press becomes **add-or-keep + open menu on UP**; the "remove this from selection" intent moves into the menu's anchor-scoped items. See §15.4 (gesture rule rewrite, shipped) and the menu wiring above.

### 15.4 Gesture rule rewrite (lands first, before the popover)
Pure-gesture slice that verifies the rule shift in isolation. Behavior-preserving for empty-selection users; replaces "long-press = toggle off" with "long-press = add-or-keep" for non-empty selection.

- [x] `CanvasAction.AddNodeToSelection(nodeId: String)` — additive, idempotent. Replaces `ToggleNodeSelection` as the long-press dispatcher.
- [x] `ToggleNodeSelection` stays in the codebase as the implementation of the future menu's "Remove this from selection" item; no gesture dispatches it.
- [x] Long-press handler in `CanvasScreen.kt` dispatches `AddNodeToSelection(hit.id)` for the 1-hit case. UP-after-no-drag → `OpenContextMenu(...)` is deferred to the popover slice.
- [x] Overlap picker uses additive `AddNodesToSelection(ids)` (already wired in `CanvasScaffold.kt`). The original "picker replaces selection" issue was resolved separately; this slice keeps the additive semantic.
- [x] Update `selection.md § 2` long-press row to **Add-or-keep**; expand § 3 action list with `AddNodeToSelection` / `AddNodesToSelection`.
- [x] Update `selection.md § 6` to remove the resolved overlap-picker bullet.

### 15.5 ContextualActionBar removal + inline rows — **shipped 2026-06-02**

> **Decided 2026-06-02** ([context-menu.md § 4 + § 6](architecture/context-menu.md#4-menu-content-by-selection-type)). Bar removal + the two inline action rows landed together after the [§ 25](#25-editor-action-catalog) catalog refactor.

- [x] **Z-order inline row** — four-button row (`⤒ ▲ ▼ ⤓`) added to single-selection menus via `EditorActionCatalog.visibleByCategory(ctx)[ZOrder]` and the `List<EditorAction>.toInlineRowItem(...)` helper in `EditContextMenuItems.kt`. Multi-selection row stays empty until §13.5 ships (the actions' `isVisible` already gates on `size == 1`).
- [x] **Frame-membership inline row** — three-button row (`⊕ Pin · ⊖ Detach · ⟲ Auto`). Visibility comes from `ctx.pinDetachEnabled` / `ctx.anyOverrideExists`, derived once in `CanvasScaffold.kt`. Direct dispatch for the unambiguous single-target case; opens `FrameTargetPickerDialog` for the multi-frame case (existing wiring, via `EditorActionEffect.FrameMembership` → `dispatchFrameMembership`).
- [x] **Popup container** — `ContextMenuItem` gained `inlineRow: List<InlineRowButton>?`; `ContextMenuPopup` renders the inline-row branch via a private `ContextMenuInlineRow` composable. Dismissal rules unchanged.
- [x] **Delete `ContextualActionBar`** — composable file removed; call site removed from `CanvasScaffold.kt`; the conditional-visibility props (`showBackgroundAction`, `showMediaAppearanceAction`, `showZOrderActions`, `showFrameMembershipActions`, `showAutoAction`) are now visible-by-construction on the catalog; `dismissPopupAndAccept` helper deleted (no remaining consumer). `dispatchFrameMembership` retained — used by the popup's frame-membership row via the central effect dispatcher.
- [x] **Verify** — every action the bar exposed is reachable from the popup; tests in `EditContextMenuItemsTest` cover the new menu shape (header / z-order row / membership row / Delete / anchor block / Clear selection).

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

See [context-menu.md § 5](architecture/context-menu.md#5-create-frame-around-selection-net-new-action). Independent of the rest of the menu wiring — schedule alongside the existing per-selection-type popup contents.

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
- [ ] New catalog entry `CreateFrameAroundSelectionAction` in `feature/canvas/actions/EditorActionCatalog.kt` (category `Lifecycle`, visible when `selectedNodeIds.size >= 1`). Renders as a text row in the long-press popup.

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
- [x] Undo for background changes — `CommandKind.SET_ALBUM_BACKGROUND` + `SET_FRAME_APPEARANCE`; `CanvasCommand.albumBackgroundChange` for album-level snapshots

### 19.6 UI
- [x] Shared `BackgroundEditor` composable in `feature/ide_ui/ui/content/BackgroundEditorContent.kt` — source radio (None / Solid / Texture / Procedural), per-source controls, opacity slider. Used by both album and frame sheets.
- [x] Album Settings bottom sheet (TopBar palette button) — wraps `BackgroundEditor` and adds the anchor toggle (CameraLocked / WorldLocked)
- [x] Frame Background bottom sheet (popup `▣ Edit frame appearance` entry when one Frame selected — pre-bar-removal this was on `ContextualActionBar`) — wraps `BackgroundEditor` with no anchor toggle (frame is its own anchor)
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

See [appearance.md](architecture/appearance.md) for the shared model and the render-pipeline contract. `overlays: List<OverlayStyle>` is a unified field on `NodeAppearance` base; the renderer dispatches per node type. The pre-2026-05-19 design had separate `MediaAppearance.overlays` / `FrameAppearance.contentOverlays` fields — see [§ 20.7](#207-overlay-field-unification-mediaappearanceoverlays--frameappearancecontentoverlays--baseoverlays) for the rename slice and [appearance.md § 13](architecture/appearance.md#13-design-history--overlay-unification) for the design-history note.  
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
- [x] `MediaFrameDecoration` (renamed from `FrameOverlay`) — assetUri, opacity, mode, slice insets, content insets (the rectangular frame opening), `openingMaskUri` (arbitrary-shape opening)
- [x] `MediaFrameDecorationMode` enum (renamed from `FrameRenderMode`) — `Stretch`, `NineSlice`
- [x] `MediaStylePreset` — id, name, appearance (saved recipe). Storage/wiring (§20.3) still pending.
- [x] `appearance: MediaAppearance?` on `CanvasNode.Media`

### 20.1b Domain model — `FrameAppearance`
- [x] `FrameAppearance : NodeAppearance` — `background: BackgroundData?` (migrated from `Frame.background`), `overlays: List<OverlayStyle> = emptyList()` (inherited from base post-§20.7; ordered; entry `[i]` over entry `[i-1]`), `titleStyle: FrameTitleStyle?`, `contentEffect: FrameContentEffect?` (sealed stub; no variants yet — see §20.2)
- [x] `appearance: FrameAppearance?` on `CanvasNode.Frame` — replaces direct `Frame.background` field; legacy JSON migration in `SceneGraphSerializer` lifts top-level `background` into `appearance.background` on read.
- [x] `FrameTitleStyle` — typography for the frame label (data shape only; title rendering pending)
- [ ] `FrameContentEffect` variants — sealed-class stub exists; concrete variants (Sepia / Grayscale / Blur) plus the off-screen pass rendering are post-MVP.

### 20.2 Rendering pipeline
- [x] **Media rendering** (`FullMediaRenderer`): shadow → cropped source (`CropMode` → `ContentScale`, confined to the frame-decoration opening when set) → `overlays` (in list order, clipped to a rounded rect when `cornerRadius > 0`) → `frameDecoration` (over the full rect, after the alpha-mask layer) → border → surface opacity via `graphicsLayer.alpha`. `colorAdjustments`, `caption` persist but render as no-ops for now.
- [x] **Layered frame rendering**: paint loop in `CanvasScreen` walks `FramePaintEvent`s built by `buildFramePaintEvents` — Surface (shadow + background) at `frame.zIndex`, members in their own z-order, Overlay (`overlays` + border) at `max(memberZ, frameZ) + epsilon`. Plain frames (no `overlays`) still paint single-pass. See [rendering.md § 6b](architecture/rendering.md#6b-layered-frame-rendering).
- [x] **Shared overlay-stack helper**: `DrawScope.drawOverlayStack(overlays, left, top, right, bottom, textureBitmaps)` in `OverlayRenderer.kt`. Used by both `FullMediaRenderer` and `FullFrameRenderer` (Overlay phase).
- [x] `CropMode.Fit` — letterbox within bounding box (`ContentScale.Fit`).
- [x] `CropMode.Fill` — crop to fill bounding box (`ContentScale.Crop`; respects focal point is deferred until the manual-crop renderer lands).
- [x] `CropMode.Manual` — pan/zoom inside bounding box (user-controlled). Shipped 2026-06-07 in § 20.8 alongside the `CropEdit` tool. Renderer is `FullMediaRenderer.drawCroppedBitmap`; reads `crop.{offsetX, offsetY, zoom}` directly.
- [x] `CropMode.Stretch` — stretch to bounds ignoring aspect ratio (`ContentScale.FillBounds`).
- [x] `OverlayStyle` rendering — Solid / Texture / Procedural source with `NodeBlendMode` via `saveLayer(Paint(blendMode, alpha))`. Texture overlays load through `rememberOverlayTextureBitmaps` (Coil `SingletonImageLoader.execute` with `allowHardware(false)`), keyed on the set of `textureRefId`s in the list.
- [x] Nine-slice decoration rendering — `MediaFrameDecorationRenderer.drawNineSlice` draws the 9 regions independently (corners uniform-scaled to keep aspect, edges scaled one axis), shared edges rounded once to avoid seams. Stretch mode also shipped. Applied by `FullMediaRenderer` + `VideoSurfaceChrome`. Shipped 2026-06-20.
- [x] `MediaFrameDecoration.contentInsets` — consumed as the **frame opening**: media is scaled into + clipped to the opening rect (`MediaFrameDecoration.openingRect`), decoration drawn over the full rect on top. Shipped 2026-06-20.
- [x] `MediaFrameDecoration.openingMaskUri` — arbitrary-shape opening (oval / arch / torn paper) overriding the rectangular insets. `openingAlphaMask()` builds a synthetic `AlphaMask` (Image / Luminance / Stretch) DstIn-composited over the media (still + video), white = opening. Editor Opening section has a mask picker. Shipped 2026-06-20.
- [x] Video: rescale (not just clip) content to the opening — `videoContentRect` computes against the opening rect via `CanvasNode.Media.contentTargetRect()` (player + poster), so framed video fills the hole. Shipped 2026-06-20.
- [ ] LOD: skip overlay/filter rendering below a threshold zoom (stub view only); at intermediate zoom the renderer may draw only the first overlay entry or only entries with non-`Normal` blend; frame overlays also drop at low LOD. Today: Full = everything, Simplified = no frame overlays, Stub/Preview = stub paint only.
- [ ] `FrameContentEffect` rendering — off-screen pass (`GraphicsLayer.record` / `ColorMatrix`); post-MVP.

### 20.3 Style presets

**Design decided 2026-06-21** → [`media-presets.md`](architecture/media-presets.md) (model + UI) + [`media-appearance.md`](architecture/media-appearance.md) content-model refactor. Live-link + per-node overrides, sectioned presets, per-field override *target* (slice-1 UI = whole-section), bake-then-unlink delete, resolution before the render boundary. Two slices, **refactor-first**:

**Slice 0 — content-model refactor (prerequisite, `to_discuss.md § 19`) — DONE 2026-06-21:**
- [x] Rename field `alphaMask` → `contentMask` on `NodeAppearance` (**no `@SerialName`, no migration** — no important projects). Value type `AlphaMask` + `AlphaMaskRenderer`/`AlphaMaskEditor` keep their names (field-only rename).
- [x] `MediaFrameDecoration` → `MediaDecoration`: add stable `id`, `placement: Above|Below`; **removed** `contentInset*` + `openingMaskUri`. `MediaFrameDecorationMode` → `MediaDecorationMode`; new `DecorationPlacement`.
- [x] `MediaAppearance.frameDecoration` → `decorations: List<MediaDecoration> = emptyList()`; added `opening: MediaOpening?` (rectangular insets only).
- [x] Renderer (`MediaDecorationRenderer`, renamed): loop decorations (Below → content[opening+crop+contentMask] → overlays → Above → border); dropped `openingAlphaMask`; `openingRect` derives from `MediaOpening`. Same in `VideoSurfaceChrome`. *(Slice-0 limitation: Below cut by contentMask when present.)*
- [x] Editor: `DecorationListEditor` (add/remove/reorder + per-item `MediaDecorationEditor` with placement) + `MediaOpeningEditor`; context-menu actions Content mask / Opening / Decorations.
- [x] No serializer migration (old keys ignored via `ignoreUnknownKeys`). Tests updated; build + unit tests green; verified on-device 2026-06-21.

**Slice 1 (core) — static presets — build + unit tests green 2026-06-21 (on-device pending):**
- [x] App-level `MediaPresetStore` (SharedPreferences + kotlinx JSON, Hilt `@Singleton`, mirrors `InteractionSettingsRepository`). `MediaStylePreset` gains `sections: Set<AppearanceSection>`; new `AppearanceSection` enum + `PresetBinding(presetId, overridden)`.
- [x] `presetBinding: PresetBinding?` field on `CanvasNode.Media` (serializer back-compat; absent = today's behavior).
- [x] Pure `resolveMediaAppearance(node, presetsById)` (`MediaAppearanceResolver.kt`) + `withSection(s)` / `nonDefaultSections` / `resolvedForRender`. Resolution applied in `CanvasViewModel.recalculateVisibleNodes` (render boundary); `_allNodes` stays raw for editors; renderers/video/export unchanged.
- [x] New `CanvasAction.UpdateMediaNodes(perId: MediaNodePatch)` (appearance + binding in one undoable snapshot). VM `applyPreset` (stamp + bind), `unlinkPreset` / `deletePreset` (**bake-then-unlink**), `saveSelectionAsPreset`, `duplicatePreset`.
- [x] `dispatchMediaConcept` gains an `AppearanceSection` arg — editing a concept on a bound node marks that section **overridden** (whole-section granularity).
- [x] `PresetLibrarySheet` + `Presets…` context-menu action: Save selection / Apply / Duplicate / Delete / Unlink, with the apply-over-overrides prompt (`Replace look` / `Keep my changes`).
- [x] Undo: `presetBinding` + node override changes ride the canvas snapshot stack; preset *definition* edits (store) stay off it.

**Slice 1b:**
- [x] Aggregate **preset-definition editor** (`PresetDefinitionEditor` — name + per-section governs-checkbox + expandable concept editor, reusing the existing concept composables) opened from a per-card **Edit** in `PresetLibrarySheet`; `CanvasViewModel.updatePreset` passthrough. Build + unit tests green 2026-06-21.

**Slice 1c:**
- [x] Rich card previews — `MediaPresetPreview` renders a synthetic card-sized node through the existing public `CanvasNodeRenderer` (full media pipeline, zero renderer changes), wired into `PresetLibrarySheet` cards (preview on the selection's first media; falls back to name+count if no uri). Build + unit tests green 2026-06-21.

**Slice 1c — remaining:**
- [ ] Inherited / overridden visual language (muted/active + per-section reset) on the per-concept node sheets.
- [ ] Per-field (`AppearancePath`) overrides, section-by-section (ColorAdjustments first).
- [ ] `CanvasAction.ResetAppearance`; `CopyAppearance` / `PasteAppearance`.

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
- [x] `overlays` list editor — reorder (↑/↓), remove, per-entry source picker (Solid / Texture / Procedural), opacity slider, blend mode picker. Operates on `appearance.overlays` for both media and frame (unified post-§20.7).
- [x] Frame appearance panel — `background` editor + `overlays` list editor + opacity / cornerRadius / border / shadow sections (`FrameAppearanceBottomSheet`).
- [x] `BorderStyleEditor` + `ShadowStyleEditor` — reused by both sheets.
- [x] Crop mode selector (Fit / Fill / Manual / Stretch) + focal-point sliders (Fill) / manual offset+zoom sliders (Manual).
- [x] `frameDecoration` asset picker — SAF `OpenDocument` (`image/*`) with thumbnail + Pick/Replace/Clear (mirrors mask/overlay pickers), replacing the asset-URI text field. Slice + opening insets are integer-percent fields with per-section asymmetric toggles. Shipped 2026-06-20.
- [ ] `frameDecoration` library browse — built-in + user assets via the media-library registry (§ 1.4); today it's the SAF picker only.
- [ ] Color adjustments — sliders exist (`ColorAdjustmentsEditor`) but the renderer doesn't apply them yet; sliders persist values for the future renderer.
- [x] Manual crop handle in canvas — shipped 2026-06-07 as `EditorTool.CropEdit`. See [editor-tools.md § 4.8](architecture/editor-tools.md#48-cropedit) + § 20.8 for the slice details.
- [ ] Context menu actions: Copy Appearance, Paste Appearance, Save as Preset, Reset Appearance, Save Edited Image.

### Implementation priority
MVP-adjacent: shared `NodeAppearance` base (with unified `overlays: List<OverlayStyle>`) + `OverlayStyle` value type; `MediaAppearance` with opacity, crop (Fit/Fill/Manual), cornerRadius, border, shadow, `overlays` (1–2 entries common), `frameDecoration` (Stretch), copy/paste appearance, save as preset, ResetAppearance; `FrameAppearance` with migrated `background` field plus an empty default `overlays` list and the layered renderer.
Post-MVP: additional `NodeBlendMode` values, parametric color adjustments, rendered derivatives, `FrameContentEffect`, animated overlays. (NineSlice decoration + rectangular opening crop shipped 2026-06-20; arbitrary `openingMaskUri` openings still pending.)

### 20.6 Clip + alpha mask (replaces `cornerRadius`)

See [appearance.md § 12](architecture/appearance.md#12-proposed-evolution--appearance-layers). Status: proposal. Replaces `NodeAppearance.cornerRadius: Float` with two composable fields — `clip: ClipShape` (geometric) and `alphaMask: AlphaMask?` (continuous).

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

See [appearance.md § 13](architecture/appearance.md#13-design-history--overlay-unification). Code work complete 2026-05-19; doc cleanup (§20.7.5) still pending. Behavior-preserving rename — legacy albums migrate transparently via `SceneGraphSerializer`.

#### 20.7.1 Domain model
- [x] Move `overlays: List<OverlayStyle>` to `NodeAppearance` base abstract; default `emptyList()`
- [x] Remove `MediaAppearance.overlays` declaration (keep override on the subclass)
- [x] Remove `FrameAppearance.contentOverlays` field; override `overlays` on the subclass

#### 20.7.2 Serializer migration
- [x] `SceneGraphSerializer` read-time lift: when reading a `FrameAppearance`, if `contentOverlays` is present in the JSON, populate `overlays` instead
- [x] `MediaAppearance.overlays` already has the right name — no JSON change needed
- [x] Write path emits `overlays` for both subtypes; `contentOverlays` is removed from the wire format

#### 20.7.3 Renderer rename
- [x] `buildFramePaintEvents` (`feature/canvas/view/FramePaintEvents.kt`) — read `appearance.overlays` for the layered-frame check (was `appearance.contentOverlays`)
- [x] `FullFrameRenderer` overlay pass — read `appearance.overlays`
- [x] `FullMediaRenderer` — no change (field name unchanged for media)
- [x] `drawOverlayStack` call sites — no change (helper takes a `List<OverlayStyle>` regardless of which field it came from)

#### 20.7.4 Editor rename
- [x] `FrameAppearanceBottomSheet` — `OverlayListEditor` now binds to `appearance.overlays` (was `appearance.contentOverlays`)
- [x] `MediaAppearanceBottomSheet` — no change
- [x] `CanvasAction.SetFrameAppearance` / `SetMediaAppearance` payloads — already carry the whole `FrameAppearance` / `MediaAppearance`, so payload shape changes only through the model rename
- [x] If popup-based per-concept editors (§5d / context-menu) land first, the unified field naturally fits the unified "Edit overlays" popup

#### 20.7.5 Doc cleanup (after code lands)
- [x] Collapse `appearance.md §§ 4–5` into a one-line historical note
- [x] Update `appearance.md § 6` (render pipeline), § 8 (terminology), § 10 (impl status), § 11 (short rule) to use the single `overlays` name throughout
- [x] Update `rendering.md § 6b` (`buildFramePaintEvents` description), `background.md § 5` (frame-overlays bullet), `data-model.md § NodeAppearance` to use the unified field
- [x] Remove the "proposed evolution" banner on `appearance.md` once shipped
- [x] Refresh `decisions.md`, `media-appearance.md`, and historical §20 entries to drop stale `contentOverlays` mentions

### 20.8 CropEdit slice — Manual renderer + in-canvas handles

Locked 2026-06-06 in [editor-tools.md § 4.8](architecture/editor-tools.md#48-cropedit), shipped 2026-06-07. Renderer + tool ship together — the renderer without the tool reproduces today's persist-only confusion, and the tool without the renderer cannot show its effect.

**Model A (no new `cropRect` field).** The media node's world rect is the fixed viewport; `CropSettings.offsetX / offsetY / zoom` pan and zoom the source behind it. `Selection` and `CropEdit` resize the rect with **different** crop-compensation rules per § 4.8's "Conceptual split": Selection scales offset by the resize factor (same content, just bigger); CropEdit holds the source's world position+size (reveals more of the image). Source rotation, source-space cropRect, aspect-ratio preset chips, faded outside-rect preview, and multi-media CropEdit are explicitly out of scope.

#### 20.8.1 Manual crop renderer
- [x] `FullMediaRenderer` reads `CropSettings.{offsetX, offsetY, zoom}` for `CropMode.Manual` via a new `drawCroppedBitmap` helper instead of falling through to `ContentScale.Crop`. `zoom = 1` is defined as the Fill scale (`drawScale = fillScale × zoom` where `fillScale = max(renderW/srcW, renderH/srcH)`).
- [x] Manual composition is verified end-to-end against the per-media pipeline in [media-appearance.md § Rendering Pipeline](architecture/media-appearance.md#rendering-pipeline-per-media-node): rounded corners, `appearance.alphaMask`, `overlays`, border, shadow, surface `opacity`. Source pans / zooms inside the clipped viewport; chrome clips the rendered viewport, not the source.
- [x] `colorAdjustments` and `frameDecoration` continue to no-op / pass through unchanged.
- [x] Mode-switch behavior at `CropEdit` tool entry (v1 — no intrinsic-size cache): snap mode to Manual, keep existing offset/zoom values. Fit/Stretch/non-default-Fill → Manual with default values renders as centered Fill (documented one-frame snap).
- [ ] Precise focal-aware Fill / Fit seed math via a media-asset intrinsic-size cache — follow-up.

#### 20.8.2 `EditorTool.CropEdit` (context-gated)
- [x] `CropEdit` added to `EditorTool` sealed vocabulary under `feature/canvas/editor/`.
- [x] Entry gated by `selection == {one Media}`; `enforceCropEditInvariant` auto-exits to `Selection` (and clears the entry snapshot) when the invariant breaks. Wired into `recomputeGroupTransform`, `DeselectAll`, `deleteNodesById`.
- [x] Not exposed in the primary tool selector; `EditorTool.label` shows "Crop" only when active.
- [x] On entry, `crop.mode` snaps to `Manual`. `entrySnapshot` captures the pre-entry media transform + appearance (for Cancel **and** as the session-compound baseline). **As of § 20.9.2** the snap is no-history (`snapCropModeToManualNoHistory`) — folded into the single session entry committed on exit, not its own `SetMediaAppearance` command.
- [x] On tool-switch out (including via `Apply`), `entrySnapshot` is cleared; node stays in `Manual`.

#### 20.8.3 Context menu wiring
- [x] `EditCropAction` dispatches `CanvasAction.SetActiveTool(EditorTool.CropEdit)` via `EditorActionEffect.Dispatch`. Visibility: `isAllMedia && selectedMediaInOrder.size == 1` (multi-media hidden — out of scope).
- [x] Old `OpenCropEditor` sheet wiring preserved unwired in `CanvasScaffold`; kept for a future "Crop mode…" popup item.
- [x] `(Edit, CropEdit, [Media])` popup entry per [editor-tools.md § 5](architecture/editor-tools.md#5-popup-derivation) reads `Reset crop / Cancel / Apply`.

#### 20.8.4 Gestures + handles
- [x] Eight handles drawn while `CropEdit` is active (4 corners + 4 edges) — `SelectionOverlay.NodeChrome` rendering, new `edgeHandlesEnabled` flag. Screen-space sized via `CanvasViewModel.HANDLE_SCREEN_PX`. No rotation handle.
- [x] New `EdgeHandle { TOP, RIGHT, BOTTOM, LEFT }` enum + `TransformUtils.hitTestEdgeHandle`.
- [x] `nodeInteractionGestures` extended with optional `hitTestEdgeHandle` / `onEdgeResizeDrag` params; Selection callers pay no cost via defaults.
- [x] Drag inside viewport → `PanCropSource(nodeId, worldDx, worldDy)`; handler rotates world delta into node-local and adds to `crop.offset`.
- [x] Drag corner → `ResizeMediaFreeCorner` (always — aspect lock is enforced by projecting the world delta onto the corner's diagonal in node-local before dispatch, so the source-stability compensation always fires).
- [x] Drag edge → `ResizeMediaEdge` (always one-axis, ignores aspect lock).
- [x] Two-finger pinch → `PinchCropSource` with centroid-anchored zoom + screen pan. **Overrides** the locked "two-finger = global nav" rule § 1.3 — `infiniteCanvasGestures` callback branches on `activeTool`. Rotation component dropped.
- [x] `GestureRouter` routes: `routeSelectedNodeTransformStart` Allows `CropEdit`; `routeEditTap` / `routeDoubleTap` route CropEdit to `Ignore`.
- [x] Tap on empty / tap on viewport without drag is a no-op (router → `Ignore`).
- [x] Long-press is global popup per § 5.
- [x] ~~One `Compound` undo entry per gesture via existing `BeginInteraction(RESIZE)` / `FinishInteraction` wraps.~~ **Superseded 2026-06-17 by § 20.9.2:** per-gesture history is suppressed while CropEdit is active; the whole session is one `Compound` entry on Apply/exit. Pinch session still wraps via `infiniteCanvasGestures` hooks (the snapshot is just dropped, not committed).
- [x] Other canvas nodes render but are non-interactive while `CropEdit` is active.
- [x] Hit testing uses `HANDLE_TOUCH_RADIUS_PX / cam.scale` (same screen-space convention as Selection).

#### 20.8.5 Topbar (`ToolControlBar`)
- [x] Aspect-ratio lock toggle (default **on**); persists for the tool session in `EditorState.cropEdit.aspectLocked`. Mutation: `SetCropEditAspectLocked`.
- [x] Source-zoom slider bound to `crop.zoom`, range `[MIN_SOURCE_ZOOM, MAX_SOURCE_ZOOM]` (0.1..4). One `Compound` undo entry per drag session via `BeginInteraction(RESIZE)` / `FinishInteraction` wrapping `SetCropZoom`. Per-frame slider movement does not spam undo.
- [x] Reset button (`ResetCropManual`): `offsetX = offsetY = 0`, `zoom = 1`. One undo entry.
- [x] Cancel button (`CancelCropEdit`): restores the `entrySnapshot` transform + appearance, exits to Selection. Pushes **nothing** to undo history — and **as of § 20.9.2 there are no intermediate session entries to leave** (per-gesture history is suppressed; the session commits once on Apply only).
- [x] Apply button (formerly "Leave crop edit"): exits to Selection without changes.
- [ ] Aspect-ratio preset chips (1:1, 4:3, 16:9) — deferred.
- [ ] Single-finger zoom thumb on the viewport — deferred (pinch covers the gesture-level zoom).

#### 20.8.6 Preview policy
- [x] Selected media re-renders continuously during any `CropEdit` gesture. `_state.update` inline-patches both `_allNodes` and `visibleNodes` per event; no full re-cull per frame.

#### 20.8.7 Selection-tool resize compensation
- [x] `ResizeSelection` action handler now compensates `crop.{offsetX, offsetY}` on media nodes with Manual crop: `offsetX *= factor` (same for Y), `zoom` unchanged. Result: Selection-tool resize on a cropped media keeps the **same content** visible, just at a different size. Frames and non-Manual media pass through unchanged. Distinct from `applyMediaCornerEdgeResize` (CropEdit), which holds the source in world coords instead.

#### 20.8.8 Test updates
- [x] `EditContextMenuItemsTest`: `Edit crop` now asserts `SetActiveTool(CropEdit)` dispatch (was `OpenCropEditor`). Multi-media expectations dropped the `Edit crop (2)` row (action is single-media only).

#### 20.8.9 Docs follow-up
- [x] [editor-tools.md § 4.8](architecture/editor-tools.md#48-cropedit) updated to reflect shipped semantics: pinch override, Cancel button, Selection-tool compensation rule, undo wrap details.
- [x] [media-appearance.md § Implementation status](architecture/media-appearance.md#implementation-status) marks `CropMode.Manual` rendering and in-canvas crop handle as scheduled in the slice.
- [x] § 20.2 line for `CropMode.Manual` updated to reference this slice.
- [x] § 20.5 "Manual crop handle in canvas" updated to reference this slice.

### 20.9 CropEdit stabilization — invariant + session-compound undo

Decided 2026-06-17 (graduated from `to_discuss.md § 15`); see [editor-tools.md § 4.8](architecture/editor-tools.md#48-cropedit) **Persistence + invariant**, **Undo granularity**, **Cancel**. Pre-video hardening pass. Two strands: close the invariant gaps, and collapse the session into one undoable command.

#### 20.9.1 Snapshot-bound invariant — shipped 2026-06-17 (`CanvasViewModel.kt`)
- [x] Strengthened the `CropEdit` invariant to "selection is exactly `entrySnapshot.nodeId`". `enforceCropEditInvariant()` exits via `exitCropEditToSelection(keepChanges=true)` when `snapshot == null || singleSelectedMedia() == null || media.id != snapshot.nodeId`.
- [x] **Gap 1 — View/Present switch:** `SetMode` calls `exitCropEditToSelection(keepChanges=true)` on any leave-Edit, so the tool + snapshot can't survive into View/Present.
- [x] **Gap 2 — different single media:** covered by the strengthened invariant — `SelectNode`/`ToggleNodeSelection` → `recomputeGroupTransform` → `enforceCropEditInvariant`. *Re-anchoring* explicitly deferred.
- [x] **Gap 3 — Undo/Redo:** `Undo`/`Redo` call `exitCropEditToSelection(keepChanges=true)` before navigating history (and `applyCommand` → `recomputeGroupTransform` → `enforce` is a second guard).
- [x] Already-safe cases preserved: delete (`deleteNodesById`), multi-select (`recomputeGroupTransform`), `DeselectAll`, explicit tool-switch (`SetActiveTool` else-branch).

#### 20.9.2 Session-compound undo — shipped 2026-06-17 (`CanvasViewModel.kt`)
- [x] Per-gesture history suppressed while `CropEdit` is active — `commitPendingInteraction()` early-returns (drops the in-flight snapshot) when `activeTool === CropEdit`, covering pan / corner / edge / pinch / slider / Reset. Entry Manual-flip routed through `snapCropModeToManualNoHistory` (no command).
- [x] One `Compound` entry pushed on **Apply / exit** via `finalizeCropEditSession()` (entry-snapshot → current); no-op if unchanged or the node was deleted.
- [x] `CancelCropEdit` restores the entry snapshot then `exitCropEditToSelection(keepChanges=false)` — pushes nothing; stack identical before-entry and after-Cancel.
- [x] Updated the `CancelCropEdit` comment in `CanvasViewModel.kt`; § 20.8.5 / § 20.8.4 lines refreshed below.

#### 20.9.3 Tests
- [x] **Invariant predicate** extracted to a pure function `cropEditInvariantBroken` (`feature/canvas/editor/CropEditInvariant.kt`); `enforceCropEditInvariant()` delegates to it. Unit-tested in `CropEditInvariantTest` — holds for matching node; breaks on different-media / empty-or-multi / missing-snapshot; never breaks outside CropEdit. Pure-function approach matches the repo's test convention (no VM/coroutine harness needed).
- [ ] **Stateful undo/exit flows — deferred.** A multi-gesture session = one undo entry on Apply; Cancel leaves the stack unchanged; one Undo after Apply restores pre-entry state; View/Present + Undo/Redo exits. These need a `CanvasViewModel` harness (no harness exists; VM uses hardcoded `Dispatchers.Default`/`IO` + `viewModelScope`, so a dispatcher-injection seam + fakes for `Context`/`MediaRepository`/`HistoryRepository` are required first). Covered by manual QA for now; revisit if VM-level testing is invested in.

#### 20.9.4 Docs follow-up
- [x] [editor-tools.md § 4.8](architecture/editor-tools.md#48-cropedit) **Persistence + invariant** / **Undo granularity** / **Cancel** rewritten for the snapshot-bound invariant + session-compound model (2026-06-17).
- [x] § 20.8.5 Cancel line and § 20.8.4 undo line updated to point at the session-compound behavior (below).

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

## 23. Frame Chrome

Editor / viewer hint layer that draws on the *edge* of each frame (outline, glow, label tab) without altering the album's visual content. Distinct from [`FrameAppearance`](architecture/appearance.md) (which is album content). Mode-dependent defaults + transient session overrides, resolved per frame by a pure pick-one resolver.

See [architecture/frame-chrome.md](architecture/frame-chrome.md) for the full design, vocabulary, resolver rules, and open questions.

**Depends on §22** (`AlbumPresentationProfile` — chrome defaults nest under `profile.frameChrome`).

### 23.1 Domain model
- [ ] `FrameChromeStyle` closed enum — `Hidden`, `CornersOnly`, `SubtleOutline`, `SoftGlow`, `LabelTab`, `FullOutline`, `DebugBounds`
- [ ] `FrameChromeDefaults(perMode: Map<CanvasInteractionMode, FrameChromeStyle>)` with `defaultPerMode` companion
- [ ] `ChromeOverrideTarget` enum — `ALL`, `SELECTED`, `CURRENT` (MVP only; `HOVERED` / `RELATED` / `NAV_TARGET` deferred — see frame-chrome.md § 8)
- [ ] `ChromeOverrideLifetime` sealed — `Timed(durationMillis)`, `WhilePanelOpen`, `WhileGestureActive`, `UntilCancelled`
- [ ] `FrameChromeOverride(target, style, lifetime, reason: FrameOverrideReason?)`
- [ ] `FrameOverrideReason` enum — diagnostic only; resolver MUST NOT branch on it
- [ ] All `@Serializable` where they live in the album (defaults only); overrides are not serialized

### 23.2 Profile integration
- [ ] Add `frameChrome: FrameChromeDefaults = FrameChromeDefaults()` to `AlbumPresentationProfile`
- [ ] `SceneGraphSerializer` migration: missing key is fine (default fallback), no rewrite path needed
- [ ] Serializer round-trip test for `frameChrome`

### 23.3 Resolver
- [ ] `FrameChromeResolverUseCase` in `domain/usecase/` — pure function `(frame, mode, selection, currentFrameId, profile, overrides) → FrameChromeStyle`
- [ ] MVP specificity order: `CURRENT > SELECTED > ALL`; mode default as implicit base layer
- [ ] Most-recent-pushed wins within same specificity bucket
- [ ] No numeric priority; no `reason` branching
- [ ] Unit tests: empty stack → mode default; per-target specificity; tiebreaker via push order; missing mode → fallback default

### 23.4 Session state
- [ ] `CanvasState.editor.chromeOverrides: List<FrameChromeOverride>` (push-ordered, oldest first). Add to `EditorState` per the editor-session-state rule — chrome overrides drive canvas rendering, like `contextAnchorNodeId`.
- [ ] `CanvasAction.PushChromeOverride` / `RemoveChromeOverride` (or producer-scoped equivalents)
- [ ] Lifetime tracker — coroutine in `CanvasViewModel` decrements `Timed` overrides, listens to gesture-end / panel-close, prunes expired

### 23.5 Render layer
- [ ] `FrameChromeOverlay` composable in `feature/canvas/view/`
- [ ] Draws per-frame chrome above `LayeredFrameOverlay` and above `SelectionOverlay` handles, inside the camera `graphicsLayer`
- [ ] Strokes scale by `1/camera.scale` (same pattern as guidelines / selection handles)
- [ ] All seven `FrameChromeStyle` cases implemented
- [ ] Chrome paint clipped to edge / outside / label region only — never paints inside the frame content rect (see frame-chrome.md § 1)

### 23.6 Migrate `Frame.color` outline
- [ ] Stop drawing the colored outline in `FullFrameRenderer` / `SimplifiedFrameRenderer`
- [ ] `FrameChromeOverlay` reads `frame.color` as the chrome paint color for `FullOutline` / `SubtleOutline` / `CornersOnly` / `SoftGlow` / `LabelTab`
- [ ] Verify behavior-preserving when Edit-mode default resolves to `FullOutline` (every existing frame still renders its colored outline) — screenshot diff

### 23.7 First producer
- [ ] "Show frame bounds" debug toggle in TopBar HUD menu → pushes `(ALL, DebugBounds, UntilCancelled)` — end-to-end validation

### 23.8 Settings UI
- [ ] Per-mode chrome picker in the album-settings surface (depends on §22.6 album-settings sheet)
- [ ] `CanvasAction.SetAlbumPresentationProfile` already covers the mutation; just extend the editor to show `frameChrome.perMode`

### 23.9 Additional producers (land as host UIs ship)
- [ ] FocusNode-completion glow — `(CURRENT, SoftGlow, Timed(~600ms))` in View / Present modes
- [ ] Frame-list panel highlights — `(SELECTED, SubtleOutline, WhilePanelOpen)` on row interactions
- [ ] Long-press-and-hold "show all frames" gesture — `(ALL, SubtleOutline, WhileGestureActive)`

### Deferred (post-MVP)
- `HOVERED` target — needs a stylus / mouse / external input producer
- `RELATED` target — needs a concrete producer definition (frame-list, minimap, navigation edges, inverse membership)
- `NAV_TARGET` target — lands with the navigation flash producer
- Per-frame `FrameChromePerFrameOverride` on `CanvasNode.Frame`
- Animation curves carried by the override (per-style ownership is MVP)
- `LabelTab` placement collision avoidance + zoom-based culling
- Process-death restoration semantics (producers re-push on resume; no recovery in session state)

### Out of scope
- Chrome that paints inside the frame content area — that's appearance.
- Chrome as published-album output — chrome never reaches the exported album.
- Merge-style resolution (closed-enum pick-one is the rule).

---

## 24. Editor Tools Framework

> Source: `docs/architecture/editor-tools.md` (decided 2026-05-24, fully locked 2026-06-03 once `MaskEdit` joined the others). Three-axis interaction model (`EditorMode` × `ActiveTool` × Global navigation) + per-tool gesture maps for all seven in-scope tools. Implementation of each tool is still gated on its data-model dependency landing.

Framework itself is small. Per-tool implementations are each gated on the data-model concept the tool depends on.

### 24.0 `EditorState` extraction — **shipped 2026-06-02**

Foundational refactor that lands before any tool slice. See `editor-tools.md § 7.1` and `to_discuss.md § 11` graduation.

- [x] Declare `EditorState` + `EditorTool` under `feature/canvas/editor/` (editor interaction concepts, not persisted album content — kept out of `domain/model/`).
- [x] Nest `mode`, `selectedNodeIds`, `selectionRect`, `groupSelectionTransform`, `frameEditOptions`, `contextAnchorNodeId`, and the new `activeTool` under `CanvasState.editor`.
- [x] Add `CanvasAction.SetActiveTool(tool)` + ViewModel handler. Selection persists across tool switches by construction.
- [x] Migrate every call site (`CanvasViewModel`, `CanvasScreen`, `CanvasScaffold`) to nested access. Helper `updateEditor { ... }` for editor-only updates.
- [x] Behavior-preserving: build + `:app:testDebugUnitTest` pass; existing `EditContextMenuItemsTest` works unchanged because it operates on `SelectionContext`, not `CanvasState`.
- [x] UI-surface state (`mediaApprEditing`, `frameBgEditing`, `contextMenuRequest`, `showAddSheet`, `showFrameList`, `showPanelConfig`, `showAlbumSettings`) deliberately remains local to `CanvasScaffold` — phone uses bottom sheets, tablet later uses docked panels; editor-session state must not couple to that.

`SelectionState` extraction deferred until `SelectionTool` accumulates substantial own state (Rectangle / Lasso modes, selection rules, in-progress lasso path, hover, additive flag, anchor-level selection). Today selection is flat on `EditorState`.

### 24.1 Type declarations
- [x] `EditorTool` sealed interface in `feature/canvas/editor/EditorTool.kt`. Initial variants: `Selection`, `Eraser`. Other variants (`FreeDraw`, `Shape`, `Text`, `VectorEdit`, `MaskEdit`) are added one at a time as each tool actually ships — disabled toolbar slots are not predeclared. (Shipped 2026-06-02.)

### 24.2 State
- [x] `activeTool: EditorTool = EditorTool.Selection` added to `EditorState`. (Shipped 2026-06-02.)
- [x] `CanvasAction.SetActiveTool(tool)` + ViewModel handler. (Shipped 2026-06-02.)
- [ ] Confirm `editorMode` plumbing. Today's `CanvasInteractionMode` covers View / Edit / Presentation in the type but the UI toggle only cycles View ↔ Edit. Rename to `EditorMode` only if it reduces confusion.

### 24.3 Semantic `GestureRouter` (per `editor-tools.md § 7.2`)

Two-slice landing. Slice A introduces the routing layer with behavior preserved where possible; Slice B applies the locked per-tool semantics on top.

**Slice A — router foundation.**
- [x] Add `GestureRouter` + intent / decision types under `feature/canvas/editor/gestures/`. Pure, testable, depends only on `CanvasInteractionMode` + `EditorTool` + primitive ids (no Compose / ViewModel / hit-test).
- [x] Cover routes: tap, double-tap, long-press (press + lift), selected-node transform start, camera transform start.
- [x] All recognized gestures route through `GestureRouter`. **Long-press is owned by global popup policy and is never delegated to the active tool** — the router answers "open popup / fall-through / suppress" by mode + hit-list, not by tool identity. Tools influence popup *contents* (per `editor-tools.md § 5`), never whether long-press fires.
- [x] Wire `CanvasScreen` through the router: `tapAndLongPressGestures`, `nodeInteractionGestures`, and `infiniteCanvasGestures` callbacks contain no inline mode / tool branching. Detectors themselves stay unchanged — touch slop, multi-finger arbitration, and event-consumption mechanics are detector concerns.
- [x] **Mode-aware transform gating.** When `routeSelectedNodeTransformStart` returns `Block`, pass `selectedNodeIds = emptySet()` (named `transformableSelectionIds` at the call site) to `nodeInteractionGestures` so its `pointerInput` short-circuits and events flow through. Selection persists in the model; only its interactivity is gated. Explicit correctness change (not strictly behavior-preserving): `Edit + Eraser`, `View`, and `Presentation` all block selected-node transform even when stored `activeTool == Selection`.
- [x] **Eraser long-press on empty suppressed** (no rect-select fall-through). Rect-select stays gated to `Selection` until § 24.4 migrates it to drag-on-empty.
- [x] Unit tests cover the routing matrix: Edit + Selection, Edit + Eraser, View, Presentation, popup-open suppression, transform gating under each mode + tool.
- [x] Update `editor-tools.md § 7.2` to a router-first description; update implementation order in § 10.

**Slice B — apply locked per-tool tap + double-tap semantics.**
- [x] Edit-mode tap dispatch: `Selection` keeps select / deselect; `Eraser` is a no-op until § 24.8 wires Object-mode delete. (Already at this target in Slice A's router — the tool-aware branch was implemented from the start; Slice B is the audit checkpoint.)
- [x] Edit-mode double-tap dispatch: no-op for `Selection` / `Eraser` per the locked per-tool gesture maps in `editor-tools.md § 4`. Reset-camera affordance preserved in `View` / `Presentation`.
- [x] Router's `routeDoubleTap` flipped from `ResetCamera` to `Ignore` in Edit; `CanvasScreen` wiring unchanged.
- [x] Tests updated: Edit + Selection / Edit + Eraser double-tap → `Ignore`; View / Presentation → `ResetCamera`.

### 24.4 Drag-on-empty migration (per `editor-tools.md § 7.3`) — **shipped 2026-06-04**

Final migration — no transitional long-press path retained.

- [x] Dedicated `selectionMarqueeGestures` detector under `feature/canvas/gestures/`. Single-finger drag-on-empty past touch slop initiates marquee selection; second-finger handoff cleanly yields to camera nav (both pre-slop and mid-marquee).
- [x] Long-press-then-drag rect-select removed from `tapAndLongPressGestures` (drag callbacks + drag-fall-through loop deleted). `onLongPress` lost its `Boolean` return — detector always consumes the rest of the gesture.
- [x] `LongPressRoute.FallThroughToRectSelectDrag` removed from the router. `routeLongPress` on empty in Edit returns `Suppress` for every tool. `routeLongPressLift` dropped the now-unreachable variant.
- [x] New `MarqueeStartRoute` + `routeMarqueeStart(ctx)` in the router. Edit + Selection + popup closed → `Start`; + popup open → `DismissContextMenuOnly` (dismiss without creating a rectangle on the same gesture); Eraser / View / Presentation → `Suppress`.
- [x] Detector gated by `isMarqueeEnabled(state)` — stable across popup state so an in-progress marquee survives the popup closing.
- [x] Long-press on node / stacked nodes unchanged (still global popup policy through `GestureRouter.routeLongPress`).
- [x] Router tests cover the marquee matrix (Edit+Selection closed/open, Eraser, View, Presentation, hasSelection orthogonality) and the universal empty-long-press `Suppress`.
- [x] `selection.md § 2` / § 5 / § 7 updated: marquee row added, long-press-on-empty marked no-op, future-model note partially graduated (item (a) shipped here; (b) View pan shipped in § 24.5).

### 24.5 View-mode single-finger pan (per `editor-tools.md § 7.4`) — **shipped 2026-06-05**

- [x] Dedicated `viewModePanGestures` modifier (Layer 2d in the gesture stack) — chosen over extending Layer 3 to match the per-mode/per-tool detector pattern already used by `selectionMarqueeGestures` + `eraserScrubGestures`. `infiniteCanvasGestures` (Layer 3) stays multi-finger-only and mode-agnostic.
- [x] Router: `ViewPanRoute` + `routeViewPanStart(ctx)`. `Allow` in View + popup closed; `DismissContextMenuAndProceed` in View + popup open (continuous-gesture dismissal rule); `Suppress` in Edit (single-finger reserved for active tool) and Presentation (separate gesture vocabulary).
- [x] Detector callback shape: `onPanStart` (fires once on slop-crossed; caller consults router for popup-open dismissal) + `onPan(dx, dy)` (per-event movement deltas). Camera dispatches `viewModel.onGesture(centroid = Offset.Zero, pan, zoom = 1, rotationDelta = 0)` — pure translation.
- [x] Router tests cover the View / Edit / Presentation matrix plus popup-open + stored-non-Selection-tool-in-View edge cases.
- [x] `selection.md § 5` (gesture stack: Layer 2d added) + `§ 7` (mode interaction table extended for Edit/Selection, Edit/Eraser, View/Presentation columns including Layer 2c + 2d). `editor-tools.md § 7.4` marked shipped. Future-model note in `selection.md` top callout updated — item (b) now shipped.

### 24.6 `ToolControlBar` — renders `ToolControlSurface`

> Source: `docs/architecture/editor-surfaces.md § 4`. Horizontal bar below the global top bar; left = active tool selector, remainder = primary controls of the active tool. Same on phone and tablet — no separate phone "floating tool switcher" route. Hidden in `View` / `Present`.

**First slice — shipped 2026-06-04** alongside Object-mode `Eraser` (§ 24.8).
- [x] **Lazy ship.** Trigger met: Object-mode `Eraser` is the second functional tool.
- [x] Single `DropdownMenu` tool selector — button shows active tool with a `▾`; menu lists `Select` / `Erase`. Eraser's "Object" mode chip omitted while there's only one mode (returns with Vector-partial, as a secondary control to the right of the dropdown).
- [x] Show only implemented tools. No disabled placeholder slots for `FreeDraw` / `Shape` / `Text`.
- [x] Active tool is the only chip whose `selected = true`; destructive Eraser is obviously active when chosen.
- [x] Visibility gated on `editor.mode == Edit` at the `CanvasScaffold` call site, so View / Presentation reclaim the pixels.
- [x] `VectorEdit` / `MaskEdit` are **context-gated** in the selector — not rendered as permanent rows. Discoverability UX deferred to first-use feedback (`editor-surfaces.md § 6`).

### 24.7 Present-mode trigger
- [ ] View/Edit toggle stays as today. Present is reached via a separate fullscreen / play action — location TBD with the Present surface (out of scope for this section).

### 24.8 Per-tool implementations

Each lands as its own slice once its data-model dependency is ready. Order per `editor-tools.md § 10`:

- [ ] **`FreeDraw` (`editor-tools.md § 4.2`)** — depends on `StrokeNode` (new data-model type), sample-stream input pipeline, smoothed-bezier render-path cache.
- [ ] **`Shape` (§ 4.3)** — depends on `ShapeNode` (new data-model type), rubber-band drag input, primitive picker topbar.
- [ ] **`Text` (§ 4.4)** — depends on `TextNode` (verify exists in data-model), overlay `BasicTextField` integration, IME gesture-stack deferral.
- [ ] **`VectorEdit` (§ 4.5)** — depends on vector-node existence (from `FreeDraw` / `Shape`), per-tool `VectorEditState.selectedAnchors`, anchor / curve screen-space hit testing, node-type editor topbar.
- [x] **`Eraser` (§ 4.6) Object mode — shipped 2026-06-04.** Router additions: `TapRoute.EraserDeleteNode(nodeId)` and `EraserScrubStartRoute` + `routeEraserScrubStart`. Single delete path: `CanvasAction.DeleteNodes(ids)` (one `CanvasCommand` per call, prunes deleted ids from selection in place). Tap-on-node dispatches `DeleteNodes(setOf(id))`. Scrub uses the new `eraserScrubGestures` detector (Layer 2c): on slop, `onScrubStart` consults the router (Start / DismissContextMenuOnly / Suppress) and sets a per-gesture `allowed` flag; on each new crossed node, `onCrossNode(id)` dispatches `DeleteNodes(setOf(id))` if `allowed`. The detector dedupes by id so re-crossing is a no-op. **Undo granularity is per-node** — an N-node scrub commits N entries, symmetric with tap-on-node. The earlier "one gesture, one Compound undo" rule was relaxed when Object-mode shipped to drop the Begin/Add/Finish protocol, the orphan-pending recovery, and ~80 LOC of ViewModel buffer state. Frame interaction: deletes the frame only (members stay) — falls out for free since `DeleteNodes` operates on explicit ids. Cascading frame-with-contents is a popup action (not a topbar toggle).
- [ ] **`Eraser` (§ 4.6) Vector partial mode** — depends on `VectorEdit`'s path-splitting math; brush-corridor cut + boundary anchor insertion.
- [ ] **`MaskEdit` (§ 4.7)** — gesture map locked 2026-06-03. Implementation depends on `MaskNode` data-model landing per `appearance.md § 12.8`. Creation flow: selection-aware (empty → rubber-band a new mask via the topbar primitive picker; one `MaskNode` → edit mode; other selection states → disabled slot). Primitive masks (Rect / Ellipse) edit via corner/edge handles; path masks (Path / Free) edit via per-anchor + per-handle gestures mirroring `VectorEdit`. Preview is commit-only — masked siblings re-clip on gesture lift, not during drag.

### Deferred (post-MVP)
- Raster partial erase (`editor-tools.md § 4.6` topbar post-MVP section) — depends on `MediaAppearance.alphaMask` per `appearance.md § 12` becoming load-bearing.
- Lasso geometry beyond bounding-box overlap (true per-path intersection).
- "Restore last tool" preference across app restarts (`editor-tools.md § 6`).
- `SelectionState` extraction inside `EditorState` (`editor-tools.md § 7.1` / `to_discuss.md § 11` graduation note) — defer until `SelectionTool` accumulates substantial own state (Rectangle / Lasso modes, selection rules, in-progress lasso path, hover, additive flag, anchor-level selection).

### Out of scope
- Single-finger pan fallback in Edit mode (predictability over convenience, per `editor-tools.md § 9`).
- Present as a third position on the View/Edit toggle.
- Per-tool camera control (camera always belongs to Layer 3).

---

## 25. Editor Action Catalog

> **Decided 2026-06-02.** Replace stringly-typed action dispatch (`onAction(label: String) → when(label)`) with a typed `EditorAction` model. Foundational refactor that precedes § 15.5 (`ContextualActionBar` removal + inline rows) — both bar and popup will consume the catalog.

### 25.1 Motivation

Today's bar dispatches actions via `(String) -> Unit`:
- `ContextualActionBar.kt` defines `ActionItem(icon, label)` lists, calls `onAction(label)` per tap.
- `CanvasScaffold.kt` matches `when (label) { "Delete" -> ...; "ToFront" -> ... }` to `CanvasAction` variants.
- Visibility is plumbed as parallel booleans (`hasSelection`, `showBackgroundAction`, `showMediaAppearanceAction`, `showZOrderActions`, `showFrameMembershipActions`, `showAutoAction`, plus computed `pinDetachEnabled` + `anyOverrideExists`).
- The popup (`buildEditContextMenuItems`) duplicates the same vocabulary via a separate path.

This costs: magic strings (typos pass compile), divergence between bar and popup, fan-out for adding a single action (definition + visibility flag + when branch + plumbing), and a hard cap on growth before § 10's category grouping lands.

### 25.2 Model

`feature/canvas/actions/EditorAction.kt`:

```kotlin
sealed interface EditorAction {
    val id: String                    // stable id, used as list key
    val label: String
    val icon: String?                 // null = text-only menu item; non-null = renders in inline row
    val category: ActionCategory
    fun isVisible(ctx: SelectionContext): Boolean = true
    fun isEnabled(ctx: SelectionContext): Boolean = true
    /** null return = caller handles fallback (e.g. opening FrameTargetPickerDialog). */
    fun dispatch(ctx: SelectionContext): CanvasAction?
}

enum class ActionCategory { Edit, Navigation, Transform, ZOrder, Membership, Lifecycle, SelectionMeta }

data class SelectionContext(
    val selectedNodeIds: Set<String>,
    val selectedFrames: List<CanvasNode.Frame>,
    val anchorNodeId: String?,
    val singleSelectedFrame: CanvasNode.Frame?,
    val singleSelectedMedia: CanvasNode.Media?,
    val pinDetachEnabled: Boolean,
    val anyOverrideExists: Boolean,
)
```

`feature/canvas/actions/EditorActionCatalog.kt`:

```kotlin
object EditorActionCatalog {
    val all: List<EditorAction> = listOf(
        DeleteSelection, DuplicateSelection, ClearSelection,
        BringToFront, BringForward, SendBackward, SendToBack,
        PinToFrame, DetachFromFrame, ClearOverrides,
        EditFrameBackground, EditMediaAppearance, NavigateToFrame,
        RemoveFromSelection, EditThisOnly,
    )
    fun visibleByCategory(ctx: SelectionContext): Map<ActionCategory, List<EditorAction>> =
        all.filter { it.isVisible(ctx) }.groupBy { it.category }
}
```

Each concrete action is a `data object` implementing `EditorAction`. Visibility / enable / dispatch are co-located with definition.

### 25.3 Decisions (recap from 2026-06-02 discussion)

- **Sealed interface** — exhaustive compile-time check at the catalog level; data-object boilerplate is the cost.
- **`SelectionContext` as a single bundle** — adding a field doesn't break every action signature.
- **`dispatch` returns `CanvasAction?`** — single dispatch for MVP; switch to `List<CanvasAction>` if compound actions (e.g. Align) need it later.
- **Lives in `feature/canvas/actions/`** — close to `CanvasAction` (its dispatch target), not in `domain/` (UI-facing concern).
- **Lands before § 15.5** — catalog ships first, bar + popup both migrate to consume it, then § 15.5 is purely UI deletion.

### 25.4 Migration tasks — **shipped 2026-06-02**
- [x] Add `EditorAction` sealed interface, `ActionCategory`, `EditorActionEffect`, `SelectionContext`, `EditorActionCatalog` under `feature/canvas/actions/`.
- [x] Implement every action as a `data object` — visibility / enable / effect co-located with the action definition.
- [x] Derive `SelectionContext` in `CanvasScaffold.kt` from `CanvasState` + the existing `singleSelectedFrame` / `singleSelectedMedia` / `selectedFramesInOrder` / `pinDetachEnabled` / `anyOverrideExists` projections. (Did *not* move the projections out of the composable — they live as `remember` cells next to the popup state, which is the right scope. After § 11 `EditorState` extraction landed 2026-06-02, the read is now `canvasState.editor.selectedNodeIds` / `editorState.contextAnchorNodeId` — pure-derivation extraction would only matter when popup state lifts into `EditorState`.)
- [x] Migrate `ContextualActionBar` to render from `EditorActionCatalog.visibleByCategory(ctx)`. (Subsequently deleted in §15.5; for one commit it consumed the catalog directly.)
- [x] Migrate `buildEditContextMenuItems` to render from the same catalog. Per-concept popup entries (`Edit appearance`, etc.) are catalog entries that produce `EditorActionEffect.OpenMediaAppearance` / `OpenFrameBackground`; anchor-scoped items (`Remove this from selection`, `Edit this only`) stay out of the catalog because of their `keepOpenOnClick` + `onAnchorRemoved` semantics.
- [x] Drop the stringly-typed `onAction(label)` indirection. Tap → `action.effect(ctx)?.let(runEditorActionEffect)`; a single sealed-type `when` in `CanvasScaffold.runEditorActionEffect` handles every effect kind.
- [x] No behavior change at this step — verified via `EditContextMenuItemsTest`. (Bar order regression: catalog declaration order put Duplicate before Delete in the bar, where the old bar had Delete first; accepted because §15.5 deletes the bar immediately afterward.)
- **Effect-type model.** The pure-`CanvasAction?` dispatch proposed in § 25.2 wasn't enough — Pin/Detach/Auto need to flow through `dispatchFrameMembership`, and Edit appearance / Edit frame background mutate local UI state (`mediaApprEditing`, `frameBgEditing`). Replaced with a sealed `EditorActionEffect` (`Dispatch(CanvasAction)`, `FrameMembership(intent)`, `OpenMediaAppearance`, `OpenFrameBackground`, `OpenAddSheet`). Effects are a one-place `when` in the host. Same single-source-of-truth wins; richer than originally planned.
- **Context-aware labels.** `label` became a function of `SelectionContext` instead of a property, so `Duplicate` / `Delete` can render as `Duplicate selection` / `Delete selection` for group selections. Most actions ignore the parameter.

### 25.5 Dependencies
- Precondition for [§ 15.5](#155-contextualactionbar-removal--inline-rows--shipped-2026-06-02). Land § 25 first; § 15.5 then becomes a pure UI deletion + two row composables consuming `Category.ZOrder` / `Category.Membership`.
- Connects to `EditorState` (extracted 2026-06-02 per `editor-tools.md § 7.1`) — `SelectionContext` is the action-dispatch view of editor state, reading `editor.selectedNodeIds` + `editor.contextAnchorNodeId` from the nested container.

---

## 26. Cloud Sync (deferred)

> Source of truth: [cloud-sync.md](architecture/cloud-sync.md). **Decided 2026-06-03, implementation deferred.** Not part of the current `EditorState` / `ActiveTool` / `Eraser` work — do not start any slice below until the active editor-tool track lands.

Local-first automatic snapshot sync with conflict-safe editing. Slices are ordered foundation → first connected provider; none are scheduled.

### 26.1 Revision lineage on local albums (foundation)
- [ ] Add `headRevisionId: RevisionId` + `parentRevisionId: RevisionId?` to the album / scene-graph root (final field placement decided when the slice ships).
- [ ] Mint a new `RevisionId` at every stable local commit boundary (existing `FinishInteraction` snapshot — see [undo-redo.md](architecture/undo-redo.md)).
- [ ] Persist `(headRevisionId, parentRevisionId)` in the JSON scene-graph root; on first read of a legacy album, seed `headRevisionId` and `parentRevisionId = null`.
- [ ] No remote code in this slice — lineage stands alone as the local versioning boundary.

### 26.2 `RemoteBinding` model (foundation, provider-agnostic)
- [ ] Sealed `RemoteBinding` keyed by stable `AlbumId`; zero-or-one per album.
- [ ] Owns: `lastSyncedRevisionId: RevisionId?`, pending-sync queue marker, provider-specific fields.
- [ ] New Room table (e.g. `remote_bindings`) — separate from `albums`; absence = local-only.
- [ ] **Explicitly do not** add `storageMode: Local | GoogleDrive` to `Album`. Cloud-connection is the *presence* of a binding, not a field on the album.

### 26.3 Sync engine (provider-agnostic core)
- [ ] Define stable-commit → sync trigger wiring on top of `FinishInteraction`.
- [ ] Debounce + coalesce successive commits into a single upload of the latest local head; at most one in-flight sync per album.
- [ ] Retry on network return (collapse older queued attempts).
- [ ] Manual `Sync now` action — same path as automatic.
- [ ] Conflict-detection logic over revision lineage (local-ahead / remote-ahead / divergence); no timestamp inputs.
- [ ] Conflict resolution: preserve local branch as a separate conflict-copy album (`<name> — local conflict copy`), restore primary from remote head. No auto-merge, no overwrite.

### 26.4 Open-flow gating (UI)
- [ ] Open synced album → View mode immediately; remote head-revision check in background.
- [ ] Edit mode disabled until the check resolves; banner explains the gate.
- [ ] Offline-open path: `Edit offline anyway` action with explicit warning that this may later produce a conflict copy.
- [ ] Hook into existing Edit/View split (§12) — additive, not a replacement.

### 26.5 Google Drive provider (first concrete `RemoteBinding`)
- [ ] OAuth / account flow + token storage (out of scope of model decisions in [cloud-sync.md](architecture/cloud-sync.md)).
- [ ] `RemoteBinding.GoogleDrive(folderId, accountId, lastSyncedRevisionId)` variant + repository impl.
- [ ] Per-album = per-Drive-folder mapping (implementation detail of this variant).
- [ ] Atomic head-pointer update on the remote (compare-and-swap semantics).
- [ ] Album-settings UI: connect / disconnect / `Sync now` / surface last-sync revision.

### 26.6 Encryption-readiness check (no key UX in this slice)
- [ ] Verify the sync engine treats album payloads as opaque blobs — no provider-side merge, no provider-side thumbnailing, no plaintext indexing.
- [ ] Document the server-side capability ceiling (store/fetch blob, list revisions, atomic CAS on head) so future zero-knowledge encryption can land additively.
- [ ] Actual key management UX and encryption-at-rest of remote blobs are explicitly out of scope here.

### 26.7 Explicit non-goals (do not pull into any slice above)
- [ ] No real-time collaboration / live presence / live cursors.
- [ ] No automatic three-way merge over the scene graph.
- [ ] No CRDT-backed scene graph (tracked separately in §18 Future).
- [ ] No cross-album linking through the cloud.
- [ ] No web viewer / share-via-link / server-rendered thumbnails (incompatible with the encryption-readiness constraint).

---

## 27. Video MVP

Design decided 2026-06-17 (graduated from `to_discuss.md § 13`) → [video.md](architecture/video.md) is the source of truth. Goal: video as playable "living media" on the canvas, behaving like an image node for all transform/selection.

### Implementation plan (delivery order — decided 2026-06-17)

The § 27.x checklists below are grouped into delivery slices, **re-ordered to front-load the poster and a single player** so something ships on a working base and the hardest part (concurrency) lands last. Grounding notes reference the code that each slice touches.

- **Slice A — Poster only, no playback.** § 27.1 + § 27.2 + § 27.3. Add Media3 + `coil-video` deps; register `VideoFrameDecoder.Factory()` by making `ZoomAlbumApp` a `SingletonImageLoader.Factory`; video picker + `MediaMetadataRetriever` dimensions; parameterize `CanvasNodeFactory.createMedia` with `mediaType`; branch `MediaRenderer` (`CanvasRenderer.kt`) on `mediaType == VIDEO` → same Coil path + a `videoFrameMillis` request param + play-icon overlay. *Outcome: drop a video, see poster + play badge, transform it like an image. No ExoPlayer.*
- **Slice B — Single-player View playback.** Subset of § 27.6 + § 27.4 (View half). `CanvasScaffold`-level playback holder keyed by `nodeId` (one `ExoPlayer` first, not a pool); `AndroidView` ExoPlayer surface mounted inside the camera Box in `CanvasScreen.kt` for the one playing node at `RenderDetail.Full`; new `GestureRouter` tap route — View-mode tap on a video plays/pauses instead of `FocusNode`. *Outcome: tap-to-play in View; proves host + gesture deferral + transform compat.*
- **Slice C — Bounded pool + concurrency.** § 27.5 + rest of § 27.6. Device decoder probe → *K* (clamp to a conservative ceiling, e.g. 2–3, for MVP); pool of *K* `ExoPlayer`s; relevance ranking; eviction; poster fallback.
- **Slice D — Edit-mode play affordance.** § 27.4 (Edit half). Shipped as a context-menu `Play / Pause` item (`PlayVideoAction`) instead of a node-local poster button — see § 27.4.
- **Slice E — Tests.** § 27.7.

**Status: COMPLETE — Slices A–E + § 27.9 live appearance, implemented + verified on-device 2026-06-20.** Edit play landed as a menu item *and* uniform double-tap (Slice D, revised § 27.4). Posters render through the shared surface chrome (§ 27.6 masked-poster fix). All § 27 checklist items done; § 27.8 out-of-scope items remain deferred by design.

### 27.1 Dependencies
- [x] Add Media3 ExoPlayer (playback) to the version catalog + `app/build.gradle.kts`. (`media3 = "1.5.1"`, `media3-exoplayer` + `media3-ui`; wired, used from Slice B.)
- [x] Add Coil `coil-video` decoder (poster-frame extraction) and register it with the existing Coil `ImageLoader`. (`ZoomAlbumApp : SingletonImageLoader.Factory` adds `VideoFrameDecoder.Factory()`.)

### 27.2 Video path activation (no model change)
- [x] Branch media rendering on `mediaType == MediaType.VIDEO`; keep `mediaRefId` a raw URI (no `MediaAsset`, no migration — see [video.md § 2](architecture/video.md#2-model-bridge--no-migration)). (`MediaRenderer`/`FullMediaRenderer` in `CanvasRenderer.kt`.)
- [x] Place a video on the canvas the same way as an image (add-content path accepts video URIs; `mediaType` set to `VIDEO`). (`videoPicker` (VideoOnly) in `CanvasScaffold.kt` → `addMedia(uri, VIDEO)`; `decodeVideoDimensions` via `MediaMetadataRetriever`; `createMedia(mediaType=…)`.)
- [x] Scheme-safe `mediaRefId` → `Uri` for playback (`mediaRefToUri` in `playback/MediaUri.kt`): `content://` / `file://` / `http(s)://` preserved via `Uri.parse`; bare paths via `Uri.fromFile`. Fixes the latent bug where `Uri.fromFile(File(ref))` would corrupt an already-formed URI. Decision logic (`mediaRefHasScheme`) unit-tested in `MediaUriTest`; the `Uri` round-trip is in the § 27.10 QA list (no Robolectric in this module).

### 27.3 Poster (zero storage)
- [x] Lazy `coil-video` frame extraction, no import step / stored poster / model field. **Rendered through `VideoPosterSurface`, not the shared image renderer** — loaded as an explicit `ARGB_8888` bitmap via `rememberVideoPosterBitmap` (a `coil-video` frame in `FullMediaRenderer`'s offscreen masks as opaque black; see § 27.6). [video.md § 3](architecture/video.md#3-poster--thumbnail--zero-storage).
- [x] Subtle play-icon badge on the poster. (`drawPlayBadge` inside `VideoPosterSurface`, inside the mask so it doesn't paint over masked-out regions. While playing there's no badge — the live surface replaces the poster.)
- [x] LOD: poster participates in `RenderDetail` (`Stub`/`Preview`/`Simplified` = placeholder via `MediaRenderer`; `Full` = `VideoPosterSurface`).

### 27.4 Playback affordance — uniform double-tap (revised 2026-06-19)

Superseded the original "View single-tap = play" design with a **uniform double-tap** across modes (see [video.md § 4](architecture/video.md#4-playback-affordance--uniform-double-tap)).
- [x] Single-tap = mode default everywhere (View/Present = focus, Edit = select); no video special-case on single-tap.
- [x] Double-tap on a video → play/pause in View, Presentation, and Edit (Selection only). `DoubleTapRoute.PlayPauseVideo` + `hitIsVideo` flag on `routeDoubleTap`; `CanvasScreen.onDoubleTap` hit-tests and toggles `VideoPlaybackController`. Eraser/CropEdit keep their own double-tap.
- [x] **Pause/resume in place (2026-06-20):** `togglePlayback` now pauses an assigned video (`playWhenReady = false`, keeps position + frozen frame) and resumes from there on the next double-tap — instead of stop+restart-from-0. Tracked via `pausedNodeIds` (Compose state) so it survives `reconcile`; a play badge is drawn over the paused frame so it reads as paused, not a still. A video never *stops* via gesture now — it leaves only by eviction (off-screen / pool pressure) or node delete. **Caveat:** an evicted-while-paused video loses position (poster returns); reacquiring shows it paused at frame 0.
- [x] Edit context-menu `Play / Pause` (`PlayVideoAction` → `EditorActionEffect.ToggleVideoPlayback`) — discoverable menu alternative to the gesture.
- [x] No accidental playback: single-tap never plays; double-tap on a non-video keeps the mode default (View = reset camera, Edit = no-op).

### 27.5 Bounded player pool (simultaneous playback)
- [x] Device decoder-capability probe → derive pool size *K* (clamped to a safe ceiling; hardware `MediaCodec` decoders are capped). (`VideoDecoderProbe.maxConcurrentVideoPlayers()` — best per-codec `maxSupportedInstances`, clamped to `[1, POOL_CEILING=4]`, default 2 on failure.)
- [x] Candidates bounded by LOD: only `RenderDetail.Full` videos are playback candidates. (`CanvasScreen` computes `fullVideoIds`; `reconcile`/`selectPlaybackKeepSet` intersect with it.)
- [x] Pool of *K* `ExoPlayer` instances attaches to the *K* most-relevant playing videos; eviction policy (off-screen / least-recently-started first). (`VideoPlaybackController` pool + `selectPlaybackKeepSet` — recency via `startOrder`; evicted players recycled to a free list.)
- [x] Poster fallback when a video wants to play but can't get a pooled player. (Unassigned playing nodes mount no surface → poster + badge show through.)

### 27.6 Playback host & rendering
Full-detail videos render through a shared **`VideoSurfaceChrome`** (two-layer: rotation/opacity outer, clipped + offscreen mask inner) used by both `VideoPlayerSurface` (live) and `VideoPosterSurface` (static). See [video.md § 6](architecture/video.md#6-playback-host--rendering-implemented-2026-06-20).
- [x] Live surface = bare **`TextureView`** (`isOpaque = false`) bound via `player.setVideoTextureView` — *not* `PlayerView`/`SurfaceView`, which ignore container alpha / clip / offscreen masking. Mounted only at `RenderDetail.Full` for pool-assigned nodes (keyed by `nodeId`).
- [x] **Masked-poster fix (2026-06-20):** a `coil-video` frame in the shared image renderer's single-layer offscreen (rotation on the offscreen layer) composited the mask's cut-away region as opaque black. Fixed by rendering posters through the same two-layer chrome as the live player (`VideoPosterSurface`), as an `ARGB_8888` bitmap.
- [x] **Z-order (2026-06-20, verified on-device):** both `VideoPlayerSurface` and `VideoPosterSurface` are emitted **inline in the paint loop** at the node's z position. The live player's `TextureView` (in-hierarchy view, not a `SurfaceView` window layer) interleaves with the drawn nodes by composition order, so a playing video keeps z-order — superseding the earlier "playing video draws on top" limitation. **Revertible via the `PLAYER_SURFACE_INLINE` constant in `CanvasScreen.kt`:** set it `false` to fall back to mounting the player surfaces after the loop (always-on-top but stable) if a device mis-z-orders the inline TextureView. The fallback path is kept in code, not just git history.
- [x] Playback state + pool live in a `CanvasScaffold`-level holder keyed by `nodeId` — never in domain models (per [editor-tools.md § 7.1](architecture/editor-tools.md#71-state)). (`VideoPlaybackController` via `rememberVideoPlaybackController`; node carries no `isPlaying`.)
- [x] Surfaces defer pan/zoom/selection to the existing routers (non-clickable/non-focusable view) — confirmed on-device that pinch/pan over a playing video still route to the canvas.

### 27.7 Tests
- [x] Video node transforms (move/resize/rotate/select) identically to an image node. (`MediaTransformParityTest` — `createMedia` geometry parity + `withTransform` move/resize/rotate is type-agnostic and preserves `mediaType`.)
- [x] Playback gesture routing: single-tap never plays (mode default), double-tap on a video plays in View/Present/Edit-Selection, specialized tools keep their map, non-video double-tap keeps the mode default. (`GestureRouterTest` — `single-tap on a video focuses-selects…`, `double-tap on a video plays-pauses…`, `double-tap play is claimed only by Selection…`, `double-tap on a non-video keeps the mode default`.)
- [x] Pool respects *K*: the (K+1)-th requested playback evicts or falls back to poster per policy. (`VideoPlaybackKeepSetTest` — under-K / over-K eviction / off-screen exclusion / empty + zero-pool.)

### 27.8 Explicitly out of scope (first slice)
- [ ] loop / mute / start-position / custom poster / inline controls / autoplay-on-frame-entry / pause-on-leave / `AlbumVideoDefaults` / full Media Library (`to_discuss.md § 14`).

### 27.9 Live appearance on playing video (decided 2026-06-19)

Today `MediaAppearance` renders only on the **poster** (`FullMediaRenderer`); the live `PlayerView` surface applies none of it, so styling snaps off during playback. Decision: make the appearance ops **that already render on the poster** also apply to the live surface. Scope is the 7 rendered ops only — `colorAdjustments` / `frameDecoration` / `caption` render *nowhere* today (model + editor stubs), so they are explicitly **not** part of this slice (separate general-appearance work). *(Update 2026-06-20: `frameDecoration` — Stretch/NineSlice + rectangular opening crop — now renders on both the poster and the live video via `VideoSurfaceChrome`. See [media-appearance.md](architecture/media-appearance.md).)*

Foundational requirement: switch `PlayerView` from its default `SurfaceView` to **`TextureView`** (`surface_type=texture_view`) — a SurfaceView ignores container alpha / clip / offscreen compositing, which all the ops below need.

**§ 27.9a — TextureView + container-level ops — DONE 2026-06-19:**
- [x] Replaced `PlayerView` with a bare `TextureView` bound via `player.setVideoTextureView` (composites with alpha/clip; SurfaceView can't).
- [x] Opacity → container `graphicsLayer.alpha`.
- [x] Corner radius → rounded clip on the content container (px-space `Shape` via `roundedPxShape`).
- [x] Border → Compose stroke on the clip edge (reuses `drawNodeBorder`, now `internal`).
- [x] Shadow → Compose draw behind the surface (reuses `drawNodeShadow`, now `internal`).
- [x] Crop Fit / Fill / Stretch → content sizing (`videoContentRect` + `cropPlaced` layout modifier), TextureView sized aspect-correct, overflow clipped. Null crop matches the poster's Fill default.

**§ 27.9b — Manual crop + overlays — DONE 2026-06-19:**
- [x] Manual crop → `videoContentRect` handles `CropMode.Manual` (`zoom`/`offsetX`/`offsetY` over the Fill scale, mirroring `drawCroppedBitmap`). Landed with 9a since it's the same sizing function.
- [x] Overlays → `drawOverlayStack` + `rememberOverlayTextureBitmaps` drawn over the live frames in a `drawWithContent` block (inside the mask, matching FullMediaRenderer).

**§ 27.9c — Live alpha mask — DONE 2026-06-19 (highest risk — needs on-device confirmation):**
- [x] Offscreen-composite the TextureView (`graphicsLayer { compositingStrategy = Offscreen }`, gated on mask presence) and apply the mask via `drawWithAlphaMask` (`BlendMode.DstIn`) + `rememberAlphaMaskBitmap` in the same `drawWithContent`. **Risk:** offscreen-compositing a layer containing an embedded `AndroidView`/`TextureView` is the part most likely to misbehave on real hardware — verify a masked playing video actually shows the mask (and falls back to unmasked, not blank, if it doesn't).

**§ 27.9d — Transparency fixes (2026-06-19, from on-device feedback):**
- [x] `TextureView.isOpaque = false` — a TextureView is opaque by default, which defeated both the DstIn mask and `< 1` container opacity; masked-out regions / transparent opacity now show the canvas behind.
- [x] Skip the poster (`CanvasNodeRenderer`) for nodes with a live surface (`CanvasScreen` paint loop checks `playbackController.assignments.keys`). Was double-drawing: a semi-transparent / masked playing surface revealed the static frame-0 poster underneath (the "first frame shows during playback" bug), and border/overlays painted twice. Known trade-off: a brief transparent gap shows during buffering before the first frame (poster-until-first-frame is a possible polish via `Player.onRenderedFirstFrame`). (The "playing video draws on top of z-order" issue was resolved by inline emission — see § 27.6 / § 27.11.)

### 27.10 Manual QA checklist (real-device)

Unit tests can't cover ExoPlayer + TextureView + Compose lifecycle, the `Uri` round-trip, or the offscreen mask on real hardware. Run on a device:

- [ ] Import a video from the Android media picker → poster frame appears.
- [ ] **View mode** double-tap → plays, poster replaced by live frames, pan/zoom still work; double-tap again toggles.
- [ ] **Edit mode** → single-tap selects; double-tap plays/pauses only in **Selection** tool; **Eraser** / **CropEdit** do not play.
- [ ] **Alpha mask** on a video → poster respects the mask, live playback respects the mask, masked-out area is **transparent (not black)**.
- [ ] **Overlay / border / shadow / opacity / crop** → poster and live playback look consistent.
- [ ] Start **more videos than the pool size** → most-recently-started keep players; older / offscreen fall back to poster.
- [ ] Pan/zoom until a playing video leaves the viewport or drops below `RenderDetail.Full` → player evicted, poster returns.
- [ ] Delete a **playing** video → no crash; player released/recycled.
- [ ] Save / reopen album → node stays a video; playback state is **not** persisted.
- [ ] **URI coverage:** test both a `content://` source and a `file://` / bare local path (the `mediaRefToUri` paths). Today the import copies to an app-storage path; `content://` matters when refs are stored directly later.

### 27.11 Known limitations / future polish

- [x] **Z-order:** resolved + verified on-device 2026-06-20 — the player surface is emitted inline in the paint loop (see § 27.6); its `TextureView` interleaves with drawn nodes by composition order. Gated by `PLAYER_SURFACE_INLINE` in `CanvasScreen.kt` (default `true`); flip to `false` for the after-loop fallback if another device mis-z-orders it.
- [ ] **Buffering gap:** poster is hidden the moment a clip is assigned a player, so there's a brief transparent gap before the first decoded frame. Polish: keep the poster until `Player.Listener.onRenderedFirstFrame`.
- [ ] **`content://` longevity:** if refs are ever stored as `content://` (instead of copied to app storage), persistable URI read permission must be taken at import, else playback fails after process death.
- [ ] **No cleanup on node delete:** `VideoPlaybackController`'s `playingNodeIds` / `pausedNodeIds` / `uris` / `startOrder` retain ids for deleted videos forever (and, since pause/resume removed the gesture "stop", these sets only grow). No crash — `reconcile` evicts the player since the node is no longer `Full`-visible — but it's an unbounded leak. Fix: a `forget(nodeId)` (or `retainOnly(existingIds)`) called from the delete path. Needs VM↔controller wiring (controller is Compose-scoped, deletes happen in `CanvasViewModel`).
- [ ] **Ended / paused clips hold a decoder:** an ended or paused on-screen clip keeps occupying a pool slot, so with a small pool several of them starve new playback to poster. Acceptable for MVP; revisit (release on `STATE_ENDED`? reconsider no-loop?).
- [ ] **Build warning:** `compileDebugKotlin` emits `w: Class androidx.media3.common.util.UnstableApi is not an opt-in requirement marker` — the module-wide `-opt-in` flag is being ignored by the compiler yet the build is green, so the flag may be unnecessary in media3 1.5.1 / AGP 9.2. Investigate whether the opt-in arg can be dropped (don't remove blind — verify ExoPlayer/PlayerView still compile without it).

---

## 18. Future (Post-MVP)

See [future-ideas.md](product/future-ideas.md) for the full list.

- [ ] Smart tags — tap tag to teleport to frame
- [ ] User-defined layers full feature parity (multi-membership? layer locking? per-layer effects?)
- [ ] Present mode (read-only viewing for shared albums)
- [ ] Audio / Live Photos support
- [ ] Crop — media masking via bounding box editing
- [ ] Cloud sync — CRDT or Protobuf for real-time collaboration (separate from § 26's local-first snapshot sync)

# ZoomAlboom Project Memory

## Key Architecture
- Single-module Android app, Clean Architecture (core / domain / data / feature)
- MVI pattern: State/Intent/Effect interfaces in `core/mvi/MviContract.kt`
- Canvas: single `graphicsLayer` on Box, GPU-transformed, never recomposes nodes on gesture

## Coordinate Model (refactored 2026-03-21)
- `Transform.cx/cy` = **center** of node in world space (not top-left corner)
- `Transform.w/h` = actual world-unit base dimensions (not normalized)
- `Transform.scale` = user-applied multiplier; `renderW = w*scale`, `renderH = h*scale`
- `Camera.cx/cy` = graphicsLayer **translationX/Y** (screen-pixel units, NOT world coords)
  - Screen pos of world point (wx,wy) = (wx*scale + cx, wy*scale + cy)
  - To center world point (wx,wy) at screen center: cx = screenW/2 - wx*scale
- **Camera.scale ≠ Transform.scale** — do NOT copy one to the other
  - Camera.scale=2.0 → world appears 2× magnified; Transform.scale=2.0 → node is 2× its base size
- `Transform.toCamera(screenW, screenH)` in `TransformUtils.kt` computes the Camera to center and fit a node
- `TransformOrigin(0.5f, 0.5f)` in FrameRenderer — rotation around visual center
- Node render: `translationX = t.cx`, `translationY = t.cy`; `drawBehind` draws at `topLeft=Offset(-renderW/2,-renderH/2)`. `TransformOrigin(0f,0f)` then rotates around (cx,cy). NEVER use TransformOrigin(0.5f,0.5f) on a zero-size Spacer — it computes pivot=(0,0)=top-left, not center, causing center shift on rotation.
- `Camera` lives in `core/math/Camera.kt` (moved from CanvasViewModel to fix layer violation)
- `CanvasViewModel._allNodes` is `MutableStateFlow<List<CanvasNode>>` (was mutable var)
- `CanvasViewModel.frames` is a reactive `StateFlow<List<Frame>>` derived from `_allNodes`
- `CanvasNodeFactory.createFrame()` sizes frames from `screenWidth/camera.scale` — NOT from `viewport.width/height`. The viewport AABB changes aspect ratio with camera rotation (at 45° it is nearly square even on a portrait screen). Screen pixels are always rotation-independent.

## IDE Panel System (implemented 2026-03-12)
- PanelPosition: LeftTop, LeftBottom, RightTop, RightBottom, Top, Bottom, Floating
- PanelState: `expandedWidth`/`expandedHeight` preserve dims across collapse cycles; `width`/`height` are current rendered dims
- IdeUiState: `activePanelPerSlot: Map<PanelPosition, String>` — which panel tab is active per slot
- IdeViewModel: TogglePanelExpanded changes width (Left/Right) or height (Top/Bottom); ResizePanel updates expanded dims too
- IdeOverlayScreen: Column[Top slot | Row[LeftCol | centre | RightCol] | Bottom slot] + floating overlay layer
- PanelSlot: shows SlotTabBar (PrimaryTabRow) when >1 panel shares position; only active panel rendered
- DockedPanel: position-aware ‹›▲▼ toggle, InnerTabBar (MediaLibrary/FrameList only for LeftTop/LeftBottom)
- FloatingPanel: drag-to-dock snaps to LeftTop/RightTop; resize corner handle
- Panel stubs: `ui/panels/MediaLibraryPanel.kt`, `ui/panels/FrameListPanel.kt`

## Dependencies / Gotch
- NO `material-icons-core` in deps — use Unicode chars (‹ › ⠿) instead of Icons.Default.*
- `TabRow` is deprecated in this BOM (2026.02.01) — use `PrimaryTabRow` with `@OptIn(ExperimentalMaterial3Api::class)`
- `PrimaryTabRow.divider` takes a `@Composable () -> Unit`, not a Color parameter
- `Modifier.offset { IntOffset }` needs `import androidx.compose.foundation.layout.offset`

## Node Interaction (2026-04-10, refined 2026-04-11)
- `CanvasAction` sealed interface dispatched via `CanvasViewModel.onAction()`
- `selectedNodeIds: Set<String>` + `groupSelectionTransform: Transform?` in `CanvasState`
- 3-layer gesture stack on CanvasScreen outer Box:
  1. `nodeInteractionGestures` at `PointerEventPass.Initial` — handles/body drag only when selected
  2. `tapAndLongPressGestures` at Main pass — single handler: tap (toggle select), double-tap (reset), long-press (overlap picker or rectangle selection)
  3. `infiniteCanvasGestures` at Main pass — 2+ finger only (pan/zoom/rotate)
- Tap toggles selection (tap selected → deselect, tap unselected → add)
- Selection persists through camera pan/zoom/rotate
- Rotation handle uses atan2, gesture emits absolute positions via `positionLoop()`
- Multi-select overlay: individual borders on each node + group rect (rigid body rotation)
- Group rect stored as `groupSelectionTransform`, only rotation changes during handle drag, recomputed on FinishInteraction
- Key files: `NodeInteractionGestureDetector.kt`, `TapAndLongPressGestureDetector.kt`, `SelectionOverlay.kt`, `SelectionDebugPanel.kt`, `OverlapPickerDialog.kt`
- `LongPressGestureDetector.kt` exists but unused (superseded by `TapAndLongPressGestureDetector.kt`)
- **Key lesson**: never mix long-press detection with Initial-pass event reading; don't use two separate pointerInput blocks for tap + long-press

## Workflow
- See `feedback_workflow.md` in claude memory — do NOT auto-run builds or auto-commit
- LodResolverTest has 6 pre-existing failures (not related to node interaction work)

## Build Commands
- `./gradlew :app:compileDebugKotlin` — fast compile check
- `./gradlew assembleDebug` — full APK
- `./gradlew testDebugUnitTest` — unit tests

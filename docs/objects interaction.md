Context
The canvas renders and navigates frames, but nothing is selectable, movable, or resizable. This is the biggest gap blocking MVP (PRD success criteria #3, #7). Node interaction enables everything downstream: media editing, undo/redo, and the spatial storytelling experience.
Approach
Single selection with tap-to-select, drag-to-move, and corner-handle resize. Three-layer gesture stack on the outer Box using Compose's PointerEventPass.Initial for disambiguation.
┌──────────────────────────────────────────────────────────┐
│  CanvasScreen outer Box — modifier chain (outermost → innermost)  │
│                                                          │
│  1. nodeInteractionGestures (Initial pass)               │
│     ├─ down on selected node body → consume, drag loop → MoveNode │
│     ├─ down on resize handle      → consume, drag loop → ResizeNode │
│     └─ down elsewhere             → pass through         │
│                                                          │
│  2. detectTapGestures (Main pass)                        │
│     ├─ onTap   → hitTest → SelectNode / Deselect         │
│     └─ onDoubleTap → reset()                             │
│                                                          │
│  3. infiniteCanvasGestures (Main pass, unchanged)        │
│     └─ pan/zoom/rotate → onGesture()                     │
└──────────────────────────────────────────────────────────┘

Phase 1: Foundation — State, Actions, Math
1a. CanvasNode.withTransform() extension
File: domain/model/CanvasNode.kt
kotlinfun CanvasNode.withTransform(transform: Transform): CanvasNode = when (this) {
is CanvasNode.Frame -> copy(transform = transform)
is CanvasNode.Media -> copy(transform = transform)
}
1b. Selection state + CanvasAction
File: feature/canvas/viewmodel/CanvasViewModel.kt
Add selectedNodeId: String? to CanvasState:
kotlindata class CanvasState(
val camera: Camera = Camera(),
val visibleNodes: List<VisibleNode> = emptyList(),
val totalNodeCount: Int = 0,
val isLoading: Boolean = true,
val selectedNodeId: String? = null,      // ← new
)
Define actions following MVI convention (IdeAction pattern in IdeState.kt:55):
kotlinsealed interface CanvasAction : Intent {
data class SelectNode(val nodeId: String) : CanvasAction
data object Deselect : CanvasAction
data class MoveNode(val nodeId: String, val worldDx: Float, val worldDy: Float) : CanvasAction
data class ResizeNode(val nodeId: String, val newScale: Float) : CanvasAction
data class DeleteNode(val nodeId: String) : CanvasAction
data class DuplicateNode(val nodeId: String) : CanvasAction
data object FinishInteraction : CanvasAction
}
Add fun onAction(action: CanvasAction) with when dispatch:

SelectNode: _state.update { it.copy(selectedNodeId = nodeId) }
Deselect: _state.update { it.copy(selectedNodeId = null) }
MoveNode: update node cx/cy in _allNodes via withTransform() + inline-patch _state.visibleNodes (no recalculateVisibleNodes() during drag — performance)
ResizeNode: update node scale in _allNodes + inline-patch visibleNodes
FinishInteraction: calls recalculateVisibleNodes() (node may have moved out of viewport)
DeleteNode: removes node from _allNodes, deselects, updates totalNodeCount, recalculates
DuplicateNode: copies node with id = "${type}_${System.currentTimeMillis()}", offset cx+50/cy+50 world units, next zIndex; adds via existing addNode()

1c. Hit-testing math
File: core/math/TransformUtils.kt
Three new functions inside TransformUtils object. Reuses existing rotateVector().
kotlinfun screenToWorld(screenX: Float, screenY: Float, camera: Camera): Pair<Float, Float>
Inverse of the camera graphicsLayer (TransformOrigin(0,0)):

graphicsLayer applies: scale → rotate → translate
Inverse: un-translate → un-rotate → un-scale

kotlinval dx = screenX - camera.cx
val dy = screenY - camera.cy
val (ux, uy) = rotateVector(dx, dy, -camera.rotation)
return Pair(ux / camera.scale, uy / camera.scale)
kotlinfun pointInNode(worldX: Float, worldY: Float, transform: Transform): Boolean
Rotation-aware OBB test — translate to node center, un-rotate by node rotation, check half-extents:
kotlinval dx = worldX - transform.cx
val dy = worldY - transform.cy
val (lx, ly) = rotateVector(dx, dy, -transform.rotation)
return abs(lx) <= transform.renderW / 2f && abs(ly) <= transform.renderH / 2f
kotlinfun screenDeltaToWorld(screenDx: Float, screenDy: Float, camera: Camera): Pair<Float, Float>
For drag deltas (position-independent, just direction+magnitude):
kotlinval (rx, ry) = rotateVector(screenDx, screenDy, -camera.rotation)
return Pair(rx / camera.scale, ry / camera.scale)
1d. ViewModel helpers
File: feature/canvas/viewmodel/CanvasViewModel.kt
kotlinfun hitTest(screenX: Float, screenY: Float): CanvasNode? {
val (wx, wy) = TransformUtils.screenToWorld(screenX, screenY, _state.value.camera)
return _state.value.visibleNodes
.sortedByDescending { it.node.transform.zIndex }
.firstOrNull { TransformUtils.pointInNode(wx, wy, it.node.transform) }
?.node
}
1e. Unit tests
File (new): app/src/test/java/com/mamton/zoomalbum/core/math/HitTestingTest.kt
Test cases for screenToWorld, pointInNode, screenDeltaToWorld:

Identity camera (no rotation, scale=1, translate=0,0)
Rotated camera (45°)
Zoomed camera (scale=2)
Node with rotation: point inside, point outside, point on edge
screenDeltaToWorld with rotated camera


Phase 2: Tap-to-Select + Action Bar
2a. Add tap gesture to CanvasScreen
File: feature/canvas/view/CanvasScreen.kt
Replace the existing detectTapGestures(onDoubleTap = { viewModel.reset() }) with:
kotlindetectTapGestures(
onTap = { offset ->
val hit = viewModel.hitTest(offset.x, offset.y)
if (hit != null) viewModel.onAction(CanvasAction.SelectNode(hit.id))
else viewModel.onAction(CanvasAction.Deselect)
},
onDoubleTap = { viewModel.reset() },
)
Note: onTap has ~300ms delay (double-tap disambiguation). Acceptable for MVP.
2b. Wire CanvasScaffold + ContextualActionBar
File: feature/ide_ui/ui/CanvasScaffold.kt
Replace val selectedNodeId: String? = null (line 46) with:
kotlinval selectedNodeId = canvasState.selectedNodeId
Wire onAction callback to route string labels to CanvasAction:
kotlinContextualActionBar(
selectedNodeId = selectedNodeId,
modifier = Modifier.align(Alignment.BottomCenter),
onAction = { label ->
val nodeId = selectedNodeId ?: return@ContextualActionBar
when (label) {
"Delete" -> canvasViewModel.onAction(CanvasAction.DeleteNode(nodeId))
"Duplicate" -> canvasViewModel.onAction(CanvasAction.DuplicateNode(nodeId))
}
},
)
File: feature/ide_ui/ui/ContextualActionBar.kt
Remove "Move" and "Resize" action items (handled by gestures). Keep: Delete, Duplicate, Edit.

Phase 3: Selection Visual Overlay
File (new): feature/canvas/view/SelectionOverlay.kt
Composable drawn inside the camera-transformed inner Box, on top of all nodes. Uses the same Spacer + graphicsLayer + drawBehind rendering pattern as FullFrameRenderer (CanvasRenderer.kt:57-85):

graphicsLayer: translationX = transform.cx, translationY = transform.cy, rotationZ = transform.rotation, transformOrigin = TransformOrigin(0f, 0f) — exactly matching node renderers
drawBehind: draw at topLeft = Offset(-renderW/2, -renderH/2) with Size(renderW, renderH)

Draws:

Cyan selection border — AccentCyan from Color.kt, stroke width = 2f / cameraScale (constant screen-space width)
4 corner handle squares — filled squares, side = 24f / cameraScale (constant screen-space size), drawn at the 4 corners of the node rect

File: feature/canvas/view/CanvasScreen.kt
After the visible nodes loop in the inner Box:
kotlinval selectedNode = state.visibleNodes.find { it.node.id == state.selectedNodeId }
if (selectedNode != null) {
SelectionOverlay(transform = selectedNode.node.transform, cameraScale = cam.scale)
}

Phase 4: Drag-to-Move
4a. Node interaction gesture detector
File (new): feature/canvas/gestures/NodeInteractionGestureDetector.kt
Modifier.nodeInteractionGestures(...) extension function wrapping pointerInput.
Core logic:

awaitEachGesture + awaitFirstDown(pass = PointerEventPass.Initial) — sees events before detectTapGestures and infiniteCanvasGestures (Main pass)
Hit-test down position: resize handle first (Phase 5), then selected node body
If on selected node body → consume() the down event, enter drag loop (awaitPointerEvent in while-loop), emit screen deltas via onDrag callback, emit onDragEnd when all pointers up
If not on node → return@awaitEachGesture without consuming → events flow through to tap + canvas gestures

When selectedNodeId == null, the pointerInput returns immediately from each gesture (zero overhead).
4b. Updated modifier chain on CanvasScreen outer Box
Replace the current modifier chain on the outer Box:
kotlinBox(
modifier = Modifier
.fillMaxSize()
.background(CanvasDark)
.onSizeChanged { ... }
.nodeInteractionGestures(                          // 1. Initial pass
selectedNodeId = state.selectedNodeId,
hitTestBody = { x, y -> viewModel.isOnSelectedNode(x, y) },
hitTestHandle = { x, y -> viewModel.hitTestHandle(x, y) },  // Phase 5
onDrag = { dx, dy ->
val id = state.selectedNodeId ?: return@nodeInteractionGestures
val (wdx, wdy) = TransformUtils.screenDeltaToWorld(dx, dy, state.camera)
viewModel.onAction(CanvasAction.MoveNode(id, wdx, wdy))
},
onDragEnd = { viewModel.onAction(CanvasAction.FinishInteraction) },
)
.pointerInput(Unit) {                               // 2. Main pass
detectTapGestures(
onTap = { ... },
onDoubleTap = { viewModel.reset() },
)
}
.infiniteCanvasGestures { ... },                    // 3. Main pass
)
4c. isOnSelectedNode helper
File: feature/canvas/viewmodel/CanvasViewModel.kt
kotlinfun isOnSelectedNode(screenX: Float, screenY: Float): Boolean {
val nodeId = _state.value.selectedNodeId ?: return false
val node = _state.value.visibleNodes.find { it.node.id == nodeId }?.node ?: return false
val (wx, wy) = TransformUtils.screenToWorld(screenX, screenY, _state.value.camera)
return TransformUtils.pointInNode(wx, wy, node.transform)
}
4d. Deselect on camera gesture
File: feature/canvas/viewmodel/CanvasViewModel.kt
In onGesture() (line 90), deselect if a node is selected — camera gesture means user is navigating:
kotlinfun onGesture(centroid: Offset, pan: Offset, zoom: Float, rotationDelta: Float) {
_state.update { s ->
val oldCam = s.camera
// ... existing camera math (unchanged) ...
s.copy(
camera = Camera(...),
selectedNodeId = null,   // ← deselect on camera gesture
)
}
recalculateVisibleNodes()
}

Phase 5: Resize via Corner Handles
5a. Handle hit-testing
File: core/math/TransformUtils.kt
kotlinenum class ResizeHandle { TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT }

fun hitTestHandle(
worldX: Float, worldY: Float,
transform: Transform,
handleWorldRadius: Float,   // caller passes (handleScreenPx / 2) / cameraScale
): ResizeHandle?
Transform to node-local coords (same as pointInNode), then check distance to each corner against handleWorldRadius.
5b. Extend gesture detector
File: feature/canvas/gestures/NodeInteractionGestureDetector.kt
In the awaitFirstDown handler, check handles before node body:

hitTestHandle(...) → if hit, enter resize drag loop, call onResizeDrag(handle, dx, dy) + onDragEnd
hitTestBody(...) → if hit, enter move drag loop (existing)
Neither → pass through

5c. Resize action wiring
kotlinonResizeDrag = { handle, dx, dy ->
val id = state.selectedNodeId ?: return@...
val node = ... // find node
// Convert screen delta → world delta → node-local delta (un-rotate by node.transform.rotation)
// Project onto corner diagonal direction
// scaleFactor = (diagonalLen + projection) / diagonalLen
// newScale = (node.transform.scale * scaleFactor).coerceIn(0.01f, 1000f)
viewModel.onAction(CanvasAction.ResizeNode(id, newScale))
}
5d. ViewModel helper
File: feature/canvas/viewmodel/CanvasViewModel.kt
kotlinfun hitTestHandle(screenX: Float, screenY: Float): ResizeHandle? {
val nodeId = _state.value.selectedNodeId ?: return null
val node = _state.value.visibleNodes.find { it.node.id == nodeId }?.node ?: return null
val cam = _state.value.camera
val (wx, wy) = TransformUtils.screenToWorld(screenX, screenY, cam)
val handleWorldRadius = 12f / cam.scale   // 24px screen-space handle, check within 12px radius
return TransformUtils.hitTestHandle(wx, wy, node.transform, handleWorldRadius)
}

Files Summary
FileChangedomain/model/CanvasNode.ktAdd withTransform() extensioncore/math/TransformUtils.ktAdd screenToWorld, pointInNode, screenDeltaToWorld, hitTestHandle, ResizeHandlefeature/canvas/viewmodel/CanvasViewModel.ktselectedNodeId in state, CanvasAction sealed interface, onAction(), hit-test helpers, deselect-on-gesturefeature/canvas/gestures/NodeInteractionGestureDetector.ktNew — node drag + resize gesture modifierfeature/canvas/view/SelectionOverlay.ktNew — selection border + corner handlesfeature/canvas/view/CanvasScreen.kt3-layer gesture chain, SelectionOverlay in inner Boxfeature/ide_ui/ui/CanvasScaffold.ktWire selectedNodeId from canvasState + action bar callbacksfeature/ide_ui/ui/ContextualActionBar.ktRemove Move/Resize items, keep Delete/Duplicate/Editapp/src/test/.../core/math/HitTestingTest.ktNew — unit tests for hit-testing math
Implementation Order

Phase 1 (foundation) — state + math, independently testable. Run testDebugUnitTest after.
Phase 2 (tap-to-select) — first visible result on device.
Phase 3 (selection overlay) — visual feedback needed to test everything after.
Phase 4 (drag-to-move) — the hardest gesture part. Depends on Phase 3 for visual verification.
Phase 5 (resize handles) — extends Phase 4's gesture detector.

Verification

./gradlew testDebugUnitTest — hit-testing math tests pass
./gradlew assembleDebug — clean build after all phases
Run on device/emulator:

Create album, add 2-3 frames
Tap frame → cyan border + handles appear, action bar slides up
Tap empty space → deselect, border gone, bar hidden
Tap frame → Delete → frame removed
Tap frame → Duplicate → offset copy appears
Tap frame → drag body → frame moves with finger
Drag on empty space → canvas pans normally
Pinch/rotate → camera zoom/rotate, selection clears
Tap frame → drag corner handle → frame resizes proportionally
Rotate canvas, then select a rotated frame → border follows rotation, move/resize still work

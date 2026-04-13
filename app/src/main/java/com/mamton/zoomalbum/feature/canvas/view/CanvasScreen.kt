package com.mamton.zoomalbum.feature.canvas.view

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mamton.zoomalbum.core.designsystem.CanvasDark
import com.mamton.zoomalbum.core.math.TransformUtils
import com.mamton.zoomalbum.domain.model.CanvasNode
import com.mamton.zoomalbum.feature.canvas.gestures.infiniteCanvasGestures
import com.mamton.zoomalbum.feature.canvas.gestures.nodeInteractionGestures
import com.mamton.zoomalbum.feature.canvas.gestures.tapAndLongPressGestures
import com.mamton.zoomalbum.feature.canvas.viewmodel.CanvasAction
import com.mamton.zoomalbum.feature.canvas.viewmodel.CanvasViewModel
import kotlin.math.atan2

@Composable
fun CanvasScreen(
    viewModel: CanvasViewModel = hiltViewModel(),
    onShowOverlapPicker: (List<CanvasNode>) -> Unit = {},
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val cam = state.camera

    // Tracks previous angle (degrees) for atan2-based rotation handle.
    // [0] = previous angle, [1] = initialized flag (0 = no, 1 = yes)
    val rotAngleRef = remember { floatArrayOf(0f, 0f) }

    // Mutable ref for rectangle selection start point (world coords).
    val rectStartWorld = remember { floatArrayOf(0f, 0f) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(CanvasDark)
            .onSizeChanged { size ->
                viewModel.onScreenSizeChanged(size.width.toFloat(), size.height.toFloat())
            }
            // Layer 1 (outermost): node drag/resize/rotate — Initial pass
            // Only active when something is selected. Consumes events on
            // handles and selected node body; passes through otherwise.
            .nodeInteractionGestures(
                selectedNodeIds = state.selectedNodeIds,
                hitTestBody = { x, y -> viewModel.isOnSelectedNode(x, y) },
                hitTestHandle = { x, y -> viewModel.hitTestHandle(x, y) },
                hitTestRotationHandle = { x, y -> viewModel.hitTestRotationHandle(x, y) },
                onDrag = { dx, dy ->
                    val (wdx, wdy) = TransformUtils.screenDeltaToWorld(dx, dy, state.camera)
                    viewModel.onAction(CanvasAction.MoveSelection(wdx, wdy))
                },
                onResizeDrag = { handle, dx, dy ->
                    val ids = state.selectedNodeIds
                    val selected = state.visibleNodes
                        .filter { it.node.id in ids }
                        .map { it.node }
                    if (selected.isEmpty()) return@nodeInteractionGestures
                    val t = if (selected.size == 1) {
                        selected.first().transform
                    } else {
                        val bbox = TransformUtils.selectionBoundingBox(selected)
                        com.mamton.zoomalbum.domain.model.Transform(
                            cx = bbox.centerX, cy = bbox.centerY,
                            w = bbox.width, h = bbox.height,
                        )
                    }
                    val halfW = t.renderW / 2f
                    val halfH = t.renderH / 2f
                    val (cornerX, cornerY) = when (handle) {
                        com.mamton.zoomalbum.core.math.ResizeHandle.TOP_LEFT -> -halfW to -halfH
                        com.mamton.zoomalbum.core.math.ResizeHandle.TOP_RIGHT -> halfW to -halfH
                        com.mamton.zoomalbum.core.math.ResizeHandle.BOTTOM_LEFT -> -halfW to halfH
                        com.mamton.zoomalbum.core.math.ResizeHandle.BOTTOM_RIGHT -> halfW to halfH
                    }
                    val diagonalLen = kotlin.math.sqrt(cornerX * cornerX + cornerY * cornerY)
                    if (diagonalLen < 0.001f) return@nodeInteractionGestures
                    val (worldDx, worldDy) = TransformUtils.screenDeltaToWorld(dx, dy, cam)
                    val (localDx, localDy) = TransformUtils.rotateVector(worldDx, worldDy, -t.rotation)
                    val dirX = cornerX / diagonalLen
                    val dirY = cornerY / diagonalLen
                    val projection = localDx * dirX + localDy * dirY
                    val scaleFactor = (diagonalLen + projection) / diagonalLen
                    viewModel.onAction(CanvasAction.ResizeSelection(scaleFactor))
                },
                onRotationDragPosition = { screenX, screenY ->
                    // Compute selection center in screen space
                    val ids = state.selectedNodeIds
                    val selected = state.visibleNodes
                        .filter { it.node.id in ids }
                        .map { it.node }
                    if (selected.isEmpty()) return@nodeInteractionGestures
                    val (wcx, wcy) = if (selected.size == 1) {
                        selected.first().transform.cx to selected.first().transform.cy
                    } else {
                        TransformUtils.groupCenter(selected)
                    }
                    val (scx, scy) = TransformUtils.worldToScreen(wcx, wcy, cam)

                    // atan2 angle from center to current drag position
                    val currentAngle = Math.toDegrees(
                        atan2(
                            (screenY - scy).toDouble(),
                            (screenX - scx).toDouble(),
                        ),
                    ).toFloat()

                    if (rotAngleRef[1] == 0f) {
                        // First event — just store angle, no rotation yet
                        rotAngleRef[0] = currentAngle
                        rotAngleRef[1] = 1f
                    } else {
                        var delta = currentAngle - rotAngleRef[0]
                        // Normalize to [-180, 180] for smooth crossing at ±180
                        if (delta > 180f) delta -= 360f
                        if (delta < -180f) delta += 360f
                        rotAngleRef[0] = currentAngle
                        viewModel.onAction(CanvasAction.RotateSelection(delta))
                    }
                },
                onDragEnd = {
                    rotAngleRef[1] = 0f // reset for next rotation gesture
                    viewModel.onAction(CanvasAction.FinishInteraction)
                },
            )
            // Layer 2: tap + double-tap + long-press+drag — single Main pass handler
            .tapAndLongPressGestures(
                onTap = { offset ->
                    val hit = viewModel.hitTest(offset.x, offset.y)
                    if (hit != null) {
                        // Tap selected node → deselect it
                        // Tap unselected node → add to selection
                        viewModel.onAction(CanvasAction.ToggleNodeSelection(hit.id))
                    } else {
                        viewModel.onAction(CanvasAction.DeselectAll)
                    }
                },
                onDoubleTap = { viewModel.reset() },
                onLongPress = { screenX, screenY ->
                    val hits = viewModel.hitTestAll(screenX, screenY)
                    if (hits.size > 1) {
                        onShowOverlapPicker(hits)
                        true
                    } else {
                        false // start rectangle selection
                    }
                },
                onDragStart = { screenX, screenY ->
                    val (wx, wy) = TransformUtils.screenToWorld(screenX, screenY, cam)
                    rectStartWorld[0] = wx
                    rectStartWorld[1] = wy
                    viewModel.onAction(
                        CanvasAction.UpdateSelectionRect(
                            com.mamton.zoomalbum.core.math.BoundingBox(wx, wy, wx, wy),
                        ),
                    )
                },
                onDragUpdate = { screenX, screenY ->
                    val (wx, wy) = TransformUtils.screenToWorld(screenX, screenY, cam)
                    val startX = rectStartWorld[0]
                    val startY = rectStartWorld[1]
                    viewModel.onAction(
                        CanvasAction.UpdateSelectionRect(
                            com.mamton.zoomalbum.core.math.BoundingBox(
                                left = kotlin.math.min(startX, wx),
                                top = kotlin.math.min(startY, wy),
                                right = kotlin.math.max(startX, wx),
                                bottom = kotlin.math.max(startY, wy),
                            ),
                        ),
                    )
                },
                onDragEnd = {
                    val rect = state.selectionRect
                    if (rect != null) {
                        viewModel.onAction(CanvasAction.SelectNodesInRect(rect))
                    }
                },
            )
            // Layer 3: canvas pan/zoom/rotate — Main pass
            // When two-finger centroid is on a selected node and rotation is non-zero,
            // route rotation to the node instead of the camera.
            .infiniteCanvasGestures { centroid, pan, zoom, rotation ->
                if (state.selectedNodeIds.isNotEmpty()
                    && rotation != 0f
                    && viewModel.isOnSelectedNode(centroid.x, centroid.y)
                ) {
                    viewModel.onAction(CanvasAction.RotateSelection(rotation))
                    viewModel.onGesture(centroid, pan, zoom, rotationDelta = 0f)
                } else {
                    viewModel.onGesture(centroid, pan, zoom, rotation)
                }
            },
    ) {
        // The single graphicsLayer on this inner Box handles ALL pan/zoom.
        // Individual node composables never recalculate their position during gestures;
        // the GPU performs the transform on the entire layer.
        Box(
            modifier = Modifier.graphicsLayer {
                translationX = cam.cx
                translationY = cam.cy
                scaleX = cam.scale
                scaleY = cam.scale
                rotationZ = cam.rotation
                transformOrigin = TransformOrigin(0f, 0f)
            },
        ) {
            for ((node, detail) in state.visibleNodes) {
                CanvasNodeRenderer(node, detail)
            }

            // Selection overlay — drawn on top of all nodes
            val selectedNodes = state.visibleNodes
                .filter { it.node.id in state.selectedNodeIds }
                .map { it.node }
            if (selectedNodes.isNotEmpty()) {
                SelectionOverlay(
                    selectedNodes = selectedNodes,
                    cameraScale = cam.scale,
                    rotationHandleEnabled = true, // TODO: wire to InteractionSettings
                    groupTransform = state.groupSelectionTransform,
                )
            }
            state.selectionRect?.let { rect -> SelectionRectOverlay(rect) }
        }

        if (state.isLoading) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        }
    }
}

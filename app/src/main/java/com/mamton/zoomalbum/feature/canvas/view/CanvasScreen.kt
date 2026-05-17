package com.mamton.zoomalbum.feature.canvas.view

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mamton.zoomalbum.core.designsystem.CanvasDark
import com.mamton.zoomalbum.core.designsystem.CanvasLight
import com.mamton.zoomalbum.core.math.TransformUtils
import com.mamton.zoomalbum.domain.model.CanvasInteractionMode
import com.mamton.zoomalbum.domain.model.CanvasNode
import com.mamton.zoomalbum.domain.model.MembershipState
import com.mamton.zoomalbum.domain.usecase.FrameMembershipUseCase
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

    // NOTE: do NOT cache `state.camera` into a local `val` — gesture modifiers
    // keep their pointerInput blocks running across recompositions (keyed on
    // `selectedNodeIds` or `Unit`), so a local snapshot would become stale
    // after zoom/pan/rotate and make resize/rotate feel frozen at the old
    // scale. Always read `state.camera` directly — the State<T> delegate
    // reads the current value on each access.

    // Tracks previous angle (degrees) for atan2-based rotation handle.
    // [0] = previous angle, [1] = initialized flag (0 = no, 1 = yes)
    val rotAngleRef = remember { floatArrayOf(0f, 0f) }

    // Mutable ref for rectangle selection start point (world coords).
    val rectStartWorld = remember { floatArrayOf(0f, 0f) }

    // Screen size in pixels — needed by world-locked background renderer to
    // compute the visible world rect. Updated synchronously with the viewmodel's
    // own screen-size cache.
    var screenSize by remember { mutableStateOf(IntSize.Zero) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(CanvasLight)
            .onSizeChanged { size ->
                screenSize = size
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
                    // Use effective selection transform — independent of viewport culling.
                    val t = viewModel.selectionTransform() ?: return@nodeInteractionGestures
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

                    // Pivot = opposite corner in world coords. Stays geometrically fixed
                    // throughout the gesture; the per-event recomputation produces the
                    // same world point because the rect grows symmetrically around it.
                    val (oppXRot, oppYRot) = TransformUtils.rotateVector(
                        -cornerX, -cornerY, t.rotation,
                    )
                    val pivotX = t.cx + oppXRot
                    val pivotY = t.cy + oppYRot

                    val (worldDx, worldDy) = TransformUtils.screenDeltaToWorld(dx, dy, state.camera)
                    val (localDx, localDy) = TransformUtils.rotateVector(
                        worldDx, worldDy, -t.rotation,
                    )
                    val dirX = cornerX / diagonalLen
                    val dirY = cornerY / diagonalLen
                    val projection = localDx * dirX + localDy * dirY

                    // Distance from pivot (opposite corner) to dragged corner = 2 * diagonalLen
                    // (the full diagonal). The scale factor is the relative change in that distance.
                    val fullDiag = 2f * diagonalLen
                    val scaleFactor = (fullDiag + projection) / fullDiag
                    viewModel.onAction(
                        CanvasAction.ResizeSelection(scaleFactor, pivotX, pivotY),
                    )
                },
                onRotationDragPosition = { screenX, screenY ->
                    // Pivot = selection transform center (single node or group rect center).
                    // Must match the ViewModel's RotateSelection pivot exactly; otherwise
                    // angle deltas won't correspond to the user's gesture.
                    val t = viewModel.selectionTransform() ?: return@nodeInteractionGestures
                    val (scx, scy) = TransformUtils.worldToScreen(t.cx, t.cy, state.camera)

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
                onDragBegin = { kind ->
                    viewModel.onAction(CanvasAction.BeginInteraction(kind))
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
                    when (state.mode) {
                        CanvasInteractionMode.Edit -> {
                            // Tap = replace-style selection. Hit → {node}; miss → clear.
                            // To add/remove individual nodes, use long-press (toggle).
                            if (hit != null) {
                                viewModel.onAction(CanvasAction.SelectNode(hit.id))
                            } else {
                                viewModel.onAction(CanvasAction.DeselectAll)
                            }
                        }
                        CanvasInteractionMode.View,
                        CanvasInteractionMode.Presentation -> {
                            // View / Presentation: tap a node to focus it. Miss = no-op.
                            if (hit != null) viewModel.onAction(CanvasAction.FocusNode(hit.id))
                        }
                    }
                },
                onDoubleTap = { viewModel.reset() },
                onLongPress = { screenX, screenY ->
                    // View / Presentation: swallow long-press — no overlap picker,
                    // no selection toggle, no rect-select drag.
                    if (state.mode != CanvasInteractionMode.Edit) return@tapAndLongPressGestures true

                    // Edit long-press routing:
                    //   >1 hits → overlap picker dialog (consume)
                    //   1 hit   → toggle that node in selection (consume; no rect drag)
                    //   0 hits  → fall through to rect-select drag
                    val hits = viewModel.hitTestAll(screenX, screenY)
                    when {
                        hits.size > 1 -> {
                            onShowOverlapPicker(hits)
                            true
                        }

                        hits.size == 1 -> {
                            viewModel.onAction(CanvasAction.ToggleNodeSelection(hits[0].id))
                            true
                        }

                        else -> false
                    }
                },
                onDragStart = { screenX, screenY ->
                    val (wx, wy) = TransformUtils.screenToWorld(screenX, screenY, state.camera)
                    rectStartWorld[0] = wx
                    rectStartWorld[1] = wy
                    viewModel.onAction(
                        CanvasAction.UpdateSelectionRect(
                            com.mamton.zoomalbum.core.math.BoundingBox(wx, wy, wx, wy),
                        ),
                    )
                },
                onDragUpdate = { screenX, screenY ->
                    val (wx, wy) = TransformUtils.screenToWorld(screenX, screenY, state.camera)
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
                        // Additive iff selection was non-empty when the rect started.
                        // Rect-select doesn't mutate selectedNodeIds during drag,
                        // so reading it at onDragEnd reflects the pre-drag state.
                        val additive = state.selectedNodeIds.isNotEmpty()
                        viewModel.onAction(
                            CanvasAction.SelectNodesInRect(
                                rect,
                                additive = additive
                            )
                        )
                    }
                },
            )
            // Layer 3: canvas pan/zoom/rotate — Main pass handler
            .infiniteCanvasGestures { centroid, pan, zoom, rotation ->
                viewModel.onGesture(centroid, pan, zoom, rotation)
            },
    ) {
        // Camera-locked album background: screen-fixed, drawn under everything.
        state.albumBackground?.let { bg ->
            if (bg.anchorMode == com.mamton.zoomalbum.domain.model.AnchorMode.CameraLocked) {
                CameraLockedAlbumBackground(bg)
            }
        }

        // The single graphicsLayer on this inner Box handles ALL pan/zoom.
        // Individual node composables never recalculate their position during gestures;
        // the GPU performs the transform on the entire layer.
        Box(
            modifier = Modifier.graphicsLayer {
                val c = state.camera
                translationX = c.cx
                translationY = c.cy
                scaleX = c.scale
                scaleY = c.scale
                rotationZ = c.rotation
                transformOrigin = TransformOrigin(0f, 0f)
            },
        ) {
            // World-locked album background: moves/scales with camera; painted under nodes.
            state.albumBackground?.let { bg ->
                if (bg.anchorMode == com.mamton.zoomalbum.domain.model.AnchorMode.WorldLocked) {
                    WorldLockedAlbumBackground(
                        background = bg,
                        camera = state.camera,
                        screenSize = screenSize,
                    )
                }
            }

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
                    cameraScale = state.camera.scale,
                    rotationHandleEnabled = true, // TODO: wire to InteractionSettings
                    groupTransform = state.groupSelectionTransform,
                )
            }
            state.selectionRect?.let { rect -> SelectionRectOverlay(rect) }

            // Membership visualisation: for EVERY frame in the selection, draw thin
            // borders around its effective members so the membership relation is visible.
            // Two tiers: lighter for purely geometric, darker for nodes with an Included
            // override on at least one of the selected frames (so the user can tell what
            // will / won't change when they press Auto).
            val selectedFrames = selectedNodes.filterIsInstance<CanvasNode.Frame>()
            if (selectedFrames.isNotEmpty()) {
                val membershipUseCase = remember { FrameMembershipUseCase() }
                val allVisible = state.visibleNodes.map { it.node }
                val classified = remember(selectedFrames, allVisible) {
                    val allMemberIds = mutableSetOf<String>()
                    val manualMemberIds = mutableSetOf<String>()
                    for (frame in selectedFrames) {
                        val members = membershipUseCase.effectiveMembers(frame, allVisible)
                        allMemberIds += members
                        manualMemberIds += frame.overrides
                            .filterValues { it.state == MembershipState.Included }
                            .keys
                            .intersect(members)
                    }
                    MembershipClassification(
                        allMembers = allMemberIds,
                        manualMembers = manualMemberIds,
                    )
                }
                if (classified.allMembers.isNotEmpty()) {
                    val autoMembers = allVisible.filter {
                        it.id in classified.allMembers && it.id !in classified.manualMembers
                    }
                    val manualMembers = allVisible.filter { it.id in classified.manualMembers }
                    MembershipBorderOverlay(
                        autoMembers = autoMembers,
                        manualMembers = manualMembers,
                        cameraScale = state.camera.scale,
                    )
                }
            }
        }

        if (state.isLoading) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        }
    }
}

/**
 * Result of classifying a frame selection's effective members into auto (purely
 * geometric) vs manual (Included override on at least one selected frame).
 *
 * Declared `@Immutable` so Compose can skip recomposition when an unchanged
 * instance flows through composables. A `Pair<Set<String>, Set<String>>` works
 * but is treated as unstable (generic library type with interface-typed args).
 */
@Immutable
private data class MembershipClassification(
    val allMembers: Set<String>,
    val manualMembers: Set<String>,
)

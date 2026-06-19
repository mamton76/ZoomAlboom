package com.mamton.zoomalbum.feature.canvas.view

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mamton.zoomalbum.core.designsystem.CanvasDark
import com.mamton.zoomalbum.core.designsystem.CanvasLight
import com.mamton.zoomalbum.core.math.ResizeHandle
import com.mamton.zoomalbum.core.math.TransformUtils
import com.mamton.zoomalbum.domain.model.CanvasInteractionMode
import com.mamton.zoomalbum.domain.model.CanvasNode
import com.mamton.zoomalbum.domain.model.MediaType
import com.mamton.zoomalbum.domain.model.RenderDetail
import com.mamton.zoomalbum.feature.canvas.playback.VideoPlaybackController
import com.mamton.zoomalbum.feature.canvas.playback.VideoPlayerSurface
import com.mamton.zoomalbum.feature.canvas.playback.VideoPosterSurface
import com.mamton.zoomalbum.domain.undo.InteractionKind
import com.mamton.zoomalbum.feature.canvas.editor.EditorTool
import com.mamton.zoomalbum.domain.model.MembershipState
import com.mamton.zoomalbum.domain.usecase.FrameMembershipUseCase
import com.mamton.zoomalbum.feature.canvas.editor.gestures.CameraTransformRoute
import com.mamton.zoomalbum.feature.canvas.editor.gestures.DoubleTapRoute
import com.mamton.zoomalbum.feature.canvas.editor.gestures.EraserScrubStartRoute
import com.mamton.zoomalbum.feature.canvas.editor.gestures.GestureRouter
import com.mamton.zoomalbum.feature.canvas.editor.gestures.GestureRoutingContext
import com.mamton.zoomalbum.feature.canvas.editor.gestures.LongPressLiftRoute
import com.mamton.zoomalbum.feature.canvas.editor.gestures.LongPressRoute
import com.mamton.zoomalbum.feature.canvas.editor.gestures.MarqueeStartRoute
import com.mamton.zoomalbum.feature.canvas.editor.gestures.SelectedNodeTransformRoute
import com.mamton.zoomalbum.feature.canvas.editor.gestures.TapRoute
import com.mamton.zoomalbum.feature.canvas.editor.gestures.ViewPanRoute
import com.mamton.zoomalbum.feature.canvas.gestures.eraserScrubGestures
import com.mamton.zoomalbum.feature.canvas.gestures.infiniteCanvasGestures
import com.mamton.zoomalbum.feature.canvas.gestures.nodeInteractionGestures
import com.mamton.zoomalbum.feature.canvas.gestures.selectionMarqueeGestures
import com.mamton.zoomalbum.feature.canvas.gestures.viewModePanGestures
import androidx.compose.runtime.rememberUpdatedState
import com.mamton.zoomalbum.feature.canvas.gestures.tapAndLongPressGestures
import com.mamton.zoomalbum.feature.canvas.viewmodel.CanvasAction
import com.mamton.zoomalbum.feature.canvas.viewmodel.CanvasState
import com.mamton.zoomalbum.feature.canvas.viewmodel.CanvasViewModel
import kotlin.math.atan2

@Composable
fun CanvasScreen(
    viewModel: CanvasViewModel = hiltViewModel(),
    /**
     * Shared single-player playback holder (`video.md § 6`). Hoisted in
     * `CanvasScaffold`; View-mode taps on a video toggle playback through it
     * and the render loop mounts its ExoPlayer surface over the active node.
     */
    playbackController: VideoPlaybackController,
    onShowContextMenu: (ContextMenuRequest) -> Unit = {},
    /**
     * Fired when the user starts any canvas gesture that should dismiss an open
     * context menu (tap, double-tap, rect-select drag-start, camera pan/pinch/
     * rotate, node body/handle drag). NOT fired on long-press, because the
     * long-press path produces a new [ContextMenuRequest] via [onShowContextMenu]
     * which replaces the existing popup naturally (no transient dismiss frame).
     *
     * Back-press is not routed through here either — it's handled by a
     * `BackHandler` in `CanvasScaffold` (gated on menu-open) because the popup
     * uses `focusable = false` and so cannot receive key events itself.
     *
     * See `docs/architecture/context-menu.md § 3 — Dismissal rules`.
     */
    onCanvasGesture: () -> Unit = {},
    /**
     * True iff a context-menu popup is currently open. While open:
     *
     * - Discrete gestures (tap, double-tap, rect-select drag-start) dismiss the
     *   popup via [onCanvasGesture] and **suppress** their normal canvas action
     *   so users can close the popup without losing their selection or
     *   triggering a camera reset.
     * - Continuous gestures (camera pan/pinch/rotate, node body/handle drag)
     *   dismiss the popup *and* proceed — the user's edit intent is preserved.
     * - Long-press fires normally to support single-gesture popup replacement
     *   on another node.
     */
    isContextMenuOpen: Boolean = false,
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

    // Marquee start point in SCREEN coords ([0] = sx, [1] = sy). Stored
    // directly without world conversion — the marquee is screen-axis-aligned
    // and the per-node intersection check happens in screen space via
    // `TransformUtils.toScreenBoundingBox`. See `selection.md § 2`.
    val marqueeStartScreen = remember { floatArrayOf(0f, 0f) }

    // Long-press → context-menu handoff. `onLongPress` resolves the press-time
    // route from `GestureRouter`; `onLongPressLift` derives the lift route
    // from it. The router returns only `hasPicker` (it doesn't depend on the
    // domain `CanvasNode` type), so the raw picker hit list is stashed
    // separately for the popup wiring. Mutable state is captured by reference
    // via this remembered holder so it's stable across recompositions (the
    // gesture lambdas need to read values set in a sibling lambda).
    val longPressCtx = remember {
        object {
            var pressRoute: LongPressRoute = LongPressRoute.Suppress
            var pickerNodes: List<CanvasNode>? = null
        }
    }

    // Marquee gesture state. `active = true` only when `routeMarqueeStart`
    // returned `Start`; the DismissContextMenuOnly route sets `active = false`
    // so subsequent update / end callbacks short-circuit and do NOT create a
    // selection rectangle on the dismiss gesture.
    val marqueeCtx = remember {
        object {
            var active: Boolean = false
        }
    }

    // Eraser scrub gesture state. `allowed = true` only when
    // `routeEraserScrubStart` returned `Start` at scrub-start time; subsequent
    // per-cross callbacks short-circuit otherwise so a dismiss gesture
    // (popup-open + drag) doesn't also delete nodes. Each crossing is its
    // own `DeleteNodes(setOf(id))` dispatch — no Begin/Finish protocol,
    // no orphan-recovery needed.
    val eraserCtx = remember {
        object {
            var allowed: Boolean = false
        }
    }

    // `tapAndLongPressGestures` is implemented as `pointerInput(Unit) { … }`,
    // which captures its callbacks ONCE at first composition and never restarts.
    // Plain primitive parameters (`isContextMenuOpen`) captured by those lambdas
    // would be stuck at their initial value. `rememberUpdatedState` wraps the
    // boolean in a stable State holder whose `.value` reads fresh on each
    // lambda invocation — so the gesture handlers see the live menu-open flag.
    val isContextMenuOpenLatest by rememberUpdatedState(isContextMenuOpen)

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
            // Only active when something is selected AND the routing policy
            // allows selected-node transform under the current mode + tool.
            // Selection persists across tool switches by design, so we can't
            // gate purely on `selectedNodeIds`: an Eraser-active user with a
            // leftover selection must not be able to drag/resize/rotate that
            // node. Same applies to View / Presentation. See
            // `GestureRouter.routeSelectedNodeTransformStart`.
            //
            // Passing `emptySet()` short-circuits the detector at its
            // `pointerInput(selectedNodeIds)` keying — events flow through
            // to the tap and canvas layers untouched.
            .nodeInteractionGestures(
                selectedNodeIds = transformableSelectionIds(state),
                hitTestBody = { x, y -> viewModel.isOnSelectedNode(x, y) },
                hitTestHandle = { x, y -> viewModel.hitTestHandle(x, y) },
                hitTestRotationHandle = { x, y ->
                    // CropEdit doesn't expose a rotation handle (no source-pixel
                    // rotation in v1; whole-node rotation belongs to Selection).
                    if (state.editor.activeTool === EditorTool.CropEdit) false
                    else viewModel.hitTestRotationHandle(x, y)
                },
                hitTestEdgeHandle = { x, y ->
                    // Edge handles only render + hit in CropEdit.
                    if (state.editor.activeTool === EditorTool.CropEdit) {
                        viewModel.hitTestEdgeHandle(x, y)
                    } else null
                },
                onDrag = { dx, dy ->
                    val (wdx, wdy) = TransformUtils.screenDeltaToWorld(dx, dy, state.camera)
                    if (state.editor.activeTool === EditorTool.CropEdit) {
                        // Body drag in CropEdit pans the source pixels under the
                        // viewport — `editor-tools.md § 4.8`.
                        val mediaId = state.editor.selectedNodeIds.firstOrNull()
                            ?: return@nodeInteractionGestures
                        viewModel.onAction(CanvasAction.PanCropSource(mediaId, wdx, wdy))
                    } else {
                        viewModel.onAction(CanvasAction.MoveSelection(wdx, wdy))
                    }
                },
                onResizeDrag = { handle, dx, dy ->
                    val (worldDx, worldDy) = TransformUtils.screenDeltaToWorld(dx, dy, state.camera)
                    val tool = state.editor.activeTool
                    if (tool === EditorTool.CropEdit) {
                        // CropEdit corner drag — always routes through
                        // ResizeMediaFreeCorner so the source-stability
                        // compensation in `applyMediaCornerEdgeResize` fires
                        // (Fix 2). Aspect-lock is enforced by projecting the
                        // world delta onto the corner's diagonal in node-local
                        // coords, then rotating back to world.
                        val mediaId = state.editor.selectedNodeIds.firstOrNull()
                            ?: return@nodeInteractionGestures
                        val t = viewModel.selectionTransform() ?: return@nodeInteractionGestures
                        val (constrainedWx, constrainedWy) = if (state.editor.cropEdit.aspectLocked) {
                            val (sx, sy) = when (handle) {
                                ResizeHandle.TOP_LEFT -> -1f to -1f
                                ResizeHandle.TOP_RIGHT -> 1f to -1f
                                ResizeHandle.BOTTOM_LEFT -> -1f to 1f
                                ResizeHandle.BOTTOM_RIGHT -> 1f to 1f
                            }
                            val (lx, ly) = TransformUtils.rotateVector(worldDx, worldDy, -t.rotation)
                            // Project (lx, ly) onto the corner's unit-diagonal
                            // direction (sx, sy)/√2. Resulting constrained delta
                            // moves the dragged corner along its diagonal only,
                            // preserving the aspect ratio of the rect.
                            val dot = sx * lx + sy * ly
                            val cLx = sx * dot / 2f
                            val cLy = sy * dot / 2f
                            TransformUtils.rotateVector(cLx, cLy, t.rotation)
                        } else {
                            worldDx to worldDy
                        }
                        viewModel.onAction(
                            CanvasAction.ResizeMediaFreeCorner(
                                mediaId, handle, constrainedWx, constrainedWy,
                            ),
                        )
                        return@nodeInteractionGestures
                    }
                    // Selection-tool corner drag — uniform scale around opposite
                    // corner via ResizeSelection (does NOT compensate Manual crop
                    // offsets; that's the user's choice for whole-object resize).
                    val t = viewModel.selectionTransform() ?: return@nodeInteractionGestures
                    val halfW = t.renderW / 2f
                    val halfH = t.renderH / 2f
                    val (cornerX, cornerY) = when (handle) {
                        ResizeHandle.TOP_LEFT -> -halfW to -halfH
                        ResizeHandle.TOP_RIGHT -> halfW to -halfH
                        ResizeHandle.BOTTOM_LEFT -> -halfW to halfH
                        ResizeHandle.BOTTOM_RIGHT -> halfW to halfH
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
                onEdgeResizeDrag = { edge, dx, dy ->
                    // CropEdit only — edge handles aren't rendered or hit in
                    // any other tool. Edges always ignore aspect lock.
                    val mediaId = state.editor.selectedNodeIds.firstOrNull()
                        ?: return@nodeInteractionGestures
                    val (worldDx, worldDy) = TransformUtils.screenDeltaToWorld(dx, dy, state.camera)
                    viewModel.onAction(
                        CanvasAction.ResizeMediaEdge(mediaId, edge, worldDx, worldDy),
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
                    // Layer 1 (node body drag / resize / rotation handle): dismiss
                    // the popup on gesture start, but proceed with the interaction.
                    // Same rationale as the camera-gesture branch — continuous edit
                    // intent shouldn't be lost just because the menu was open.
                    if (isContextMenuOpenLatest) onCanvasGesture()
                    viewModel.onAction(CanvasAction.BeginInteraction(kind))
                },
                onDragEnd = {
                    rotAngleRef[1] = 0f // reset for next rotation gesture
                    viewModel.onAction(CanvasAction.FinishInteraction)
                },
            )
            // Layer 2a: Selection drag-on-empty marquee — Main pass handler.
            // Recognizes single-finger drag-on-empty past touch slop and
            // dispatches `UpdateSelectionRect` (or dismisses an open popup
            // and suppresses the marquee, per the router's
            // `routeMarqueeStart`). Gated off when the policy returns
            // `Suppress` so a future View-mode single-finger pan and the
            // Eraser scrub detector can claim drag-on-empty without
            // competing here.
            .selectionMarqueeGestures(
                enabled = isMarqueeEnabled(state),
                // Marquee may only start where no Layer-1 interactive surface
                // sits. Handles + rotation handle aren't returned by
                // `hitTest`, so checking only nodes would let a marquee fire
                // alongside a resize / rotate drag — visible as a stray
                // rectangle drawn during the transform gesture.
                canStartHere = { x, y ->
                    viewModel.hitTest(x, y) == null &&
                        viewModel.hitTestHandle(x, y) == null &&
                        !viewModel.hitTestRotationHandle(x, y)
                },
                onMarqueeStart = { sx, sy ->
                    val ctx = routingContext(state, isContextMenuOpenLatest)
                    when (GestureRouter.routeMarqueeStart(ctx)) {
                        MarqueeStartRoute.Start -> {
                            marqueeCtx.active = true
                            // Screen coords — `selectionRect` is screen-space
                            // so the marquee stays axis-aligned to the screen
                            // under camera rotation.
                            marqueeStartScreen[0] = sx
                            marqueeStartScreen[1] = sy
                            viewModel.onAction(
                                CanvasAction.UpdateSelectionRect(
                                    com.mamton.zoomalbum.core.math.BoundingBox(sx, sy, sx, sy),
                                ),
                            )
                        }
                        MarqueeStartRoute.DismissContextMenuOnly -> {
                            marqueeCtx.active = false
                            onCanvasGesture()
                        }
                        MarqueeStartRoute.Suppress -> {
                            // Defensive — `enabled` should already gate this off.
                            marqueeCtx.active = false
                        }
                    }
                },
                onMarqueeUpdate = { sx, sy ->
                    if (!marqueeCtx.active) return@selectionMarqueeGestures
                    val startX = marqueeStartScreen[0]
                    val startY = marqueeStartScreen[1]
                    viewModel.onAction(
                        CanvasAction.UpdateSelectionRect(
                            com.mamton.zoomalbum.core.math.BoundingBox(
                                left = kotlin.math.min(startX, sx),
                                top = kotlin.math.min(startY, sy),
                                right = kotlin.math.max(startX, sx),
                                bottom = kotlin.math.max(startY, sy),
                            ),
                        ),
                    )
                },
                onMarqueeEnd = {
                    if (!marqueeCtx.active) return@selectionMarqueeGestures
                    marqueeCtx.active = false
                    val rect = state.editor.selectionRect
                    if (rect != null) {
                        // Additive iff selection was non-empty when the rect
                        // started. Marquee doesn't mutate selectedNodeIds
                        // during drag, so reading it at onMarqueeEnd reflects
                        // the pre-drag state.
                        val additive = state.editor.selectedNodeIds.isNotEmpty()
                        viewModel.onAction(
                            CanvasAction.SelectNodesInRect(rect, additive = additive),
                        )
                    }
                },
            )
            // Layer 2c: Object-mode Eraser scrub — Main pass handler.
            // Single-finger drag past slop in `Edit + Eraser` deletes each
            // crossed node live as its own `DeleteNodes(setOf(id))` —
            // immediate visual feedback, one undo entry per node (symmetric
            // with tap-on-node Eraser). Gated off in any other mode/tool.
            // Mutually exclusive with `selectionMarqueeGestures` by router
            // policy.
            .eraserScrubGestures(
                enabled = isEraserScrubEnabled(state),
                hitTestNode = { x, y -> viewModel.hitTest(x, y)?.id },
                onScrubStart = {
                    val ctx = routingContext(state, isContextMenuOpenLatest)
                    when (GestureRouter.routeEraserScrubStart(ctx)) {
                        EraserScrubStartRoute.Start -> eraserCtx.allowed = true
                        EraserScrubStartRoute.DismissContextMenuOnly -> {
                            // Drag-with-popup-open: dismiss the popup, do
                            // NOT delete on this gesture. Re-grip to scrub.
                            eraserCtx.allowed = false
                            onCanvasGesture()
                        }
                        // Defensive — `enabled` should already gate this off.
                        EraserScrubStartRoute.Suppress -> eraserCtx.allowed = false
                    }
                },
                onCrossNode = { nodeId ->
                    if (!eraserCtx.allowed) return@eraserScrubGestures
                    // Per-crossing atomic delete — each becomes its own
                    // undo entry, symmetric with tap-on-node Eraser.
                    viewModel.onAction(CanvasAction.DeleteNodes(setOf(nodeId)))
                },
            )
            // Layer 2d: View-mode single-finger pan — Main pass handler.
            // In View mode there's no active tool claiming single-finger
            // input, so one finger drives the camera. Gated off in Edit /
            // Presentation by `routeViewPanStart`. Mutually exclusive with
            // marquee + eraser scrub (those are Edit-only).
            .viewModePanGestures(
                enabled = isViewPanEnabled(state),
                onPanStart = {
                    val ctx = routingContext(state, isContextMenuOpenLatest)
                    if (GestureRouter.routeViewPanStart(ctx) is
                        ViewPanRoute.DismissContextMenuAndProceed) {
                        onCanvasGesture()
                    }
                },
                onPan = { dx, dy ->
                    // Single-finger pan = pure translation; zoom and rotation
                    // identity. Centroid is irrelevant for pan-only (cancels
                    // out in the camera math) — pass Offset.Zero.
                    viewModel.onGesture(
                        centroid = Offset.Zero,
                        pan = Offset(dx, dy),
                        zoom = 1f,
                        rotationDelta = 0f,
                    )
                },
            )
            // Layer 2b: tap + double-tap + long-press — single Main pass handler.
            // All semantic decisions go through `GestureRouter`; the callback
            // bodies are pure dispatch translation. Mode/tool branching never
            // lives inline here. Drag-on-empty rect-select lives in
            // `selectionMarqueeGestures` (Layer 2a). Eraser scrub lives in
            // `eraserScrubGestures` (Layer 2c). View-mode single-finger pan
            // lives in `viewModePanGestures` (Layer 2d).
            .tapAndLongPressGestures(
                onTap = { offset ->
                    val hit = viewModel.hitTest(offset.x, offset.y)
                    val ctx = routingContext(state, isContextMenuOpenLatest)
                    when (val route = GestureRouter.routeTap(ctx, hit?.id)) {
                        TapRoute.DismissContextMenuOnly -> onCanvasGesture()
                        is TapRoute.EditSelect ->
                            viewModel.onAction(CanvasAction.SelectNode(route.nodeId))
                        TapRoute.EditDeselectAll ->
                            viewModel.onAction(CanvasAction.DeselectAll)
                        is TapRoute.EraserDeleteNode ->
                            viewModel.onAction(CanvasAction.DeleteNodes(setOf(route.nodeId)))
                        is TapRoute.ViewFocus ->
                            viewModel.onAction(CanvasAction.FocusNode(route.nodeId))
                        TapRoute.Ignore -> Unit
                    }
                },
                onDoubleTap = { offset ->
                    // Double-tap a video → play/pause (uniform across modes). Hit-
                    // test so the router can branch on video-ness; non-video
                    // double-taps keep their mode default (camera reset / no-op).
                    val hit = viewModel.hitTest(offset.x, offset.y)
                    val hitIsVideo = (hit as? CanvasNode.Media)?.mediaType == MediaType.VIDEO
                    val ctx = routingContext(state, isContextMenuOpenLatest)
                    when (GestureRouter.routeDoubleTap(ctx, hit?.id, hitIsVideo)) {
                        DoubleTapRoute.DismissContextMenuOnly -> onCanvasGesture()
                        DoubleTapRoute.ResetCamera -> viewModel.reset()
                        is DoubleTapRoute.PlayPauseVideo ->
                            (hit as? CanvasNode.Media)?.let {
                                playbackController.togglePlayback(it.id, it.mediaRefId)
                            }
                        DoubleTapRoute.Ignore -> Unit
                    }
                },
                onLongPress = { screenX, screenY ->
                    // Reset the press-route handoff for this gesture.
                    longPressCtx.pressRoute = LongPressRoute.Suppress
                    longPressCtx.pickerNodes = null

                    // Hit-test only in Edit; View / Presentation swallow long-press
                    // unconditionally per the router. Skipping the hit test here
                    // saves work; the router's contract handles either input the
                    // same in non-Edit modes.
                    val hits = if (state.editor.mode == CanvasInteractionMode.Edit) {
                        viewModel.hitTestAll(screenX, screenY)
                    } else {
                        emptyList()
                    }
                    val ctx = routingContext(state, isContextMenuOpenLatest)
                    val route = GestureRouter.routeLongPress(ctx, hits.map { it.id })
                    longPressCtx.pressRoute = route
                    when (route) {
                        // Suppressed (View / Presentation / any Edit tool on
                        // empty canvas). Detector consumes events until
                        // pointer-up — no selection or popup state changes.
                        LongPressRoute.Suppress -> Unit
                        is LongPressRoute.ResolveAnchor -> {
                            if (route.extendsSelection) {
                                viewModel.onAction(
                                    CanvasAction.AddNodeToSelection(route.anchorNodeId),
                                )
                            }
                            if (route.hasPicker) longPressCtx.pickerNodes = hits
                        }
                    }
                },
                onLongPressLift = { screenX, screenY ->
                    val ctx = routingContext(state, isContextMenuOpenLatest)
                    when (GestureRouter.routeLongPressLift(ctx, longPressCtx.pressRoute)) {
                        LongPressLiftRoute.Suppress -> Unit
                        LongPressLiftRoute.OpenMenu -> {
                            val anchor = (longPressCtx.pressRoute as? LongPressRoute.ResolveAnchor)
                                ?.anchorNodeId
                            onShowContextMenu(
                                ContextMenuRequest(
                                    selection = viewModel.state.value.editor.selectedNodeIds,
                                    anchorNodeId = anchor,
                                    anchorScreenX = screenX,
                                    anchorScreenY = screenY,
                                    pickerNodes = longPressCtx.pickerNodes,
                                ),
                            )
                        }
                    }
                },
            )
            // Layer 3: canvas pan/zoom/rotate — Main pass handler.
            // Camera gestures stream many updates per second. The router
            // emits `DismissContextMenuAndProceed` for every event while the
            // popup is open; `onCanvasGesture` is idempotent so repeated
            // calls just leave the popup closed. (Differs from tap /
            // drag-start, where the gesture is discrete and "dismiss only"
            // is the route.)
            .infiniteCanvasGestures(
                onGestureBegin = {
                    // CropEdit captures pinch + two-finger pan as source-edit
                    // gestures; wrap the whole pinch session in one undo entry.
                    if (viewModel.state.value.editor.activeTool ===
                        EditorTool.CropEdit
                    ) {
                        viewModel.onAction(
                            CanvasAction.BeginInteraction(
                                InteractionKind.RESIZE,
                            ),
                        )
                    }
                },
                onGestureEnd = {
                    if (viewModel.state.value.editor.activeTool ===
                        EditorTool.CropEdit
                    ) {
                        viewModel.onAction(CanvasAction.FinishInteraction)
                    }
                },
                onGesture = onGesture@{ centroid, pan, zoom, rotation ->
                    val ctx = routingContext(state, isContextMenuOpenLatest)
                    when (GestureRouter.routeCameraTransformStart(ctx)) {
                        CameraTransformRoute.DismissContextMenuAndProceed -> onCanvasGesture()
                        CameraTransformRoute.Allow -> Unit
                    }
                    if (state.editor.activeTool ===
                        EditorTool.CropEdit
                    ) {
                        // Two-finger pinch in CropEdit drives source pan/zoom,
                        // not the camera. Rotation is intentionally dropped —
                        // source rotation is out of scope per editor-tools.md
                        // § 4.8.
                        val mediaId = state.editor.selectedNodeIds.firstOrNull()
                        if (mediaId != null) {
                            val (wcx, wcy) = TransformUtils.screenToWorld(
                                centroid.x, centroid.y, state.camera,
                            )
                            val (wpx, wpy) = TransformUtils.screenDeltaToWorld(
                                pan.x, pan.y, state.camera,
                            )
                            viewModel.onAction(
                                CanvasAction.PinchCropSource(
                                    nodeId = mediaId,
                                    worldCentroidX = wcx,
                                    worldCentroidY = wcy,
                                    worldPanX = wpx,
                                    worldPanY = wpy,
                                    zoomFactor = zoom,
                                ),
                            )
                            return@onGesture
                        }
                    }
                    viewModel.onGesture(centroid, pan, zoom, rotation)
                },
            ),
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

            // Paint visible nodes in z-order. Most frames render as a single
            // pass via CanvasNodeRenderer; frames with FrameAppearance.overlays
            // need their paint split so member nodes sandwich between the frame's
            // background (Surface phase) and its overlays + border (Overlay
            // phase). See docs/architecture/rendering.md § 6b.
            val membershipUseCase = remember { FrameMembershipUseCase() }
            val paintEvents = remember(state.visibleNodes) {
                buildFramePaintEvents(state.visibleNodes, membershipUseCase)
            }
            // Full-detail videos render through a Compose surface (the proven
            // mask path), NOT FullMediaRenderer — a video frame in
            // FullMediaRenderer's offscreen masked as opaque black. Not playing →
            // VideoPosterSurface inline here (keeps z-order). Playing → its
            // VideoPlayerSurface paints on top (below). Lower-LOD videos and all
            // other nodes keep CanvasNodeRenderer.
            val playingVideoIds = playbackController.assignments.keys
            for (event in paintEvents) {
                when (event) {
                    is FramePaintEvent.NodePass -> {
                        val n = event.node
                        val isFullVideo = n is CanvasNode.Media &&
                            n.mediaType == MediaType.VIDEO &&
                            event.detail == RenderDetail.Full
                        if (isFullVideo) {
                            if (n.id !in playingVideoIds) {
                                key(n.id) {
                                    val poster = rememberVideoPosterBitmap(n.mediaRefId)
                                    if (poster != null) {
                                        VideoPosterSurface(node = n, bitmap = poster)
                                    }
                                }
                            }
                        } else {
                            CanvasNodeRenderer(n, event.detail)
                        }
                    }
                    is FramePaintEvent.LayeredFrameSurface ->
                        FrameRendererPhased(event.frame, event.detail, FramePaintPhase.Surface)
                    is FramePaintEvent.LayeredFrameOverlay ->
                        FrameRendererPhased(event.frame, event.detail, FramePaintPhase.Overlay)
                }
            }

            // Video playback: a bounded pool of ExoPlayers mounted over the
            // playing video nodes, painted on top of their posters. Candidacy is
            // LOD-bounded — only RenderDetail.Full videos can hold a player; when
            // a node leaves the viewport or drops below Full it loses its player
            // and the poster shows again (`video.md § 5–6`, `todo.md § 27.5`).
            val fullVideoIds = remember(state.visibleNodes) {
                state.visibleNodes.asSequence()
                    .filter { it.detail == RenderDetail.Full }
                    .map { it.node }
                    .filterIsInstance<CanvasNode.Media>()
                    .filter { it.mediaType == MediaType.VIDEO }
                    .map { it.id }
                    .toSet()
            }
            LaunchedEffect(fullVideoIds, playbackController.playingNodeIds) {
                playbackController.reconcile(fullVideoIds)
            }
            for ((nodeId, player) in playbackController.assignments) {
                key(nodeId) {
                    val mediaNode = state.visibleNodes
                        .firstOrNull { it.node.id == nodeId }?.node as? CanvasNode.Media
                    if (mediaNode != null) {
                        VideoPlayerSurface(node = mediaNode, player = player)
                    }
                }
            }

            // Selection overlay — drawn on top of all nodes
            val selectedNodes = state.visibleNodes
                .filter { it.node.id in state.editor.selectedNodeIds }
                .map { it.node }
            if (selectedNodes.isNotEmpty()) {
                SelectionOverlay(
                    selectedNodes = selectedNodes,
                    cameraScale = state.camera.scale,
                    rotationHandleEnabled = true, // TODO: wire to InteractionSettings
                    groupTransform = state.editor.groupSelectionTransform,
                    anchorNodeId = state.editor.contextAnchorNodeId,
                    cropEdit = state.editor.activeTool === EditorTool.CropEdit,
                )
            }

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

        // Marquee rectangle — drawn OUTSIDE the camera-transformed inner Box
        // so it stays axis-aligned to the screen even when the camera is
        // rotated. `selectionRect` is stored in screen coords.
        state.editor.selectionRect?.let { rect -> SelectionRectOverlay(rect) }

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

/**
 * Builds the [GestureRoutingContext] read by [GestureRouter] from the
 * current [CanvasState] + popup-open flag. The router is pure / testable
 * and knows nothing about Compose, so the call site is responsible for
 * snapshotting the relevant editor fields. Kept here (not on `EditorState`)
 * because the popup-open flag lives in `CanvasScaffold`, not the editor
 * model — see `editor-tools.md § 7.1` (UI-surface state stays out of
 * `EditorState`).
 */
private fun routingContext(
    state: CanvasState,
    isContextMenuOpen: Boolean,
): GestureRoutingContext = GestureRoutingContext(
    mode = state.editor.mode,
    activeTool = state.editor.activeTool,
    hasSelection = state.editor.selectedNodeIds.isNotEmpty(),
    isContextMenuOpen = isContextMenuOpen,
)

/**
 * Returns the selection ids that should be treated as interactive by
 * [nodeInteractionGestures]. Empty when the router blocks selected-node
 * transform — selection persists in the model, but body / handle drags must
 * not respond. See [GestureRouter.routeSelectedNodeTransformStart].
 *
 * The popup-open flag is intentionally not threaded in: popup state
 * dismissal on transform start happens inside `onDragBegin` and does not
 * itself block the transform, so the routing context here pretends the
 * popup is closed. This keeps `pointerInput` keying stable across popup
 * open/close cycles.
 */
private fun transformableSelectionIds(state: CanvasState): Set<String> {
    val ctx = GestureRoutingContext(
        mode = state.editor.mode,
        activeTool = state.editor.activeTool,
        hasSelection = state.editor.selectedNodeIds.isNotEmpty(),
        isContextMenuOpen = false,
    )
    return when (GestureRouter.routeSelectedNodeTransformStart(ctx)) {
        SelectedNodeTransformRoute.Allow -> state.editor.selectedNodeIds
        SelectedNodeTransformRoute.Block -> emptySet()
    }
}

/**
 * `enabled` input to [selectionMarqueeGestures]. Stable across popup state
 * by construction — the popup-open branch produces
 * [MarqueeStartRoute.DismissContextMenuOnly], which the detector still
 * needs to run for (so it consumes the gesture and the caller can dismiss
 * the popup). Keying the detector off `isContextMenuOpen` would also
 * restart its `pointerInput` mid-gesture if the popup closes during a
 * drag, which would drop the in-progress marquee.
 */
private fun isMarqueeEnabled(state: CanvasState): Boolean {
    val ctxNoPopup = GestureRoutingContext(
        mode = state.editor.mode,
        activeTool = state.editor.activeTool,
        hasSelection = state.editor.selectedNodeIds.isNotEmpty(),
        isContextMenuOpen = false,
    )
    return GestureRouter.routeMarqueeStart(ctxNoPopup) != MarqueeStartRoute.Suppress
}

/**
 * `enabled` input to [eraserScrubGestures]. Same popup-state stability
 * rationale as [isMarqueeEnabled].
 */
private fun isEraserScrubEnabled(state: CanvasState): Boolean {
    val ctxNoPopup = GestureRoutingContext(
        mode = state.editor.mode,
        activeTool = state.editor.activeTool,
        hasSelection = state.editor.selectedNodeIds.isNotEmpty(),
        isContextMenuOpen = false,
    )
    return GestureRouter.routeEraserScrubStart(ctxNoPopup) != EraserScrubStartRoute.Suppress
}

/**
 * `enabled` input to [viewModePanGestures]. View pan is mode-only — it
 * doesn't depend on tool, selection, or popup state for the enable
 * decision (popup-open just adds a dismiss-then-pan step at
 * gesture-start time). Same popup-state stability rationale as
 * [isMarqueeEnabled] — `pointerInput` keying must not churn on every
 * popup open/close.
 */
private fun isViewPanEnabled(state: CanvasState): Boolean {
    val ctxNoPopup = GestureRoutingContext(
        mode = state.editor.mode,
        activeTool = state.editor.activeTool,
        hasSelection = state.editor.selectedNodeIds.isNotEmpty(),
        isContextMenuOpen = false,
    )
    return GestureRouter.routeViewPanStart(ctxNoPopup) != ViewPanRoute.Suppress
}

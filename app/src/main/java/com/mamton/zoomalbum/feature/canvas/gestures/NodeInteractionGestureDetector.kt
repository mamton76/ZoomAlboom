package com.mamton.zoomalbum.feature.canvas.gestures

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import com.mamton.zoomalbum.core.math.ResizeHandle
import com.mamton.zoomalbum.domain.undo.InteractionKind

/**
 * Gesture layer for interacting with selected canvas nodes.
 *
 * Placed **outermost** in the modifier chain so it sees pointer events at
 * [PointerEventPass.Initial] before [tapAndLongPressGestures] and
 * [infiniteCanvasGestures] (which run at [PointerEventPass.Main]).
 *
 * When the pointer lands on a resize handle, rotation handle, or the
 * selected node body, this modifier consumes all events for that gesture,
 * preventing canvas gestures from activating. Otherwise it returns
 * immediately without consuming, letting events flow through to the
 * tap and canvas gesture layers.
 *
 * Long-press (overlap picker, rectangle selection) is handled separately
 * via `tapAndLongPressGestures` in the tap layer — NOT here.
 * Mixing long-press detection with Initial-pass event reading breaks
 * tap detection in lower layers.
 */
fun Modifier.nodeInteractionGestures(
    selectedNodeIds: Set<String>,
    hitTestBody: (screenX: Float, screenY: Float) -> Boolean,
    hitTestHandle: (screenX: Float, screenY: Float) -> ResizeHandle?,
    hitTestRotationHandle: (screenX: Float, screenY: Float) -> Boolean,
    onDrag: (screenDx: Float, screenDy: Float) -> Unit,
    onResizeDrag: (handle: ResizeHandle, screenDx: Float, screenDy: Float) -> Unit,
    onRotationDragPosition: (screenX: Float, screenY: Float) -> Unit,
    onDragBegin: (kind: InteractionKind) -> Unit,
    onDragEnd: () -> Unit,
): Modifier = this.pointerInput(selectedNodeIds) {
    if (selectedNodeIds.isEmpty()) return@pointerInput

    awaitEachGesture {
        val down = awaitFirstDown(pass = PointerEventPass.Initial)
        val downX = down.position.x
        val downY = down.position.y

        // All three interactive zones use deferred-consume: wait for movement
        // beyond touchSlop before committing to a drag. Until then, events
        // flow through to the Main pass so taps can reach tap/long-press
        // handlers — needed for tap-to-collapse on multi-selection and
        // long-press-to-toggle on selected nodes.
        val slopSq = viewConfiguration.touchSlop * viewConfiguration.touchSlop

        // Priority 1: resize handle
        val handle = hitTestHandle(downX, downY)
        if (handle != null) {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                if (event.changes.size > 1) return@awaitEachGesture
                val change = event.changes.firstOrNull() ?: return@awaitEachGesture
                if (change.changedToUp() || change.isConsumed) return@awaitEachGesture
                val dx = change.position.x - downX
                val dy = change.position.y - downY
                if (dx * dx + dy * dy > slopSq) {
                    change.consume()
                    onDragBegin(InteractionKind.RESIZE)
                    dragLoop(
                        onDelta = { mdx, mdy -> onResizeDrag(handle, mdx, mdy) },
                        onEnd = onDragEnd,
                    )
                    return@awaitEachGesture
                }
            }
        }

        // Priority 2: rotation handle — emit absolute screen positions
        if (hitTestRotationHandle(downX, downY)) {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                if (event.changes.size > 1) return@awaitEachGesture
                val change = event.changes.firstOrNull() ?: return@awaitEachGesture
                if (change.changedToUp() || change.isConsumed) return@awaitEachGesture
                val dx = change.position.x - downX
                val dy = change.position.y - downY
                if (dx * dx + dy * dy > slopSq) {
                    change.consume()
                    onDragBegin(InteractionKind.ROTATE)
                    positionLoop(
                        onPosition = { x, y -> onRotationDragPosition(x, y) },
                        onEnd = onDragEnd,
                    )
                    return@awaitEachGesture
                }
            }
        }

        // Priority 3: selected node body
        if (hitTestBody(downX, downY)) {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                if (event.changes.size > 1) return@awaitEachGesture
                val change = event.changes.firstOrNull() ?: return@awaitEachGesture
                if (change.changedToUp() || change.isConsumed) return@awaitEachGesture
                val dx = change.position.x - downX
                val dy = change.position.y - downY
                if (dx * dx + dy * dy > slopSq) {
                    change.consume()
                    onDragBegin(InteractionKind.MOVE)
                    dragLoop(
                        onDelta = { mdx, mdy -> onDrag(mdx, mdy) },
                        onEnd = onDragEnd,
                    )
                    return@awaitEachGesture
                }
            }
        }

        // Not on anything interactive → pass through immediately
        // (no event reading/consuming — tap + canvas layers handle it)
    }
}

/**
 * Drag loop emitting movement deltas until all pointers lift.
 */
private suspend fun AwaitPointerEventScope.dragLoop(
    onDelta: (dx: Float, dy: Float) -> Unit,
    onEnd: () -> Unit,
) {
    while (true) {
        val event = awaitPointerEvent(PointerEventPass.Initial)
        if (event.changes.size > 1) {
            // Second finger arrived mid-drag → cancel node interaction so the
            // two-finger canvas gesture can take over.
            onEnd()
            break
        }
        val change = event.changes.firstOrNull() ?: break
        if (change.changedToUp()) {
            change.consume()
            onEnd()
            break
        }
        val delta = change.positionChange()
        if (delta.x != 0f || delta.y != 0f) {
            onDelta(delta.x, delta.y)
        }
        change.consume()
    }
}

/**
 * Drag loop emitting absolute screen positions until all pointers lift.
 * Used for rotation handle where atan2 needs position, not delta.
 */
private suspend fun AwaitPointerEventScope.positionLoop(
    onPosition: (screenX: Float, screenY: Float) -> Unit,
    onEnd: () -> Unit,
) {
    while (true) {
        val event = awaitPointerEvent(PointerEventPass.Initial)
        if (event.changes.size > 1) {
            onEnd()
            break
        }
        val change = event.changes.firstOrNull() ?: break
        if (change.changedToUp()) {
            change.consume()
            onEnd()
            break
        }
        onPosition(change.position.x, change.position.y)
        change.consume()
    }
}

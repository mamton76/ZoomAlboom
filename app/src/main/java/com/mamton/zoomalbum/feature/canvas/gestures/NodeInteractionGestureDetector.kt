package com.mamton.zoomalbum.feature.canvas.gestures

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import com.mamton.zoomalbum.core.math.ResizeHandle

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
 * via [tapAndLongPressGestures.onLongPress] in the tap layer — NOT here.
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
    onDragEnd: () -> Unit,
): Modifier = this.pointerInput(selectedNodeIds) {
    if (selectedNodeIds.isEmpty()) return@pointerInput

    awaitEachGesture {
        val down = awaitFirstDown(pass = PointerEventPass.Initial)
        val downX = down.position.x
        val downY = down.position.y

        // Priority 1: resize handle
        val handle = hitTestHandle(downX, downY)
        if (handle != null) {
            down.consume()
            dragLoop(
                onDelta = { dx, dy -> onResizeDrag(handle, dx, dy) },
                onEnd = onDragEnd,
            )
            return@awaitEachGesture
        }

        // Priority 2: rotation handle — emit absolute screen positions
        if (hitTestRotationHandle(downX, downY)) {
            down.consume()
            positionLoop(
                onPosition = { x, y -> onRotationDragPosition(x, y) },
                onEnd = onDragEnd,
            )
            return@awaitEachGesture
        }

        // Priority 3: selected node body → deferred-consume move.
        //
        // Do NOT consume DOWN immediately. If we did, tap/long-press on an
        // already-selected node would never reach the Main pass — the user
        // could not tap to collapse a multi-selection, and could not
        // long-press to toggle a node out of the selection. Instead, watch
        // for movement beyond touchSlop: once the pointer actually drags,
        // we commit to a move gesture (consume + enter dragLoop). Until
        // then, events flow through to the Main pass unchanged.
        if (hitTestBody(downX, downY)) {
            val slopSq = viewConfiguration.touchSlop * viewConfiguration.touchSlop
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val change = event.changes.firstOrNull() ?: return@awaitEachGesture
                if (change.changedToUp() || change.isConsumed) {
                    // Released without moving, or another layer consumed —
                    // let Main pass handle tap/long-press.
                    return@awaitEachGesture
                }
                val dx = change.position.x - downX
                val dy = change.position.y - downY
                if (dx * dx + dy * dy > slopSq) {
                    change.consume()
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
private suspend fun androidx.compose.ui.input.pointer.AwaitPointerEventScope.dragLoop(
    onDelta: (dx: Float, dy: Float) -> Unit,
    onEnd: () -> Unit,
) {
    while (true) {
        val event = awaitPointerEvent(PointerEventPass.Initial)
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
private suspend fun androidx.compose.ui.input.pointer.AwaitPointerEventScope.positionLoop(
    onPosition: (screenX: Float, screenY: Float) -> Unit,
    onEnd: () -> Unit,
) {
    while (true) {
        val event = awaitPointerEvent(PointerEventPass.Initial)
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

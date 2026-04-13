package com.mamton.zoomalbum.feature.canvas.gestures

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput

/**
 * Single gesture handler that detects tap, double-tap, and long-press+drag.
 *
 * Combining these in one `pointerInput` avoids event conflicts between
 * separate `detectTapGestures` and `longPressDragGestures` modifiers.
 *
 * Gesture priority:
 * 1. **Long-press** (hold > [LONG_PRESS_MS] without moving) →
 *    [onLongPress] fires. If it returns `false`, enters drag loop
 *    ([onDragStart] → [onDragUpdate] → [onDragEnd]).
 * 2. **Double-tap** (two taps within [DOUBLE_TAP_MS]) → [onDoubleTap]
 * 3. **Single tap** (no second tap within [DOUBLE_TAP_MS]) → [onTap]
 */
fun Modifier.tapAndLongPressGestures(
    onTap: (Offset) -> Unit,
    onDoubleTap: (Offset) -> Unit,
    onLongPress: (screenX: Float, screenY: Float) -> Boolean,
    onDragStart: (screenX: Float, screenY: Float) -> Unit,
    onDragUpdate: (screenX: Float, screenY: Float) -> Unit,
    onDragEnd: () -> Unit,
): Modifier = this.pointerInput(Unit) {
    val touchSlop = viewConfiguration.touchSlop
    val slopSq = touchSlop * touchSlop

    awaitEachGesture {
        val down = awaitFirstDown()
        down.consume()
        val startPos = down.position

        // Phase 1: Wait for long-press timeout OR pointer lift/movement.
        val longPressReached = withTimeoutOrNull(LONG_PRESS_MS) {
            while (true) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull() ?: return@withTimeoutOrNull false
                if (change.changedToUp() || change.isConsumed) return@withTimeoutOrNull false
                val delta = change.position - startPos
                if (delta.x * delta.x + delta.y * delta.y > slopSq) {
                    return@withTimeoutOrNull false
                }
            }
            @Suppress("UNREACHABLE_CODE")
            true
        } == null // timeout = long-press

        if (longPressReached) {
            // ── Long-press path ──────────────────────────────
            val handled = onLongPress(startPos.x, startPos.y)
            if (handled) {
                consumeUntilAllUp()
            } else {
                onDragStart(startPos.x, startPos.y)
                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull() ?: break
                    if (change.changedToUp()) {
                        change.consume()
                        onDragEnd()
                        break
                    }
                    onDragUpdate(change.position.x, change.position.y)
                    change.consume()
                }
            }
            return@awaitEachGesture
        }

        // Phase 2: Not a long-press. Wait for pointer up (it's a tap or drag).
        val up = waitForUp()
        if (up == null) return@awaitEachGesture // dragged away or cancelled

        // Phase 3: First tap detected. Wait for possible second tap (double-tap).
        val secondDown = withTimeoutOrNull(DOUBLE_TAP_MS) {
            awaitFirstDown()
        }

        if (secondDown != null) {
            secondDown.consume()
            // Wait for second pointer up
            waitForUp()
            onDoubleTap(startPos)
        } else {
            onTap(startPos)
        }
    }
}

/**
 * Waits for the current pointer to lift. Returns the up change, or null
 * if the pointer moved beyond touch slop (gesture cancelled).
 */
private suspend fun androidx.compose.ui.input.pointer.AwaitPointerEventScope.waitForUp(): androidx.compose.ui.input.pointer.PointerInputChange? {
    while (true) {
        val event = awaitPointerEvent()
        val change = event.changes.firstOrNull() ?: return null
        if (change.changedToUp()) {
            change.consume()
            return change
        }
        if (change.isConsumed) return null
    }
}

private suspend fun androidx.compose.ui.input.pointer.AwaitPointerEventScope.consumeUntilAllUp() {
    while (true) {
        val event = awaitPointerEvent()
        event.changes.forEach { it.consume() }
        if (event.changes.all { it.changedToUp() }) break
    }
}

private const val LONG_PRESS_MS = 400L
private const val DOUBLE_TAP_MS = 300L

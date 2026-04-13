package com.mamton.zoomalbum.feature.canvas.gestures

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput

/**
 * Detects long-press and optional subsequent drag at [PointerEventPass.Main].
 *
 * Runs **after** [nodeInteractionGestures] (Initial pass) but at the same
 * pass as [detectTapGestures]. Because this modifier awaits events during the
 * long-press timeout without consuming them, [detectTapGestures] on a separate
 * `pointerInput` still sees quick taps normally.
 *
 * When a long-press is confirmed (finger held still for [LONG_PRESS_MS]):
 * - [onLongPress] fires with the screen position
 * - If the caller returns `true` (handled, e.g. overlap picker), events are
 *   consumed until pointer-up
 * - If the caller returns `false`, a drag loop starts for rectangle selection:
 *   [onDragStart] → [onDragUpdate] (absolute positions) → [onDragEnd]
 */
fun Modifier.longPressDragGestures(
    onLongPress: (screenX: Float, screenY: Float) -> Boolean,
    onDragStart: (screenX: Float, screenY: Float) -> Unit,
    onDragUpdate: (screenX: Float, screenY: Float) -> Unit,
    onDragEnd: () -> Unit,
): Modifier = this.pointerInput(Unit) {
    val touchSlop = viewConfiguration.touchSlop
    val slopSq = touchSlop * touchSlop

    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val startPos = down.position

        // Wait for long-press: pointer must stay within touch slop for the timeout.
        // Events are NOT consumed during detection — tap gestures still work.
        val isLongPress = withTimeoutOrNull(LONG_PRESS_MS) {
            while (true) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull() ?: return@withTimeoutOrNull false
                if (change.changedToUp()) return@withTimeoutOrNull false
                val delta = change.position - startPos
                if (delta.x * delta.x + delta.y * delta.y > slopSq) {
                    return@withTimeoutOrNull false
                }
            }
            @Suppress("UNREACHABLE_CODE")
            true
        } == null // timeout reached = long-press confirmed

        if (!isLongPress) return@awaitEachGesture

        // Long-press confirmed — consume to prevent tap from also firing
        down.consume()

        val handled = onLongPress(startPos.x, startPos.y)
        if (handled) {
            // Caller handled it (e.g. overlap picker) — just consume until up
            while (true) {
                val event = awaitPointerEvent()
                event.changes.forEach { it.consume() }
                if (event.changes.all { it.changedToUp() }) break
            }
        } else {
            // Start rectangle selection drag
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
    }
}

private const val LONG_PRESS_MS = 400L

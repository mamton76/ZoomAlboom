package com.mamton.zoomalbum.feature.canvas.gestures

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput

/**
 * Single gesture handler that detects tap, double-tap, and long-press.
 *
 * Combining these in one `pointerInput` avoids event conflicts between
 * separate `detectTapGestures` and `longPressDragGestures` modifiers.
 *
 * Gesture priority:
 * 1. **Long-press** (hold > [LONG_PRESS_MS] without moving) →
 *    [onLongPress] fires; events are then consumed until pointer-up.
 *    [onLongPressLift] fires on UP.
 * 2. **Double-tap** (two taps within [DOUBLE_TAP_MS]) → [onDoubleTap]
 * 3. **Single tap** (no second tap within [DOUBLE_TAP_MS]) → [onTap]
 *
 * Drag-on-empty for rectangle selection is **not** detected here — see
 * [selectionMarqueeGestures]. This detector reports tap / double-tap /
 * long-press only; if the user starts dragging without a prior long-press,
 * this detector's tap candidate is invalidated (its `waitForUp()` returns
 * null when another layer consumes), and the marquee detector takes over.
 */
fun Modifier.tapAndLongPressGestures(
    onTap: (Offset) -> Unit,
    onDoubleTap: (Offset) -> Unit,
    onLongPress: (screenX: Float, screenY: Float) -> Unit,
    onLongPressLift: (screenX: Float, screenY: Float) -> Unit = { _, _ -> },
): Modifier = this.pointerInput(Unit) {
    val touchSlop = viewConfiguration.touchSlop
    val slopSq = touchSlop * touchSlop

    awaitEachGesture {
        val down = awaitFirstDown()
        down.consume()
        val startPos = down.position

        // Phase 1: wait for long-press timeout OR pointer lift/movement.
        //
        // If the pointer lifts within the timeout (= tap candidate), we have
        // to remember we already saw the UP — Phase 2's waitForUp() awaits
        // the NEXT pointer event and would otherwise hang until the user
        // taps again, delivering the prior tap one gesture late.
        //
        // We also flag `slopCrossedInPhase1` separately: a gesture that
        // moved past slop is a drag, not a tap. Without this flag, Phase 2's
        // `waitForUp` could race with a sibling drag detector for the UP
        // event — if `waitForUp` wins, Phase 3 fires `onTap` after the
        // double-tap window, which (for tap on empty) dispatches
        // `DeselectAll` and silently undoes a freshly-completed marquee
        // selection.
        var sawUp = false
        var slopCrossedInPhase1 = false
        val longPressReached = withTimeoutOrNull(LONG_PRESS_MS) {
            while (true) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull() ?: return@withTimeoutOrNull false
                if (change.changedToUp()) {
                    change.consume()
                    sawUp = true
                    return@withTimeoutOrNull false
                }
                if (change.isConsumed) return@withTimeoutOrNull false
                val delta = change.position - startPos
                if (delta.x * delta.x + delta.y * delta.y > slopSq) {
                    slopCrossedInPhase1 = true
                    return@withTimeoutOrNull false
                }
            }
            @Suppress("UNREACHABLE_CODE")
            true
        } == null // timeout = long-press

        if (longPressReached) {
            // Long-press fired. Caller dispatches selection-resolution side
            // effects in [onLongPress]; the detector then consumes the rest
            // of the gesture so a stray tap / drag doesn't fire afterwards.
            // [onLongPressLift] fires on UP — the caller decides whether to
            // open the context menu by consulting `GestureRouter`.
            onLongPress(startPos.x, startPos.y)
            consumeUntilAllUp()
            onLongPressLift(startPos.x, startPos.y)
            return@awaitEachGesture
        }

        if (slopCrossedInPhase1) {
            // Drag, not a tap. The sibling drag detector
            // (`selectionMarqueeGestures` / `eraserScrubGestures`) owns this
            // gesture from here. Returning before Phase 2 is essential —
            // otherwise `waitForUp` can race the drag detector for the UP
            // event and fire a stray `onTap` (which translates to
            // `DeselectAll` on empty space, clobbering the marquee result).
            return@awaitEachGesture
        }

        // Phase 2: not a long-press, no slop crossed. If Phase 1 already
        // observed UP, we're good to go. Otherwise the pointer is either
        // still down or the gesture was cancelled by another layer
        // consuming events.
        if (!sawUp) {
            waitForUp() ?: return@awaitEachGesture
        }

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
        // IMPORTANT: check allUp BEFORE consuming. `changedToUp()` returns
        // false for consumed changes, so calling consume() first would make
        // the condition never match and the loop would hang forever,
        // preventing awaitEachGesture from looping to the next gesture.
        val allUp = event.changes.all { it.changedToUp() }
        event.changes.forEach { it.consume() }
        if (allUp) break
    }
}

private const val LONG_PRESS_MS = 400L
private const val DOUBLE_TAP_MS = 300L

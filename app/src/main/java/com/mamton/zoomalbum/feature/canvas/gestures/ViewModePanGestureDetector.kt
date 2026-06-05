package com.mamton.zoomalbum.feature.canvas.gestures

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange

/**
 * View-mode single-finger pan detector. Recognizes a single-finger drag
 * past touch slop and emits per-event position deltas so the camera can
 * pan. Gated by the router's `routeViewPanStart` via [enabled].
 *
 * In View mode the strict "two-finger nav only" rule that holds in Edit
 * (`editor-tools.md § 2`) is relaxed — there's no active tool claiming
 * single-finger input, so one finger drives the camera directly. This
 * detector is the Edit/View difference at the gesture layer; in Edit the
 * detector is gated off and tap / long-press / marquee / scrub own the
 * single-finger axis.
 *
 * Callback shape:
 *
 *  - [onPanStart] — fired once when touch slop is crossed; caller consults
 *    `routeViewPanStart` here for popup-open dismissal (mirrors the
 *    `MarqueeStartRoute.DismissContextMenuAndProceed` pattern).
 *  - [onPan] — fired for every subsequent movement event with the screen
 *    delta. Caller dispatches `viewModel.onGesture(...)` with zoom = 1 and
 *    rotation = 0 — pure translation.
 *
 * Coordination invariants:
 *
 *  - **Second finger before slop** → detector yields (no consume); the
 *    multi-finger `infiniteCanvasGestures` claims via the same DOWN.
 *  - **Second finger mid-pan** → detector exits cleanly so two-finger
 *    pinch/rotate can take over from the same gesture without dropping
 *    pan state on the floor.
 *  - **No `change.isConsumed` exit.** `tapAndLongPressGestures` consumes
 *    DOWN and that flag carries on the same pointer's subsequent change
 *    deliveries; bailing on `isConsumed` would make the detector exit on
 *    its first movement before slop is ever evaluated. See
 *    `memory/project_gesture_consumed_carry.md`. `changedToUpIgnoreConsumed`
 *    is used for lift detection for the same reason.
 */
fun Modifier.viewModePanGestures(
    enabled: Boolean,
    onPanStart: () -> Unit,
    onPan: (panDx: Float, panDy: Float) -> Unit,
): Modifier = this.pointerInput(enabled) {
    if (!enabled) return@pointerInput

    val slopSq = viewConfiguration.touchSlop * viewConfiguration.touchSlop

    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val startX = down.position.x
        val startY = down.position.y

        // Wait for slop. Lift before slop = tap (handled by Layer 2b);
        // second finger before slop = camera nav (handled by Layer 3).
        while (true) {
            val event = awaitPointerEvent()
            if (event.changes.size > 1) return@awaitEachGesture
            val change = event.changes.firstOrNull() ?: return@awaitEachGesture
            if (change.changedToUpIgnoreConsumed()) return@awaitEachGesture
            val dx = change.position.x - startX
            val dy = change.position.y - startY
            if (dx * dx + dy * dy > slopSq) {
                change.consume()
                onPanStart()
                // The slop-crossing event already represents a movement —
                // emit it so the camera starts panning on the same frame.
                onPan(dx, dy)
                break
            }
        }

        // Pan active. Emit per-event deltas until lift or second finger.
        while (true) {
            val event = awaitPointerEvent()
            if (event.changes.size > 1) return@awaitEachGesture
            val change = event.changes.firstOrNull() ?: break
            if (change.changedToUpIgnoreConsumed()) {
                change.consume()
                return@awaitEachGesture
            }
            val delta = change.positionChange()
            if (delta.x != 0f || delta.y != 0f) {
                onPan(delta.x, delta.y)
            }
            change.consume()
        }
    }
}

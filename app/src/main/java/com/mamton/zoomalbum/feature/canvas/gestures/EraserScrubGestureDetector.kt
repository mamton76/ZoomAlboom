package com.mamton.zoomalbum.feature.canvas.gestures

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput

/**
 * Object-mode Eraser scrub detector. Recognizes a single-finger drag past
 * touch slop and emits one [onCrossNode] call per *new* node id the pointer
 * crosses (deduplicated by the detector — re-crossing the same node within
 * one gesture is a no-op). Gated by the router's `routeEraserScrubStart`
 * via [enabled]. [onScrubStart] fires once when slop is crossed so the
 * caller can consult `routeEraserScrubStart` for popup-open dismissal and
 * gate subsequent [onCrossNode] callbacks accordingly.
 *
 * Each crossing is the caller's atomic delete (one `CanvasCommand` per
 * node, symmetric with tap-on-node Eraser). There is no batched
 * "one-gesture-one-undo" commit at lift — the detector emits per-node
 * events, the ViewModel commits per-node.
 *
 * Design notes:
 *
 *  - **Tap vs. scrub disambiguation.** Tap-on-node is owned by the tap
 *    detector + `GestureRouter.routeTap` (returns `EraserDeleteNode`). The
 *    scrub detector only fires after touch slop is crossed, so a quick tap
 *    flows through the tap detector untouched.
 *  - **Second-finger handoff.** Mid-scrub second finger exits the detector
 *    so global camera navigation can take over. No "scrub end" event is
 *    needed — each crossing was already committed atomically.
 *  - **Down-position retroactive hit-test.** When slop is crossed the
 *    detector hit-tests the *down* position once. This handles the
 *    "tap-then-drag" UX where the initial node sits under the down point
 *    but the slop-crossing event has already moved off it.
 *  - **No `change.isConsumed` exit.** `tapAndLongPressGestures` consumes
 *    the DOWN and that consumption flag carries on the same pointer's
 *    subsequent change deliveries; bailing on `isConsumed` would make the
 *    detector exit on its first movement before slop is ever evaluated.
 *    `changedToUpIgnoreConsumed` is used for lift detection to defend
 *    against a sibling detector consuming UP first. See
 *    `memory/project_gesture_consumed_carry.md`.
 */
fun Modifier.eraserScrubGestures(
    enabled: Boolean,
    hitTestNode: (screenX: Float, screenY: Float) -> String?,
    onScrubStart: () -> Unit,
    onCrossNode: (nodeId: String) -> Unit,
): Modifier = this.pointerInput(enabled) {
    if (!enabled) return@pointerInput

    val slopSq = viewConfiguration.touchSlop * viewConfiguration.touchSlop

    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val startX = down.position.x
        val startY = down.position.y

        // Wait for slop, lift, or second finger.
        while (true) {
            val event = awaitPointerEvent()
            if (event.changes.size > 1) {
                // Two-finger before slop — yield to camera navigation.
                return@awaitEachGesture
            }
            val change = event.changes.firstOrNull() ?: return@awaitEachGesture
            if (change.changedToUpIgnoreConsumed()) {
                // Quick tap — owned by `tapAndLongPressGestures` + router.
                return@awaitEachGesture
            }
            val dx = change.position.x - startX
            val dy = change.position.y - startY
            if (dx * dx + dy * dy > slopSq) {
                change.consume()
                break
            }
        }

        // Scrub claimed. Track dedup'd ids; emit each *new* crossing live.
        // Retroactive DOWN hit-test handles "tap-then-drag" — the initial
        // node is deleted even though the slop-crossing event might be
        // somewhere off it.
        val crossedIds = mutableSetOf<String>()
        onScrubStart()
        fun maybeCross(id: String?) {
            if (id != null && crossedIds.add(id)) onCrossNode(id)
        }
        maybeCross(hitTestNode(startX, startY))

        while (true) {
            val event = awaitPointerEvent()
            if (event.changes.size > 1) return@awaitEachGesture
            val change = event.changes.firstOrNull() ?: break
            if (change.changedToUpIgnoreConsumed()) {
                change.consume()
                maybeCross(hitTestNode(change.position.x, change.position.y))
                return@awaitEachGesture
            }
            maybeCross(hitTestNode(change.position.x, change.position.y))
            change.consume()
        }
    }
}

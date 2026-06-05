package com.mamton.zoomalbum.feature.canvas.gestures

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput

/**
 * Direct drag-on-empty marquee detector for the Selection tool. Replaces an
 * earlier long-press-then-drag rect-select path. Recognizes one specific
 * gesture pattern: **single-finger drag on empty canvas past touch slop in
 * Edit + Selection.** Anything else is left to the other layers.
 *
 * The detector is intentionally narrow:
 *
 *  - It calls [canStartHere] on DOWN; if any interactive surface (node body,
 *    resize handle, rotation handle) is under the pointer, it returns
 *    immediately so node body / handle / tap / long-press logic in the
 *    other layers behaves exactly as before.
 *  - It waits for touch slop without consuming, so taps on empty still
 *    fire `DeselectAll` via [tapAndLongPressGestures] and long-press on
 *    empty still fires (and routes to `Suppress` per `GestureRouter`).
 *  - If a second finger arrives before slop, the detector yields (camera
 *    navigation owns multi-finger).
 *  - If a second finger arrives mid-marquee, the marquee finalizes via
 *    [onMarqueeEnd] and the detector exits — the next event picks up the
 *    two-finger camera gesture cleanly.
 *  - If another layer consumes an event before slop (e.g. node interaction
 *    at the Initial pass), the detector exits without claiming.
 *
 * Policy lives in `GestureRouter.routeMarqueeStart` — [enabled] is the
 * router-derived gate. When `false`, the detector's `pointerInput` block
 * short-circuits at composition and the modifier becomes effectively
 * absent, so other modes / tools (View-mode single-finger pan via
 * `viewModePanGestures`, Eraser scrub via `eraserScrubGestures`) can
 * claim the same drag-on-empty pattern.
 *
 * Context-menu-open suppression is decided at [onMarqueeStart] time by the
 * caller — the detector still runs in that case (and consumes the gesture
 * so it doesn't re-claim later), but the caller dispatches `onCanvasGesture`
 * instead of `UpdateSelectionRect`. See `GestureRouter.routeMarqueeStart`.
 */
fun Modifier.selectionMarqueeGestures(
    enabled: Boolean,
    canStartHere: (screenX: Float, screenY: Float) -> Boolean,
    onMarqueeStart: (screenX: Float, screenY: Float) -> Unit,
    onMarqueeUpdate: (screenX: Float, screenY: Float) -> Unit,
    onMarqueeEnd: () -> Unit,
): Modifier = this.pointerInput(enabled) {
    if (!enabled) return@pointerInput

    val slopSq = viewConfiguration.touchSlop * viewConfiguration.touchSlop

    awaitEachGesture {
        // Don't consume DOWN — taps and long-presses on empty still need to
        // be observed by the Main-pass tap detector.
        val down = awaitFirstDown(requireUnconsumed = false)
        val startX = down.position.x
        val startY = down.position.y

        // Gate on DOWN: marquee only starts on truly empty canvas. [canStartHere]
        // must return false for any Layer-1 interactive surface (node body,
        // resize handle, rotation handle) so node move / resize / rotate drags
        // — which Layer 1 owns — don't also draw a marquee rectangle. Layer 1's
        // own `change.isConsumed` defense at Initial pass is not enough because
        // sibling-detector consumption carries on the same pointer's subsequent
        // change deliveries (see `memory/project_gesture_consumed_carry.md`).
        if (!canStartHere(startX, startY)) return@awaitEachGesture

        // Wait for touch slop, pointer lift, or second finger. We
        // deliberately do NOT bail on `change.isConsumed` here:
        // `tapAndLongPressGestures` consumes the DOWN event by design, and
        // the consumption flag carries on the same pointer's subsequent
        // change deliveries — bailing would make the detector exit on its
        // very first movement event before slop is ever evaluated.
        // `changedToUpIgnoreConsumed` is used so a UP that some other layer
        // consumed first can still terminate the wait loop cleanly.
        while (true) {
            val event = awaitPointerEvent()
            if (event.changes.size > 1) {
                // Second finger arrived before slop — yield to global camera
                // navigation (Layer 3). We have not consumed anything, so
                // the camera detector sees the events normally.
                return@awaitEachGesture
            }
            val change = event.changes.firstOrNull() ?: return@awaitEachGesture
            if (change.changedToUpIgnoreConsumed()) {
                // Pointer lifted before slop — this was a tap (handled by
                // `tapAndLongPressGestures`) or a no-op long-press lift.
                return@awaitEachGesture
            }
            val dx = change.position.x - startX
            val dy = change.position.y - startY
            if (dx * dx + dy * dy > slopSq) {
                // Slop crossed. Claim the gesture: consume so the tap
                // detector's `waitForUp()` sees `isConsumed` and exits
                // cleanly without firing a stray `DeselectAll`.
                change.consume()
                onMarqueeStart(startX, startY)
                onMarqueeUpdate(change.position.x, change.position.y)
                break
            }
        }

        // Marquee is active — drain pointer events until lift or second finger.
        while (true) {
            val event = awaitPointerEvent()
            if (event.changes.size > 1) {
                // Second finger mid-marquee → finalize current marquee and
                // let camera navigation pick up the multi-finger gesture.
                onMarqueeEnd()
                break
            }
            val change = event.changes.firstOrNull() ?: break
            if (change.changedToUpIgnoreConsumed()) {
                change.consume()
                onMarqueeEnd()
                break
            }
            onMarqueeUpdate(change.position.x, change.position.y)
            change.consume()
        }
    }
}

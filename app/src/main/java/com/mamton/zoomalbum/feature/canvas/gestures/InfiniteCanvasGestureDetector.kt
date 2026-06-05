package com.mamton.zoomalbum.feature.canvas.gestures

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.positionChanged
import kotlin.math.abs
import kotlin.math.atan2

/**
 * Detects multi-finger (2+) pan + pinch-zoom + rotation and forwards
 * all values to the caller.
 *
 * Single-finger drags are ignored — they pass through to lower gesture
 * layers (tap-to-select, node drag, etc.). Only when a second finger
 * touches down does this detector activate.
 *
 * [onGestureBegin] fires when the second finger arrives (gesture activates).
 * [onGestureEnd] fires when the gesture deactivates (drops below 2 fingers).
 * These are useful for callers that need explicit gesture lifecycle (e.g.
 * snapshotting state for undo). Both default to no-op.
 */
/**
 * Per-finger position-change threshold below which an active 2-finger
 * event is considered "still finger noise" and skipped entirely. Chosen
 * empirically from the noise floor in `memory/project_gesture_consumed_carry.md`
 * — finger micro-jitter rarely exceeds 1 px per event when the user is
 * holding both fingers in place; intentional gestures easily clear this.
 */
private const val STILL_FINGER_PX = 1.0f

/**
 * Per-finger position-change threshold below which a finger is treated as
 * "stationary enough to disqualify zoom/rotation". Pan can still fire when
 * one finger drags and the other holds — but a real pinch/rotate requires
 * BOTH fingers to move. Without this, the centroid math turns asymmetric
 * single-finger jitter into a steady rotation/zoom drizzle (observed in
 * logcat 2026-06-05 — phantom 0.3–0.7°/event when min-finger ~0px even
 * though max-finger cleared `STILL_FINGER_PX`).
 */
private const val ROTZOOM_MIN_FINGER_PX = 1.5f

/**
 * cos(angle) between the two finger delta vectors above which the gesture is
 * treated as pure pan — rotation and zoom are suppressed. ~0.7 ≈ 45°.
 *
 * Why: when both fingers move in roughly the same direction, the user is
 * panning; any rotation/zoom the centroid math reports is just amplified
 * asymmetric pan (finger 2 moving 1–3px farther than finger 1 manifests as
 * fake rotation, observed 2026-06-05 — e.g. `fingers=11,-7 | 14.5,-8` →
 * phantom rot=0.466°). Real rotation drives the fingers in opposing arcs
 * around the centroid (cosθ ≪ 0); real zoom drives them toward / away from
 * each other (cosθ ≪ 0). Both clear this gate easily, even mid-pan.
 */
private const val PAN_PARALLEL_COS = 0.7f

/**
 * Output of the noise-filter math applied to each two-finger event. `null`
 * means "drop the event entirely" — finger movement was below the still-
 * finger gate. Non-null with zero-valued components means "fingers moved
 * but every dimension was filtered out as noise" (caller will skip
 * dispatch).
 *
 * Extracted from the gesture loop as a pure value type so the filter rules
 * can be unit-tested without standing up a Compose pointer-input harness.
 * See `InfiniteCanvasGestureNoiseTest`.
 */
internal data class FilteredTransform(
    val pan: Offset,
    val zoom: Float,
    val rotation: Float,
) {
    fun hasMovement(): Boolean =
        pan != Offset.Zero || zoom != 1f || rotation != 0f
}

/**
 * Pure noise-filter for one two-finger pointer event. Inputs are the raw
 * geometry derived from the event; output is the filtered (pan, zoom,
 * rotation) tuple the camera should consume — or `null` when the event is
 * pure noise and should be skipped.
 *
 * Gates applied (in order):
 *  1. Still-finger drop — if no finger moved more than [STILL_FINGER_PX],
 *     return `null`. Catches the DC-bias finger-jitter case where both
 *     fingers are held near-still.
 *  2. Pan deadband — pan is suppressed below ~0.5 px to avoid sub-pixel
 *     noise reaching the camera.
 *  3. Direction agreement — when the two finger delta vectors point the
 *     same way (cosθ > [PAN_PARALLEL_COS]), the user is panning; any
 *     reported rotation / zoom is just amplified asymmetric pan, so both
 *     are suppressed.
 *  4. Rotation noise floor — 0.3° per event.
 *  5. Zoom: requires both fingers actually moving ([ROTZOOM_MIN_FINGER_PX])
 *     plus a 0.5% magnitude floor. Anchor-and-orbit rotations slightly
 *     vary inter-finger distance; without this gate that would manifest
 *     as fake zoom.
 *
 * Rotation is computed from the change in angle of the inter-finger vector
 * `p1 − p0` rather than Compose's centroid-based `calculateRotation`, so
 * the natural anchor-and-orbit grip (one finger near-still while the other
 * arcs) produces a clean signal.
 */
internal fun computeFilteredTransform(
    fingerDeltas: List<Offset>,
    fingerPositions: List<Offset>,
    fingerPrev: List<Offset>,
    pan: Offset,
    zoom: Float,
): FilteredTransform? {
    require(fingerDeltas.size == 2 && fingerPositions.size == 2 && fingerPrev.size == 2) {
        "two-finger event expected"
    }

    val mag0 = fingerDeltas[0].getDistance()
    val mag1 = fingerDeltas[1].getDistance()
    val maxFingerMovement = maxOf(mag0, mag1)
    val minFingerMovement = minOf(mag0, mag1)
    if (maxFingerMovement < STILL_FINGER_PX) return null

    val panMag2 = pan.x * pan.x + pan.y * pan.y
    val filteredPan = if (panMag2 > 0.25f) pan else Offset.Zero

    val d0 = fingerDeltas[0]
    val d1 = fingerDeltas[1]
    val cosTheta = if (mag0 > 0.01f && mag1 > 0.01f) {
        (d0.x * d1.x + d0.y * d1.y) / (mag0 * mag1)
    } else {
        0f
    }
    val fingersDiverging = cosTheta < PAN_PARALLEL_COS

    val p0Prev = fingerPrev[0]
    val p1Prev = fingerPrev[1]
    val p0Curr = fingerPositions[0]
    val p1Curr = fingerPositions[1]
    val anglePrev = atan2(p1Prev.y - p0Prev.y, p1Prev.x - p0Prev.x)
    val angleCurr = atan2(p1Curr.y - p0Curr.y, p1Curr.x - p0Curr.x)
    var rotationDegrees = Math.toDegrees(
        (angleCurr - anglePrev).toDouble(),
    ).toFloat()
    if (rotationDegrees > 180f) rotationDegrees -= 360f
    if (rotationDegrees < -180f) rotationDegrees += 360f

    val filteredRotation = if (fingersDiverging && abs(rotationDegrees) > 0.3f) {
        rotationDegrees
    } else {
        0f
    }

    val bothFingersMoving = minFingerMovement >= ROTZOOM_MIN_FINGER_PX
    val filteredZoom =
        if (bothFingersMoving && fingersDiverging && abs(zoom - 1f) > 0.005f) {
            zoom
        } else {
            1f
        }

    return FilteredTransform(filteredPan, filteredZoom, filteredRotation)
}

fun Modifier.infiniteCanvasGestures(
    onGestureBegin: () -> Unit = {},
    onGestureEnd: () -> Unit = {},
    onGesture: (centroid: Offset, pan: Offset, zoom: Float, rotation: Float) -> Unit,
): Modifier = this.pointerInput(Unit) {
    awaitEachGesture {
        // Wait for the first pointer down (don't consume — let other layers see it)
        awaitFirstDown(requireUnconsumed = false)

        // Wait until a second finger touches down
        var secondFingerDown = false
        while (!secondFingerDown) {
            val event = awaitPointerEvent()
            val pressedCount = event.changes.count { it.pressed }
            if (pressedCount >= 2) {
                secondFingerDown = true
            } else if (pressedCount == 0) {
                // All fingers lifted before a second finger arrived — not our gesture
                return@awaitEachGesture
            }
            // Don't consume single-finger events — let them pass through
        }

        // Two or more fingers are down — gesture activates.
        onGestureBegin()
        try {
            // Enter transform tracking loop
            do {
                val event = awaitPointerEvent()
                // Sort by pointer id so finger 0 / finger 1 identity is
                // stable across events — the inter-finger angle calc would
                // see a 180° flip if Compose ever re-ordered the list.
                val pressed = event.changes.filter { it.pressed }.sortedBy { it.id.value }
                if (pressed.size < 2) {
                    // Dropped below 2 fingers — stop tracking
                    // Consume remaining changes to avoid stale state
                    event.changes.forEach { if (it.positionChanged()) it.consume() }
                    break
                }

                val fingerDeltas = pressed.map { it.positionChange() }
                val fingerPositions = pressed.map { it.position }
                val fingerPrev = pressed.map { it.previousPosition }

                val centroid = event.calculateCentroid(useCurrent = true)
                val filtered = computeFilteredTransform(
                    fingerDeltas = fingerDeltas,
                    fingerPositions = fingerPositions,
                    fingerPrev = fingerPrev,
                    pan = event.calculatePan(),
                    zoom = event.calculateZoom(),
                ) ?: continue

                if (filtered.hasMovement()) {
                    onGesture(centroid, filtered.pan, filtered.zoom, filtered.rotation)
                    event.changes.forEach { if (it.positionChanged()) it.consume() }
                }
            } while (true)
        } finally {
            onGestureEnd()
        }
    }
}

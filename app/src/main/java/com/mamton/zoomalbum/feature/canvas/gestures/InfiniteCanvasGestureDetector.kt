package com.mamton.zoomalbum.feature.canvas.gestures

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculateCentroidSize
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateRotation
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import kotlin.math.abs

/**
 * Detects multi-finger (2+) pan + pinch-zoom + rotation and forwards
 * all values to the caller.
 *
 * Single-finger drags are ignored — they pass through to lower gesture
 * layers (tap-to-select, node drag, etc.). Only when a second finger
 * touches down does this detector activate.
 */
fun Modifier.infiniteCanvasGestures(
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

        // Two or more fingers are down — enter transform tracking loop
        do {
            val event = awaitPointerEvent()
            val pressed = event.changes.filter { it.pressed }
            if (pressed.size < 2) {
                // Dropped below 2 fingers — stop tracking
                // Consume remaining changes to avoid stale state
                event.changes.forEach { if (it.positionChanged()) it.consume() }
                break
            }

            val centroid = event.calculateCentroid(useCurrent = true)
            val pan = event.calculatePan()
            val zoom = event.calculateZoom()
            val rotation = event.calculateRotation()

            val centroidSize = event.calculateCentroidSize(useCurrent = false)
            val hasMovement = pan != Offset.Zero
                || abs(zoom - 1f) > 0.001f
                || abs(rotation) > 0.01f
                || centroidSize > viewConfiguration.touchSlop

            if (hasMovement) {
                onGesture(centroid, pan, zoom, rotation)
                event.changes.forEach { if (it.positionChanged()) it.consume() }
            }
        } while (true)
    }
}

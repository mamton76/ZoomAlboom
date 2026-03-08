package com.mamton.zoomalbum.feature.canvas.gestures

import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput

/**
 * Detects pan + pinch-zoom + rotation and forwards all values to the caller.
 */
fun Modifier.infiniteCanvasGestures(
    onGesture: (centroid: Offset, pan: Offset, zoom: Float, rotation: Float) -> Unit,
): Modifier = this.pointerInput(Unit) {
    detectTransformGestures { centroid, pan, zoom, rotation ->
        onGesture(centroid, pan, zoom, rotation)
    }
}

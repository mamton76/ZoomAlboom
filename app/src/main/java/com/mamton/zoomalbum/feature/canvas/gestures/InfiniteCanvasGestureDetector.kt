package com.mamton.zoomalbum.feature.canvas.gestures

import androidx.compose.ui.Modifier

/**
 * Custom gesture detector handling pan, pinch-zoom, and rotation on the
 * infinite canvas. Will be implemented as a [Modifier] extension.
 */
fun Modifier.infiniteCanvasGestures(
    onPan: (dx: Float, dy: Float) -> Unit = { _, _ -> },
    onZoom: (scaleFactor: Float, focusX: Float, focusY: Float) -> Unit = { _, _, _ -> },
    onRotate: (degrees: Float) -> Unit = {},
): Modifier {
    // TODO: combine detectTransformGestures with custom fling
    return this
}

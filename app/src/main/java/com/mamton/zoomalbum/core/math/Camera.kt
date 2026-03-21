package com.mamton.zoomalbum.core.math

/**
 * Camera state for the infinite canvas.
 *
 * (cx, cy) = graphicsLayer translationX/Y in screen-pixel units.
 *            Screen position of world point (wx, wy) = (wx * scale + cx, wy * scale + cy).
 *            To center world point (wx, wy) at screen center:
 *            cx = screenWidth/2 - wx * scale, cy = screenHeight/2 - wy * scale.
 * scale    = zoom factor (1.0 = 100%, 2.0 = zoomed in 2x).
 * rotation = canvas rotation in degrees.
 */
data class Camera(
    val cx: Float = 0f,
    val cy: Float = 0f,
    val scale: Float = 1f,
    val rotation: Float = 0f,
) {
    companion object {
        const val MIN_SCALE = 0.00005f
        const val MAX_SCALE = 10000f
    }
}
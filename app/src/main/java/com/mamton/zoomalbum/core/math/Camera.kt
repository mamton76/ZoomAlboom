package com.mamton.zoomalbum.core.math

/**
 * Camera state for the infinite canvas.
 *
 * (cx, cy) = world-coordinate point at the center of the screen.
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
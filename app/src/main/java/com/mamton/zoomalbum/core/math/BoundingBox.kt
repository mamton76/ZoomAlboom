package com.mamton.zoomalbum.core.math

/**
 * Axis-aligned bounding box used for spatial queries and viewport culling.
 * Pure math — no Android dependencies.
 */
data class BoundingBox(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
    val centerX: Float get() = (left + right) / 2f
    val centerY: Float get() = (top + bottom) / 2f

    fun intersects(other: BoundingBox): Boolean =
        left < other.right && right > other.left &&
            top < other.bottom && bottom > other.top

    fun contains(x: Float, y: Float): Boolean =
        x in left..right && y in top..bottom
}

package com.mamton.zoomalbum.domain.model

import kotlinx.serialization.Serializable

/**
 * Spatial placement and size of a canvas node in world coordinates.
 *
 * (cx, cy) = center of the node in world space.
 * w, h     = base width/height in world units (actual size, NOT normalized).
 * scale    = user-applied scale multiplier (default 1.0; used for pinch-to-resize on node).
 *            Render size = w * scale × h * scale.
 * rotation = node rotation in degrees.
 * zIndex   = draw order.
 */
@Serializable
data class Transform(
    val cx: Float = 0f,
    val cy: Float = 0f,
    val w: Float = 100f,
    val h: Float = 100f,
    val scale: Float = 1f,
    val rotation: Float = 0f,
    val zIndex: Float = 0f,
) {
    /** Render width in world units. */
    val renderW: Float get() = w * scale

    /** Render height in world units. */
    val renderH: Float get() = h * scale
}
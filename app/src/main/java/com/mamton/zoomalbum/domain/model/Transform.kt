package com.mamton.zoomalbum.domain.model

import kotlinx.serialization.Serializable

/**
 * Spatial placement and size of a canvas node in world coordinates.
 *
 * (cx, cy) = center of the node in world space.
 * w, h     = base width/height in world units. NOT native pixel size and NOT immutable —
 *            may be rebased while preserving renderW × renderH (see docs/architecture/data-model.md).
 * scale    = current multiplier over w/h. Resize gestures mutate this, NOT w/h.
 *            At creation: scale = 1/camera.scale (both Frame and Media); w/h = targetRender * camera.scale.
 *            This puts the camera-zoom factor in `scale` and leaves `w/h` camera-independent —
 *            i.e., `w/h` are the canonical render size at scale=1.
 * rotation = node rotation in degrees.
 * zIndex   = draw order.
 *
 * Render invariant: every consumer (rendering, AABB, hit-test, culling, selection) MUST
 * read renderW/renderH, never raw w/h.
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
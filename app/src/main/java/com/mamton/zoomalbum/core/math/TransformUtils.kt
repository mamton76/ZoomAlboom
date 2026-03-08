package com.mamton.zoomalbum.core.math

import com.mamton.zoomalbum.domain.model.Transform

/**
 * Pure-math utilities for coordinate transforms on the infinite canvas.
 * No Android / Compose dependencies.
 */
object TransformUtils {

    fun toBoundingBox(transform: Transform): BoundingBox = BoundingBox(
        left = transform.x,
        top = transform.y,
        right = transform.x + transform.width * transform.scale,
        bottom = transform.y + transform.height * transform.scale,
    )
}

package com.mamton.zoomalbum.core.math

import com.mamton.zoomalbum.domain.model.Transform
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Pure-math utilities for coordinate transforms on the infinite canvas.
 * No Android / Compose dependencies.
 */
object TransformUtils {

    /** Rotates vector (x, y) by angleDeg degrees. */
    fun rotateVector(x: Float, y: Float, angleDeg: Float): Pair<Float, Float> {
        val rad = Math.toRadians(angleDeg.toDouble())
        val cosR = cos(rad).toFloat()
        val sinR = sin(rad).toFloat()
        return Pair(x * cosR - y * sinR, x * sinR + y * cosR)
    }

    /** AABB of a node in world coordinates. */
    fun toBoundingBox(transform: Transform): BoundingBox = BoundingBox(
        left = transform.x,
        top = transform.y,
        right = transform.x + transform.w * transform.scale,
        bottom = transform.y + transform.h * transform.scale,
    )

    /**
     * Camera viewport in world coordinates (axis-aligned bounding box).
     *
     * graphicsLayer applies: translate(cameraX, cameraY) → rotate(cameraRotation) → scale
     * all around origin (0,0).
     *
     * To find what world area is visible, we map the four screen corners through the
     * inverse transform:  world = invRotate( (screen - camera) / scale )
     *
     * When rotation is non-zero the visible world area is a rotated rectangle;
     * we return its AABB (slightly larger, but correct for culling).
     */
    fun cameraViewport(
        cameraX: Float,
        cameraY: Float,
        cameraScale: Float,
        cameraRotation: Float,
        screenWidth: Float,
        screenHeight: Float,
    ): BoundingBox {
        val rad = Math.toRadians(-cameraRotation.toDouble())
        val cosR = cos(rad).toFloat()
        val sinR = sin(rad).toFloat()
        val invS = 1f / cameraScale

        // screen corners
        val corners = floatArrayOf(
            0f, 0f,
            screenWidth, 0f,
            screenWidth, screenHeight,
            0f, screenHeight,
        )

        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE

        for (i in corners.indices step 2) {
            val sx = (corners[i] - cameraX) * invS
            val sy = (corners[i + 1] - cameraY) * invS
            val wx = sx * cosR - sy * sinR
            val wy = sx * sinR + sy * cosR
            minX = min(minX, wx)
            minY = min(minY, wy)
            maxX = max(maxX, wx)
            maxY = max(maxY, wy)
        }

        return BoundingBox(minX, minY, maxX, maxY)
    }
}

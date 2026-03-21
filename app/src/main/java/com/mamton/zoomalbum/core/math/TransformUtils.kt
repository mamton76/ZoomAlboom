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

    /** AABB of a node in world coordinates (non-rotation-aware). */
    fun toBoundingBox(transform: Transform): BoundingBox {
        val halfW = transform.renderW / 2f
        val halfH = transform.renderH / 2f
        return BoundingBox(
            left = transform.cx - halfW,
            top = transform.cy - halfH,
            right = transform.cx + halfW,
            bottom = transform.cy + halfH,
        )
    }

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
        cameraCx: Float,
        cameraCy: Float,
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
            val sx = (corners[i] - cameraCx) * invS
            val sy = (corners[i + 1] - cameraCy) * invS
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

/**
 * Compute the [Camera] state that centers this [Transform] on screen and
 * scales it so its render size fills [fillFraction] of the screen.
 *
 * Camera.cx/cy are graphicsLayer translation values (screen-pixel units), not
 * world coordinates. The translation that places world point (wx, wy) at the
 * screen center is: cx = screenWidth/2 - wx*scale, cy = screenHeight/2 - wy*scale.
 *
 * NOTE: Camera.scale and Transform.scale have different semantics —
 * do NOT copy one to the other.
 * Camera.scale = 2.0 → world appears 2× magnified (zoom in).
 * Transform.scale = 2.0 → this node is 2× its base size.
 */
fun Transform.toCamera(
    screenWidth: Float,
    screenHeight: Float,
    fillFraction: Float = 0.9f,
): Camera {
    val fitScaleW = (screenWidth * fillFraction) / renderW
    val fitScaleH = (screenHeight * fillFraction) / renderH
    val scale = minOf(fitScaleW, fitScaleH)
    return Camera(
        cx = screenWidth / 2f - cx * scale,
        cy = screenHeight / 2f - cy * scale,
        scale = scale,
        rotation = rotation,
    )
}

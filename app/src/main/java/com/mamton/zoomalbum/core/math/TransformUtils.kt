package com.mamton.zoomalbum.core.math

import com.mamton.zoomalbum.domain.model.CanvasNode
import com.mamton.zoomalbum.domain.model.Transform
import kotlin.math.abs
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

    // ── Screen ↔ World conversion ────────────────────────────────────

    /**
     * Converts a screen-space point to world-space coordinates.
     *
     * Inverse of the camera graphicsLayer transform (transformOrigin = 0,0):
     *   forward: screen = translate(scale(rotate(world)))
     *   inverse: world  = un-rotate(un-scale(un-translate(screen)))
     *
     * Matches [cameraViewport] inverse order: un-translate → un-scale → un-rotate.
     */
    fun screenToWorld(screenX: Float, screenY: Float, camera: Camera): Pair<Float, Float> {
        val invS = 1f / camera.scale
        val dx = (screenX - camera.cx) * invS
        val dy = (screenY - camera.cy) * invS
        return rotateVector(dx, dy, -camera.rotation)
    }

    /**
     * Converts a world-space point to screen-space coordinates.
     * Forward camera transform: rotate → scale → translate (with origin 0,0).
     */
    fun worldToScreen(worldX: Float, worldY: Float, camera: Camera): Pair<Float, Float> {
        val (rx, ry) = rotateVector(worldX, worldY, camera.rotation)
        return Pair(rx * camera.scale + camera.cx, ry * camera.scale + camera.cy)
    }

    /**
     * Converts a screen-space drag delta to world-space delta.
     * Position-independent (no translation involved).
     */
    fun screenDeltaToWorld(
        screenDx: Float,
        screenDy: Float,
        camera: Camera,
    ): Pair<Float, Float> {
        val invS = 1f / camera.scale
        val dx = screenDx * invS
        val dy = screenDy * invS
        return rotateVector(dx, dy, -camera.rotation)
    }

    // ── Hit-testing ──────────────────────────────────────────────────

    /**
     * Rotation-aware point-in-node test (OBB, not AABB).
     *
     * Translates the point into the node's local coordinate system
     * (node center = origin, un-rotated), then checks against half-extents.
     */
    fun pointInNode(worldX: Float, worldY: Float, transform: Transform): Boolean {
        val dx = worldX - transform.cx
        val dy = worldY - transform.cy
        val (lx, ly) = rotateVector(dx, dy, -transform.rotation)
        return abs(lx) <= transform.renderW / 2f && abs(ly) <= transform.renderH / 2f
    }

    /**
     * Tests whether a world-space point hits one of the 4 resize corner handles.
     * Returns the handle, or null if no handle is hit.
     *
     * @param handleWorldRadius touch tolerance in world units
     *        (caller typically passes `handleScreenPx / 2 / cameraScale`).
     */
    fun hitTestHandle(
        worldX: Float,
        worldY: Float,
        transform: Transform,
        handleWorldRadius: Float,
    ): ResizeHandle? {
        val dx = worldX - transform.cx
        val dy = worldY - transform.cy
        val (lx, ly) = rotateVector(dx, dy, -transform.rotation)

        val halfW = transform.renderW / 2f
        val halfH = transform.renderH / 2f

        val corners = arrayOf(
            ResizeHandle.TOP_LEFT to (-halfW to -halfH),
            ResizeHandle.TOP_RIGHT to (halfW to -halfH),
            ResizeHandle.BOTTOM_LEFT to (-halfW to halfH),
            ResizeHandle.BOTTOM_RIGHT to (halfW to halfH),
        )
        return corners.firstOrNull { (_, pos) ->
            abs(lx - pos.first) < handleWorldRadius && abs(ly - pos.second) < handleWorldRadius
        }?.first
    }

    /**
     * Tests whether a world-space point hits the rotation handle.
     * The handle sits above the top-center of the node, offset by [handleOffset] world units.
     */
    fun hitTestRotationHandle(
        worldX: Float,
        worldY: Float,
        transform: Transform,
        handleWorldRadius: Float,
        handleOffset: Float,
    ): Boolean {
        val dx = worldX - transform.cx
        val dy = worldY - transform.cy
        val (lx, ly) = rotateVector(dx, dy, -transform.rotation)
        val handleY = -transform.renderH / 2f - handleOffset
        return abs(lx) < handleWorldRadius && abs(ly - handleY) < handleWorldRadius
    }

    // ── Group / selection helpers ────────────────────────────────────

    /** AABB enclosing all nodes (union of individual AABBs). */
    fun selectionBoundingBox(nodes: List<CanvasNode>): BoundingBox {
        require(nodes.isNotEmpty()) { "Cannot compute bounding box for empty list" }
        var left = Float.MAX_VALUE
        var top = Float.MAX_VALUE
        var right = -Float.MAX_VALUE
        var bottom = -Float.MAX_VALUE
        for (node in nodes) {
            val bb = toBoundingBox(node.transform)
            left = min(left, bb.left)
            top = min(top, bb.top)
            right = max(right, bb.right)
            bottom = max(bottom, bb.bottom)
        }
        return BoundingBox(left, top, right, bottom)
    }

    /** Average center of all nodes. */
    fun groupCenter(nodes: List<CanvasNode>): Pair<Float, Float> {
        require(nodes.isNotEmpty()) { "Cannot compute center for empty list" }
        var cx = 0f
        var cy = 0f
        for (node in nodes) {
            cx += node.transform.cx
            cy += node.transform.cy
        }
        val n = nodes.size.toFloat()
        return cx / n to cy / n
    }

    /** Rotates point (px, py) around pivot (pivotX, pivotY) by [angleDeg] degrees. */
    fun rotatePointAround(
        px: Float, py: Float,
        pivotX: Float, pivotY: Float,
        angleDeg: Float,
    ): Pair<Float, Float> {
        val dx = px - pivotX
        val dy = py - pivotY
        val (rx, ry) = rotateVector(dx, dy, angleDeg)
        return pivotX + rx to pivotY + ry
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

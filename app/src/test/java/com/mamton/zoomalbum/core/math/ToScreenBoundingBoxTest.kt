package com.mamton.zoomalbum.core.math

import com.mamton.zoomalbum.domain.model.Transform
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sqrt

/**
 * Coverage for [TransformUtils.toScreenBoundingBox] — the screen-space AABB
 * helper that drives the marquee selection's hit test in screen coordinates.
 *
 * The function rotates a node by `transform.rotation`, projects its four
 * world corners through the camera (rotation + scale + translation), then
 * returns the screen-axis-aligned bounding box of those four projected
 * corners. Marquee selection (`CanvasAction.SelectNodesInRect`) intersects
 * this AABB against the marquee rect (also screen-space) so the marquee
 * stays parallel to the screen under any camera state.
 */
class ToScreenBoundingBoxTest {

    private val eps = 0.5f

    private fun transform(
        cx: Float,
        cy: Float,
        w: Float,
        h: Float,
        rotation: Float = 0f,
    ): Transform = Transform(cx = cx, cy = cy, w = w, h = h, rotation = rotation)

    @Test
    fun `identity camera + unrotated node returns world AABB`() {
        val t = transform(cx = 100f, cy = 200f, w = 50f, h = 30f)
        val cam = Camera(cx = 0f, cy = 0f, scale = 1f, rotation = 0f)
        val bb = TransformUtils.toScreenBoundingBox(t, cam)
        assertEquals(75f, bb.left, eps)
        assertEquals(185f, bb.top, eps)
        assertEquals(125f, bb.right, eps)
        assertEquals(215f, bb.bottom, eps)
    }

    @Test
    fun `camera translation shifts the screen AABB`() {
        val t = transform(cx = 0f, cy = 0f, w = 40f, h = 40f)
        val cam = Camera(cx = 100f, cy = 50f, scale = 1f, rotation = 0f)
        val bb = TransformUtils.toScreenBoundingBox(t, cam)
        assertEquals(80f, bb.left, eps)
        assertEquals(30f, bb.top, eps)
        assertEquals(120f, bb.right, eps)
        assertEquals(70f, bb.bottom, eps)
    }

    @Test
    fun `camera scale grows the screen AABB`() {
        val t = transform(cx = 0f, cy = 0f, w = 10f, h = 10f)
        val cam = Camera(cx = 0f, cy = 0f, scale = 3f, rotation = 0f)
        val bb = TransformUtils.toScreenBoundingBox(t, cam)
        assertEquals(30f, bb.width, eps)
        assertEquals(30f, bb.height, eps)
    }

    @Test
    fun `node rotation 45 deg with identity camera expands AABB by sqrt2`() {
        // A 45-degree rotation makes a square's bounding box bigger because
        // the corners now span the original diagonal. side = 10 → diagonal
        // = sqrt(200) ≈ 14.14. AABB width = AABB height = sqrt(2) * 10.
        val t = transform(cx = 0f, cy = 0f, w = 10f, h = 10f, rotation = 45f)
        val cam = Camera(cx = 0f, cy = 0f, scale = 1f, rotation = 0f)
        val bb = TransformUtils.toScreenBoundingBox(t, cam)
        val expectedSide = 10f * sqrt(2f)
        assertEquals(expectedSide, bb.width, eps)
        assertEquals(expectedSide, bb.height, eps)
        // Still centered at origin.
        assertEquals(-expectedSide / 2f, bb.left, eps)
        assertEquals(expectedSide / 2f, bb.right, eps)
    }

    @Test
    fun `camera rotation 90 deg with unrotated node — AABB stays axis-aligned`() {
        // Square node, camera rotated 90 deg. The node visually rotates 90
        // on screen, but its screen AABB still has the original side length.
        // Center moves per the camera rotation around origin: (0, 0) stays
        // at (0, 0).
        val t = transform(cx = 0f, cy = 0f, w = 20f, h = 20f, rotation = 0f)
        val cam = Camera(cx = 0f, cy = 0f, scale = 1f, rotation = 90f)
        val bb = TransformUtils.toScreenBoundingBox(t, cam)
        assertEquals(20f, bb.width, eps)
        assertEquals(20f, bb.height, eps)
    }

    @Test
    fun `node 45 deg + camera 45 deg — net 90 deg, square AABB stays 10x10`() {
        // Rotations stack additively on screen: node rotation 45° + camera
        // rotation 45° = 90° net screen rotation. A square at any multiple
        // of 90° has the same AABB as unrotated. The intermediate world
        // bbox (post-node-rotation, pre-camera) is sqrt(2)·10 spread, but
        // the camera's matching 45° rotation projects those world corners
        // back to the axis-aligned square on screen. This pins down that
        // [toScreenBoundingBox] composes the two rotations correctly — not
        // by accidentally double-rotating to 0° net, or by stopping at the
        // intermediate world bbox.
        val t = transform(cx = 0f, cy = 0f, w = 10f, h = 10f, rotation = 45f)
        val cam = Camera(cx = 0f, cy = 0f, scale = 1f, rotation = 45f)
        val bb = TransformUtils.toScreenBoundingBox(t, cam)
        assertEquals(10f, bb.width, eps)
        assertEquals(10f, bb.height, eps)
    }

    @Test
    fun `node 30 deg + camera -30 deg cancels — square AABB stays 10x10`() {
        // The other directional sanity check: opposing rotations cancel
        // exactly. Verifies the sign convention agrees between the node
        // rotation step and the camera rotation step inside
        // `toScreenBoundingBox`.
        val t = transform(cx = 0f, cy = 0f, w = 10f, h = 10f, rotation = 30f)
        val cam = Camera(cx = 0f, cy = 0f, scale = 1f, rotation = -30f)
        val bb = TransformUtils.toScreenBoundingBox(t, cam)
        assertEquals(10f, bb.width, eps)
        assertEquals(10f, bb.height, eps)
    }

    @Test
    fun `rectangular node rotated 90 deg swaps width and height in AABB`() {
        val t = transform(cx = 0f, cy = 0f, w = 40f, h = 10f, rotation = 90f)
        val cam = Camera(cx = 0f, cy = 0f, scale = 1f, rotation = 0f)
        val bb = TransformUtils.toScreenBoundingBox(t, cam)
        assertEquals(10f, bb.width, eps)
        assertEquals(40f, bb.height, eps)
    }

    @Test
    fun `combined translation + scale + node rotation places center correctly`() {
        // Node at world (100, 50), camera at (-50, -25) with 2x scale and
        // no rotation. World center (100, 50) → screen (2*100 + (-50), 2*50
        // + (-25)) = (150, 75). Node rotation doesn't shift the center.
        val t = transform(cx = 100f, cy = 50f, w = 20f, h = 20f, rotation = 30f)
        val cam = Camera(cx = -50f, cy = -25f, scale = 2f, rotation = 0f)
        val bb = TransformUtils.toScreenBoundingBox(t, cam)
        assertEquals(150f, bb.centerX, eps)
        assertEquals(75f, bb.centerY, eps)
    }

    @Test
    fun `screen AABB intersection is the marquee selection contract`() {
        // Sanity: the function returns a BoundingBox that supports
        // `intersects` — the marquee selection uses this to pick nodes that
        // visually overlap the screen-space marquee rect.
        val nodeBb = TransformUtils.toScreenBoundingBox(
            transform = transform(cx = 0f, cy = 0f, w = 20f, h = 20f),
            camera = Camera(cx = 0f, cy = 0f, scale = 1f, rotation = 0f),
        )
        // Marquee covering [5, 5] — [15, 15] overlaps the node's [-10, 10] box.
        val marquee = BoundingBox(left = 5f, top = 5f, right = 15f, bottom = 15f)
        assertTrue(nodeBb.intersects(marquee))
        // Marquee outside the node entirely.
        val outside = BoundingBox(left = 100f, top = 100f, right = 120f, bottom = 120f)
        assertTrue(!nodeBb.intersects(outside))
    }
}

package com.mamton.zoomalbum.core.math

import com.mamton.zoomalbum.domain.model.CanvasNode
import com.mamton.zoomalbum.domain.model.Transform
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HitTestingTest {

    private val eps = 0.1f

    // ── screenToWorld ─────────────────────────────────────────────────

    @Test
    fun `screenToWorld with identity camera returns same point`() {
        val cam = Camera(cx = 0f, cy = 0f, scale = 1f, rotation = 0f)
        val (wx, wy) = TransformUtils.screenToWorld(100f, 200f, cam)
        assertEquals(100f, wx, eps)
        assertEquals(200f, wy, eps)
    }

    @Test
    fun `screenToWorld with translation offset`() {
        val cam = Camera(cx = 50f, cy = 100f, scale = 1f, rotation = 0f)
        val (wx, wy) = TransformUtils.screenToWorld(150f, 300f, cam)
        assertEquals(100f, wx, eps)
        assertEquals(200f, wy, eps)
    }

    @Test
    fun `screenToWorld with zoom`() {
        val cam = Camera(cx = 0f, cy = 0f, scale = 2f, rotation = 0f)
        val (wx, wy) = TransformUtils.screenToWorld(200f, 400f, cam)
        assertEquals(100f, wx, eps)
        assertEquals(200f, wy, eps)
    }

    @Test
    fun `screenToWorld with 90 degree rotation`() {
        val cam = Camera(cx = 0f, cy = 0f, scale = 1f, rotation = 90f)
        val (wx, wy) = TransformUtils.screenToWorld(100f, 0f, cam)
        // After un-scale (noop at 1), un-rotate by -90:
        // (100, 0) rotated -90 = (0, -100)
        assertEquals(0f, wx, eps)
        assertEquals(-100f, wy, eps)
    }

    @Test
    fun `screenToWorld round-trip with cameraViewport`() {
        // A world point mapped through the camera should land in the viewport
        val cam = Camera(cx = 200f, cy = 300f, scale = 1.5f, rotation = 30f)
        val screenWidth = 1080f
        val screenHeight = 1920f

        // Screen center should map to a world point inside the viewport
        val (wx, wy) = TransformUtils.screenToWorld(
            screenWidth / 2f, screenHeight / 2f, cam,
        )
        val viewport = TransformUtils.cameraViewport(
            cam.cx, cam.cy, cam.scale, cam.rotation, screenWidth, screenHeight,
        )
        assertTrue(
            "Screen center world point ($wx, $wy) should be inside viewport $viewport",
            viewport.contains(wx, wy),
        )
    }

    // ── pointInNode ───────────────────────────────────────────────────

    @Test
    fun `pointInNode with no rotation - center`() {
        val t = Transform(cx = 100f, cy = 100f, w = 200f, h = 100f)
        assertTrue(TransformUtils.pointInNode(100f, 100f, t))
    }

    @Test
    fun `pointInNode with no rotation - inside`() {
        val t = Transform(cx = 100f, cy = 100f, w = 200f, h = 100f)
        assertTrue(TransformUtils.pointInNode(50f, 80f, t))
    }

    @Test
    fun `pointInNode with no rotation - outside`() {
        val t = Transform(cx = 100f, cy = 100f, w = 200f, h = 100f)
        assertFalse(TransformUtils.pointInNode(300f, 100f, t))
    }

    @Test
    fun `pointInNode with 45 degree rotation - corner case`() {
        // A 100x100 node at origin rotated 45 degrees.
        // The AABB would say (35, 0) is inside, but the rotated OBB disagrees.
        val t = Transform(cx = 0f, cy = 0f, w = 100f, h = 100f, rotation = 45f)
        // (50, 50) in AABB is a corner — but in the rotated rect it's outside
        // because the diagonal of the rotated square at (50,50) exceeds half-extent
        assertFalse(TransformUtils.pointInNode(50f, 50f, t))
    }

    @Test
    fun `pointInNode with 45 degree rotation - inside`() {
        val t = Transform(cx = 0f, cy = 0f, w = 100f, h = 100f, rotation = 45f)
        // Origin is always inside
        assertTrue(TransformUtils.pointInNode(0f, 0f, t))
        // A point along the diagonal of the rotated rect
        assertTrue(TransformUtils.pointInNode(30f, 0f, t))
    }

    // ── screenDeltaToWorld ────────────────────────────────────────────

    @Test
    fun `screenDeltaToWorld with identity camera`() {
        val cam = Camera(cx = 0f, cy = 0f, scale = 1f, rotation = 0f)
        val (dx, dy) = TransformUtils.screenDeltaToWorld(10f, 20f, cam)
        assertEquals(10f, dx, eps)
        assertEquals(20f, dy, eps)
    }

    @Test
    fun `screenDeltaToWorld with zoom`() {
        val cam = Camera(cx = 0f, cy = 0f, scale = 2f, rotation = 0f)
        val (dx, dy) = TransformUtils.screenDeltaToWorld(10f, 20f, cam)
        assertEquals(5f, dx, eps)
        assertEquals(10f, dy, eps)
    }

    @Test
    fun `screenDeltaToWorld with rotation`() {
        val cam = Camera(cx = 0f, cy = 0f, scale = 1f, rotation = 90f)
        val (dx, dy) = TransformUtils.screenDeltaToWorld(10f, 0f, cam)
        assertEquals(0f, dx, eps)
        assertEquals(-10f, dy, eps)
    }

    // ── selectionBoundingBox ──────────────────────────────────────────

    @Test
    fun `selectionBoundingBox encloses all nodes`() {
        val nodes = listOf(
            CanvasNode.Frame(id = "a", transform = Transform(cx = 0f, cy = 0f, w = 100f, h = 100f)),
            CanvasNode.Frame(id = "b", transform = Transform(cx = 200f, cy = 300f, w = 100f, h = 100f)),
        )
        val bb = TransformUtils.selectionBoundingBox(nodes)
        assertEquals(-50f, bb.left, eps)
        assertEquals(-50f, bb.top, eps)
        assertEquals(250f, bb.right, eps)
        assertEquals(350f, bb.bottom, eps)
    }

    // ── groupCenter ──────────────────────────────────────────────────

    @Test
    fun `groupCenter averages node centers`() {
        val nodes = listOf(
            CanvasNode.Frame(id = "a", transform = Transform(cx = 0f, cy = 0f)),
            CanvasNode.Frame(id = "b", transform = Transform(cx = 200f, cy = 400f)),
        )
        val (cx, cy) = TransformUtils.groupCenter(nodes)
        assertEquals(100f, cx, eps)
        assertEquals(200f, cy, eps)
    }

    // ── rotatePointAround ────────────────────────────────────────────

    @Test
    fun `rotatePointAround 90 degrees`() {
        val (rx, ry) = TransformUtils.rotatePointAround(
            px = 100f, py = 0f,
            pivotX = 0f, pivotY = 0f,
            angleDeg = 90f,
        )
        assertEquals(0f, rx, eps)
        assertEquals(100f, ry, eps)
    }

    // ── hitTestHandle ────────────────────────────────────────────────

    @Test
    fun `hitTestHandle detects top-left corner`() {
        val t = Transform(cx = 100f, cy = 100f, w = 200f, h = 100f)
        // Top-left corner is at (0, 50)
        val handle = TransformUtils.hitTestHandle(1f, 51f, t, handleWorldRadius = 10f)
        assertEquals(ResizeHandle.TOP_LEFT, handle)
    }

    @Test
    fun `hitTestHandle returns null when far from corners`() {
        val t = Transform(cx = 100f, cy = 100f, w = 200f, h = 100f)
        val handle = TransformUtils.hitTestHandle(100f, 100f, t, handleWorldRadius = 10f)
        assertNull(handle)
    }

    // ── hitTestRotationHandle ────────────────────────────────────────

    @Test
    fun `hitTestRotationHandle detects handle above node`() {
        val t = Transform(cx = 0f, cy = 0f, w = 100f, h = 100f)
        // Rotation handle is above top-center: (0, -50 - offset)
        val hit = TransformUtils.hitTestRotationHandle(
            0f, -70f, t, handleWorldRadius = 10f, handleOffset = 20f,
        )
        assertTrue(hit)
    }

    @Test
    fun `hitTestRotationHandle misses far points`() {
        val t = Transform(cx = 0f, cy = 0f, w = 100f, h = 100f)
        val hit = TransformUtils.hitTestRotationHandle(
            0f, 0f, t, handleWorldRadius = 10f, handleOffset = 20f,
        )
        assertFalse(hit)
    }
}

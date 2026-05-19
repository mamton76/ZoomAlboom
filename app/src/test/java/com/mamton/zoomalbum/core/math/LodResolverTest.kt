package com.mamton.zoomalbum.core.math

import com.mamton.zoomalbum.domain.model.CanvasNode
import com.mamton.zoomalbum.domain.model.RenderDetail
import com.mamton.zoomalbum.domain.model.Transform
import com.mamton.zoomalbum.domain.model.VisibilityPolicy
import org.junit.Assert.assertEquals
import org.junit.Test

class LodResolverTest {

    // ── Screen-size culling ──────────────────────────────────────────

    @Test
    fun `tiny node at low zoom is Hidden by screen-size culling`() {
        val node = frame(w = 1f, h = 1f)
        val camera = Camera(scale = 0.5f) // screen size = 0.5px < MIN_VISIBLE_PX
        assertEquals(RenderDetail.Hidden, LodResolver.resolveRenderDetail(node, camera))
    }

    @Test
    fun `node just above MIN_VISIBLE_PX passes screen-size culling`() {
        val node = frame(w = 10f, h = 10f)
        val camera = Camera(scale = 1f) // screen size = 10px > MIN_VISIBLE_PX
        // Should proceed to semantic filtering (Full with default policy)
        assertEquals(RenderDetail.Full, LodResolver.resolveRenderDetail(node, camera))
    }

    // ── Semantic zoom — default Frame policy ─────────────────────────

    @Test
    fun `frame in zoom range renders Full`() {
        val node = frame(w = 100f)
        // relativeZoom = 1.0, within default frame range
        val camera = Camera(scale = LodResolver.DEFAULT_REFERENCE_SCALE)
        assertEquals(RenderDetail.Full, LodResolver.resolveRenderDetail(node, camera))
    }

    @Test
    fun `frame below min relative zoom renders Stub`() {
        // Pick a scale safely below DEFAULT_FRAME_MIN_RELATIVE_ZOOM, then size the
        // node so screen-size culling does NOT fire — otherwise this test would
        // assert Stub but get Hidden via culling.
        val cameraScale = LodResolver.DEFAULT_FRAME_MIN_RELATIVE_ZOOM / 2f
        val node = frame(w = sizePassingCulling(cameraScale))
        val camera = Camera(scale = cameraScale)
        assertEquals(RenderDetail.Stub, LodResolver.resolveRenderDetail(node, camera))
    }

    @Test
    fun `frame above max relative zoom renders Simplified`() {
        val node = frame(w = 100f)
        val camera = Camera(scale = LodResolver.DEFAULT_FRAME_MAX_RELATIVE_ZOOM * 1.2f)
        assertEquals(RenderDetail.Simplified, LodResolver.resolveRenderDetail(node, camera))
    }

    // ── Semantic zoom — default Media policy ─────────────────────────

    @Test
    fun `media in zoom range renders Full`() {
        val node = media(w = 100f)
        val camera = Camera(scale = LodResolver.DEFAULT_REFERENCE_SCALE)
        assertEquals(RenderDetail.Full, LodResolver.resolveRenderDetail(node, camera))
    }

    @Test
    fun `media below min relative zoom renders Hidden`() {
        val cameraScale = LodResolver.DEFAULT_MEDIA_MIN_RELATIVE_ZOOM / 2f
        val node = media(w = sizePassingCulling(cameraScale))
        val camera = Camera(scale = cameraScale)
        assertEquals(RenderDetail.Hidden, LodResolver.resolveRenderDetail(node, camera))
    }

    @Test
    fun `media above max relative zoom renders Full`() {
        val node = media(w = 100f)
        val camera = Camera(scale = LodResolver.DEFAULT_MEDIA_MAX_RELATIVE_ZOOM * 1.5f)
        assertEquals(RenderDetail.Full, LodResolver.resolveRenderDetail(node, camera))
    }

    // ── Per-node override ────────────────────────────────────────────

    @Test
    fun `per-node policy overrides default`() {
        val policy = VisibilityPolicy(
            referenceScale = 2f,
            minRelativeZoom = 0.5f,
            maxRelativeZoom = 2f,
            belowRangeMode = RenderDetail.Preview,
            aboveRangeMode = RenderDetail.Stub,
        )
        val node = frame(w = 100f, visibilityPolicy = policy)

        // relativeZoom = 0.5/2.0 = 0.25 < 0.5 → belowRangeMode = Preview
        val below = LodResolver.resolveRenderDetail(node, Camera(scale = 0.5f))
        assertEquals(RenderDetail.Preview, below)

        // relativeZoom = 2.0/2.0 = 1.0 → in range → Full
        val inRange = LodResolver.resolveRenderDetail(node, Camera(scale = 2f))
        assertEquals(RenderDetail.Full, inRange)

        // relativeZoom = 5.0/2.0 = 2.5 > 2.0 → aboveRangeMode = Stub
        val above = LodResolver.resolveRenderDetail(node, Camera(scale = 5f))
        assertEquals(RenderDetail.Stub, above)
    }

    // ── Boundary values ──────────────────────────────────────────────

    @Test
    fun `relativeZoom exactly at minRelativeZoom is Full`() {
        val policy = VisibilityPolicy(referenceScale = 1f, minRelativeZoom = 0.5f)
        val node = frame(w = 100f, visibilityPolicy = policy)
        // relativeZoom = 0.5/1.0 = 0.5, NOT < 0.5 → Full
        assertEquals(RenderDetail.Full, LodResolver.resolveRenderDetail(node, Camera(scale = 0.5f)))
    }

    @Test
    fun `relativeZoom exactly at maxRelativeZoom is Full`() {
        val policy = VisibilityPolicy(referenceScale = 1f, maxRelativeZoom = 4f)
        val node = frame(w = 100f, visibilityPolicy = policy)
        // relativeZoom = 4.0/1.0 = 4.0, NOT > 4.0 → Full
        assertEquals(RenderDetail.Full, LodResolver.resolveRenderDetail(node, Camera(scale = 4f)))
    }

    @Test
    fun `referenceScale from creation zoom changes thresholds`() {
        // Node created at zoom 0.1 → referenceScale = 0.1
        // VisibilityPolicy uses its DEFAULT_MIN_RELATIVE_ZOOM / DEFAULT_MAX_RELATIVE_ZOOM
        // and the default belowRangeMode = Hidden.
        val refScale = 0.1f
        val policy = VisibilityPolicy(referenceScale = refScale)

        // Camera at refScale → relativeZoom = 1.0 → Full
        val inRangeCamera = Camera(scale = refScale)
        val inRangeNode = frame(w = sizePassingCulling(inRangeCamera.scale), visibilityPolicy = policy)
        assertEquals(RenderDetail.Full, LodResolver.resolveRenderDetail(inRangeNode, inRangeCamera))

        // Camera at refScale * (DEFAULT_MIN_RELATIVE_ZOOM / 2) → relativeZoom < default min → Hidden
        val belowMinCamera = Camera(scale = refScale * VisibilityPolicy.DEFAULT_MIN_RELATIVE_ZOOM / 2f)
        val belowMinNode = frame(w = sizePassingCulling(belowMinCamera.scale), visibilityPolicy = policy)
        assertEquals(RenderDetail.Hidden, LodResolver.resolveRenderDetail(belowMinNode, belowMinCamera))
    }

    // ── Helpers ──────────────────────────────────────────────────────

    /**
     * World-unit node size that comfortably exceeds [LodResolver.MIN_VISIBLE_PX] at
     * the given [cameraScale]. Use when a test wants to exercise the semantic-zoom
     * filter and must not be short-circuited by screen-size culling.
     */
    private fun sizePassingCulling(cameraScale: Float): Float =
        LodResolver.MIN_VISIBLE_PX * 1.5f / cameraScale

    private fun frame(
        w: Float = 100f,
        h: Float = w,
        visibilityPolicy: VisibilityPolicy? = null,
    ) = CanvasNode.Frame(
        id = "test_frame",
        transform = Transform(cx = 0f, cy = 0f, w = w, h = h),
        visibilityPolicy = visibilityPolicy,
    )

    private fun media(
        w: Float = 100f,
        h: Float = w,
        visibilityPolicy: VisibilityPolicy? = null,
    ) = CanvasNode.Media(
        id = "test_media",
        transform = Transform(cx = 0f, cy = 0f, w = w, h = h),
        mediaRefId = "ref",
        visibilityPolicy = visibilityPolicy,
    )
}

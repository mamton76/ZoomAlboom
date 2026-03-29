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
        val camera = Camera(scale = 1f)
        // relativeZoom = 1.0/1.0 = 1.0, within default frame range [0.01, 50]
        assertEquals(RenderDetail.Full, LodResolver.resolveRenderDetail(node, camera))
    }

    @Test
    fun `frame below min relative zoom renders Stub`() {
        // Default frame policy: referenceScale=1, minRelativeZoom=0.01, belowRangeMode=Stub
        val node = frame(w = 100f)
        val camera = Camera(scale = 0.005f) // relativeZoom = 0.005 < 0.01
        assertEquals(RenderDetail.Stub, LodResolver.resolveRenderDetail(node, camera))
    }

    @Test
    fun `frame above max relative zoom renders Simplified`() {
        // Default frame policy: referenceScale=1, maxRelativeZoom=50, aboveRangeMode=Simplified
        val node = frame(w = 100f)
        val camera = Camera(scale = 60f) // relativeZoom = 60 > 50
        assertEquals(RenderDetail.Simplified, LodResolver.resolveRenderDetail(node, camera))
    }

    // ── Semantic zoom — default Media policy ─────────────────────────

    @Test
    fun `media in zoom range renders Full`() {
        val node = media(w = 100f)
        val camera = Camera(scale = 1f)
        assertEquals(RenderDetail.Full, LodResolver.resolveRenderDetail(node, camera))
    }

    @Test
    fun `media below min relative zoom renders Hidden`() {
        // Default media: referenceScale=1, minRelativeZoom=0.1, belowRangeMode=Hidden
        val node = media(w = 100f)
        val camera = Camera(scale = 0.05f) // relativeZoom = 0.05 < 0.1
        assertEquals(RenderDetail.Hidden, LodResolver.resolveRenderDetail(node, camera))
    }

    @Test
    fun `media above max relative zoom renders Full`() {
        // Default media: maxRelativeZoom=10, aboveRangeMode=Full
        val node = media(w = 100f)
        val camera = Camera(scale = 15f) // relativeZoom = 15 > 10
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
        // Node created at zoom 0.1 — referenceScale = 0.1
        val policy = VisibilityPolicy(referenceScale = 0.1f)
        val node = frame(w = 500f, visibilityPolicy = policy)

        // Camera at 0.1 → relativeZoom = 1.0 → Full
        assertEquals(RenderDetail.Full, LodResolver.resolveRenderDetail(node, Camera(scale = 0.1f)))

        // Camera at 0.01 → relativeZoom = 0.1 → still in [0.25..4] default? No, 0.1 < 0.25 → Hidden
        assertEquals(RenderDetail.Hidden, LodResolver.resolveRenderDetail(node, Camera(scale = 0.01f)))
    }

    // ── Helpers ──────────────────────────────────────────────────────

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

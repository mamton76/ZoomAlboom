package com.mamton.zoomalbum.feature.canvas.view

import com.mamton.zoomalbum.domain.model.BackgroundData
import com.mamton.zoomalbum.domain.model.CanvasNode
import com.mamton.zoomalbum.domain.model.FrameAppearance
import com.mamton.zoomalbum.domain.model.NodeBlendMode
import com.mamton.zoomalbum.domain.model.OverlaySource
import com.mamton.zoomalbum.domain.model.OverlayStyle
import com.mamton.zoomalbum.domain.model.RenderDetail
import com.mamton.zoomalbum.domain.model.Transform
import com.mamton.zoomalbum.domain.usecase.FrameMembershipUseCase
import com.mamton.zoomalbum.feature.canvas.viewmodel.VisibleNode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FramePaintEventsTest {

    private val membership = FrameMembershipUseCase()

    private fun frame(
        id: String,
        zIndex: Float,
        cx: Float = 0f, cy: Float = 0f, w: Float = 100f, h: Float = 100f,
        appearance: FrameAppearance? = null,
    ): CanvasNode.Frame = CanvasNode.Frame(
        id = id,
        transform = Transform(cx = cx, cy = cy, w = w, h = h, zIndex = zIndex),
        appearance = appearance,
    )

    private fun media(
        id: String, zIndex: Float,
        cx: Float = 0f, cy: Float = 0f, w: Float = 50f, h: Float = 50f,
    ): CanvasNode.Media = CanvasNode.Media(
        id = id,
        transform = Transform(cx = cx, cy = cy, w = w, h = h, zIndex = zIndex),
        mediaRefId = "asset-$id",
    )

    private fun vn(node: CanvasNode, detail: RenderDetail = RenderDetail.Full) =
        VisibleNode(node = node, detail = detail)

    private val solidOverlay = FrameAppearance(
        background = BackgroundData.SolidBackgroundData("#202020"),
        contentOverlays = listOf(
            OverlayStyle(
                source = OverlaySource.SolidColor("#40FFFFFF"),
                opacity = 0.25f,
                blendMode = NodeBlendMode.SoftLight,
            ),
        ),
    )

    @Test
    fun `frame without contentOverlays emits a single NodePass`() {
        val f = frame("f", zIndex = 0f)
        val events = buildFramePaintEvents(listOf(vn(f)), membership)

        assertEquals(1, events.size)
        assertTrue(events.single() is FramePaintEvent.NodePass)
    }

    @Test
    fun `layered frame emits Surface at frame z and Overlay strictly after`() {
        val f = frame("f", zIndex = 5f, appearance = solidOverlay)
        val events = buildFramePaintEvents(listOf(vn(f)), membership)

        assertEquals(2, events.size)
        val surface = events[0] as FramePaintEvent.LayeredFrameSurface
        val overlay = events[1] as FramePaintEvent.LayeredFrameOverlay
        assertEquals("f", surface.frame.id)
        assertEquals(5f, surface.sortKey, 0f)
        assertTrue("Overlay must sort strictly after Surface", overlay.sortKey > surface.sortKey)
    }

    @Test
    fun `layered frame Overlay sorts past its highest-z member`() {
        // Frame at z=1, two members fully inside it at z=5 and z=10.
        val f = frame("f", zIndex = 1f, w = 200f, h = 200f, appearance = solidOverlay)
        val mLow = media("low", zIndex = 5f)        // inside frame (default cx/cy = 0)
        val mHigh = media("high", zIndex = 10f)     // inside frame

        val events = buildFramePaintEvents(
            visibleNodes = listOf(vn(f), vn(mLow), vn(mHigh)),
            membershipUseCase = membership,
        )

        // Expected order: Surface(f), NodePass(mLow), NodePass(mHigh), Overlay(f)
        assertEquals(4, events.size)
        assertTrue(events[0] is FramePaintEvent.LayeredFrameSurface)
        assertEquals("low", (events[1] as FramePaintEvent.NodePass).node.id)
        assertEquals("high", (events[2] as FramePaintEvent.NodePass).node.id)
        val overlay = events[3] as FramePaintEvent.LayeredFrameOverlay
        assertTrue(
            "Overlay sortKey ${overlay.sortKey} should be > highest member z (10)",
            overlay.sortKey > 10f,
        )
    }

    @Test
    fun `layered frame with no members anchors Overlay just past the frame z`() {
        val f = frame("f", zIndex = 3f, appearance = solidOverlay)
        // A media node well outside the frame's geometry (so it's NOT a member).
        val outside = media("outside", zIndex = 8f, cx = 10_000f)

        val events = buildFramePaintEvents(listOf(vn(f), vn(outside)), membership)

        val overlay = events.filterIsInstance<FramePaintEvent.LayeredFrameOverlay>().single()
        // Outside is not a member, so it must NOT drag the overlay's z above its own.
        assertTrue("Overlay (${overlay.sortKey}) should be just past frame z (3)", overlay.sortKey > 3f)
        assertTrue("Overlay (${overlay.sortKey}) should remain below 'outside' (z=8)", overlay.sortKey < 8f)
    }

    @Test
    fun `non-layered frame with members preserves single-pass order`() {
        // A frame with no contentOverlays — even with members, no Surface/Overlay split.
        val f = frame("f", zIndex = 1f, w = 200f, h = 200f)
        val m = media("m", zIndex = 5f)

        val events = buildFramePaintEvents(listOf(vn(f), vn(m)), membership)

        assertEquals(2, events.size)
        assertTrue(events.all { it is FramePaintEvent.NodePass })
        assertEquals("f", (events[0] as FramePaintEvent.NodePass).node.id)
        assertEquals("m", (events[1] as FramePaintEvent.NodePass).node.id)
    }
}

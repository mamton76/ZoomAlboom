package com.mamton.zoomalbum.feature.canvas.gestures

import androidx.compose.ui.geometry.Offset
import kotlin.math.cos
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure-function tests for [computeFilteredTransform], the noise-filter
 * underpinning the 2-finger camera detector. Covers each gate:
 *
 *  - still-finger drop (both fingers below 1 px)
 *  - parallel-pan suppression of phantom rotation / zoom (the 2026-06-05
 *    drizzle bug: `fingers=11,-7 | 14.5,-8` → fake 0.47° rotation)
 *  - anchor-and-orbit rotation (one finger fixed, other arcs)
 *  - pure pinch
 *  - combined pinch + rotate
 *  - rotation noise floor (0.3°)
 *  - pan sub-pixel deadband
 */
class InfiniteCanvasGestureNoiseTest {

    /**
     * Build a two-finger event from before / after positions. Returns
     * positions, deltas, plus the centroid-derived pan and Compose-style
     * zoom (currentDist / previousDist) the caller would have computed
     * from the event.
     */
    private data class Inputs(
        val prev: List<Offset>,
        val curr: List<Offset>,
        val deltas: List<Offset>,
        val pan: Offset,
        val zoom: Float,
    )

    private fun inputs(p0Prev: Offset, p0Curr: Offset, p1Prev: Offset, p1Curr: Offset): Inputs {
        val d0 = p0Curr - p0Prev
        val d1 = p1Curr - p1Prev
        val pan = Offset((d0.x + d1.x) / 2f, (d0.y + d1.y) / 2f)
        val distPrev = (p1Prev - p0Prev).getDistance()
        val distCurr = (p1Curr - p0Curr).getDistance()
        val zoom = if (distPrev > 0f) distCurr / distPrev else 1f
        return Inputs(
            prev = listOf(p0Prev, p1Prev),
            curr = listOf(p0Curr, p1Curr),
            deltas = listOf(d0, d1),
            pan = pan,
            zoom = zoom,
        )
    }

    private fun Inputs.filter(): FilteredTransform? = computeFilteredTransform(
        fingerDeltas = deltas,
        fingerPositions = curr,
        fingerPrev = prev,
        pan = pan,
        zoom = zoom,
    )

    @Test
    fun `still fingers below 1px are dropped entirely`() {
        // Both fingers jitter by 0.5 px — sub-threshold for the
        // still-finger gate. Whole event is discarded.
        val out = inputs(
            p0Prev = Offset(100f, 100f),
            p0Curr = Offset(100.5f, 100f),
            p1Prev = Offset(200f, 100f),
            p1Curr = Offset(200.4f, 100.1f),
        ).filter()
        assertNull(out)
    }

    @Test
    fun `parallel pan flows, rotation and zoom suppressed`() {
        // Both fingers translate identically — pure pan. cosθ = 1.
        val out = inputs(
            p0Prev = Offset(100f, 100f),
            p0Curr = Offset(110f, 100f),
            p1Prev = Offset(200f, 100f),
            p1Curr = Offset(210f, 100f),
        ).filter()
        requireNotNull(out)
        assertEquals(10f, out.pan.x, EPS)
        assertEquals(0f, out.pan.y, EPS)
        assertEquals(1f, out.zoom, EPS)
        assertEquals(0f, out.rotation, EPS)
    }

    @Test
    fun `asymmetric parallel pan does not leak phantom rotation`() {
        // The 2026-06-05 bug: fingers move in roughly the same direction
        // but with different magnitudes. Centroid-based rotation reports a
        // fake angle change; the diverging-direction gate must suppress it.
        val out = inputs(
            p0Prev = Offset(100f, 100f),
            p0Curr = Offset(111f, 93f),     // d0 = (11, -7)
            p1Prev = Offset(200f, 100f),
            p1Curr = Offset(214.5f, 92f),   // d1 = (14.5, -8)
        ).filter()
        requireNotNull(out)
        // Pan still flows (mean of deltas).
        assertEquals(12.75f, out.pan.x, EPS)
        assertEquals(-7.5f, out.pan.y, EPS)
        // Rotation + zoom must be identity — fingers are moving in
        // roughly the same direction (cosθ ≈ 0.998).
        assertEquals(0f, out.rotation, EPS)
        assertEquals(1f, out.zoom, EPS)
    }

    @Test
    fun `anchor-and-orbit fires rotation even though one finger is fixed`() {
        // Natural rotation grip: finger 0 anchors, finger 1 arcs by 10°.
        // The `bothFingersMoving` gate would have killed this — the new
        // angle-of-inter-finger-vector path lets it through.
        val angleRad = Math.toRadians(10.0).toFloat()
        val r = 100f
        val p0 = Offset(100f, 100f)
        val out = inputs(
            p0Prev = p0,
            p0Curr = p0,
            p1Prev = Offset(p0.x + r, p0.y),
            p1Curr = Offset(p0.x + r * cos(angleRad), p0.y + r * sin(angleRad)),
        ).filter()
        requireNotNull(out)
        assertEquals(10f, out.rotation, 0.05f)
        // Zoom must stay 1 — the radius is constant in a pure orbit.
        assertEquals(1f, out.zoom, EPS)
    }

    @Test
    fun `pure pinch fires zoom, rotation and pan stay identity`() {
        // Fingers move toward each other along x. dist 100 → 80, zoom 0.8.
        val out = inputs(
            p0Prev = Offset(100f, 100f),
            p0Curr = Offset(110f, 100f),    // d0 = (10, 0)
            p1Prev = Offset(200f, 100f),
            p1Curr = Offset(190f, 100f),    // d1 = (-10, 0)
        ).filter()
        requireNotNull(out)
        assertEquals(Offset.Zero, out.pan)
        assertEquals(0.8f, out.zoom, EPS)
        assertEquals(0f, out.rotation, EPS)
    }

    @Test
    fun `combined pinch and rotate dispatches all three components`() {
        // Fingers move in opposing arcs with a radial component — the
        // canonical pinch-and-twist. All three components should fire.
        val out = inputs(
            p0Prev = Offset(100f, 100f),
            p0Curr = Offset(105f, 105f),    // d0 = (5, 5)
            p1Prev = Offset(200f, 100f),
            p1Curr = Offset(190f, 90f),     // d1 = (-10, -10)
        ).filter()
        requireNotNull(out)
        assertNotEquals(Offset.Zero, out.pan)
        // Zoom in (distance shrinks): expect < 1.
        assertTrue("expected zoom < 1, got ${out.zoom}", out.zoom < 1f)
        // Rotation has a definite sign (counter-clockwise here per
        // y-down screen coords). Magnitude > 0.3° is the only firm
        // contract; exact value depends on the geometry.
        assertTrue(
            "expected |rotation| > 0.3°, got ${out.rotation}",
            kotlin.math.abs(out.rotation) > 0.3f,
        )
    }

    @Test
    fun `rotation below 0_3deg noise floor is suppressed`() {
        // Diverging deltas (cosθ ≈ -1) but the inter-finger angle change
        // is only ~0.19° — below the rotation noise floor. Zoom may still
        // fire because fingers do move toward / away.
        val out = inputs(
            p0Prev = Offset(100f, 100f),
            p0Curr = Offset(105f, 100f),    // d0 = (5, 0)
            p1Prev = Offset(200f, 100f),
            p1Curr = Offset(195f, 100.3f),  // d1 = (-5, 0.3)
        ).filter()
        requireNotNull(out)
        assertEquals(0f, out.rotation, EPS)
    }

    @Test
    fun `pan below half-pixel is filtered to zero`() {
        // Fingers move equally and oppositely along x — centroid pan
        // cancels to zero. Sub-pixel jitter in the pan dimension never
        // reaches the camera.
        val out = inputs(
            p0Prev = Offset(100f, 100f),
            p0Curr = Offset(102f, 100f),    // d0 = (2, 0)
            p1Prev = Offset(200f, 100f),
            p1Curr = Offset(198f, 100f),    // d1 = (-2, 0)
        ).filter()
        requireNotNull(out)
        assertEquals(Offset.Zero, out.pan)
    }

    companion object {
        private const val EPS = 1e-3f
    }
}

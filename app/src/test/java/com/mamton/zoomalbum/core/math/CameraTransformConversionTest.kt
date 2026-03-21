package com.mamton.zoomalbum.core.math

import com.mamton.zoomalbum.domain.model.Transform
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for [Transform.toCamera] conversion.
 *
 * Camera.cx/cy are graphicsLayer translation values (screen pixels), not world coords.
 * Screen position of world point (wx, wy) = (wx * scale + cx, wy * scale + cy).
 */
class CameraTransformConversionTest {

    private val eps = 0.01f

    /**
     * A 800×600 frame at world (100, 200) with scale=1 on a 1080×1920 screen.
     * Expected camera scale = min(1080*0.9/800, 1920*0.9/600) = min(1.215, 2.88) = 1.215.
     * Camera cx/cy should place the frame center at screen center.
     */
    @Test
    fun `toCamera produces correct scale to fit frame on screen`() {
        val transform = Transform(
            cx = 100f,
            cy = 200f,
            w = 800f,
            h = 600f,
            scale = 1f,
            rotation = 0f,
        )
        val camera = transform.toCamera(screenWidth = 1080f, screenHeight = 1920f)

        // Scale: frame width should fill 90% of screen width
        val expectedScale = (1080f * 0.9f) / 800f  // 1.215
        assertEquals(expectedScale, camera.scale, eps)

        // cx/cy: place frame center at screen center
        val expectedCx = 1080f / 2f - 100f * expectedScale
        val expectedCy = 1920f / 2f - 200f * expectedScale
        assertEquals(expectedCx, camera.cx, eps)
        assertEquals(expectedCy, camera.cy, eps)
    }

    /**
     * Round-trip: create frame → toCamera → cameraViewport → frame center inside viewport.
     */
    @Test
    fun `round trip - frame center is inside viewport bounds`() {
        val transform = Transform(
            cx = 500f,
            cy = 300f,
            w = 400f,
            h = 300f,
            scale = 1f,
            rotation = 0f,
        )
        val screenWidth = 1080f
        val screenHeight = 1920f

        val camera = transform.toCamera(screenWidth, screenHeight)
        val viewport = TransformUtils.cameraViewport(
            cameraCx = camera.cx,
            cameraCy = camera.cy,
            cameraScale = camera.scale,
            cameraRotation = camera.rotation,
            screenWidth = screenWidth,
            screenHeight = screenHeight,
        )

        assertTrue(
            "Frame center (${transform.cx}, ${transform.cy}) must be inside viewport $viewport",
            viewport.contains(transform.cx, transform.cy),
        )
    }

    /** Camera rotation is copied from Transform rotation. */
    @Test
    fun `toCamera copies rotation from transform`() {
        val transform = Transform(cx = 0f, cy = 0f, w = 100f, h = 100f, rotation = 45f)
        val camera = transform.toCamera(1080f, 1920f)
        assertEquals(45f, camera.rotation, eps)
    }

    /**
     * Larger fillFraction → more zoomed in → larger camera scale.
     * scale = screenWidth * fillFraction / renderW.
     */
    @Test
    fun `toCamera respects fillFraction - larger fraction means more zoom`() {
        val transform = Transform(cx = 0f, cy = 0f, w = 200f, h = 200f, scale = 1f)
        val camera50 = transform.toCamera(1000f, 1000f, fillFraction = 0.5f)
        val camera90 = transform.toCamera(1000f, 1000f, fillFraction = 0.9f)
        // fillFraction=0.9 → scale=4.5, fillFraction=0.5 → scale=2.5
        assertTrue(
            "fillFraction=0.9 scale (${camera90.scale}) should be > fillFraction=0.5 scale (${camera50.scale})",
            camera90.scale > camera50.scale,
        )
    }
}
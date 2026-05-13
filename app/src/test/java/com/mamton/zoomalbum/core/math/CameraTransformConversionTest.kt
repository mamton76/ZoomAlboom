package com.mamton.zoomalbum.core.math

import com.mamton.zoomalbum.domain.model.FrameFitMode
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
     * Default safeAreaInset = 0.1 → fill = 0.8 on each axis.
     * Expected camera scale = min(1080*0.8/800, 1920*0.8/600) = min(1.08, 2.56) = 1.08.
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

        // CONTAIN at default safeAreaInset=0.1 → 80% width fill is the binding axis.
        val expectedScale = (1080f * 0.8f) / 800f  // 1.08
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
     * Smaller safeAreaInset → less padding → larger camera scale.
     * scale = screenWidth * (1 - 2*inset) / renderW.
     */
    @Test
    fun `toCamera respects safeAreaInset - smaller inset means more zoom`() {
        val transform = Transform(cx = 0f, cy = 0f, w = 200f, h = 200f, scale = 1f)
        val cameraInset25 = transform.toCamera(1000f, 1000f, safeAreaInset = 0.25f)  // fill=0.5
        val cameraInset05 = transform.toCamera(1000f, 1000f, safeAreaInset = 0.05f)  // fill=0.9
        assertTrue(
            "inset=0.05 scale (${cameraInset05.scale}) should be > inset=0.25 scale (${cameraInset25.scale})",
            cameraInset05.scale > cameraInset25.scale,
        )
    }

    /**
     * COVER produces a larger scale than CONTAIN when frame and screen aspect ratios differ —
     * COVER uses max(sx, sy), CONTAIN uses min.
     */
    @Test
    fun `toCamera COVER vs CONTAIN - COVER scales larger when aspects differ`() {
        // 800×600 frame on 1080×1920 screen: tall screen, wide frame.
        // sx = 1080*0.8/800 = 1.08, sy = 1920*0.8/600 = 2.56.
        val transform = Transform(cx = 0f, cy = 0f, w = 800f, h = 600f, scale = 1f)
        val contain = transform.toCamera(1080f, 1920f, fitMode = FrameFitMode.CONTAIN)
        val cover = transform.toCamera(1080f, 1920f, fitMode = FrameFitMode.COVER)
        assertEquals(1.08f, contain.scale, eps)
        assertEquals(2.56f, cover.scale, eps)
    }
}
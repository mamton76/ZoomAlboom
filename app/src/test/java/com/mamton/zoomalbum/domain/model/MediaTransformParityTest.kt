package com.mamton.zoomalbum.domain.model

import com.mamton.zoomalbum.core.math.BoundingBox
import com.mamton.zoomalbum.core.math.Camera
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Guards the video MVP invariant (`video.md`, `todo.md § 27.7`): a video node
 * behaves like an image node for *all* transform/selection purposes. Geometry
 * must never fork on [MediaType] — image vs. video is only a rendering /
 * playback branch.
 *
 * These are pure guards: if someone later special-cases video geometry in the
 * factory or the shared transform path, one of these breaks.
 */
class MediaTransformParityTest {

    private val camera = Camera(cx = 0f, cy = 0f, scale = 1f, rotation = 0f)
    private val viewport = BoundingBox(left = -500f, top = -500f, right = 500f, bottom = 500f)

    @Test
    fun `createMedia produces identical geometry for IMAGE and VIDEO`() {
        fun make(type: MediaType) = CanvasNodeFactory.createMedia(
            uri = "file:///clip",
            imageWidth = 1920,
            imageHeight = 1080,
            screenWidth = 1080f,
            screenHeight = 1920f,
            viewport = viewport,
            nextZIndex = 3f,
            camera = camera,
            mediaType = type,
        )

        val image = make(MediaType.IMAGE)
        val video = make(MediaType.VIDEO)

        // Only the media type differs; the transform + intrinsic dims match.
        assertEquals(image.transform, video.transform)
        assertEquals(image.intrinsicPixelWidth, video.intrinsicPixelWidth)
        assertEquals(image.intrinsicPixelHeight, video.intrinsicPixelHeight)
        assertEquals(MediaType.IMAGE, image.mediaType)
        assertEquals(MediaType.VIDEO, video.mediaType)
    }

    @Test
    fun `move resize rotate via withTransform are type-agnostic and preserve media type`() {
        val base = Transform(cx = 10f, cy = 20f, w = 200f, h = 100f, scale = 1.5f, rotation = 15f, zIndex = 2f)
        val image = CanvasNode.Media(id = "img", transform = base, mediaRefId = "a", mediaType = MediaType.IMAGE)
        val video = CanvasNode.Media(id = "vid", transform = base, mediaRefId = "b", mediaType = MediaType.VIDEO)

        // Simulate the shared reducer math: move, then resize, then rotate.
        val moved = base.copy(cx = base.cx + 50f, cy = base.cy - 30f)
        val resized = moved.copy(scale = moved.scale * 2f)
        val rotated = resized.copy(rotation = resized.rotation + 90f)

        val imageOut = image.withTransform(rotated)
        val videoOut = video.withTransform(rotated)

        // Identical resulting geometry for both types.
        assertEquals(imageOut.transform, videoOut.transform)
        assertEquals(rotated, imageOut.transform)
        // Type is preserved across the transform.
        assertEquals(MediaType.IMAGE, (imageOut as CanvasNode.Media).mediaType)
        assertEquals(MediaType.VIDEO, (videoOut as CanvasNode.Media).mediaType)
    }
}

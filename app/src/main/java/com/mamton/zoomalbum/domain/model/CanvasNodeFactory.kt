package com.mamton.zoomalbum.domain.model

import com.mamton.zoomalbum.core.math.BoundingBox
import com.mamton.zoomalbum.core.math.Camera
import kotlin.random.Random

/**
 * Creates new [CanvasNode] instances positioned relative to the current viewport.
 */
object CanvasNodeFactory {

    private val PALETTE = arrayOf(
        "#E53935", "#1E88E5", "#43A047", "#FB8C00", "#8E24AA",
        "#00897B", "#F4511E", "#3949AB", "#C0CA33", "#D81B60",
    )

    /**
     * Creates a frame centered in the viewport, ~80% of visible area.
     *
     * Frame dimensions are derived from screen pixels / camera.scale — NOT from
     * the viewport AABB. The AABB expands when the camera is rotated (at 45° it is
     * nearly square even on a portrait screen), so using it for w/h produces wrong
     * aspect ratios. Screen pixels are always rotation-independent.
     *
     * viewport is only used for cx/cy (the world point at the screen center).
     * rotation = -camera.rotation so the frame appears axis-aligned on screen.
     *
     * Scaling convention: same as [createMedia] — `transform.scale = 1 / camera.scale` and
     * `w/h = targetRender / scale = targetRender * camera.scale`. Visual is
     * `renderW = w*scale = targetRender`. The `1/camera.scale` factor lives in `scale`,
     * making `w/h` camera-independent ("canonical render size at scale=1").
     */
    fun createFrame(
        screenWidth: Float,
        screenHeight: Float,
        viewport: BoundingBox,
        nextZIndex: Float,
        camera: Camera,
    ): CanvasNode.Frame {
        // Visible world extent along screen axes — rotation-independent.
        val visibleWorldW = screenWidth / camera.scale
        val visibleWorldH = screenHeight / camera.scale

        val targetRenderW = visibleWorldW * 0.8f
        val targetRenderH = visibleWorldH * 0.8f
        val initialScale = 1f / camera.scale

        return CanvasNode.Frame(
            id = "frame_${System.currentTimeMillis()}",
            transform = Transform(
                cx = viewport.centerX,
                cy = viewport.centerY,
                w = targetRenderW / initialScale,
                h = targetRenderH / initialScale,
                scale = initialScale,
                rotation = -camera.rotation,
                zIndex = nextZIndex,
            ),
            color = PALETTE[Random.nextInt(PALETTE.size)],
            visibilityPolicy = VisibilityPolicy(referenceScale = camera.scale),
        )
    }

    /**
     * Creates a media node centered in the viewport, sized to preserve [imageWidth]×[imageHeight]
     * aspect ratio and fit within 80% of the visible world area.
     *
     * [imageWidth]/[imageHeight] are the image's native pixel dimensions (after EXIF rotation).
     * Pass 0/0 to fall back to a 4:3 portrait default; the node will store 0 as "unknown" intrinsic
     * size and LOD will fall back to render-size-only decisions.
     *
     * Scaling convention: `transform.scale = 1 / camera.scale` at creation;
     * `w/h = targetRender / scale = targetRender * camera.scale`. The `1/camera.scale` factor
     * (inherent in `targetRender`, since the viewport in world units shrinks as we zoom in)
     * lives in `scale`, leaving `w/h` camera-independent — they represent the canonical render
     * size at `scale = 1`. Visual: `renderW = w * scale = targetRender`.
     */
    fun createMedia(
        uri: String,
        imageWidth: Int,
        imageHeight: Int,
        screenWidth: Float,
        screenHeight: Float,
        viewport: BoundingBox,
        nextZIndex: Float,
        camera: Camera,
    ): CanvasNode.Media {
        val visibleWorldW = screenWidth / camera.scale
        val visibleWorldH = screenHeight / camera.scale
        val maxW = visibleWorldW * 0.8f
        val maxH = visibleWorldH * 0.8f

        // Target rendered size in world units (preserves aspect ratio, fits 80% of viewport).
        val (targetRenderW, targetRenderH) = if (imageWidth > 0 && imageHeight > 0) {
            val scaleW = maxW / imageWidth
            val scaleH = maxH / imageHeight
            val fit = minOf(scaleW, scaleH)
            imageWidth * fit to imageHeight * fit
        } else {
            val w = visibleWorldW * 0.6f
            w to (w * 4f / 3f).coerceAtMost(maxH)
        }

        val initialScale = 1f / camera.scale
        return CanvasNode.Media(
            id = "media_${System.currentTimeMillis()}",
            transform = Transform(
                cx = viewport.centerX,
                cy = viewport.centerY,
                w = targetRenderW / initialScale,
                h = targetRenderH / initialScale,
                scale = initialScale,
                rotation = -camera.rotation,
                zIndex = nextZIndex,
            ),
            mediaRefId = uri,
            mediaType = MediaType.IMAGE,
            intrinsicPixelWidth = imageWidth.coerceAtLeast(0),
            intrinsicPixelHeight = imageHeight.coerceAtLeast(0),
            visibilityPolicy = VisibilityPolicy(referenceScale = camera.scale),
        )
    }
}
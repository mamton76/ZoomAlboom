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

        val frameW = visibleWorldW * 0.8f
        val frameH = visibleWorldH * 0.8f

        return CanvasNode.Frame(
            id = "frame_${System.currentTimeMillis()}",
            transform = Transform(
                cx = viewport.centerX,
                cy = viewport.centerY,
                w = frameW,
                h = frameH,
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
     * [imageWidth]/[imageHeight] are the image's native pixel dimensions. Pass 0/0 to fall back
     * to a 4:3 portrait default.
     *
     * Like [createFrame], dimensions use screen pixels / camera.scale so the node appears
     * at a consistent physical size regardless of zoom level at creation time.
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

        val (mediaW, mediaH) = if (imageWidth > 0 && imageHeight > 0) {
            val scaleW = maxW / imageWidth
            val scaleH = maxH / imageHeight
            val fit = minOf(scaleW, scaleH)
            imageWidth * fit to imageHeight * fit
        } else {
            // Fallback when dimensions are unavailable
            val w = visibleWorldW * 0.6f
            w to (w * 4f / 3f).coerceAtMost(maxH)
        }

        return CanvasNode.Media(
            id = "media_${System.currentTimeMillis()}",
            transform = Transform(
                cx = viewport.centerX,
                cy = viewport.centerY,
                w = mediaW,
                h = mediaH,
                rotation = -camera.rotation,
                zIndex = nextZIndex,
            ),
            mediaRefId = uri,
            mediaType = MediaType.IMAGE,
        )
    }
}
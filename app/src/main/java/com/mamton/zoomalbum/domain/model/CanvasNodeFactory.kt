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
     * Transform uses center-based coordinates: cx/cy = center of the frame in world space.
     * w/h = actual world-unit dimensions (not normalized).
     * rotation = -camera.rotation so the frame appears axis-aligned on screen.
     */
    fun createFrame(
        viewport: BoundingBox,
        nextZIndex: Float,
        camera: Camera,
    ): CanvasNode.Frame {
        val frameW = viewport.width * 0.8f
        val frameH = viewport.height * 0.8f

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
        )
    }

    // Future: createMedia(), createText(), createSticker()
}
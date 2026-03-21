package com.mamton.zoomalbum.domain.model

import com.mamton.zoomalbum.core.math.BoundingBox
import com.mamton.zoomalbum.core.math.TransformUtils
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
     * - `w`, `h` encode the viewport's aspect ratio (normalized so the short side = 1).
     * - `scale` is derived from camera zoom so `w * scale` fills ~80% of the viewport width.
     * - `rotation` = `-camera.rotation` so the frame appears axis-aligned on screen.
     * - `x`, `y` are computed so the frame's visual center sits at the viewport center,
     *    accounting for rotation around the top-left origin.
     */
    fun createFrame(
        viewport: BoundingBox,
        nextZIndex: Float,
        camera: Camera,
    ): CanvasNode.Frame {
        // 1. Proportion: normalize so short side = 1
        val aspect = viewport.width / viewport.height
        val w: Float
        val h: Float
        if (aspect >= 1f) {
            h = 1f
            w = aspect
        } else {
            w = 1f
            h = 1f / aspect
        }

        // 2. Scale: make w * nodeScale ≈ 80% of viewport width
        val nodeScale = viewport.width * 0.8f / w

        // 3. Rotation: negate camera rotation so frame aligns with screen
        val frameRotation = -camera.rotation

        // 4. Position: center the rotated frame at viewport center.
        //    The renderer rotates around top-left (0,0), so the visual center
        //    of the rect shifts from (halfW, halfH) to rotate(halfW, halfH).
        val halfW = w * nodeScale / 2f
        val halfH = h * nodeScale / 2f
        val (rcx, rcy) = TransformUtils.rotateVector(halfW, halfH, frameRotation)
        val x = viewport.centerX - rcx
        val y = viewport.centerY - rcy

        return CanvasNode.Frame(
            id = "frame_${System.currentTimeMillis()}",
            transform = Transform(
                x = x,
                y = y,
                w = w,
                h = h,
                scale = nodeScale,
                rotation = frameRotation,
                zIndex = nextZIndex,
            ),
            color = PALETTE[Random.nextInt(PALETTE.size)],
        )
    }

    // Future: createMedia(), createText(), createSticker()
}

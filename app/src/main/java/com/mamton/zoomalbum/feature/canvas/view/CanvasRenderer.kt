package com.mamton.zoomalbum.feature.canvas.view

import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import com.mamton.zoomalbum.domain.model.CanvasNode
import androidx.core.graphics.toColorInt

/**
 * Renders a single [CanvasNode] inside the camera-transformed container.
 *
 * Positioning and sizing use [graphicsLayer] (GPU-only) + [drawBehind],
 * bypassing Compose layout Constraints entirely. This allows arbitrarily
 * large world-coordinate dimensions without crashing.
 */
@Composable
fun CanvasNodeRenderer(node: CanvasNode) {
    when (node) {
        is CanvasNode.Frame -> FrameRenderer(node)
        is CanvasNode.Media -> { /* Stage 2 — Coil image loading */ }
    }
}

@Composable
private fun FrameRenderer(frame: CanvasNode.Frame) {
    val t = frame.transform
    val fillColor = Color(frame.color.toColorInt())
    val borderColor = fillColor.copy(alpha = 0.6f)
    val widthPx = t.w * t.scale
    val heightPx = t.h * t.scale

    Spacer(
        modifier = Modifier
            .graphicsLayer {
                translationX = t.x
                translationY = t.y
                rotationZ = t.rotation
                transformOrigin = TransformOrigin(0f, 0f)
                clip = false
            }
            .drawBehind {
                val nodeSize = Size(widthPx, heightPx)
                val radius = CornerRadius(4f, 4f)
                drawRoundRect(
                    color = fillColor.copy(alpha = 0.35f),
                    size = nodeSize,
                    cornerRadius = radius,
                )
                drawRoundRect(
                    color = borderColor,
                    size = nodeSize,
                    cornerRadius = radius,
                    style = Stroke(width = 1.5f),
                )
            },
    )
}

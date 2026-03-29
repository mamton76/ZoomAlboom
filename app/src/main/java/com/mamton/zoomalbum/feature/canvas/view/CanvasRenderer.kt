package com.mamton.zoomalbum.feature.canvas.view

import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import com.mamton.zoomalbum.domain.model.CanvasNode
import com.mamton.zoomalbum.domain.model.RenderDetail
import androidx.core.graphics.toColorInt

/**
 * Renders a single [CanvasNode] inside the camera-transformed container.
 *
 * Positioning and sizing use [graphicsLayer] (GPU-only) + [drawBehind],
 * bypassing Compose layout Constraints entirely. This allows arbitrarily
 * large world-coordinate dimensions without crashing.
 */
@Composable
fun CanvasNodeRenderer(node: CanvasNode, detail: RenderDetail) {
    when (node) {
        is CanvasNode.Frame -> FrameRenderer(node, detail)
        is CanvasNode.Media -> { /* Stage 2 — Coil image loading */ }
    }
}

@Composable
private fun FrameRenderer(frame: CanvasNode.Frame, detail: RenderDetail) {
    val t = frame.transform
    val renderW = t.renderW
    val renderH = t.renderH

    when (detail) {
        RenderDetail.Hidden -> return
        RenderDetail.Stub -> StubRenderer(t.cx, t.cy, t.rotation, renderW, renderH, frame.color)
        RenderDetail.Preview -> StubRenderer(t.cx, t.cy, t.rotation, renderW, renderH, frame.color)
        RenderDetail.Simplified -> SimplifiedFrameRenderer(t.cx, t.cy, t.rotation, renderW, renderH, frame.color)
        RenderDetail.Full -> FullFrameRenderer(t.cx, t.cy, t.rotation, renderW, renderH, frame.color)
    }
}

/** Full render: filled rounded rect with border. */
@Composable
private fun FullFrameRenderer(
    cx: Float, cy: Float, rotation: Float,
    renderW: Float, renderH: Float, colorHex: String,
) {
    val fillColor = Color(colorHex.toColorInt())
    val borderColor = fillColor.copy(alpha = 0.6f)

    Spacer(
        modifier = Modifier
            .graphicsLayer {
                translationX = cx
                translationY = cy
                rotationZ = rotation
                transformOrigin = TransformOrigin(0f, 0f)
                clip = false
            }
            .drawBehind {
                val topLeft = Offset(-renderW / 2f, -renderH / 2f)
                val nodeSize = Size(renderW, renderH)
                val radius = CornerRadius(4f, 4f)
                drawRoundRect(
                    color = fillColor.copy(alpha = 0.35f),
                    topLeft = topLeft,
                    size = nodeSize,
                    cornerRadius = radius,
                )
                drawRoundRect(
                    color = borderColor,
                    topLeft = topLeft,
                    size = nodeSize,
                    cornerRadius = radius,
                    style = Stroke(width = 1.5f),
                )
            },
    )
}

/** Simplified: border only, no fill. Lightweight for zoomed-in view of large frames. */
@Composable
private fun SimplifiedFrameRenderer(
    cx: Float, cy: Float, rotation: Float,
    renderW: Float, renderH: Float, colorHex: String,
) {
    val borderColor = Color(colorHex.toColorInt()).copy(alpha = 0.4f)

    Spacer(
        modifier = Modifier
            .graphicsLayer {
                translationX = cx
                translationY = cy
                rotationZ = rotation
                transformOrigin = TransformOrigin(0f, 0f)
                clip = false
            }
            .drawBehind {
                val topLeft = Offset(-renderW / 2f, -renderH / 2f)
                val nodeSize = Size(renderW, renderH)
                drawRoundRect(
                    color = borderColor,
                    topLeft = topLeft,
                    size = nodeSize,
                    cornerRadius = CornerRadius(4f, 4f),
                    style = Stroke(width = 1f),
                )
            },
    )
}

/** Stub: small colored rect — minimal draw for zoomed-out overview. */
@Composable
private fun StubRenderer(
    cx: Float, cy: Float, rotation: Float,
    renderW: Float, renderH: Float, colorHex: String,
) {
    val color = Color(colorHex.toColorInt()).copy(alpha = 0.5f)

    Spacer(
        modifier = Modifier
            .graphicsLayer {
                translationX = cx
                translationY = cy
                rotationZ = rotation
                transformOrigin = TransformOrigin(0f, 0f)
                clip = false
            }
            .drawBehind {
                val topLeft = Offset(-renderW / 2f, -renderH / 2f)
                val nodeSize = Size(renderW, renderH)
                drawRect(color = color, topLeft = topLeft, size = nodeSize)
            },
    )
}

package com.mamton.zoomalbum.feature.canvas.view

import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.core.graphics.toColorInt
import coil3.compose.rememberAsyncImagePainter
import com.mamton.zoomalbum.domain.model.BackgroundData
import com.mamton.zoomalbum.domain.model.CanvasNode
import com.mamton.zoomalbum.domain.model.RenderDetail

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
        is CanvasNode.Media -> MediaRenderer(node, detail)
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
        RenderDetail.Simplified -> SimplifiedFrameRenderer(
            t.cx, t.cy, t.rotation, renderW, renderH, frame.color, frame.background,
        )
        RenderDetail.Full -> FullFrameRenderer(
            t.cx, t.cy, t.rotation, renderW, renderH, frame.color, frame.background,
        )
    }
}

/**
 * Paints [background] into the frame-local rect [-renderW/2..+renderW/2] ×
 * [-renderH/2..+renderH/2]. Solid is drawn with a rounded-rect shape; Texture
 * and Procedural are clipped to the rectangular bounds (square corners — the
 * 4 px corner radius is invisible at most zoom levels).
 *
 * Caller must thread the bitmap from [rememberBackgroundBitmap].
 */
private fun DrawScope.drawFrameBackground(
    background: BackgroundData,
    renderW: Float,
    renderH: Float,
    textureBitmap: ImageBitmap?,
) {
    val left = -renderW / 2f
    val top = -renderH / 2f
    val right = renderW / 2f
    val bottom = renderH / 2f
    when (background) {
        is BackgroundData.SolidBackgroundData -> {
            // Honor the rounded-rect shape so the fill matches the border outline.
            val hex = runCatching { Color(background.color.toColorInt()) }.getOrNull() ?: return
            drawRoundRect(
                color = hex.copy(alpha = (hex.alpha * background.opacity).coerceIn(0f, 1f)),
                topLeft = Offset(left, top),
                size = Size(renderW, renderH),
                cornerRadius = CornerRadius(4f, 4f),
            )
        }
        is BackgroundData.TextureBackgroundData, is BackgroundData.ProceduralBackgroundData -> {
            clipRect(left, top, right, bottom) {
                drawBackgroundData(
                    data = background,
                    left = left, top = top, right = right, bottom = bottom,
                    textureBitmap = textureBitmap,
                )
            }
        }
    }
}

/** Full render: optional frame background (any source), then frame border. */
@Composable
private fun FullFrameRenderer(
    cx: Float, cy: Float, rotation: Float,
    renderW: Float, renderH: Float, colorHex: String,
    background: BackgroundData?,
) {
    val fillColor = Color(colorHex.toColorInt())
    val borderColor = fillColor.copy(alpha = 0.6f)
    // Always-stable remember slot; returns null when background isn't a Texture.
    val textureBitmap = rememberBackgroundBitmap(background)

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
                background?.let { drawFrameBackground(it, renderW, renderH, textureBitmap) }
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

/** Simplified: optional frame background, then border only. */
@Composable
private fun SimplifiedFrameRenderer(
    cx: Float, cy: Float, rotation: Float,
    renderW: Float, renderH: Float, colorHex: String,
    background: BackgroundData?,
) {
    val borderColor = Color(colorHex.toColorInt()).copy(alpha = 0.4f)
    val textureBitmap = rememberBackgroundBitmap(background)

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
                background?.let { drawFrameBackground(it, renderW, renderH, textureBitmap) }
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

// ── Media renderers ──────────────────────────────────────────────────────────

@Composable
private fun MediaRenderer(media: CanvasNode.Media, detail: RenderDetail) {
    val t = media.transform
    when (detail) {
        RenderDetail.Hidden -> return
        RenderDetail.Stub, RenderDetail.Preview ->
            MediaPlaceholder(t.cx, t.cy, t.rotation, t.renderW, t.renderH, filled = false)
        RenderDetail.Simplified ->
            MediaPlaceholder(t.cx, t.cy, t.rotation, t.renderW, t.renderH, filled = true)
        RenderDetail.Full ->
            FullMediaRenderer(t.cx, t.cy, t.rotation, t.renderW, t.renderH, media.mediaRefId)
    }
}

@Composable
private fun FullMediaRenderer(
    cx: Float, cy: Float, rotation: Float,
    renderW: Float, renderH: Float, uri: String,
) {
    // Same zero-size Spacer + drawBehind pattern as FrameRenderer so that
    // draw-phase pixels and graphicsLayer translations are in the same coordinate
    // space at any camera zoom level.
    val painter = rememberAsyncImagePainter(
        model = uri,
        contentScale = ContentScale.Crop,
    )
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
                clipRect(
                    left = -renderW / 2f,
                    top = -renderH / 2f,
                    right = renderW / 2f,
                    bottom = renderH / 2f,
                ) {
                    translate(left = -renderW / 2f, top = -renderH / 2f) {
                        with(painter) { draw(Size(renderW, renderH)) }
                    }
                }
            },
    )
}

@Composable
private fun MediaPlaceholder(
    cx: Float, cy: Float, rotation: Float,
    renderW: Float, renderH: Float, filled: Boolean,
) {
    val color = Color(0xFF888888)
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
                if (filled) {
                    drawRect(color = color.copy(alpha = 0.3f), topLeft = topLeft, size = nodeSize)
                }
                drawRect(
                    color = color.copy(alpha = 0.6f),
                    topLeft = topLeft,
                    size = nodeSize,
                    style = Stroke(width = 1.5f),
                )
            },
    )
}

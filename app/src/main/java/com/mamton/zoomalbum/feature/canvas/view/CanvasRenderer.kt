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
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.core.graphics.toColorInt
import coil3.compose.rememberAsyncImagePainter
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import com.mamton.zoomalbum.domain.model.BackgroundData
import com.mamton.zoomalbum.domain.model.BorderStyle
import com.mamton.zoomalbum.domain.model.CanvasNode
import com.mamton.zoomalbum.domain.model.CropMode
import com.mamton.zoomalbum.domain.model.FrameAppearance
import com.mamton.zoomalbum.domain.model.MediaAppearance
import com.mamton.zoomalbum.domain.model.RenderDetail
import com.mamton.zoomalbum.domain.model.ShadowStyle

/**
 * Renders a single [CanvasNode] inside the camera-transformed container.
 *
 * Positioning and sizing use [graphicsLayer] (GPU-only) + [drawBehind],
 * bypassing Compose layout Constraints entirely. This allows arbitrarily
 * large world-coordinate dimensions without crashing.
 *
 * For frames whose appearance demands layered painting (a non-empty
 * `contentOverlays` list), the caller should use [FrameRendererPhased] directly
 * with [FramePaintPhase.Surface] / [FramePaintPhase.Overlay] interleaved around
 * the frame's member nodes — see `docs/architecture/rendering.md § 6b`.
 */
@Composable
fun CanvasNodeRenderer(node: CanvasNode, detail: RenderDetail) {
    when (node) {
        is CanvasNode.Frame -> FrameRendererPhased(node, detail, FramePaintPhase.Both)
        is CanvasNode.Media -> MediaRenderer(node, detail)
    }
}

/**
 * Which "half" of a frame's paint stack to draw.
 *
 * - [Both] = today's single-pass paint (background + border in one Spacer).
 * - [Surface] = shadow + background only. Use as the first half of a layered
 *   render so members can be painted on top.
 * - [Overlay] = contentOverlays + border (+ future title/contentEffect). Use as
 *   the second half, after all the frame's members have painted.
 */
enum class FramePaintPhase { Both, Surface, Overlay }

/**
 * True iff this frame needs the renderer to split its paint into a separate
 * Surface / Overlay pair so member nodes can be sandwiched between background
 * and contentOverlays. False = single-pass paint is sufficient.
 */
val CanvasNode.Frame.needsLayeredPaint: Boolean
    get() = !appearance?.contentOverlays.isNullOrEmpty()

@Composable
fun FrameRendererPhased(
    frame: CanvasNode.Frame,
    detail: RenderDetail,
    phase: FramePaintPhase,
) {
    val t = frame.transform
    val renderW = t.renderW
    val renderH = t.renderH

    when (detail) {
        RenderDetail.Hidden -> return
        RenderDetail.Stub, RenderDetail.Preview -> {
            // Stubs ignore appearance entirely; paint once on the Surface pass
            // so the second pass is a no-op.
            if (phase == FramePaintPhase.Overlay) return
            StubRenderer(t.cx, t.cy, t.rotation, renderW, renderH, frame.color)
        }
        RenderDetail.Simplified -> SimplifiedFrameRenderer(
            t.cx, t.cy, t.rotation, renderW, renderH, frame.color, frame.appearance, phase,
        )
        RenderDetail.Full -> FullFrameRenderer(
            t.cx, t.cy, t.rotation, renderW, renderH, frame.color, frame.appearance, phase,
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
        is BackgroundData.TextureBackgroundData -> {
            // Frame-local coords are centered on (0, 0), but users expect
            // `tileOriginX/Y = (0, 0)` to mean "texture's (0, 0) pixel lands
            // at the frame's TOP-LEFT" — not its center. Translate the stored
            // (top-left-relative) origin into frame-local centered coords for
            // the shader. Only matters for Repeat mode; non-Repeat ignores
            // tileOriginX/Y.
            val adjusted = background.copy(
                tile = background.tile.copy(
                    tileOriginX = left + background.tile.tileOriginX,
                    tileOriginY = top + background.tile.tileOriginY,
                ),
            )
            clipRect(left, top, right, bottom) {
                drawBackgroundData(
                    data = adjusted,
                    left = left, top = top, right = right, bottom = bottom,
                    textureBitmap = textureBitmap,
                )
            }
        }
        is BackgroundData.ProceduralBackgroundData -> {
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

/** Full render: shadow + background on the Surface pass; contentOverlays + border on the Overlay pass. */
@Composable
private fun FullFrameRenderer(
    cx: Float, cy: Float, rotation: Float,
    renderW: Float, renderH: Float, colorHex: String,
    appearance: FrameAppearance?,
    phase: FramePaintPhase,
) {
    val background = appearance?.background
    val surfaceAlpha = appearance?.opacity ?: 1f
    val cornerRadiusValue = appearance?.cornerRadius ?: DEFAULT_FRAME_CORNER_RADIUS
    val textureBitmap = rememberBackgroundBitmap(background)
    val contentOverlays = appearance?.contentOverlays.orEmpty()
    val overlayTextureBitmaps = rememberOverlayTextureBitmaps(contentOverlays)

    Spacer(
        modifier = Modifier
            .graphicsLayer {
                translationX = cx
                translationY = cy
                rotationZ = rotation
                transformOrigin = TransformOrigin(0f, 0f)
                clip = false
                alpha = surfaceAlpha
            }
            .drawBehind {
                val left = -renderW / 2f
                val top = -renderH / 2f
                val right = renderW / 2f
                val bottom = renderH / 2f
                val topLeft = Offset(left, top)
                val nodeSize = Size(renderW, renderH)
                val radius = CornerRadius(cornerRadiusValue, cornerRadiusValue)

                if (phase != FramePaintPhase.Overlay) {
                    appearance?.shadow?.let { drawNodeShadow(it, topLeft, nodeSize, radius) }

                    if (background == null) {
                        drawRoundRect(
                            color = Color.White.copy(alpha = 0.2f),
                            topLeft = topLeft,
                            size = nodeSize,
                            cornerRadius = radius,
                        )
                    } else {
                        drawFrameBackground(background, renderW, renderH, textureBitmap)
                    }
                }

                if (phase != FramePaintPhase.Surface) {
                    if (contentOverlays.isNotEmpty()) {
                        withRoundedClip(left, top, right, bottom, cornerRadiusValue) {
                            drawOverlayStack(
                                overlays = contentOverlays,
                                left = left, top = top, right = right, bottom = bottom,
                                textureBitmaps = overlayTextureBitmaps,
                            )
                        }
                    }
                    drawNodeBorder(
                        border = appearance?.border,
                        fallbackColorHex = colorHex,
                        fallbackAlpha = DEFAULT_FULL_BORDER_ALPHA,
                        fallbackWidthPx = DEFAULT_FULL_BORDER_WIDTH_PX,
                        topLeft = topLeft,
                        size = nodeSize,
                        cornerRadius = radius,
                    )
                }
            },
    )
}

/** Simplified: thin border (+ optional background) on the Surface pass; no contentOverlays at this LOD. */
@Composable
private fun SimplifiedFrameRenderer(
    cx: Float, cy: Float, rotation: Float,
    renderW: Float, renderH: Float, colorHex: String,
    appearance: FrameAppearance?,
    phase: FramePaintPhase,
) {
    // Simplified never paints contentOverlays — collapse the Overlay pass into the Surface pass
    // by skipping the Overlay-only invocation.
    if (phase == FramePaintPhase.Overlay) return

    val background = appearance?.background
    val surfaceAlpha = appearance?.opacity ?: 1f
    val cornerRadiusValue = appearance?.cornerRadius ?: DEFAULT_FRAME_CORNER_RADIUS
    val textureBitmap = rememberBackgroundBitmap(background)

    Spacer(
        modifier = Modifier
            .graphicsLayer {
                translationX = cx
                translationY = cy
                rotationZ = rotation
                transformOrigin = TransformOrigin(0f, 0f)
                clip = false
                alpha = surfaceAlpha
            }
            .drawBehind {
                val topLeft = Offset(-renderW / 2f, -renderH / 2f)
                val nodeSize = Size(renderW, renderH)
                val radius = CornerRadius(cornerRadiusValue, cornerRadiusValue)

                background?.let { drawFrameBackground(it, renderW, renderH, textureBitmap) }

                drawNodeBorder(
                    border = appearance?.border,
                    fallbackColorHex = colorHex,
                    fallbackAlpha = DEFAULT_SIMPLIFIED_BORDER_ALPHA,
                    fallbackWidthPx = DEFAULT_SIMPLIFIED_BORDER_WIDTH_PX,
                    topLeft = topLeft,
                    size = nodeSize,
                    cornerRadius = radius,
                )
            },
    )
}

private fun DrawScope.drawNodeBorder(
    border: BorderStyle?,
    fallbackColorHex: String?,
    fallbackAlpha: Float,
    fallbackWidthPx: Float,
    topLeft: Offset,
    size: Size,
    cornerRadius: CornerRadius,
) {
    val color: Color
    val width: Float
    if (border != null) {
        val parsed = runCatching { Color(border.color.toColorInt()) }.getOrNull() ?: return
        color = parsed.copy(alpha = (parsed.alpha * border.opacity).coerceIn(0f, 1f))
        width = border.widthPx
    } else {
        if (fallbackColorHex == null) return  // no border, no fallback → skip
        color = Color(fallbackColorHex.toColorInt()).copy(alpha = fallbackAlpha)
        width = fallbackWidthPx
    }
    drawRoundRect(
        color = color,
        topLeft = topLeft,
        size = size,
        cornerRadius = cornerRadius,
        style = Stroke(width = width),
    )
}

private fun DrawScope.drawNodeShadow(
    shadow: ShadowStyle,
    topLeft: Offset,
    size: Size,
    cornerRadius: CornerRadius,
) {
    val parsed = runCatching { Color(shadow.color.toColorInt()) }.getOrNull() ?: return
    val tinted = parsed.copy(alpha = (parsed.alpha * shadow.opacity).coerceIn(0f, 1f))
    val shadowLeft = topLeft.x + shadow.offsetX
    val shadowTop = topLeft.y + shadow.offsetY
    val shadowRight = shadowLeft + size.width
    val shadowBottom = shadowTop + size.height

    if (shadow.blurRadius <= 0f) {
        drawRoundRect(
            color = tinted,
            topLeft = Offset(shadowLeft, shadowTop),
            size = size,
            cornerRadius = cornerRadius,
        )
        return
    }

    val paint = android.graphics.Paint().apply {
        isAntiAlias = true
        color = tinted.toArgb()
        maskFilter = android.graphics.BlurMaskFilter(
            shadow.blurRadius,
            android.graphics.BlurMaskFilter.Blur.NORMAL,
        )
    }
    drawContext.canvas.nativeCanvas.drawRoundRect(
        shadowLeft, shadowTop, shadowRight, shadowBottom,
        cornerRadius.x, cornerRadius.y,
        paint,
    )
}

private const val DEFAULT_FRAME_CORNER_RADIUS = 4f
private const val DEFAULT_FULL_BORDER_ALPHA = 0.6f
private const val DEFAULT_FULL_BORDER_WIDTH_PX = 10f
private const val DEFAULT_SIMPLIFIED_BORDER_ALPHA = 0.4f
private const val DEFAULT_SIMPLIFIED_BORDER_WIDTH_PX = 1f

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
            FullMediaRenderer(
                cx = t.cx, cy = t.cy, rotation = t.rotation,
                renderW = t.renderW, renderH = t.renderH,
                uri = media.mediaRefId, appearance = media.appearance,
            )
    }
}

@Composable
private fun FullMediaRenderer(
    cx: Float, cy: Float, rotation: Float,
    renderW: Float, renderH: Float, uri: String,
    appearance: MediaAppearance?,
) {
    val painter = rememberAsyncImagePainter(
        model = uri,
        contentScale = appearance?.crop?.mode.toContentScale(),
    )
    val overlays = appearance?.overlays.orEmpty()
    val overlayTextureBitmaps = rememberOverlayTextureBitmaps(overlays)
    val surfaceAlpha = appearance?.opacity ?: 1f
    val cornerRadiusValue = appearance?.cornerRadius ?: 0f

    Spacer(
        modifier = Modifier
            .graphicsLayer {
                translationX = cx
                translationY = cy
                rotationZ = rotation
                transformOrigin = TransformOrigin(0f, 0f)
                clip = false
                alpha = surfaceAlpha
            }
            .drawBehind {
                val left = -renderW / 2f
                val top = -renderH / 2f
                val right = renderW / 2f
                val bottom = renderH / 2f
                val topLeft = Offset(left, top)
                val nodeSize = Size(renderW, renderH)
                val radius = CornerRadius(cornerRadiusValue, cornerRadiusValue)

                appearance?.shadow?.let { drawNodeShadow(it, topLeft, nodeSize, radius) }

                withRoundedClip(left, top, right, bottom, cornerRadiusValue) {
                    translate(left = left, top = top) {
                        with(painter) { draw(Size(renderW, renderH)) }
                    }
                    drawOverlayStack(
                        overlays = overlays,
                        left = left, top = top, right = right, bottom = bottom,
                        textureBitmaps = overlayTextureBitmaps,
                    )
                }

                drawNodeBorder(
                    border = appearance?.border,
                    fallbackColorHex = null,
                    fallbackAlpha = 0f,
                    fallbackWidthPx = 0f,
                    topLeft = topLeft,
                    size = nodeSize,
                    cornerRadius = radius,
                )
            },
    )
}

/**
 * Clips the draw block to `[left..right] × [top..bottom]`, using a rounded clip
 * when [cornerRadius] > 0 and a plain rect clip otherwise.
 */
private inline fun DrawScope.withRoundedClip(
    left: Float, top: Float, right: Float, bottom: Float,
    cornerRadius: Float,
    block: DrawScope.() -> Unit,
) {
    if (cornerRadius > 0f) {
        val path = Path().apply {
            addRoundRect(
                RoundRect(
                    left = left, top = top, right = right, bottom = bottom,
                    cornerRadius = CornerRadius(cornerRadius, cornerRadius),
                ),
            )
        }
        clipPath(path) { block() }
    } else {
        clipRect(left = left, top = top, right = right, bottom = bottom) { block() }
    }
}

private fun CropMode?.toContentScale(): ContentScale = when (this) {
    CropMode.Fit -> ContentScale.Fit
    CropMode.Stretch -> ContentScale.FillBounds
    // Manual pan/zoom rendering is deferred — fall back to Crop until the
    // manual-crop renderer lands.
    CropMode.Manual, CropMode.Fill, null -> ContentScale.Crop
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

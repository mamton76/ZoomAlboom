package com.mamton.zoomalbum.feature.canvas.view

import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
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
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.core.graphics.toColorInt
import coil3.compose.rememberAsyncImagePainter
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.geometry.Rect
import com.mamton.zoomalbum.domain.model.AlphaMask
import com.mamton.zoomalbum.domain.model.BackgroundData
import com.mamton.zoomalbum.domain.model.BorderStyle
import com.mamton.zoomalbum.domain.model.CanvasNode
import com.mamton.zoomalbum.domain.model.CropMode
import com.mamton.zoomalbum.domain.model.CropSettings
import com.mamton.zoomalbum.domain.model.DecorationPlacement
import com.mamton.zoomalbum.domain.model.FrameAppearance
import com.mamton.zoomalbum.domain.model.MediaAppearance
import com.mamton.zoomalbum.domain.model.MediaOpening
import com.mamton.zoomalbum.domain.model.RenderDetail
import com.mamton.zoomalbum.domain.model.ShadowStyle
import kotlin.math.max

/**
 * Renders a single [CanvasNode] inside the camera-transformed container.
 *
 * Positioning and sizing use [graphicsLayer] (GPU-only) + [drawBehind],
 * bypassing Compose layout Constraints entirely. This allows arbitrarily
 * large world-coordinate dimensions without crashing.
 *
 * For frames whose appearance demands layered painting (a non-empty
 * `overlays` list), the caller should use [FrameRendererPhased] directly
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
 * - [Overlay] = overlays + border (+ future title/contentEffect). Use as
 *   the second half, after all the frame's members have painted.
 */
enum class FramePaintPhase { Both, Surface, Overlay }

/**
 * True iff this frame needs the renderer to split its paint into a separate
 * Surface / Overlay pair so member nodes can be sandwiched between background
 * and overlays. False = single-pass paint is sufficient.
 */
val CanvasNode.Frame.needsLayeredPaint: Boolean
    get() = !appearance?.overlays.isNullOrEmpty()

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

/** Full render: shadow + background on the Surface pass; overlays + border on the Overlay pass. */
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
    val overlays = appearance?.overlays.orEmpty()
    val overlayTextureBitmaps = rememberOverlayTextureBitmaps(overlays)
    val contentMask = appearance?.contentMask
    val contentMaskBitmap = rememberAlphaMaskBitmap(contentMask)

    val nodeModifier = Modifier
        .nodeLayoutModifier(renderW, renderH, contentMask != null)
        .graphicsLayer {
            // When masked, the Spacer is sized to (renderW x renderH) pixels via
            // `nodeLayoutModifier` and translated by `(cx - w/2, cy - h/2)` so its
            // top-left lands at the node's world top-left and rotation pivots
            // around its centre. The drawBehind body is wrapped in a translate
            // that re-centres the drawing back onto local (0,0), so the existing
            // `(-w/2..w/2, -h/2..h/2)` draw offsets keep working unchanged.
            //
            // Without a mask we keep the legacy 0x0 Spacer path: translate to
            // (cx, cy) with transformOrigin (0,0), draw outside the (zero) bounds
            // via `clip = false`. That branch is what makes arbitrarily large
            // world dimensions work without going through Compose Constraints.
            if (contentMask != null) {
                translationX = cx - renderW / 2f
                translationY = cy - renderH / 2f
                transformOrigin = TransformOrigin(0.5f, 0.5f)
                compositingStrategy = CompositingStrategy.Offscreen
            } else {
                translationX = cx
                translationY = cy
                transformOrigin = TransformOrigin(0f, 0f)
            }
            rotationZ = rotation
            clip = false
            alpha = surfaceAlpha
        }
        .drawBehind {
            // Draw offsets centred on local (0,0): for the unmasked path this is
            // the graphicsLayer's translation point; for the masked path the
            // outer translate inside `withOptionalMaskOriginShift` shifts the
            // local origin to the centre of the sized Spacer.
            withOptionalMaskOriginShift(contentMask != null, renderW, renderH) {
                val left = -renderW / 2f
                val top = -renderH / 2f
                val right = renderW / 2f
                val bottom = renderH / 2f
                val topLeft = Offset(left, top)
                val nodeSize = Size(renderW, renderH)
                val radius = CornerRadius(cornerRadiusValue, cornerRadiusValue)
                val rect = Rect(left, top, right, bottom)

                // Shadow first — outside any mask layer per § 12.5 (shadow uses
                // the clip rect, not the mask silhouette). For the masked path
                // the offscreen buffer's bounds clip the shadow to the node
                // rect; documented MVP limitation.
                if (phase != FramePaintPhase.Overlay) {
                    appearance?.shadow?.let { drawNodeShadow(it, topLeft, nodeSize, radius) }
                }

                // Maskable content: background (Surface phase) + overlays
                // (Overlay phase). Wrapping both phases lets a single-pass
                // frame (`phase = Both`) mask its full chrome in one offscreen
                // layer. For phased frames the mask is applied separately to
                // each phase — members drawn between phases are unmasked
                // (documented MVP behavior; § 12.4).
                val drawMaskable: DrawScope.() -> Unit = drawMaskable@{
                    if (phase != FramePaintPhase.Overlay) {
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
                    if (phase != FramePaintPhase.Surface && overlays.isNotEmpty()) {
                        withRoundedClip(left, top, right, bottom, cornerRadiusValue) {
                            drawOverlayStack(
                                overlays = overlays,
                                left = left, top = top, right = right, bottom = bottom,
                                textureBitmaps = overlayTextureBitmaps,
                            )
                        }
                    }
                }
                if (contentMask == null) {
                    drawMaskable()
                } else {
                    drawWithAlphaMask(rect, contentMask, contentMaskBitmap) { drawMaskable() }
                }

                // Border last — outside the mask, on the clip edge per § 12.5.
                if (phase != FramePaintPhase.Surface) {
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
            }
        }
    Spacer(modifier = nodeModifier)
}

/** Simplified: thin border (+ optional background) on the Surface pass; no overlays at this LOD. */
@Composable
private fun SimplifiedFrameRenderer(
    cx: Float, cy: Float, rotation: Float,
    renderW: Float, renderH: Float, colorHex: String,
    appearance: FrameAppearance?,
    phase: FramePaintPhase,
) {
    // Simplified never paints overlays — collapse the Overlay pass into the Surface pass
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

internal fun DrawScope.drawNodeBorder(
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

internal fun DrawScope.drawNodeShadow(
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

/**
 * For nodes with an alpha mask we force the Spacer's measured size to
 * (renderW × renderH) pixels. The graphicsLayer downstream then uses these
 * bounds for its `CompositingStrategy.Offscreen` buffer, which is what DstIn
 * needs as a real alpha-channel destination. The reported layout size stays
 * 0×0 so the parent's layout pass is unaffected — same `bypassing Compose
 * layout Constraints` property as the no-mask path keeps holding.
 *
 * For nodes without a mask we return [Modifier] unchanged: the Spacer measures
 * to 0×0 (no inner constraints used), and drawing happens outside its bounds
 * via `clip = false`. This preserves the existing arbitrarily-large-node
 * tolerance — Constraints.fixed with huge values can crash Compose layout, so
 * we only opt in when the alpha mask actually needs it.
 */
private fun Modifier.nodeLayoutModifier(
    renderW: Float,
    renderH: Float,
    masked: Boolean,
): Modifier = if (!masked) this else this.layout { measurable, _ ->
    val pW = renderW.toInt().coerceAtLeast(1)
    val pH = renderH.toInt().coerceAtLeast(1)
    val placeable = measurable.measure(Constraints.fixed(pW, pH))
    layout(0, 0) { placeable.place(0, 0) }
}

/**
 * Inside a masked `drawBehind`, the Spacer's local origin is its TOP-LEFT (since
 * the layer has a real size). The existing draw helpers were written against
 * the legacy 0×0 Spacer convention where local `(0, 0)` was the visual *centre*
 * and offsets ranged `(-w/2..w/2, -h/2..h/2)`. Translating by `(w/2, h/2)` here
 * shifts the local origin back to the centre so the rest of the drawing code
 * keeps working unchanged.
 *
 * The unmasked path passes through with no translate — same coords as before.
 */
private inline fun DrawScope.withOptionalMaskOriginShift(
    masked: Boolean,
    renderW: Float,
    renderH: Float,
    block: DrawScope.() -> Unit,
) {
    if (masked) {
        translate(left = renderW / 2f, top = renderH / 2f) { block() }
    } else {
        block()
    }
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
    // Video nodes are NOT rendered here at Full detail — `CanvasScreen` routes
    // them to `VideoPosterSurface` / `VideoPlayerSurface` (the offscreen mask
    // path that works for video frames). This renderer stays image-only.
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
    val contentMask = appearance?.contentMask
    val contentMaskBitmap = rememberAlphaMaskBitmap(contentMask)
    val decorations = appearance?.decorations.orEmpty()
    val decorationBitmaps = rememberDecorationBitmaps(decorations)
    val colorFilter = appearance?.colorAdjustments?.toContentColorFilter()
    val vignette = appearance?.colorAdjustments?.vignette ?: 0f
    // The content mask needs the offscreen layer DstIn composites into.
    val needsLayer = contentMask != null

    val nodeModifier = Modifier
        .nodeLayoutModifier(renderW, renderH, needsLayer)
        .graphicsLayer {
            // See FullFrameRenderer for the masked / unmasked split rationale.
            if (needsLayer) {
                translationX = cx - renderW / 2f
                translationY = cy - renderH / 2f
                transformOrigin = TransformOrigin(0.5f, 0.5f)
                compositingStrategy = CompositingStrategy.Offscreen
            } else {
                translationX = cx
                translationY = cy
                transformOrigin = TransformOrigin(0f, 0f)
            }
            rotationZ = rotation
            clip = false
            alpha = surfaceAlpha
        }
        .drawBehind {
            withOptionalMaskOriginShift(needsLayer, renderW, renderH) {
                val left = -renderW / 2f
                val top = -renderH / 2f
                val right = renderW / 2f
                val bottom = renderH / 2f
                val topLeft = Offset(left, top)
                val nodeSize = Size(renderW, renderH)
                val radius = CornerRadius(cornerRadiusValue, cornerRadiusValue)
                val rect = Rect(left, top, right, bottom)

                // Shadow outside the mask layer (§ 12.5 — shadow on clip rect).
                appearance?.shadow?.let { drawNodeShadow(it, topLeft, nodeSize, radius) }

                // Rectangular content-area slot: the media is resized into this
                // rect so decorations can fill the margin. Null = fill the rect.
                val opening = appearance?.opening?.openingRect(left, top, renderW, renderH)

                fun DrawScope.paintDecorations(placement: DecorationPlacement) {
                    val layers = decorations.filter { it.placement == placement }
                    if (layers.isEmpty()) return
                    withRoundedClip(left, top, right, bottom, cornerRadiusValue) {
                        layers.forEach {
                            drawDecoration(it, decorationBitmaps[it.assetUri], left, top, right, bottom)
                        }
                    }
                }

                // Below decorations (mats / backing) — under the content.
                // Slice-0 limitation: when a contentMask is present these are
                // also cut by it (content-scoped masking is a follow-up); with
                // no mask they composite correctly under the content.
                paintDecorations(DecorationPlacement.Below)

                // Content (media + overlays), clipped to the opening rect.
                withRoundedClip(left, top, right, bottom, cornerRadiusValue) {
                    withOptionalOpeningClip(opening) {
                        val mLeft = opening?.left ?: left
                        val mTop = opening?.top ?: top
                        val mRight = opening?.right ?: right
                        val mBottom = opening?.bottom ?: bottom
                        drawCroppedBitmap(
                            painter = painter,
                            crop = appearance?.crop,
                            left = mLeft, top = mTop,
                            renderW = mRight - mLeft, renderH = mBottom - mTop,
                            colorFilter = colorFilter,
                        )
                        drawOverlayStack(
                            overlays = overlays,
                            left = mLeft, top = mTop, right = mRight, bottom = mBottom,
                            textureBitmaps = overlayTextureBitmaps,
                        )
                        drawVignette(vignette, mLeft, mTop, mRight, mBottom)
                    }
                }
                // Content mask — DstIn over the content in the offscreen layer.
                if (contentMask != null) {
                    drawWithAlphaMask(rect, contentMask, contentMaskBitmap) {}
                }

                // Above decorations over the FULL node rect, on top of the
                // (masked) content — composited AFTER the mask so they're never
                // cut by it.
                paintDecorations(DecorationPlacement.Above)

                // Border outside the mask layer (§ 12.5 — border on clip outline).
                drawNodeBorder(
                    border = appearance?.border,
                    fallbackColorHex = null,
                    fallbackAlpha = 0f,
                    fallbackWidthPx = 0f,
                    topLeft = topLeft,
                    size = nodeSize,
                    cornerRadius = radius,
                )
            }
        }
    Spacer(modifier = nodeModifier)
}

/**
 * Resolves a video's idle poster frame from the session-level
 * [AppearanceAssetCache] (provided via [LocalAppearanceAssetCache]), keyed by the
 * video URI as [AppearanceAssetKind.VideoPoster]. Sharing the cache gives the
 * poster the same residency as image appearance assets: it survives `Full ↔
 * Simplified` LOD remounts during zoom, so the poster no longer re-extracts and
 * flashes (the video-flicker fix, `docs/todo.md § 28.2`). Decode is ARGB_8888 so
 * the offscreen `DstIn` mask can still cut the frame. Returns `null` for a null
 * [uri] (non-video nodes) or until the frame decodes.
 */
@Composable
internal fun rememberVideoPosterBitmap(uri: String?): ImageBitmap? {
    if (uri == null) return null
    val cache = LocalAppearanceAssetCache.current ?: rememberAppearanceAssetCache()
    val key = remember(uri) { AppearanceAssetKey(uri, AppearanceAssetKind.VideoPoster) }
    LaunchedEffect(cache, key) { cache.ensure(listOf(key)) }
    return cache.get(key)
}

/**
 * The content-area rect in node-local coords (centred on 0,0). The media is
 * drawn only inside this rect; decorations fill the margin around it. See
 * [MediaOpening].
 */
internal data class OpeningRect(
    val left: Float, val top: Float, val right: Float, val bottom: Float,
)

/**
 * Rectangular content-area rect from a [MediaOpening] (insets as fractions of
 * the node edge), or null when no inset applies (all-zero, or insets that
 * collapse the area).
 */
internal fun MediaOpening.openingRect(
    left: Float, top: Float, renderW: Float, renderH: Float,
): OpeningRect? {
    val l = insetLeft.coerceIn(0f, 1f)
    val t = insetTop.coerceIn(0f, 1f)
    val r = insetRight.coerceIn(0f, 1f)
    val b = insetBottom.coerceIn(0f, 1f)
    if (l == 0f && t == 0f && r == 0f && b == 0f) return null
    if (l + r >= 1f || t + b >= 1f) return null
    return OpeningRect(
        left = left + l * renderW,
        top = top + t * renderH,
        right = left + renderW - r * renderW,
        bottom = top + renderH - b * renderH,
    )
}

/** Clips [block] to [opening] when present; draws unclipped otherwise. */
private inline fun DrawScope.withOptionalOpeningClip(
    opening: OpeningRect?,
    block: DrawScope.() -> Unit,
) {
    if (opening == null) {
        block()
    } else {
        clipRect(opening.left, opening.top, opening.right, opening.bottom) { block() }
    }
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
    CropMode.Fill, null -> ContentScale.Crop
    // Manual lays the bitmap out manually in `drawCroppedBitmap`; ContentScale
    // here only affects how Coil treats the requested draw size during loading.
    // Fit keeps loading state from over-filling and respects source aspect once
    // we hand the painter an aspect-correct draw size.
    CropMode.Manual -> ContentScale.Fit
}

/**
 * Draws the source bitmap inside the already-clipped media rect, respecting
 * `crop.mode`. For Fit / Fill / Stretch the painter's `ContentScale` does the
 * scaling and the rect is the full media rect. For Manual the source is drawn
 * at `fillScale × crop.zoom` and shifted by `(crop.offsetX, crop.offsetY)`,
 * with `zoom = 1` defined as the Fill scale on the limiting axis.
 *
 * `painter.intrinsicSize` is unspecified while the image loads; the Manual
 * branch falls back to a Fill-style draw until intrinsics arrive.
 */
private fun DrawScope.drawCroppedBitmap(
    painter: androidx.compose.ui.graphics.painter.Painter,
    crop: CropSettings?,
    left: Float, top: Float,
    renderW: Float, renderH: Float,
    colorFilter: androidx.compose.ui.graphics.ColorFilter? = null,
) {
    if (crop?.mode == CropMode.Manual) {
        val intrinsic = painter.intrinsicSize
        val srcW = intrinsic.width
        val srcH = intrinsic.height
        if (srcW > 0f && srcH > 0f && srcW.isFinite() && srcH.isFinite()) {
            val fillScale = max(renderW / srcW, renderH / srcH)
            val drawScale = (fillScale * crop.zoom).coerceAtLeast(MIN_DRAW_SCALE)
            val drawW = srcW * drawScale
            val drawH = srcH * drawScale
            val drawLeft = -drawW / 2f + crop.offsetX
            val drawTop = -drawH / 2f + crop.offsetY
            translate(left = drawLeft, top = drawTop) {
                with(painter) { draw(Size(drawW, drawH), colorFilter = colorFilter) }
            }
            return
        }
        // Intrinsic size not yet known — fall through to Fill-style draw so the
        // loading frame doesn't pop in from nothing.
    }
    translate(left = left, top = top) {
        with(painter) { draw(Size(renderW, renderH), colorFilter = colorFilter) }
    }
}

private const val MIN_DRAW_SCALE = 1e-3f

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

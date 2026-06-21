package com.mamton.zoomalbum.feature.canvas.playback

import android.view.TextureView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.exoplayer.ExoPlayer
import com.mamton.zoomalbum.domain.model.CanvasNode
import com.mamton.zoomalbum.domain.model.CropMode
import com.mamton.zoomalbum.domain.model.DecorationPlacement
import com.mamton.zoomalbum.feature.canvas.view.drawDecoration
import com.mamton.zoomalbum.feature.canvas.view.drawNodeBorder
import com.mamton.zoomalbum.feature.canvas.view.drawNodeShadow
import com.mamton.zoomalbum.feature.canvas.view.drawOverlayStack
import com.mamton.zoomalbum.feature.canvas.view.drawWithAlphaMask
import com.mamton.zoomalbum.feature.canvas.view.openingRect
import com.mamton.zoomalbum.feature.canvas.view.rememberAlphaMaskBitmap
import com.mamton.zoomalbum.feature.canvas.view.rememberDecorationBitmaps
import com.mamton.zoomalbum.feature.canvas.view.rememberOverlayTextureBitmaps
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Shared rendering chrome for a video node — used by BOTH the live player
 * surface ([VideoPlayerSurface]) and the static poster surface
 * ([VideoPosterSurface]). This is the single, proven structure for masking video
 * content (`todo.md § 27.9`):
 *
 * - **Outer layer** carries the node's world translation + rotation + opacity
 *   (`clip = false` so the shadow can extend past the node rect).
 * - **Inner layer** is clipped to the rounded node rect and, when an alpha mask
 *   is present, composites *offscreen* — so [drawWithAlphaMask]'s `BlendMode.DstIn`
 *   has a clean, axis-aligned alpha buffer to cut. Keeping the rotation on the
 *   outer layer (not the offscreen one) is what makes masked transparency work;
 *   `FullMediaRenderer`'s single-layer offscreen rendered masked video frames as
 *   opaque black.
 * - **Base content** (`drawBase`) paints into that inner layer: live frames
 *   (`drawContent()` over a TextureView child) or the poster bitmap. Overlays
 *   paint above it, still inside the mask; border + shadow stay outside it.
 *
 * Honours opacity, corner radius, border, shadow, crop, overlays, decorations
 * (Above/Below), opening, and content mask. `colorAdjustments` / `caption` are
 * excluded — they render nowhere yet (§ 27.9).
 */
@Composable
private fun VideoSurfaceChrome(
    node: CanvasNode.Media,
    drawBase: ContentDrawScope.() -> Unit,
    child: @Composable BoxScope.() -> Unit,
) {
    val t = node.transform
    val renderW = t.renderW
    val renderH = t.renderH
    val appearance = node.appearance
    val opacity = appearance?.opacity ?: 1f
    val cornerRadius = appearance?.cornerRadius ?: 0f
    val border = appearance?.border
    val shadow = appearance?.shadow
    val clipShape = remember(cornerRadius) { roundedPxShape(cornerRadius) }
    val overlays = appearance?.overlays.orEmpty()
    val overlayBitmaps = rememberOverlayTextureBitmaps(overlays)
    val decorations = appearance?.decorations.orEmpty()
    val decorationBitmaps = rememberDecorationBitmaps(decorations)
    val contentMask = appearance?.contentMask
    val maskBitmap = rememberAlphaMaskBitmap(contentMask)

    Box(
        modifier = Modifier
            .layout { measurable, _ ->
                val pW = renderW.toInt().coerceAtLeast(1)
                val pH = renderH.toInt().coerceAtLeast(1)
                val placeable = measurable.measure(Constraints.fixed(pW, pH))
                layout(0, 0) { placeable.place(0, 0) }
            }
            .graphicsLayer {
                translationX = t.cx - renderW / 2f
                translationY = t.cy - renderH / 2f
                transformOrigin = TransformOrigin(0.5f, 0.5f)
                rotationZ = t.rotation
                alpha = opacity
                clip = false // shadow draws outside the node rect
            }
            .drawBehind {
                shadow?.let {
                    drawNodeShadow(
                        it,
                        Offset.Zero,
                        Size(renderW, renderH),
                        CornerRadius(cornerRadius, cornerRadius),
                    )
                }
            },
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(clipShape)
                .then(
                    if (contentMask != null) {
                        Modifier.graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                    } else {
                        Modifier
                    },
                )
                .drawWithContent {
                    val opening = appearance?.opening?.openingRect(0f, 0f, size.width, size.height)
                    val content = this
                    // Below decorations under the content (slice-0: also cut by a
                    // contentMask when present; correct with no mask).
                    decorations.filter { it.placement == DecorationPlacement.Below }.forEach {
                        drawDecoration(it, decorationBitmaps[it.assetUri], 0f, 0f, size.width, size.height)
                    }
                    // The opening confines the video to the content area.
                    if (opening == null) {
                        drawBase()
                        if (overlays.isNotEmpty()) {
                            drawOverlayStack(
                                overlays = overlays,
                                left = 0f, top = 0f,
                                right = size.width, bottom = size.height,
                                textureBitmaps = overlayBitmaps,
                            )
                        }
                    } else {
                        clipRect(opening.left, opening.top, opening.right, opening.bottom) {
                            // Same ContentDrawScope instance, now clipped — drawBase
                            // (live frames or poster bitmap + badge) paints inside.
                            with(content) { drawBase() }
                            if (overlays.isNotEmpty()) {
                                drawOverlayStack(
                                    overlays = overlays,
                                    left = opening.left, top = opening.top,
                                    right = opening.right, bottom = opening.bottom,
                                    textureBitmaps = overlayBitmaps,
                                )
                            }
                        }
                    }
                    // Content mask (DstIn) — base + overlays are already in the
                    // offscreen layer; empty block just attenuates its alpha.
                    if (contentMask != null) {
                        drawWithAlphaMask(Rect(0f, 0f, size.width, size.height), contentMask, maskBitmap) {}
                    }
                },
            content = child,
        )

        // Above decorations over the FULL node rect, on top of the (masked)
        // content and below the border — composited AFTER the offscreen mask
        // layer so they're never cut by the content mask.
        val aboveDecorations = decorations.filter { it.placement == DecorationPlacement.Above }
        if (aboveDecorations.isNotEmpty()) {
            Canvas(modifier = Modifier.fillMaxSize().clip(clipShape)) {
                aboveDecorations.forEach {
                    drawDecoration(it, decorationBitmaps[it.assetUri], 0f, 0f, renderW, renderH)
                }
            }
        }

        if (border != null) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawNodeBorder(
                    border = border,
                    fallbackColorHex = null,
                    fallbackAlpha = 0f,
                    fallbackWidthPx = 0f,
                    topLeft = Offset.Zero,
                    size = Size(renderW, renderH),
                    cornerRadius = CornerRadius(cornerRadius, cornerRadius),
                )
            }
        }
    }
}

/**
 * Live player surface: a bare [TextureView] (not `PlayerView`) bound to a pooled
 * [player], rendered through [VideoSurfaceChrome] so it inherits the node's
 * transform and full appearance. A TextureView composites in the view hierarchy
 * (container alpha / clip / offscreen masking apply); a `SurfaceView` ignores
 * them. The view is non-interactive so pan / zoom / selection gestures still
 * reach the Compose layers.
 */
@Composable
fun VideoPlayerSurface(
    node: CanvasNode.Media,
    player: ExoPlayer,
    paused: Boolean = false,
) {
    val t = node.transform
    val content = remember(
        node.appearance?.crop, t.renderW, t.renderH,
        node.intrinsicPixelWidth, node.intrinsicPixelHeight,
        node.appearance?.opening,
    ) {
        val target = node.contentTargetRect()
        videoContentRect(
            mode = node.appearance?.crop?.mode ?: CropMode.Fill,
            zoom = node.appearance?.crop?.zoom ?: 1f,
            offsetX = node.appearance?.crop?.offsetX ?: 0f,
            offsetY = node.appearance?.crop?.offsetY ?: 0f,
            srcW = node.intrinsicPixelWidth.toFloat(),
            srcH = node.intrinsicPixelHeight.toFloat(),
            targetLeft = target.left, targetTop = target.top,
            targetW = target.w, targetH = target.h,
        )
    }
    VideoSurfaceChrome(
        node = node,
        drawBase = {
            drawContent() // renders the TextureView child
            // Paused-in-place: badge over the frozen frame so it reads as paused
            // (not a still). Inside the chrome's draw so it's masked/clipped too.
            if (paused) drawPlayBadge()
        },
        child = {
            AndroidView(
                factory = { ctx ->
                    TextureView(ctx).apply {
                        isClickable = false
                        isFocusable = false
                        // Non-opaque so masked-out regions and < 1 opacity are
                        // truly see-through (TextureView is opaque by default).
                        isOpaque = false
                    }
                },
                update = { view -> player.setVideoTextureView(view) },
                onRelease = { view -> player.clearVideoTextureView(view) },
                modifier = Modifier.cropPlaced(content),
            )
        },
    )
}

/**
 * Static poster surface for a video that isn't currently playing. Renders the
 * decoded poster [bitmap] through the SAME [VideoSurfaceChrome] as the live
 * player, so a masked / styled poster looks identical to the playing frame —
 * and, crucially, masks correctly (the `FullMediaRenderer` poster path rendered
 * masked video frames as opaque black). Carries a play badge.
 */
@Composable
fun VideoPosterSurface(
    node: CanvasNode.Media,
    bitmap: ImageBitmap,
) {
    val t = node.transform
    val content = remember(
        node.appearance?.crop, t.renderW, t.renderH, bitmap,
        node.appearance?.opening,
    ) {
        val target = node.contentTargetRect()
        videoContentRect(
            mode = node.appearance?.crop?.mode ?: CropMode.Fill,
            zoom = node.appearance?.crop?.zoom ?: 1f,
            offsetX = node.appearance?.crop?.offsetX ?: 0f,
            offsetY = node.appearance?.crop?.offsetY ?: 0f,
            srcW = bitmap.width.toFloat(),
            srcH = bitmap.height.toFloat(),
            targetLeft = target.left, targetTop = target.top,
            targetW = target.w, targetH = target.h,
        )
    }
    VideoSurfaceChrome(
        node = node,
        drawBase = {
            drawImage(
                image = bitmap,
                dstOffset = IntOffset(content.left.roundToInt(), content.top.roundToInt()),
                dstSize = IntSize(
                    content.w.roundToInt().coerceAtLeast(1),
                    content.h.roundToInt().coerceAtLeast(1),
                ),
            )
            drawPlayBadge()
        },
        child = {},
    )
}

/** Translucent disc + white triangle, centred — the static play affordance. */
private fun DrawScope.drawPlayBadge() {
    val radius = (minOf(size.width, size.height) * 0.12f).coerceAtLeast(1f)
    val center = Offset(size.width / 2f, size.height / 2f)
    drawCircle(color = Color.Black.copy(alpha = 0.45f), radius = radius, center = center)
    val tri = radius * 0.5f
    val cx = center.x + radius * 0.12f
    val cy = center.y
    val path = Path().apply {
        moveTo(cx - tri, cy - tri)
        lineTo(cx + tri, cy)
        lineTo(cx - tri, cy + tri)
        close()
    }
    drawPath(path, color = Color.White.copy(alpha = 0.9f))
}

/** Aspect-correct content rect for the video, in node-local px. */
private data class VideoContentRect(val w: Float, val h: Float, val left: Float, val top: Float)

/**
 * Mirrors `CanvasRenderer.drawCroppedBitmap`: sizes the content so it shows the
 * source at the right aspect, scaled into and centred on the **target rect**
 * (in node-local px). The target is the frame-decoration opening when one is
 * set, else the whole node rect — so a framed video fills the opening like a
 * framed photo, rather than just being clipped to it. Fit/Fill preserve aspect
 * (letterbox / cover); Stretch fills the target; Manual applies `zoom` + `offset`
 * over the Fill scale. Unknown source dims fall back to filling the target.
 */
private fun videoContentRect(
    mode: CropMode,
    zoom: Float,
    offsetX: Float,
    offsetY: Float,
    srcW: Float,
    srcH: Float,
    targetLeft: Float,
    targetTop: Float,
    targetW: Float,
    targetH: Float,
): VideoContentRect {
    if (srcW <= 0f || srcH <= 0f) {
        return VideoContentRect(targetW, targetH, targetLeft, targetTop)
    }
    return when (mode) {
        CropMode.Stretch -> VideoContentRect(targetW, targetH, targetLeft, targetTop)
        CropMode.Fit, CropMode.Fill -> {
            val scale = if (mode == CropMode.Fit) {
                minOf(targetW / srcW, targetH / srcH)
            } else {
                max(targetW / srcW, targetH / srcH)
            }
            val w = srcW * scale
            val h = srcH * scale
            VideoContentRect(w, h, targetLeft + (targetW - w) / 2f, targetTop + (targetH - h) / 2f)
        }
        CropMode.Manual -> {
            val fillScale = max(targetW / srcW, targetH / srcH)
            val drawScale = (fillScale * zoom).coerceAtLeast(1e-3f)
            val w = srcW * drawScale
            val h = srcH * drawScale
            VideoContentRect(
                w, h,
                targetLeft + (targetW - w) / 2f + offsetX,
                targetTop + (targetH - h) / 2f + offsetY,
            )
        }
    }
}

/** The opening rect (node-local px, 0-origin) the content should fill, or the full node when none. */
private fun CanvasNode.Media.contentTargetRect(): VideoContentRect {
    val renderW = transform.renderW
    val renderH = transform.renderH
    val opening = appearance?.opening?.openingRect(0f, 0f, renderW, renderH)
    return if (opening == null) {
        VideoContentRect(renderW, renderH, 0f, 0f)
    } else {
        VideoContentRect(opening.right - opening.left, opening.bottom - opening.top, opening.left, opening.top)
    }
}

/** Measures the child at the content rect and places it inside the (clipping) parent. */
private fun Modifier.cropPlaced(rect: VideoContentRect): Modifier = layout { measurable, constraints ->
    val pw = rect.w.roundToInt().coerceAtLeast(1)
    val ph = rect.h.roundToInt().coerceAtLeast(1)
    val placeable = measurable.measure(Constraints.fixed(pw, ph))
    val cw = if (constraints.hasBoundedWidth) constraints.maxWidth else pw
    val ch = if (constraints.hasBoundedHeight) constraints.maxHeight else ph
    layout(cw, ch) { placeable.place(rect.left.roundToInt(), rect.top.roundToInt()) }
}

/** A rounded-rect [Shape] whose corner radius is interpreted in raw px (node-local units). */
private fun roundedPxShape(cornerRadiusPx: Float): Shape = object : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline = Outline.Rounded(
        RoundRect(
            left = 0f, top = 0f, right = size.width, bottom = size.height,
            cornerRadius = CornerRadius(cornerRadiusPx, cornerRadiusPx),
        ),
    )
}

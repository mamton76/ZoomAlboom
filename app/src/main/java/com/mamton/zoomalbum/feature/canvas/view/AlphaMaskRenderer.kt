package com.mamton.zoomalbum.feature.canvas.view

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import com.mamton.zoomalbum.domain.model.AlphaMask
import com.mamton.zoomalbum.domain.model.AlphaMaskSource
import com.mamton.zoomalbum.domain.model.AlphaStop
import com.mamton.zoomalbum.domain.model.MaskChannel
import com.mamton.zoomalbum.domain.model.MaskFitMode
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Runs [block] inside a `CompositingStrategy.Offscreen` `graphicsLayer` provided
 * by the caller (see `FullFrameRenderer` / `FullMediaRenderer`), then attenuates
 * the result's alpha by the mask using `BlendMode.DstIn`.
 *
 * Pipeline per `docs/architecture/appearance.md § 12.4`:
 *
 * 1. The caller's `graphicsLayer { compositingStrategy = Offscreen }` allocates
 *    an offscreen buffer sized to the node bounds. This is what gives DstIn an
 *    alpha channel to operate on — saveLayer alone is not enough when the host
 *    `graphicsLayer` is rendered through HWUI's hardware pipeline.
 * 2. Run [block] — draws the actual node content (cropped photo, frame
 *    background, etc.) into the offscreen.
 * 3. Paint the mask source over the offscreen with `BlendMode.DstIn` applied
 *    directly on the draw call — DstIn multiplies the destination's alpha by
 *    the source's alpha per pixel, so only the masked silhouette survives.
 * 4. The caller's `graphicsLayer` composites the masked offscreen onto the
 *    parent canvas.
 *
 * Mask is interpreted per [AlphaMask.invert]:
 *  - `invert = false` — opaque source pixels = visible result.
 *  - `invert = true`  — opaque source pixels = transparent result.
 *
 * For [AlphaMaskSource.Image] the caller must pre-resolve the bitmap via
 * [rememberAlphaMaskBitmap] and pass it as [imageMaskBitmap]; if the bitmap
 * isn't ready yet, the source falls back to "no mask" (block draws unmasked).
 * Gradient and procedural sources don't need [imageMaskBitmap].
 */
internal fun DrawScope.drawWithAlphaMask(
    rect: Rect,
    mask: AlphaMask,
    imageMaskBitmap: ImageBitmap?,
    block: DrawScope.() -> Unit,
) {
    // Defensive: an Image mask with no bitmap loaded yet draws unmasked
    // rather than completely hiding the node.
    if (mask.source is AlphaMaskSource.Image && imageMaskBitmap == null) {
        block()
        return
    }
    if (rect.width <= 0f || rect.height <= 0f) {
        block()
        return
    }

    block()
    drawAlphaMaskSource(mask, rect, imageMaskBitmap)
}

/**
 * Paints the mask source over the current draw layer using `BlendMode.DstIn`
 * applied directly to each draw call. Variant dispatch: each source uses the
 * most natural Compose primitive — `drawImage` for [AlphaMaskSource.Image],
 * `Brush` for gradients, [drawProceduralPattern] for [AlphaMaskSource.Procedural].
 *
 * Why direct `blendMode` parameter instead of nested saveLayer with a DstIn
 * paint: the saveLayer-paint approach (paint applied at restore) is honoured
 * inconsistently when the host `graphicsLayer` lives in HWUI's hardware
 * pipeline. Passing `blendMode = BlendMode.DstIn` directly on the drawRect /
 * drawImage call goes through `SkPaint`'s blend-mode slot and is reliably
 * applied per pixel.
 */
private fun DrawScope.drawAlphaMaskSource(
    mask: AlphaMask,
    rect: Rect,
    imageBitmap: ImageBitmap?,
) {
    when (val src = mask.source) {
        is AlphaMaskSource.Image -> drawImageMask(src, mask.invert, rect, imageBitmap!!)
        is AlphaMaskSource.LinearGradient -> drawLinearGradientMask(src, mask.invert, rect)
        is AlphaMaskSource.RadialGradient -> drawRadialGradientMask(src, mask.invert, rect)
        is AlphaMaskSource.Procedural -> drawProceduralMask(src, mask.invert, rect)
    }
}

// ── Image source ────────────────────────────────────────────────────────────

private fun DrawScope.drawImageMask(
    src: AlphaMaskSource.Image,
    invert: Boolean,
    rect: Rect,
    bitmap: ImageBitmap,
) {
    val (srcRect, dstRect) = computeFitRects(
        srcSize = IntSize(bitmap.width, bitmap.height),
        dstRect = rect,
        mode = src.fitMode,
    )
    drawImage(
        image = bitmap,
        srcOffset = IntOffset(srcRect.left.toInt(), srcRect.top.toInt()),
        srcSize = IntSize(srcRect.width.toInt(), srcRect.height.toInt()),
        dstOffset = IntOffset(dstRect.left.toInt(), dstRect.top.toInt()),
        dstSize = IntSize(dstRect.width.toInt(), dstRect.height.toInt()),
        colorFilter = channelColorFilter(src.channel, invert),
        blendMode = BlendMode.DstIn,
    )
}

/**
 * Resolve a fit-mode layout. Returns (srcRect, dstRect) — both in pixels.
 *
 * - [MaskFitMode.Stretch]: srcRect = whole bitmap, dstRect = whole node rect.
 * - [MaskFitMode.Fit]: source scaled uniformly to fit inside the node rect;
 *   dstRect is the letterboxed inner area. Pixels outside dstRect stay alpha=0
 *   in the layer (DstIn against 0 = transparent).
 * - [MaskFitMode.Fill]: source scaled uniformly to cover the node rect;
 *   srcRect is the cropped centre region.
 */
private fun computeFitRects(
    srcSize: IntSize,
    dstRect: Rect,
    mode: MaskFitMode,
): Pair<Rect, Rect> {
    val srcW = srcSize.width.toFloat().coerceAtLeast(1f)
    val srcH = srcSize.height.toFloat().coerceAtLeast(1f)
    val dstW = dstRect.width
    val dstH = dstRect.height
    return when (mode) {
        MaskFitMode.Stretch -> Rect(0f, 0f, srcW, srcH) to dstRect
        MaskFitMode.Fit -> {
            val scale = min(dstW / srcW, dstH / srcH)
            val outW = srcW * scale
            val outH = srcH * scale
            val left = dstRect.left + (dstW - outW) / 2f
            val top = dstRect.top + (dstH - outH) / 2f
            Rect(0f, 0f, srcW, srcH) to Rect(left, top, left + outW, top + outH)
        }
        MaskFitMode.Fill -> {
            val scale = max(dstW / srcW, dstH / srcH)
            val cropW = dstW / scale
            val cropH = dstH / scale
            val srcLeft = (srcW - cropW) / 2f
            val srcTop = (srcH - cropH) / 2f
            Rect(srcLeft, srcTop, srcLeft + cropW, srcTop + cropH) to dstRect
        }
    }
}

/**
 * Build a ColorFilter that prepares the source pixels so their *alpha* carries
 * the mask. DstIn reads only the source's alpha channel, so RGB is irrelevant
 * after the filter runs — we just need to make sure alpha encodes the mask.
 *
 * Offset column is in 0..255 (Compose [ColorMatrix] convention).
 */
private fun channelColorFilter(channel: MaskChannel, invert: Boolean): ColorFilter {
    // Standard luminance weights: 0.299 R + 0.587 G + 0.114 B.
    val (luminanceRow, alphaRow) = when (channel) {
        MaskChannel.Luminance ->
            // Alpha = luminance; invert flips to (255 - luminance).
            if (invert) floatArrayOf(-0.299f, -0.587f, -0.114f, 0f, 255f) to null
            else        floatArrayOf( 0.299f,  0.587f,  0.114f, 0f,   0f) to null
        MaskChannel.Alpha ->
            // Alpha = source alpha; invert flips to (255 - alpha).
            if (invert) null to floatArrayOf(0f, 0f, 0f, -1f, 255f)
            else        null to floatArrayOf(0f, 0f, 0f,  1f,   0f)
    }
    val matrix = ColorMatrix(
        floatArrayOf(
            // R / G / B rows zeroed — DstIn ignores them, and zeroing keeps
            // the layer well-defined if a future blend mode reads RGB.
            0f, 0f, 0f, 0f, 0f,
            0f, 0f, 0f, 0f, 0f,
            0f, 0f, 0f, 0f, 0f,
            // Alpha row varies per channel + invert.
            *(luminanceRow ?: alphaRow!!),
        ),
    )
    return ColorFilter.colorMatrix(matrix)
}

// ── Gradient sources ────────────────────────────────────────────────────────

private fun DrawScope.drawLinearGradientMask(
    src: AlphaMaskSource.LinearGradient,
    invert: Boolean,
    rect: Rect,
) {
    val stops = src.stops.takeIf { it.size >= 2 }
        ?: solidFallbackStops(src.stops.firstOrNull()?.alpha ?: 0f)
    val (start, end) = linearGradientEndpoints(src.angleDeg, rect)
    val brush = Brush.linearGradient(
        colorStops = stops.toColorStops(invert),
        start = start,
        end = end,
    )
    drawRect(
        brush = brush,
        topLeft = Offset(rect.left, rect.top),
        size = Size(rect.width, rect.height),
        blendMode = BlendMode.DstIn,
    )
}

private fun DrawScope.drawRadialGradientMask(
    src: AlphaMaskSource.RadialGradient,
    invert: Boolean,
    rect: Rect,
) {
    val stops = src.stops.takeIf { it.size >= 2 }
        ?: solidFallbackStops(src.stops.firstOrNull()?.alpha ?: 0f)
    val cx = rect.left + src.centerX * rect.width
    val cy = rect.top + src.centerY * rect.height
    // Compose only supports circular radial gradients. For elliptical, scale
    // the draw so a circular brush stretches into an ellipse along Y.
    val rx = (src.radiusX * rect.width).coerceAtLeast(1f)
    val ry = (src.radiusY * rect.height).coerceAtLeast(1f)
    val yScale = ry / rx
    val brush = Brush.radialGradient(
        colorStops = stops.toColorStops(invert),
        center = Offset(cx, cy / yScale),
        radius = rx,
    )
    scale(scaleX = 1f, scaleY = yScale, pivot = Offset(0f, 0f)) {
        drawRect(
            brush = brush,
            topLeft = Offset(rect.left, rect.top / yScale),
            size = Size(rect.width, rect.height / yScale),
            blendMode = BlendMode.DstIn,
        )
    }
}

private fun solidFallbackStops(alpha: Float): List<AlphaStop> =
    listOf(AlphaStop(0f, alpha), AlphaStop(1f, alpha))

/**
 * Map sorted-by-position [AlphaStop] list to Compose `colorStops`. Each stop's
 * alpha becomes a white colour whose alpha carries the mask weight; invert
 * flips alpha to `1 - alpha` per stop.
 */
private fun List<AlphaStop>.toColorStops(invert: Boolean): Array<Pair<Float, Color>> =
    sortedBy { it.position }
        .map { stop ->
            val alpha = if (invert) (1f - stop.alpha) else stop.alpha
            stop.position.coerceIn(0f, 1f) to Color.White.copy(alpha = alpha.coerceIn(0f, 1f))
        }
        .toTypedArray()

/**
 * Resolve linear-gradient start/end endpoints from an [angleDeg] direction:
 * `0` = left→right, `90` = top→bottom, `180` = right→left, `270` = bottom→top.
 *
 * Picks the diagonal of [rect] along the angle so the gradient covers the full
 * rect without clipping the ramp.
 */
private fun linearGradientEndpoints(angleDeg: Float, rect: Rect): Pair<Offset, Offset> {
    val rad = Math.toRadians(angleDeg.toDouble())
    val dx = cos(rad).toFloat()
    val dy = sin(rad).toFloat()
    val cx = rect.center.x
    val cy = rect.center.y
    // Half-diagonal projected on the angle direction.
    val halfSpan = (rect.width * kotlin.math.abs(dx) + rect.height * kotlin.math.abs(dy)) / 2f
    val start = Offset(cx - dx * halfSpan, cy - dy * halfSpan)
    val end = Offset(cx + dx * halfSpan, cy + dy * halfSpan)
    return start to end
}

// ── Procedural source ──────────────────────────────────────────────────────

/**
 * Procedural masks reuse [drawProceduralPattern] (same renderer as procedural
 * backgrounds + overlays — see `appearance.md § 12.2`). The pattern paints RGB,
 * so we need a luminance-to-alpha conversion before DstIn-compositing onto the
 * destination. We do both in one saveLayer: the layer's restore paint carries
 * the ColorFilter (RGB → alpha) AND `BlendMode.DstIn`. On restore the layer's
 * RGB pixels are converted to alpha and composited with DstIn in a single step.
 */
private fun DrawScope.drawProceduralMask(
    src: AlphaMaskSource.Procedural,
    invert: Boolean,
    rect: Rect,
) {
    val procPaint = Paint().apply {
        colorFilter = channelColorFilter(MaskChannel.Luminance, invert)
        blendMode = BlendMode.DstIn
    }
    drawIntoCanvas { canvas ->
        canvas.saveLayer(rect, procPaint)
        drawProceduralPattern(
            pattern = src.pattern,
            left = rect.left,
            top = rect.top,
            right = rect.right,
            bottom = rect.bottom,
            opacity = 1f,
        )
        canvas.restore()
    }
}

// ── Image-source bitmap resolution ──────────────────────────────────────────

/**
 * Resolves the bitmap referenced by an [AlphaMaskSource.Image]'s [maskRefId]
 * via Coil. Mirrors [rememberOverlayTextureBitmaps] and
 * [rememberBackgroundBitmap]: `allowHardware(false)` so the bitmap can back
 * `drawImageRect` reads, decode runs in [LaunchedEffect] keyed on the refId
 * (loads survive recomposition), and a missing entry returns `null` (the
 * renderer falls back to "no mask" until the bitmap arrives).
 *
 * Returns `null` if [mask] is `null`, the source isn't [AlphaMaskSource.Image],
 * or the refId is blank.
 */
@Composable
internal fun rememberAlphaMaskBitmap(mask: AlphaMask?): ImageBitmap? {
    val refId = (mask?.source as? AlphaMaskSource.Image)?.maskRefId?.takeIf { it.isNotBlank() }
        ?: return null

    val context = LocalContext.current
    var bitmap by remember(refId) { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(refId) {
        runCatching {
            val request = ImageRequest.Builder(context)
                .data(refId)
                .allowHardware(false)
                .build()
            val result = SingletonImageLoader.get(context).execute(request)
            val image = (result as? SuccessResult)?.image
            if (image != null) {
                val raw = image.toBitmap()
                val safe = if (raw.config == android.graphics.Bitmap.Config.ARGB_8888) raw
                else raw.copy(android.graphics.Bitmap.Config.ARGB_8888, false) ?: raw
                bitmap = safe.asImageBitmap()
            }
        }
    }
    return bitmap
}

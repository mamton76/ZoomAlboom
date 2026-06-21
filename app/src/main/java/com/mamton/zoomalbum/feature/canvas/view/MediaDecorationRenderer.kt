package com.mamton.zoomalbum.feature.canvas.view

import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import com.mamton.zoomalbum.domain.model.MediaDecoration
import com.mamton.zoomalbum.domain.model.MediaDecorationMode
import kotlin.math.roundToInt

/**
 * Draws one [MediaDecoration] visual layer over a media node's rect. Decorations
 * are a *stack* ([com.mamton.zoomalbum.domain.model.MediaAppearance.decorations]):
 * `Below` layers paint under the content, `Above` layers over the content +
 * overlays. A decoration carries no clip — the media's shape is owned by
 * `opening` + `contentMask`. See `docs/architecture/media-appearance.md`.
 *
 * Two modes:
 *  - [MediaDecorationMode.Stretch]  — the whole asset scaled to the rect.
 *  - [MediaDecorationMode.NineSlice] — corners drawn uniform-scaled (keeping
 *    their source aspect ratio), the four edges stretched along one axis only,
 *    the centre stretched both axes — so one frame asset fits any media aspect
 *    ratio without distorting the corner artwork.
 *
 * `slice*` insets are fractions (0..1) of the asset's own width/height — they
 * split both the source bitmap and the destination rect. Destination corners are
 * clamped so they never overlap on small / extreme-aspect nodes.
 */
internal fun DrawScope.drawDecoration(
    decoration: MediaDecoration,
    bitmap: ImageBitmap?,
    left: Float,
    top: Float,
    right: Float,
    bottom: Float,
) {
    val bmp = bitmap ?: return
    val w = right - left
    val h = bottom - top
    if (w <= 0f || h <= 0f) return
    val alpha = decoration.opacity.coerceIn(0f, 1f)
    if (alpha <= 0f) return

    when (decoration.mode) {
        MediaDecorationMode.Stretch -> drawImage(
            image = bmp,
            srcOffset = IntOffset.Zero,
            srcSize = IntSize(bmp.width, bmp.height),
            dstOffset = IntOffset(left.roundToInt(), top.roundToInt()),
            dstSize = IntSize(w.roundToInt(), h.roundToInt()),
            alpha = alpha,
        )
        MediaDecorationMode.NineSlice ->
            drawNineSlice(bmp, decoration, left, top, w, h, alpha)
    }
}

private fun DrawScope.drawNineSlice(
    bmp: ImageBitmap,
    d: MediaDecoration,
    left: Float,
    top: Float,
    w: Float,
    h: Float,
    alpha: Float,
) {
    val bw = bmp.width
    val bh = bmp.height
    if (bw <= 0 || bh <= 0) return

    val fl = d.sliceLeft.coerceIn(0f, 1f)
    val fr = d.sliceRight.coerceIn(0f, 1f)
    val ft = d.sliceTop.coerceIn(0f, 1f)
    val fb = d.sliceBottom.coerceIn(0f, 1f)

    // Source insets in asset pixels.
    val sl = (fl * bw).roundToInt()
    val sr = (fr * bw).roundToInt()
    val st = (ft * bh).roundToInt()
    val sb = (fb * bh).roundToInt()
    val sCenterW = bw - sl - sr
    val sCenterH = bh - st - sb
    if (sCenterW < 0 || sCenterH < 0) {
        // Insets overlap in the source — fall back to a plain stretch.
        drawImage(
            image = bmp,
            srcOffset = IntOffset.Zero,
            srcSize = IntSize(bw, bh),
            dstOffset = IntOffset(left.roundToInt(), top.roundToInt()),
            dstSize = IntSize(w.roundToInt(), h.roundToInt()),
            alpha = alpha,
        )
        return
    }

    // Destination corner sizes: ONE uniform scale so every corner keeps its
    // source aspect ratio. (Scaling x by w/bw and y by h/bh independently —
    // the obvious approach — squashes the corner artwork whenever the node
    // aspect differs from the asset's.) Scale to the limiting axis, then shrink
    // further if the borders wouldn't fit, so opposite corners never overlap.
    var k = minOf(w / bw, h / bh)
    if (sl + sr > 0) k = minOf(k, w / (sl + sr))
    if (st + sb > 0) k = minOf(k, h / (st + sb))
    val dl = sl * k
    val dr = sr * k
    val dt = st * k
    val db = sb * k

    // Source split (already integer pixels).
    val sx0 = 0; val sx1 = sl; val sx2 = bw - sr
    val sy0 = 0; val sy1 = st; val sy2 = bh - sb

    // Destination split: round each shared edge ONCE so adjacent cells reuse the
    // exact same integer boundary. Rounding offset and size independently (per
    // cell) would leave 1px seams or overlaps where round(a)+round(b)≠round(a+b).
    val dx0 = left.roundToInt()
    val dx1 = (left + dl).roundToInt()
    val dx2 = (left + w - dr).roundToInt()
    val dx3 = (left + w).roundToInt()
    val dy0 = top.roundToInt()
    val dy1 = (top + dt).roundToInt()
    val dy2 = (top + h - db).roundToInt()
    val dy3 = (top + h).roundToInt()

    fun cell(
        sx: Int, sy: Int, sw: Int, sh: Int,
        dxL: Int, dyT: Int, dxR: Int, dyB: Int,
    ) {
        val dw = dxR - dxL
        val dh = dyB - dyT
        if (sw <= 0 || sh <= 0 || dw <= 0 || dh <= 0) return
        drawImage(
            image = bmp,
            srcOffset = IntOffset(sx, sy),
            srcSize = IntSize(sw, sh),
            dstOffset = IntOffset(dxL, dyT),
            dstSize = IntSize(dw, dh),
            alpha = alpha,
        )
    }

    // Corners — uniform-scaled, so each keeps its source aspect ratio.
    cell(sx0, sy0, sl, st, dx0, dy0, dx1, dy1)                       // top-left
    cell(sx2, sy0, sr, st, dx2, dy0, dx3, dy1)                       // top-right
    cell(sx0, sy2, sl, sb, dx0, dy2, dx1, dy3)                       // bottom-left
    cell(sx2, sy2, sr, sb, dx2, dy2, dx3, dy3)                       // bottom-right
    // Edges — stretched along one axis only.
    cell(sx1, sy0, sCenterW, st, dx1, dy0, dx2, dy1)                 // top
    cell(sx1, sy2, sCenterW, sb, dx1, dy2, dx2, dy3)                 // bottom
    cell(sx0, sy1, sl, sCenterH, dx0, dy1, dx1, dy2)                 // left
    cell(sx2, sy1, sr, sCenterH, dx2, dy1, dx3, dy2)                 // right
    // Centre — usually the transparent photo opening; drawn for assets that fill it.
    cell(sx1, sy1, sCenterW, sCenterH, dx1, dy1, dx2, dy2)
}

/**
 * Resolves the [ImageBitmap]s for a decoration stack into a map keyed by
 * `assetUri`. Mirrors [rememberOverlayTextureBitmaps]: Coil's
 * [SingletonImageLoader] with `allowHardware(false)` + an explicit ARGB_8888
 * copy so each bitmap keeps its alpha channel (decoration PNGs are transparent),
 * decoded in a [LaunchedEffect] keyed on the distinct URI set. Missing entries
 * stay absent — the renderer skips a decoration whose bitmap hasn't arrived yet.
 */
@Composable
internal fun rememberDecorationBitmaps(
    decorations: List<MediaDecoration>,
): Map<String, ImageBitmap> {
    val uris: List<String> = decorations
        .map { it.assetUri }
        .filter { it.isNotBlank() }
        .distinct()
    if (uris.isEmpty()) return emptyMap()

    val context = LocalContext.current
    val key = uris.joinToString("|")
    var bitmaps by remember(key) { mutableStateOf<Map<String, ImageBitmap>>(emptyMap()) }

    LaunchedEffect(key) {
        val loaded = mutableMapOf<String, ImageBitmap>()
        for (uri in uris) {
            runCatching {
                val request = ImageRequest.Builder(context)
                    .data(uri)
                    .allowHardware(false)
                    .build()
                val image = (SingletonImageLoader.get(context).execute(request) as? SuccessResult)?.image
                if (image != null) {
                    val raw = image.toBitmap()
                    val safe = if (raw.config == Bitmap.Config.ARGB_8888) raw
                    else raw.copy(Bitmap.Config.ARGB_8888, false) ?: raw
                    loaded[uri] = safe.asImageBitmap()
                }
            }
        }
        bitmaps = loaded
    }
    return bitmaps
}

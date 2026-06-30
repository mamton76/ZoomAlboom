package com.mamton.zoomalbum.feature.canvas.view

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.mamton.zoomalbum.domain.model.MediaColorAdjustments
import kotlin.math.hypot

/**
 * Builds a [ColorFilter] for the [MediaColorAdjustments] fields that map to a
 * 4×5 colour matrix: exposure (gain), brightness (offset), contrast (scale
 * about mid-grey), saturation, and temperature/tint (channel offsets). Returns
 * null when all matrix-mapped fields are at identity (0) — the renderer then
 * draws the content with no filter.
 *
 * Offsets are in the 0..255 channel space Compose's [ColorMatrix] uses. This is
 * the first-pass grade (`docs/todo.md § 29`); `highlights` / `shadows` / `blur`
 * / `sharpen` need per-pixel work and are still not rendered. [vignette] is
 * drawn separately via [drawVignette].
 */
internal fun MediaColorAdjustments.toContentColorFilter(): ColorFilter? {
    val grades = brightness != 0f || contrast != 0f || saturation != 0f ||
        temperature != 0f || tint != 0f || exposure != 0f
    if (!grades) return null

    // m *= A concatenates as m = m × A, so the LAST-multiplied matrix is applied
    // to the pixel FIRST. Order below → linear (gain/offset) → contrast → saturation.
    val m = ColorMatrix()
    if (saturation != 0f) m.setToSaturation((1f + saturation).coerceAtLeast(0f))

    if (contrast != 0f) {
        val c = (1f + contrast).coerceAtLeast(0f)
        val t = (1f - c) * 128f
        m *= ColorMatrix(
            floatArrayOf(
                c, 0f, 0f, 0f, t,
                0f, c, 0f, 0f, t,
                0f, 0f, c, 0f, t,
                0f, 0f, 0f, 1f, 0f,
            ),
        )
    }

    val gain = (1f + exposure).coerceAtLeast(0f)
    val bright = brightness * 100f
    val warm = temperature * 60f // +warm = more red, less blue
    val green = tint * 60f // +tint = more green
    if (gain != 1f || bright != 0f || warm != 0f || green != 0f) {
        m *= ColorMatrix(
            floatArrayOf(
                gain, 0f, 0f, 0f, bright + warm,
                0f, gain, 0f, 0f, bright + green,
                0f, 0f, gain, 0f, bright - warm,
                0f, 0f, 0f, 1f, 0f,
            ),
        )
    }
    return ColorFilter.colorMatrix(m)
}

/**
 * Darkens the edges of the media rect with a radial gradient (transparent centre
 * → black at the corners). [strength] is `0..1` ([MediaColorAdjustments.vignette]).
 * No-op at `0`. Drawn over the content (and overlays), inside the content clip.
 */
internal fun DrawScope.drawVignette(
    strength: Float,
    left: Float, top: Float, right: Float, bottom: Float,
) {
    val s = strength.coerceIn(0f, 1f)
    if (s <= 0f) return
    val cx = (left + right) / 2f
    val cy = (top + bottom) / 2f
    val radius = hypot((right - left) / 2f, (bottom - top) / 2f)
    if (radius <= 0f) return
    drawRect(
        brush = Brush.radialGradient(
            colorStops = arrayOf(
                0.55f to Color.Transparent,
                1f to Color.Black.copy(alpha = s),
            ),
            center = Offset(cx, cy),
            radius = radius,
        ),
        topLeft = Offset(left, top),
        size = Size(right - left, bottom - top),
    )
}

package com.mamton.zoomalbum.feature.canvas.view

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.toColorInt
import com.mamton.zoomalbum.domain.model.GradientKind
import com.mamton.zoomalbum.domain.model.ProceduralPattern
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * Draws a [ProceduralPattern] into the rectangle (`left`,`top`)→(`right`,`bottom`).
 *
 * The rect is given in whatever coordinate space the caller is currently drawing
 * into — screen pixels for camera-locked, world units for world-locked. All
 * pattern length parameters are interpreted in that same space (see
 * `ProceduralPattern` kdoc).
 *
 * [opacity] is multiplied into every color's alpha so the caller can fade the
 * whole layer (matches the `AlbumBackground.opacity` field).
 */
fun DrawScope.drawProceduralPattern(
    pattern: ProceduralPattern,
    // Viewport — what to rasterize. Bounds of `drawRect` / `drawCircle`,
    // and the slice that tileable patterns iterate over.
    left: Float,
    top: Float,
    right: Float,
    bottom: Float,
    opacity: Float,
    // Anchor — the pattern's fixed coordinate frame, used by "fill-the-rect"
    // patterns (Gradient / Watercolor / Grain / Noise) so they stay anchored
    // in world coords instead of sliding with the camera viewport. Defaults
    // to the viewport — correct for CameraLocked album and Frame backgrounds.
    // WorldLocked album passes a fixed world rect (see WorldLockedAlbumBackground).
    // Tileable patterns (Grid / DotGrid / RuledPaper / GraphPaper) ignore this:
    // their `floor((axisLow - origin) / step)` math already anchors to a
    // pattern-owned world origin.
    anchorLeft: Float = left,
    anchorTop: Float = top,
    anchorRight: Float = right,
    anchorBottom: Float = bottom,
) {
    val w = right - left
    val h = bottom - top
    if (w <= 0f || h <= 0f) return

    when (pattern) {
        is ProceduralPattern.Grid -> drawGrid(pattern, left, top, right, bottom, opacity)
        is ProceduralPattern.DotGrid -> drawDotGrid(pattern, left, top, right, bottom, opacity)
        is ProceduralPattern.RuledPaper -> drawRuledPaper(pattern, left, top, right, bottom, opacity)
        is ProceduralPattern.GraphPaper -> drawGraphPaper(pattern, left, top, right, bottom, opacity)
        is ProceduralPattern.PaperGrain -> drawGrainOrNoise(
            color = pattern.color,
            intensity = pattern.intensity,
            grainSize = pattern.grainSize,
            density = pattern.density,
            seed = pattern.seed,
            left = left, top = top, right = right, bottom = bottom, opacity = opacity,
            anchorLeft = anchorLeft, anchorTop = anchorTop,
            anchorRight = anchorRight, anchorBottom = anchorBottom,
        )
        is ProceduralPattern.Noise -> drawGrainOrNoise(
            color = pattern.color,
            intensity = pattern.intensity,
            grainSize = pattern.grainSize,
            density = pattern.density,
            seed = pattern.seed,
            left = left, top = top, right = right, bottom = bottom, opacity = opacity,
            anchorLeft = anchorLeft, anchorTop = anchorTop,
            anchorRight = anchorRight, anchorBottom = anchorBottom,
        )
        is ProceduralPattern.Gradient -> drawGradient(
            pattern, left, top, right, bottom, opacity,
            anchorLeft, anchorTop, anchorRight, anchorBottom,
        )
        is ProceduralPattern.Watercolor -> drawWatercolor(
            pattern, left, top, right, bottom, opacity,
            anchorLeft, anchorTop, anchorRight, anchorBottom,
        )
    }
}

// ── Tilable line patterns ───────────────────────────────────────────────

/** Hard cap on lines/dots per axis. Beyond this we skip — too dense to be useful. */
private const val MAX_PER_AXIS = 500

private fun DrawScope.drawGrid(
    p: ProceduralPattern.Grid,
    left: Float, top: Float, right: Float, bottom: Float,
    opacity: Float,
) {
    val color = parseHex(p.lineColor)?.withAlpha(opacity) ?: return
    val cell = p.cellSize
    if (cell <= 0f) return
    val xs = lineOffsetsOnAxis(p.originX, cell, left, right) ?: return
    val ys = lineOffsetsOnAxis(p.originY, cell, top, bottom) ?: return
    for (x in xs) {
        drawLine(color, Offset(x, top), Offset(x, bottom), strokeWidth = p.lineWidth)
    }
    for (y in ys) {
        drawLine(color, Offset(left, y), Offset(right, y), strokeWidth = p.lineWidth)
    }
}

private fun DrawScope.drawDotGrid(
    p: ProceduralPattern.DotGrid,
    left: Float, top: Float, right: Float, bottom: Float,
    opacity: Float,
) {
    val color = parseHex(p.dotColor)?.withAlpha(opacity) ?: return
    val sp = p.spacing
    if (sp <= 0f) return
    val xs = lineOffsetsOnAxis(p.originX, sp, left, right) ?: return
    val ys = lineOffsetsOnAxis(p.originY, sp, top, bottom) ?: return
    for (y in ys) for (x in xs) {
        drawCircle(color, radius = p.dotRadius, center = Offset(x, y))
    }
}

private fun DrawScope.drawRuledPaper(
    p: ProceduralPattern.RuledPaper,
    left: Float, top: Float, right: Float, bottom: Float,
    opacity: Float,
) {
    val color = parseHex(p.lineColor)?.withAlpha(opacity) ?: return
    val sp = p.lineSpacing
    if (sp <= 0f) return
    val ys = lineOffsetsOnAxis(p.originY, sp, top, bottom) ?: return
    for (y in ys) {
        drawLine(color, Offset(left, y), Offset(right, y), strokeWidth = p.lineWidth)
    }
    val marginHex = p.marginColor
    if (marginHex != null) {
        val mColor = parseHex(marginHex)?.withAlpha(opacity)
        if (mColor != null) {
            // Single vertical margin line at world X = marginX.
            val mx = p.marginX
            if (mx in left..right) {
                drawLine(mColor, Offset(mx, top), Offset(mx, bottom), strokeWidth = p.lineWidth)
            }
        }
    }
}

private fun DrawScope.drawGraphPaper(
    p: ProceduralPattern.GraphPaper,
    left: Float, top: Float, right: Float, bottom: Float,
    opacity: Float,
) {
    val minor = parseHex(p.minorColor)?.withAlpha(opacity) ?: return
    val major = parseHex(p.majorColor)?.withAlpha(opacity) ?: return
    val sp = p.minorSpacing
    if (sp <= 0f) return
    val majorEvery = p.majorEvery.coerceAtLeast(1)
    val xs = lineIndicesOnAxis(p.originX, sp, left, right) ?: return
    val ys = lineIndicesOnAxis(p.originY, sp, top, bottom) ?: return
    for (i in xs) {
        val x = p.originX + i * sp
        val c = if (i.mod(majorEvery) == 0) major else minor
        drawLine(c, Offset(x, top), Offset(x, bottom), strokeWidth = p.lineWidth)
    }
    for (j in ys) {
        val y = p.originY + j * sp
        val c = if (j.mod(majorEvery) == 0) major else minor
        drawLine(c, Offset(left, y), Offset(right, y), strokeWidth = p.lineWidth)
    }
}

// ── Noise / grain ────────────────────────────────────────────────────────

private fun DrawScope.drawGrainOrNoise(
    color: String,
    intensity: Float,
    grainSize: Float,
    density: Float,
    seed: Int,
    left: Float, top: Float, right: Float, bottom: Float,
    opacity: Float,
    anchorLeft: Float, anchorTop: Float, anchorRight: Float, anchorBottom: Float,
) {
    val baseColor = parseHex(color) ?: return
    val aw = anchorRight - anchorLeft
    val ah = anchorBottom - anchorTop
    if (aw <= 0f || ah <= 0f) return
    // Total dots in the anchor — density × area / 100. Cap to avoid GPU ddos.
    val target = (density * (aw * ah) / 100f).toInt().coerceIn(0, 4000)
    if (target == 0 || grainSize <= 0f) return
    val rnd = Random(seed)
    val alpha = (intensity * opacity).coerceIn(0f, 1f)
    val tinted = baseColor.copy(alpha = alpha)
    val r = grainSize * 0.5f
    // Always advance the RNG `target` times so positions are deterministic;
    // skip drawCircle for dots that fall outside the viewport rect.
    repeat(target) {
        val x = anchorLeft + rnd.nextFloat() * aw
        val y = anchorTop + rnd.nextFloat() * ah
        if (x + r < left || x - r > right || y + r < top || y - r > bottom) return@repeat
        drawCircle(tinted, radius = r, center = Offset(x, y))
    }
}

// ── Gradient ─────────────────────────────────────────────────────────────

/**
 * Renders [p] through the native [android.graphics.Canvas] with an
 * [android.graphics.LinearGradient] / [android.graphics.RadialGradient] shader.
 *
 * We deliberately bypass Compose's `Brush.linearGradient` / `Brush.radialGradient`
 * because both go through `ShaderBrush.applyTo(size, paint, alpha)`, which
 * **nulls its shader when `size.isEmpty()`**. The `WorldLockedAlbumBackground`
 * Spacer is zero-size (the parent camera `Box` is content-sized too), so the
 * drawscope reports `Size(0, 0)` and any Compose-brush draw renders nothing.
 * The native path doesn't care about drawscope size.
 *
 * Shader endpoints are in **anchor (world) coords** — fixed regardless of
 * viewport, so panning the camera doesn't slide the gradient. The destination
 * `drawRect` uses the viewport so we only rasterize visible pixels.
 */
private fun DrawScope.drawGradient(
    p: ProceduralPattern.Gradient,
    left: Float, top: Float, right: Float, bottom: Float,
    opacity: Float,
    anchorLeft: Float, anchorTop: Float, anchorRight: Float, anchorBottom: Float,
) {
    val aw = anchorRight - anchorLeft
    val ah = anchorBottom - anchorTop
    if (aw <= 0f || ah <= 0f) return
    if (right - left <= 0f || bottom - top <= 0f) return

    // Sort defensively; clamp positions to [0..1] in case persisted data drifts.
    val sortedStops = p.stops
        .map { it.copy(position = it.position.coerceIn(0f, 1f)) }
        .sortedBy { it.position }
    if (sortedStops.isEmpty()) return
    if (sortedStops.size == 1) {
        // Degenerate gradient — fill the viewport with the lone stop's color.
        val solo = parseHex(sortedStops[0].color)?.withAlpha(opacity) ?: return
        drawRect(color = solo, topLeft = Offset(left, top), size = Size(right - left, bottom - top))
        return
    }

    val colors = IntArray(sortedStops.size) {
        (parseHex(sortedStops[it].color)?.withAlpha(opacity) ?: Color.Transparent).toArgb()
    }
    val positions = FloatArray(sortedStops.size) { sortedStops[it].position }

    val shader: android.graphics.Shader = when (p.kind) {
        GradientKind.Linear -> {
            val rad = (p.angleDeg * PI / 180f).toFloat()
            val dx = cos(rad)
            val dy = sin(rad)
            val cx = anchorLeft + aw / 2f
            val cy = anchorTop + ah / 2f
            val halfRange = (kotlin.math.abs(dx) * aw + kotlin.math.abs(dy) * ah) / 2f
            android.graphics.LinearGradient(
                cx - dx * halfRange, cy - dy * halfRange,
                cx + dx * halfRange, cy + dy * halfRange,
                colors, positions,
                android.graphics.Shader.TileMode.CLAMP,
            )
        }
        GradientKind.Radial -> {
            val cx = anchorLeft + aw / 2f
            val cy = anchorTop + ah / 2f
            val r = min(aw, ah) / 2f * 1.25f
            android.graphics.RadialGradient(
                cx, cy, if (r > 0f) r else 1f,
                colors, positions,
                android.graphics.Shader.TileMode.CLAMP,
            )
        }
    }

    val paint = android.graphics.Paint().apply {
        this.shader = shader
        isDither = true
    }
    drawIntoCanvas { canvas ->
        canvas.nativeCanvas.drawRect(left, top, right, bottom, paint)
    }
}

// ── Watercolor wash ──────────────────────────────────────────────────────

private fun DrawScope.drawWatercolor(
    p: ProceduralPattern.Watercolor,
    left: Float, top: Float, right: Float, bottom: Float,
    opacity: Float,
    anchorLeft: Float, anchorTop: Float, anchorRight: Float, anchorBottom: Float,
) {
    val base = parseHex(p.baseColor)?.withAlpha(opacity) ?: return
    val splotch = parseHex(p.splotchColor) ?: return
    val aw = anchorRight - anchorLeft
    val ah = anchorBottom - anchorTop
    if (aw <= 0f || ah <= 0f) return
    // Base wash fills the viewport (cheap, no anchoring needed — flat color).
    drawRect(color = base, topLeft = Offset(left, top), size = Size(right - left, bottom - top))
    val splotchTint = splotch.copy(alpha = (p.splotchAlpha * opacity).coerceIn(0f, 1f))
    val count = p.splotchCount.coerceIn(0, 200)
    if (count == 0 || p.splotchRadius <= 0f) return
    val rnd = Random(p.seed)
    // Splotches live in anchor (world) coords; draw all that overlap the viewport.
    val padR = p.splotchRadius * 1.4f // largest possible passR (radiusJitter top × pass 0)
    repeat(count) {
        val cx = anchorLeft + rnd.nextFloat() * aw
        val cy = anchorTop + rnd.nextFloat() * ah
        val r = p.splotchRadius * (0.6f + rnd.nextFloat() * 0.8f)
        if (cx + padR < left || cx - padR > right || cy + padR < top || cy - padR > bottom) {
            return@repeat
        }
        // Soft-edge approximation: stack a few concentric translucent circles.
        val passes = 3
        for (pass in 0 until passes) {
            val passR = r * (1f - pass * 0.25f)
            val passA = splotchTint.alpha * (1f - pass * 0.4f)
            drawCircle(
                color = splotchTint.copy(alpha = passA.coerceIn(0f, 1f)),
                radius = passR,
                center = Offset(cx, cy),
            )
        }
    }
}

// ── Helpers ──────────────────────────────────────────────────────────────

/**
 * Returns the X (or Y) offsets of grid lines that fall inside the closed
 * interval `[axisLow, axisHigh]`, given an `origin` and `step`. Returns
 * `null` if the resulting line count would exceed [MAX_PER_AXIS].
 */
private fun lineOffsetsOnAxis(
    origin: Float,
    step: Float,
    axisLow: Float,
    axisHigh: Float,
): FloatArray? {
    val first = floor((axisLow - origin) / step).toInt()
    val last = floor((axisHigh - origin) / step).toInt() + 1
    val count = last - first
    if (count <= 0) return FloatArray(0)
    if (count > MAX_PER_AXIS) return null
    val out = FloatArray(count)
    var i = 0
    for (k in first until last) {
        out[i++] = origin + k * step
    }
    return out
}

/** Like [lineOffsetsOnAxis] but returns the *indices* (k) — needed for graph-paper major/minor. */
private fun lineIndicesOnAxis(
    origin: Float,
    step: Float,
    axisLow: Float,
    axisHigh: Float,
): IntRange? {
    val first = floor((axisLow - origin) / step).toInt()
    val last = floor((axisHigh - origin) / step).toInt() + 1
    if (last - first > MAX_PER_AXIS) return null
    return first until last
}

private fun parseHex(hex: String): Color? = runCatching { Color(hex.toColorInt()) }.getOrNull()

private fun Color.withAlpha(scale: Float): Color = copy(alpha = (alpha * scale).coerceIn(0f, 1f))

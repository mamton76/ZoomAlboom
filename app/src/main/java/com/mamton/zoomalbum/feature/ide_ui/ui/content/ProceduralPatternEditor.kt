package com.mamton.zoomalbum.feature.ide_ui.ui.content

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import androidx.core.graphics.toColorInt
import com.mamton.zoomalbum.domain.model.GradientKind
import com.mamton.zoomalbum.domain.model.GradientStop
import com.mamton.zoomalbum.domain.model.ProceduralPattern
import com.mamton.zoomalbum.feature.ide_ui.ui.color.ColorPicker
import com.mamton.zoomalbum.feature.ide_ui.ui.color.toHex
import kotlin.math.roundToInt

private fun parseOrFallback(hex: String?, fallback: Color): Color =
    runCatching { Color((hex ?: "").toColorInt()) }.getOrNull() ?: fallback

/** Dropdown picker — switches pattern type, seeding fresh defaults each time. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PatternTypeDropdown(
    current: ProceduralPattern,
    onChange: (ProceduralPattern) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { open = true }) {
            Text(current.label())
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            PATTERN_FACTORIES.forEach { (label, factory) ->
                DropdownMenuItem(
                    text = { Text(label) },
                    onClick = {
                        open = false
                        // Preserve current iff already the same type; else seed defaults.
                        if (current.label() != label) onChange(factory())
                    },
                )
            }
        }
    }
}

private fun ProceduralPattern.label(): String = when (this) {
    is ProceduralPattern.Grid -> "Grid"
    is ProceduralPattern.DotGrid -> "Dot grid"
    is ProceduralPattern.RuledPaper -> "Ruled paper"
    is ProceduralPattern.GraphPaper -> "Graph paper"
    is ProceduralPattern.PaperGrain -> "Paper grain"
    is ProceduralPattern.Noise -> "Noise"
    is ProceduralPattern.Gradient -> "Gradient"
    is ProceduralPattern.Watercolor -> "Watercolor wash"
}

private val PATTERN_FACTORIES: List<Pair<String, () -> ProceduralPattern>> = listOf(
    "Grid" to { ProceduralPattern.Grid() },
    "Dot grid" to { ProceduralPattern.DotGrid() },
    "Ruled paper" to { ProceduralPattern.RuledPaper() },
    "Graph paper" to { ProceduralPattern.GraphPaper() },
    "Paper grain" to { ProceduralPattern.PaperGrain() },
    "Noise" to { ProceduralPattern.Noise() },
    "Gradient" to { ProceduralPattern.Gradient() },
    "Watercolor wash" to { ProceduralPattern.Watercolor() },
)

/** Per-pattern parameter editor. Calls [onChange] on every tweak. */
@Composable
fun ProceduralPatternEditor(
    pattern: ProceduralPattern,
    onChange: (ProceduralPattern) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        when (pattern) {
            is ProceduralPattern.Grid -> GridEditor(pattern, onChange)
            is ProceduralPattern.DotGrid -> DotGridEditor(pattern, onChange)
            is ProceduralPattern.RuledPaper -> RuledPaperEditor(pattern, onChange)
            is ProceduralPattern.GraphPaper -> GraphPaperEditor(pattern, onChange)
            is ProceduralPattern.PaperGrain -> GrainEditor(pattern, onChange)
            is ProceduralPattern.Noise -> NoiseEditor(pattern, onChange)
            is ProceduralPattern.Gradient -> GradientEditor(pattern, onChange)
            is ProceduralPattern.Watercolor -> WatercolorEditor(pattern, onChange)
        }
    }
}

@Composable
private fun LabeledSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit,
    format: (Float) -> String = { "%.1f".format(it) },
) {
    Text("$label: ${format(value)}")
    Slider(value = value, onValueChange = onChange, valueRange = range)
}

// ── Per-pattern editors ─────────────────────────────────────────────────

@Composable
private fun GridEditor(p: ProceduralPattern.Grid, onChange: (ProceduralPattern) -> Unit) {
    SectionLabel("Line color")
    ColorPicker(
        initial = parseOrFallback(p.lineColor, Color.White),
        onChange = { onChange(p.copy(lineColor = toHex(it.copy(alpha = 1f)))) },
    )
    LabeledSlider("Cell size", p.cellSize, 5f..500f, { onChange(p.copy(cellSize = it)) })
    LabeledSlider("Line width", p.lineWidth, 0.5f..8f, { onChange(p.copy(lineWidth = it)) })
}

@Composable
private fun DotGridEditor(p: ProceduralPattern.DotGrid, onChange: (ProceduralPattern) -> Unit) {
    SectionLabel("Dot color")
    ColorPicker(
        initial = parseOrFallback(p.dotColor, Color.White),
        onChange = { onChange(p.copy(dotColor = toHex(it.copy(alpha = 1f)))) },
    )
    LabeledSlider("Spacing", p.spacing, 5f..400f, { onChange(p.copy(spacing = it)) })
    LabeledSlider("Dot radius", p.dotRadius, 0.5f..12f, { onChange(p.copy(dotRadius = it)) })
}

@Composable
private fun RuledPaperEditor(
    p: ProceduralPattern.RuledPaper,
    onChange: (ProceduralPattern) -> Unit,
) {
    SectionLabel("Line color")
    ColorPicker(
        initial = parseOrFallback(p.lineColor, Color(0xFFA0C4FF)),
        onChange = { onChange(p.copy(lineColor = toHex(it.copy(alpha = 1f)))) },
    )
    LabeledSlider("Line spacing", p.lineSpacing, 5f..200f, { onChange(p.copy(lineSpacing = it)) })
    LabeledSlider("Line width", p.lineWidth, 0.5f..6f, { onChange(p.copy(lineWidth = it)) })

    val marginEnabled = p.marginColor != null
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(
            checked = marginEnabled,
            onCheckedChange = {
                onChange(p.copy(marginColor = if (it) p.marginColor ?: "#FFB0B0" else null))
            },
        )
        Text("Vertical margin line")
    }
    if (marginEnabled) {
        SectionLabel("Margin color")
        ColorPicker(
            initial = parseOrFallback(p.marginColor, Color(0xFFFFB0B0)),
            onChange = { onChange(p.copy(marginColor = toHex(it.copy(alpha = 1f)))) },
        )
        LabeledSlider("Margin X", p.marginX, -500f..500f, { onChange(p.copy(marginX = it)) })
    }
}

@Composable
private fun GraphPaperEditor(
    p: ProceduralPattern.GraphPaper,
    onChange: (ProceduralPattern) -> Unit,
) {
    SectionLabel("Minor line color")
    ColorPicker(
        initial = parseOrFallback(p.minorColor, Color(0xFFC0E0FF)),
        onChange = { onChange(p.copy(minorColor = toHex(it.copy(alpha = 1f)))) },
    )
    SectionLabel("Major line color")
    ColorPicker(
        initial = parseOrFallback(p.majorColor, Color(0xFF80B0FF)),
        onChange = { onChange(p.copy(majorColor = toHex(it.copy(alpha = 1f)))) },
    )
    LabeledSlider(
        "Minor spacing", p.minorSpacing, 5f..200f,
        { onChange(p.copy(minorSpacing = it)) },
    )
    LabeledSlider(
        "Major every N", p.majorEvery.toFloat(), 2f..20f,
        { onChange(p.copy(majorEvery = it.roundToInt())) },
        format = { it.roundToInt().toString() },
    )
    LabeledSlider("Line width", p.lineWidth, 0.5f..6f, { onChange(p.copy(lineWidth = it)) })
}

@Composable
private fun GrainEditor(p: ProceduralPattern.PaperGrain, onChange: (ProceduralPattern) -> Unit) {
    SectionLabel("Grain color")
    ColorPicker(
        initial = parseOrFallback(p.color, Color.Black),
        onChange = { onChange(p.copy(color = toHex(it.copy(alpha = 1f)))) },
    )
    LabeledSlider("Intensity", p.intensity, 0f..1f, { onChange(p.copy(intensity = it)) },
        format = { "%.2f".format(it) })
    LabeledSlider("Grain size", p.grainSize, 0.5f..10f, { onChange(p.copy(grainSize = it)) })
    LabeledSlider("Density", p.density, 0.1f..6f, { onChange(p.copy(density = it)) },
        format = { "%.2f".format(it) })
    LabeledSlider(
        "Seed", p.seed.toFloat(), 1f..1000f,
        { onChange(p.copy(seed = it.roundToInt())) },
        format = { it.roundToInt().toString() },
    )
}

@Composable
private fun NoiseEditor(p: ProceduralPattern.Noise, onChange: (ProceduralPattern) -> Unit) {
    SectionLabel("Noise color")
    ColorPicker(
        initial = parseOrFallback(p.color, Color.White),
        onChange = { onChange(p.copy(color = toHex(it.copy(alpha = 1f)))) },
    )
    LabeledSlider("Intensity", p.intensity, 0f..1f, { onChange(p.copy(intensity = it)) },
        format = { "%.2f".format(it) })
    LabeledSlider("Grain size", p.grainSize, 0.5f..20f, { onChange(p.copy(grainSize = it)) })
    LabeledSlider("Density", p.density, 0.1f..6f, { onChange(p.copy(density = it)) },
        format = { "%.2f".format(it) })
    LabeledSlider(
        "Seed", p.seed.toFloat(), 1f..1000f,
        { onChange(p.copy(seed = it.roundToInt())) },
        format = { it.roundToInt().toString() },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GradientEditor(p: ProceduralPattern.Gradient, onChange: (ProceduralPattern) -> Unit) {
    // Render always in sorted-by-position order; pattern.stops itself may be
    // unsorted (the renderer sorts defensively too).
    val sortedStops = p.stops.sortedBy { it.position }

    SectionLabel("Preview")
    GradientPreviewStrip(sortedStops)

    SectionLabel("Stops")
    sortedStops.forEachIndexed { index, stop ->
        // First/last (in sorted order) are the locked edge stops at 0 / 1.
        // MVP: their positions are non-editable and they can't be deleted.
        val isFirst = index == 0
        val isLast = index == sortedStops.lastIndex
        val isEdge = isFirst || isLast
        StopRow(
            stop = stop,
            isEdge = isEdge,
            edgeLabel = when {
                isFirst -> "(start, locked)"
                isLast -> "(end, locked)"
                else -> null
            },
            onPositionChange = { newPos ->
                val updated = sortedStops.toMutableList()
                updated[index] = stop.copy(position = newPos.coerceIn(0f, 1f))
                onChange(p.copy(stops = updated))
            },
            onColorChange = { newColorHex ->
                val updated = sortedStops.toMutableList()
                updated[index] = stop.copy(color = newColorHex)
                onChange(p.copy(stops = updated))
            },
            onDelete = if (isEdge) null else {
                {
                    val updated = sortedStops.toMutableList()
                    updated.removeAt(index)
                    onChange(p.copy(stops = updated))
                }
            },
        )
    }

    TextButton(
        onClick = {
            val newPos = midpointOfLargestGap(sortedStops)
            val newColorHex = interpolateColorAt(sortedStops, newPos)
            val updated = (sortedStops + GradientStop(newPos, newColorHex))
                .sortedBy { it.position }
            onChange(p.copy(stops = updated))
        },
        modifier = Modifier.padding(top = 4.dp),
    ) { Text("+ Add stop") }

    SectionLabel("Kind")
    Row {
        listOf(GradientKind.Linear, GradientKind.Radial).forEach { k ->
            val selected = p.kind == k
            OutlinedButton(
                onClick = { onChange(p.copy(kind = k)) },
                border = if (selected) {
                    BorderStroke(
                        2.dp,
                        MaterialTheme.colorScheme.primary,
                    )
                } else null,
                modifier = Modifier.padding(end = 8.dp),
            ) { Text(k.name) }
        }
    }
    if (p.kind == GradientKind.Linear) {
        LabeledSlider("Angle", p.angleDeg, 0f..360f, { onChange(p.copy(angleDeg = it)) },
            format = { "${it.roundToInt()}°" })
    }
}

/**
 * Horizontal read-only preview of the current gradient. Always horizontal —
 * the angle slider controls the in-canvas direction, not the preview.
 *
 * Compose's `Brush.horizontalGradient(colorStops)` works here because the Box
 * has a non-zero drawscope size, unlike the world-locked Spacer in the canvas
 * renderer (see `memory/background_shader_gotchas.md`).
 */
@Composable
private fun GradientPreviewStrip(sortedStops: List<GradientStop>) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(28.dp)
            .clip(RoundedCornerShape(4.dp))
            .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
            .background(
                when {
                    sortedStops.size >= 2 -> Brush.horizontalGradient(
                        colorStops = sortedStops.map {
                            it.position.coerceIn(0f, 1f) to parseOrFallback(it.color, Color.Gray)
                        }.toTypedArray(),
                    )
                    sortedStops.size == 1 -> SolidColor(parseOrFallback(sortedStops[0].color, Color.Gray))
                    else -> SolidColor(Color.Gray)
                },
            ),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StopRow(
    stop: GradientStop,
    isEdge: Boolean,
    edgeLabel: String?,
    onPositionChange: (Float) -> Unit,
    onColorChange: (String) -> Unit,
    onDelete: (() -> Unit)?,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "${(stop.position * 100).roundToInt()}%",
                modifier = Modifier.width(56.dp),
            )
            if (isEdge) {
                Text(
                    text = edgeLabel ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f),
                )
            } else {
                Slider(
                    value = stop.position,
                    onValueChange = onPositionChange,
                    valueRange = 0f..1f,
                    modifier = Modifier.weight(1f),
                )
            }
            if (onDelete != null) {
                TextButton(onClick = onDelete) { Text("Delete") }
            }
        }
        // Inline color picker — preserves alpha (no .copy(alpha = 1f)).
        ColorPicker(
            initial = parseOrFallback(stop.color, Color.Gray),
            onChange = { onColorChange(toHex(it)) },
        )
    }
}

/** Picks a new stop position in the middle of the largest existing gap. */
private fun midpointOfLargestGap(sortedStops: List<GradientStop>): Float {
    if (sortedStops.size < 2) return 0.5f
    var maxGap = -1f
    var midpoint = 0.5f
    for (i in 0 until sortedStops.size - 1) {
        val gap = sortedStops[i + 1].position - sortedStops[i].position
        if (gap > maxGap) {
            maxGap = gap
            midpoint = (sortedStops[i].position + sortedStops[i + 1].position) / 2f
        }
    }
    return midpoint
}

/** Linearly interpolates a color at [position] from [sortedStops]. Alpha-aware. */
private fun interpolateColorAt(sortedStops: List<GradientStop>, position: Float): String {
    if (sortedStops.isEmpty()) return "#808080"
    val below = sortedStops.lastOrNull { it.position <= position } ?: sortedStops.first()
    val above = sortedStops.firstOrNull { it.position >= position } ?: sortedStops.last()
    if (below === above || above.position == below.position) return below.color
    val t = ((position - below.position) / (above.position - below.position)).coerceIn(0f, 1f)
    val cb = parseOrFallback(below.color, Color.Gray)
    val ca = parseOrFallback(above.color, Color.Gray)
    val mixed = Color(
        red = cb.red + (ca.red - cb.red) * t,
        green = cb.green + (ca.green - cb.green) * t,
        blue = cb.blue + (ca.blue - cb.blue) * t,
        alpha = cb.alpha + (ca.alpha - cb.alpha) * t,
    )
    return toHex(mixed)
}

@Composable
private fun WatercolorEditor(
    p: ProceduralPattern.Watercolor,
    onChange: (ProceduralPattern) -> Unit,
) {
    SectionLabel("Base color")
    ColorPicker(
        initial = parseOrFallback(p.baseColor, Color(0xFFFFEEDD)),
        onChange = { onChange(p.copy(baseColor = toHex(it.copy(alpha = 1f)))) },
    )
    SectionLabel("Splotch color")
    ColorPicker(
        initial = parseOrFallback(p.splotchColor, Color(0xFFFFD9B5)),
        onChange = { onChange(p.copy(splotchColor = toHex(it.copy(alpha = 1f)))) },
    )
    LabeledSlider(
        "Splotch count", p.splotchCount.toFloat(), 0f..100f,
        { onChange(p.copy(splotchCount = it.roundToInt())) },
        format = { it.roundToInt().toString() },
    )
    LabeledSlider(
        "Splotch radius", p.splotchRadius, 20f..1000f,
        { onChange(p.copy(splotchRadius = it)) },
    )
    LabeledSlider("Splotch alpha", p.splotchAlpha, 0f..1f, { onChange(p.copy(splotchAlpha = it)) },
        format = { "%.2f".format(it) })
    LabeledSlider(
        "Seed", p.seed.toFloat(), 1f..1000f,
        { onChange(p.copy(seed = it.roundToInt())) },
        format = { it.roundToInt().toString() },
    )
    Spacer(Modifier.height(4.dp))
}

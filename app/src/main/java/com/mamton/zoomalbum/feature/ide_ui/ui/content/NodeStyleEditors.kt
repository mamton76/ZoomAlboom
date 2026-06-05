package com.mamton.zoomalbum.feature.ide_ui.ui.content

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.core.graphics.toColorInt
import com.mamton.zoomalbum.domain.model.BorderStyle
import com.mamton.zoomalbum.domain.model.ShadowStyle
import com.mamton.zoomalbum.feature.canvas.editor.MixedValue
import com.mamton.zoomalbum.feature.ide_ui.ui.color.ColorPicker
import com.mamton.zoomalbum.feature.ide_ui.ui.color.toHex

/**
 * Editors for the four cross-cutting fields on `NodeAppearance` — reused by
 * both the frame and media appearance sheets.
 *
 *  - [OpacitySlider]      — surface opacity (`alpha` on the node's graphicsLayer)
 *  - [CornerRadiusSlider] — corner rounding of the node's rect (world units)
 *  - [BorderStyleEditor]  — `BorderStyle?` with an enabled checkbox
 *  - [ShadowStyleEditor]  — `ShadowStyle?` with an enabled checkbox
 *
 * The two `*Style?` editors emit `null` when the checkbox is off so the caller's
 * collapse-to-null logic stays clean.
 */

@Composable
fun OpacitySlider(value: Float, onChange: (Float) -> Unit) {
    SectionLabel("Opacity: ${"%.2f".format(value)}")
    Slider(
        value = value.coerceIn(0f, 1f),
        onValueChange = onChange,
        valueRange = 0f..1f,
    )
}

@Composable
fun CornerRadiusSlider(
    value: Float,
    onChange: (Float) -> Unit,
    maxValue: Float = 200f,
) {
    SectionLabel("Corner radius: ${"%.0f".format(value)}")
    Slider(
        value = value.coerceIn(0f, maxValue),
        onValueChange = onChange,
        valueRange = 0f..maxValue,
    )
}

/**
 * Mixed-aware wrapper around [OpacitySlider]. When [mixed] is
 * [MixedValue.Same], renders the standard slider with that value. When
 * [MixedValue.Mixed], labels the field "Opacity: Mixed" and seeds the slider
 * at a neutral midpoint — the first user interaction commits a uniform value
 * to every node in the editing set (destructive unify per `appearance.md §
 * 14.2`).
 */
@Composable
fun MixedAwareOpacitySlider(mixed: MixedValue<Float>, onChange: (Float) -> Unit) {
    when (mixed) {
        is MixedValue.Same -> OpacitySlider(value = mixed.value, onChange = onChange)
        MixedValue.Mixed -> {
            SectionLabel("Opacity: Mixed")
            Slider(value = 0.5f, onValueChange = onChange, valueRange = 0f..1f)
        }
    }
}

/** Mixed-aware wrapper around [CornerRadiusSlider]. See [MixedAwareOpacitySlider]. */
@Composable
fun MixedAwareCornerRadiusSlider(
    mixed: MixedValue<Float>,
    onChange: (Float) -> Unit,
    maxValue: Float = 200f,
) {
    when (mixed) {
        is MixedValue.Same -> CornerRadiusSlider(
            value = mixed.value, onChange = onChange, maxValue = maxValue,
        )
        MixedValue.Mixed -> {
            SectionLabel("Corner radius: Mixed")
            Slider(
                value = 0f,
                onValueChange = onChange,
                valueRange = 0f..maxValue,
            )
        }
    }
}

@Composable
fun BorderStyleEditor(
    initial: BorderStyle?,
    onChange: (BorderStyle?) -> Unit,
) {
    var enabled by remember { mutableStateOf(initial != null) }
    var color by remember { mutableStateOf(parseHexOrFallback(initial?.color, Color.White)) }
    var widthPx by remember { mutableStateOf(initial?.widthPx ?: 4f) }
    var opacity by remember { mutableStateOf(initial?.opacity ?: 1f) }

    fun emit() {
        onChange(
            if (enabled) BorderStyle(
                color = toHex(color.copy(alpha = 1f)),
                widthPx = widthPx,
                opacity = opacity,
            ) else null,
        )
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = enabled, onCheckedChange = { enabled = it; emit() })
        Text(
            text = "Border",
            style = MaterialTheme.typography.titleSmall,
        )
    }

    if (enabled) {
        Column(modifier = Modifier.fillMaxWidth()) {
            SectionLabel("Color")
            ColorPicker(initial = color, onChange = { color = it; emit() })

            SectionLabel("Width: ${"%.1f".format(widthPx)}")
            Slider(
                value = widthPx.coerceIn(0f, MAX_BORDER_WIDTH),
                onValueChange = { widthPx = it; emit() },
                valueRange = 0f..MAX_BORDER_WIDTH,
            )

            SectionLabel("Opacity: ${"%.2f".format(opacity)}")
            Slider(
                value = opacity.coerceIn(0f, 1f),
                onValueChange = { opacity = it; emit() },
                valueRange = 0f..1f,
            )
        }
    }
}

@Composable
fun ShadowStyleEditor(
    initial: ShadowStyle?,
    onChange: (ShadowStyle?) -> Unit,
) {
    var enabled by remember { mutableStateOf(initial != null) }
    var color by remember { mutableStateOf(parseHexOrFallback(initial?.color, Color.Black)) }
    var opacity by remember { mutableStateOf(initial?.opacity ?: 0.5f) }
    var offsetX by remember { mutableStateOf(initial?.offsetX ?: 4f) }
    var offsetY by remember { mutableStateOf(initial?.offsetY ?: 6f) }
    var blurRadius by remember { mutableStateOf(initial?.blurRadius ?: 12f) }

    fun emit() {
        onChange(
            if (enabled) ShadowStyle(
                color = toHex(color.copy(alpha = 1f)),
                opacity = opacity,
                offsetX = offsetX,
                offsetY = offsetY,
                blurRadius = blurRadius,
            ) else null,
        )
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = enabled, onCheckedChange = { enabled = it; emit() })
        Text(
            text = "Drop shadow",
            style = MaterialTheme.typography.titleSmall,
        )
    }

    if (enabled) {
        Column(modifier = Modifier.fillMaxWidth()) {
            SectionLabel("Color")
            ColorPicker(initial = color, onChange = { color = it; emit() })

            SectionLabel("Opacity: ${"%.2f".format(opacity)}")
            Slider(
                value = opacity.coerceIn(0f, 1f),
                onValueChange = { opacity = it; emit() },
                valueRange = 0f..1f,
            )

            SectionLabel("Offset X: ${"%.0f".format(offsetX)}")
            Slider(
                value = offsetX.coerceIn(-MAX_SHADOW_OFFSET, MAX_SHADOW_OFFSET),
                onValueChange = { offsetX = it; emit() },
                valueRange = -MAX_SHADOW_OFFSET..MAX_SHADOW_OFFSET,
            )

            SectionLabel("Offset Y: ${"%.0f".format(offsetY)}")
            Slider(
                value = offsetY.coerceIn(-MAX_SHADOW_OFFSET, MAX_SHADOW_OFFSET),
                onValueChange = { offsetY = it; emit() },
                valueRange = -MAX_SHADOW_OFFSET..MAX_SHADOW_OFFSET,
            )

            SectionLabel("Blur: ${"%.0f".format(blurRadius)}")
            Slider(
                value = blurRadius.coerceIn(0f, MAX_SHADOW_BLUR),
                onValueChange = { blurRadius = it; emit() },
                valueRange = 0f..MAX_SHADOW_BLUR,
            )
        }
    }
}

private fun parseHexOrFallback(hex: String?, fallback: Color): Color =
    hex?.let { runCatching { Color(it.toColorInt()) }.getOrNull() } ?: fallback

private const val MAX_BORDER_WIDTH = 40f
private const val MAX_SHADOW_OFFSET = 40f
private const val MAX_SHADOW_BLUR = 60f

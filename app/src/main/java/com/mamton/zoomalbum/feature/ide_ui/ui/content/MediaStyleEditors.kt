package com.mamton.zoomalbum.feature.ide_ui.ui.content

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.unit.dp
import androidx.core.graphics.toColorInt
import com.mamton.zoomalbum.domain.model.CaptionStyle
import com.mamton.zoomalbum.domain.model.CropMode
import com.mamton.zoomalbum.domain.model.CropSettings
import com.mamton.zoomalbum.domain.model.MediaColorAdjustments
import com.mamton.zoomalbum.domain.model.MediaFrameDecoration
import com.mamton.zoomalbum.domain.model.MediaFrameDecorationMode
import com.mamton.zoomalbum.feature.ide_ui.ui.color.ColorPicker
import com.mamton.zoomalbum.feature.ide_ui.ui.color.toHex

/**
 * Editors for the media-specific sections of `MediaAppearance`:
 *
 *  - [CropEditor]              — `CropSettings` (mode + focal point + manual offsets).
 *  - [ColorAdjustmentsEditor]  — `MediaColorAdjustments?` with an enabled checkbox.
 *                                Persist-only today (the renderer doesn't apply
 *                                color adjustments yet — see media-appearance.md).
 *  - [CaptionStyleEditor]      — `CaptionStyle?` (text + font + color).
 *                                Persist-only today.
 *  - [MediaFrameDecorationEditor] — `MediaFrameDecoration?` (asset URI + mode + insets).
 *                                Persist-only; asset-picker UI is post-MVP.
 *
 * "Persist-only" sections still round-trip through JSON and are visible to
 * future renderer slices; the user just won't see immediate visual change today.
 */

// ── Crop ──────────────────────────────────────────────────────────────────────

@Composable
fun CropEditor(
    initial: CropSettings,
    onChange: (CropSettings) -> Unit,
) {
    var mode by remember { mutableStateOf(initial.mode) }
    var focalX by remember { mutableStateOf(initial.focalX) }
    var focalY by remember { mutableStateOf(initial.focalY) }
    var offsetX by remember { mutableStateOf(initial.offsetX) }
    var offsetY by remember { mutableStateOf(initial.offsetY) }
    var zoom by remember { mutableStateOf(initial.zoom) }

    fun emit() {
        onChange(
            CropSettings(
                mode = mode,
                focalX = focalX,
                focalY = focalY,
                offsetX = offsetX,
                offsetY = offsetY,
                zoom = zoom,
            ),
        )
    }

    SectionLabel("Crop")
    Row(verticalAlignment = Alignment.CenterVertically) {
        CropMode.entries.forEach { m ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(
                    selected = mode == m,
                    onClick = { mode = m; emit() },
                )
                Text(text = m.name, modifier = Modifier.padding(end = 8.dp))
            }
        }
    }

    when (mode) {
        CropMode.Fit, CropMode.Stretch -> Unit
        CropMode.Fill -> {
            SectionLabel("Focal X: ${"%.2f".format(focalX)}")
            Slider(
                value = focalX.coerceIn(0f, 1f),
                onValueChange = { focalX = it; emit() },
                valueRange = 0f..1f,
            )
            SectionLabel("Focal Y: ${"%.2f".format(focalY)}")
            Slider(
                value = focalY.coerceIn(0f, 1f),
                onValueChange = { focalY = it; emit() },
                valueRange = 0f..1f,
            )
        }
        CropMode.Manual -> {
            Text(
                text = "Manual pan/zoom values persist but aren't rendered yet — " +
                    "the canvas currently falls back to Fill rendering.",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(vertical = 4.dp),
            )
            SectionLabel("Offset X: ${"%.0f".format(offsetX)}")
            Slider(
                value = offsetX.coerceIn(-MAX_MANUAL_OFFSET, MAX_MANUAL_OFFSET),
                onValueChange = { offsetX = it; emit() },
                valueRange = -MAX_MANUAL_OFFSET..MAX_MANUAL_OFFSET,
            )
            SectionLabel("Offset Y: ${"%.0f".format(offsetY)}")
            Slider(
                value = offsetY.coerceIn(-MAX_MANUAL_OFFSET, MAX_MANUAL_OFFSET),
                onValueChange = { offsetY = it; emit() },
                valueRange = -MAX_MANUAL_OFFSET..MAX_MANUAL_OFFSET,
            )
            SectionLabel("Zoom: ${"%.2f".format(zoom)}")
            Slider(
                value = zoom.coerceIn(MIN_MANUAL_ZOOM, MAX_MANUAL_ZOOM),
                onValueChange = { zoom = it; emit() },
                valueRange = MIN_MANUAL_ZOOM..MAX_MANUAL_ZOOM,
            )
        }
    }
}

// ── Color adjustments ─────────────────────────────────────────────────────────

@Composable
fun ColorAdjustmentsEditor(
    initial: MediaColorAdjustments?,
    onChange: (MediaColorAdjustments?) -> Unit,
) {
    var enabled by remember { mutableStateOf(initial != null) }
    var brightness by remember { mutableStateOf(initial?.brightness ?: 0f) }
    var contrast by remember { mutableStateOf(initial?.contrast ?: 0f) }
    var saturation by remember { mutableStateOf(initial?.saturation ?: 0f) }
    var temperature by remember { mutableStateOf(initial?.temperature ?: 0f) }
    var exposure by remember { mutableStateOf(initial?.exposure ?: 0f) }
    var vignette by remember { mutableStateOf(initial?.vignette ?: 0f) }

    fun emit() {
        onChange(
            if (enabled) MediaColorAdjustments(
                brightness = brightness,
                contrast = contrast,
                saturation = saturation,
                temperature = temperature,
                exposure = exposure,
                vignette = vignette,
            ) else null,
        )
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = enabled, onCheckedChange = { enabled = it; emit() })
        Text(
            text = "Color adjustments",
            style = MaterialTheme.typography.titleSmall,
        )
    }

    if (enabled) {
        Text(
            text = "Adjustments persist but aren't applied at render time yet.",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(vertical = 4.dp),
        )
        AdjustmentSlider("Brightness", brightness) { brightness = it; emit() }
        AdjustmentSlider("Contrast", contrast) { contrast = it; emit() }
        AdjustmentSlider("Saturation", saturation) { saturation = it; emit() }
        AdjustmentSlider("Temperature", temperature) { temperature = it; emit() }
        AdjustmentSlider("Exposure", exposure) { exposure = it; emit() }
        AdjustmentSlider("Vignette", vignette) { vignette = it; emit() }
    }
}

@Composable
private fun AdjustmentSlider(label: String, value: Float, onChange: (Float) -> Unit) {
    SectionLabel("$label: ${"%+.2f".format(value)}")
    Slider(
        value = value.coerceIn(-1f, 1f),
        onValueChange = onChange,
        valueRange = -1f..1f,
    )
}

// ── Caption ───────────────────────────────────────────────────────────────────

@Composable
fun CaptionStyleEditor(
    initial: CaptionStyle?,
    onChange: (CaptionStyle?) -> Unit,
) {
    var enabled by remember { mutableStateOf(initial != null) }
    var text by remember { mutableStateOf(initial?.text ?: "") }
    var fontSize by remember { mutableStateOf(initial?.fontSize ?: 14f) }
    var color by remember {
        mutableStateOf(
            runCatching { Color((initial?.color ?: "#000000").toColorInt()) }
                .getOrNull() ?: Color.Black,
        )
    }
    var show by remember { mutableStateOf(initial?.show ?: true) }

    fun emit() {
        onChange(
            if (enabled) CaptionStyle(
                text = text,
                show = show,
                fontSize = fontSize,
                color = toHex(color.copy(alpha = 1f)),
            ) else null,
        )
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = enabled, onCheckedChange = { enabled = it; emit() })
        Text(
            text = "Caption",
            style = MaterialTheme.typography.titleSmall,
        )
    }

    if (enabled) {
        Text(
            text = "Caption text persists; caption rendering lands in a future slice.",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(vertical = 4.dp),
        )
        OutlinedTextField(
            value = text,
            onValueChange = { text = it; emit() },
            label = { Text("Text") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = show, onCheckedChange = { show = it; emit() })
            Text("Show caption")
        }
        SectionLabel("Font size: ${"%.0f".format(fontSize)}")
        Slider(
            value = fontSize.coerceIn(8f, MAX_CAPTION_FONT),
            onValueChange = { fontSize = it; emit() },
            valueRange = 8f..MAX_CAPTION_FONT,
        )
        SectionLabel("Color")
        ColorPicker(initial = color, onChange = { color = it; emit() })
    }
}

// ── Media frame decoration ────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaFrameDecorationEditor(
    initial: MediaFrameDecoration?,
    onChange: (MediaFrameDecoration?) -> Unit,
) {
    var enabled by remember { mutableStateOf(initial != null) }
    var assetUri by remember { mutableStateOf(initial?.assetUri ?: "") }
    var opacity by remember { mutableStateOf(initial?.opacity ?: 1f) }
    var mode by remember { mutableStateOf(initial?.mode ?: MediaFrameDecorationMode.Stretch) }
    var modeOpen by remember { mutableStateOf(false) }

    fun emit() {
        onChange(
            if (enabled && assetUri.isNotBlank()) MediaFrameDecoration(
                assetUri = assetUri,
                opacity = opacity,
                mode = mode,
            ) else null,
        )
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = enabled, onCheckedChange = { enabled = it; emit() })
        Text(
            text = "Picture-frame decoration",
            style = MaterialTheme.typography.titleSmall,
        )
    }

    if (enabled) {
        Text(
            text = "Decoration data persists; renderer support (Stretch / NineSlice) " +
                "lands in a future slice.",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(vertical = 4.dp),
        )
        OutlinedTextField(
            value = assetUri,
            onValueChange = { assetUri = it; emit() },
            label = { Text("Asset URI") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        SectionLabel("Mode")
        Box {
            OutlinedButton(onClick = { modeOpen = true }) { Text(mode.name) }
            DropdownMenu(expanded = modeOpen, onDismissRequest = { modeOpen = false }) {
                MediaFrameDecorationMode.entries.forEach { m ->
                    DropdownMenuItem(
                        text = { Text(m.name) },
                        onClick = { mode = m; modeOpen = false; emit() },
                    )
                }
            }
        }
        SectionLabel("Opacity: ${"%.2f".format(opacity)}")
        Slider(
            value = opacity.coerceIn(0f, 1f),
            onValueChange = { opacity = it; emit() },
            valueRange = 0f..1f,
        )
    }
}

private const val MAX_MANUAL_OFFSET = 1000f
private const val MIN_MANUAL_ZOOM = 0.1f
private const val MAX_MANUAL_ZOOM = 4f
private const val MAX_CAPTION_FONT = 64f

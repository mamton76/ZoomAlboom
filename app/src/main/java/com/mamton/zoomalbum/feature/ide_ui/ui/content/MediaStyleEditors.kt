package com.mamton.zoomalbum.feature.ide_ui.ui.content

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.graphics.toColorInt
import coil3.compose.AsyncImage
import com.mamton.zoomalbum.domain.model.CaptionStyle
import com.mamton.zoomalbum.domain.model.CropMode
import com.mamton.zoomalbum.domain.model.CropSettings
import com.mamton.zoomalbum.domain.model.MediaColorAdjustments
import com.mamton.zoomalbum.domain.model.MediaFrameDecoration
import com.mamton.zoomalbum.domain.model.MediaFrameDecorationMode
import com.mamton.zoomalbum.feature.ide_ui.ui.color.ColorPicker
import com.mamton.zoomalbum.feature.ide_ui.ui.color.toHex
import kotlin.math.roundToInt

/**
 * Editors for the media-specific sections of `MediaAppearance`:
 *
 *  - [CropEditor]              — `CropSettings` (mode + focal point + manual offsets).
 *  - [ColorAdjustmentsEditor]  — `MediaColorAdjustments?` with an enabled checkbox.
 *                                Persist-only today (the renderer doesn't apply
 *                                color adjustments yet — see media-appearance.md).
 *  - [CaptionStyleEditor]      — `CaptionStyle?` (text + font + color).
 *                                Persist-only today.
 *  - [MediaFrameDecorationEditor] — `MediaFrameDecoration?` (asset image + mode + insets).
 *                                Asset is chosen via the SAF image picker (same as
 *                                masks/overlays). Rendered (Stretch / NineSlice) by
 *                                `MediaFrameDecorationRenderer`.
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
    val context = LocalContext.current
    var enabled by remember { mutableStateOf(initial != null) }
    var assetUri by remember { mutableStateOf(initial?.assetUri ?: "") }
    var opacity by remember { mutableStateOf(initial?.opacity ?: 1f) }
    var mode by remember { mutableStateOf(initial?.mode ?: MediaFrameDecorationMode.Stretch) }
    var modeOpen by remember { mutableStateOf(false) }
    // Four canonical slice + four canonical opening insets. The symmetric UI
    // edits them in lockstep (one slice field, H/V opening fields); the
    // asymmetric UI edits each edge. Defaulting `asymmetric` to the initial's
    // actual symmetry means opening an asymmetric frame (e.g. Polaroid) won't
    // silently flatten it.
    var sliceLeft by remember { mutableStateOf(initial?.sliceLeft ?: DEFAULT_SLICE_INSET) }
    var sliceTop by remember { mutableStateOf(initial?.sliceTop ?: DEFAULT_SLICE_INSET) }
    var sliceRight by remember { mutableStateOf(initial?.sliceRight ?: DEFAULT_SLICE_INSET) }
    var sliceBottom by remember { mutableStateOf(initial?.sliceBottom ?: DEFAULT_SLICE_INSET) }
    var insetLeft by remember { mutableStateOf(initial?.contentInsetLeft ?: 0f) }
    var insetTop by remember { mutableStateOf(initial?.contentInsetTop ?: 0f) }
    var insetRight by remember { mutableStateOf(initial?.contentInsetRight ?: 0f) }
    var insetBottom by remember { mutableStateOf(initial?.contentInsetBottom ?: 0f) }
    var sliceAsymmetric by remember { mutableStateOf(initial.isSliceAsymmetric()) }
    var openingAsymmetric by remember { mutableStateOf(initial.isOpeningAsymmetric()) }
    // Arbitrary-shape opening mask (white = opening). When set it overrides the
    // rectangular insets — see MediaFrameDecoration.openingRect / openingAlphaMask.
    var openingMaskUri by remember { mutableStateOf(initial?.openingMaskUri.orEmpty()) }

    fun emit() {
        onChange(
            if (enabled && assetUri.isNotBlank()) MediaFrameDecoration(
                assetUri = assetUri,
                opacity = opacity,
                mode = mode,
                sliceLeft = sliceLeft,
                sliceTop = sliceTop,
                sliceRight = sliceRight,
                sliceBottom = sliceBottom,
                contentInsetLeft = insetLeft,
                contentInsetTop = insetTop,
                contentInsetRight = insetRight,
                contentInsetBottom = insetBottom,
                openingMaskUri = openingMaskUri.ifBlank { null },
            ) else null,
        )
    }

    // SAF document picker rather than PickVisualMedia: decoration PNGs (often
    // with transparency) are commonly exported to /Downloads or app folders
    // that MediaStore doesn't index, so the Photo Picker would hide them.
    // Mirrors the mask/overlay image pickers — see ImageMaskSourceEditor.
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
        assetUri = uri.toString()
        emit()
    }
    val maskPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
        openingMaskUri = uri.toString()
        emit()
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = enabled, onCheckedChange = { enabled = it; emit() })
        Text(
            text = "Picture-frame decoration",
            style = MaterialTheme.typography.titleSmall,
        )
    }

    if (enabled) {
        AssetPickerRow(
            label = "Decoration image",
            uri = assetUri,
            onPick = { picker.launch(arrayOf("image/*")) },
            onClear = { assetUri = ""; emit() },
        )
        Spacer(Modifier.height(8.dp))
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
        if (mode == MediaFrameDecorationMode.NineSlice) {
            Text(
                text = "Slice border marks the fixed corner zone (% of the asset " +
                    "edge). Corners keep their shape; the edges between them stretch " +
                    "to fit any media proportion.",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(vertical = 4.dp),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = sliceAsymmetric,
                    onCheckedChange = { sliceAsymmetric = it; emit() },
                )
                Text("Edit each edge separately")
            }
            if (sliceAsymmetric) {
                FourPercentFields(
                    left = sliceLeft, top = sliceTop, right = sliceRight, bottom = sliceBottom,
                    onLeft = { sliceLeft = it; emit() },
                    onTop = { sliceTop = it; emit() },
                    onRight = { sliceRight = it; emit() },
                    onBottom = { sliceBottom = it; emit() },
                )
            } else {
                PercentField(
                    label = "Slice border",
                    fraction = sliceLeft,
                    onFraction = {
                        sliceLeft = it; sliceTop = it; sliceRight = it; sliceBottom = it; emit()
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        SectionLabel("Opening (crops media inside the frame)")
        Text(
            text = "Confine the photo/video to the frame's opening so it can't leak " +
                "past the decoration. Use a shape mask (white = opening) for round / " +
                "arched / torn shapes, or rectangular insets below.",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(vertical = 4.dp),
        )
        AssetPickerRow(
            label = "Opening shape mask (optional)",
            uri = openingMaskUri,
            onPick = { maskPicker.launch(arrayOf("image/*")) },
            onClear = { openingMaskUri = ""; emit() },
        )
        if (openingMaskUri.isNotBlank()) {
            Text(
                text = "Shape mask in use — rectangular insets are ignored.",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(vertical = 4.dp),
            )
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = openingAsymmetric,
                    onCheckedChange = { openingAsymmetric = it; emit() },
                )
                Text("Edit each edge separately")
            }
            if (openingAsymmetric) {
                FourPercentFields(
                    left = insetLeft, top = insetTop, right = insetRight, bottom = insetBottom,
                    onLeft = { insetLeft = it; emit() },
                    onTop = { insetTop = it; emit() },
                    onRight = { insetRight = it; emit() },
                    onBottom = { insetBottom = it; emit() },
                )
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    PercentField(
                        label = "Horizontal",
                        fraction = insetLeft,
                        onFraction = { insetLeft = it; insetRight = it; emit() },
                        modifier = Modifier.weight(1f),
                    )
                    PercentField(
                        label = "Vertical",
                        fraction = insetTop,
                        onFraction = { insetTop = it; insetBottom = it; emit() },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        SectionLabel("Opacity: ${"%.2f".format(opacity)}")
        Slider(
            value = opacity.coerceIn(0f, 1f),
            onValueChange = { opacity = it; emit() },
            valueRange = 0f..1f,
        )
    }
}

/** Thumbnail + Pick/Replace/Clear row over a content-URI string. Shared by the decoration asset + opening-mask pickers. */
@Composable
private fun AssetPickerRow(
    label: String,
    uri: String,
    onPick: () -> Unit,
    onClear: () -> Unit,
) {
    SectionLabel(label)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Color(0xFF2A2A2A))
                .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(6.dp)),
            contentAlignment = Alignment.Center,
        ) {
            if (uri.isNotBlank()) {
                AsyncImage(model = uri, contentDescription = null, modifier = Modifier.fillMaxWidth())
            } else {
                Text("∅", style = MaterialTheme.typography.titleLarge)
            }
        }
        Spacer(Modifier.width(12.dp))
        OutlinedButton(onClick = onPick) {
            Text(if (uri.isBlank()) "Pick image" else "Replace")
        }
        if (uri.isNotBlank()) {
            Spacer(Modifier.width(8.dp))
            TextButton(onClick = onClear) { Text("Clear") }
        }
    }
}

/** True when the four slice insets aren't all equal — defaults the slice asymmetric toggle. */
private fun MediaFrameDecoration?.isSliceAsymmetric(): Boolean {
    this ?: return false
    return !(sliceLeft == sliceTop && sliceTop == sliceRight && sliceRight == sliceBottom)
}

/** True when opening insets break left==right / top==bottom — defaults the opening asymmetric toggle. */
private fun MediaFrameDecoration?.isOpeningAsymmetric(): Boolean {
    this ?: return false
    return !(contentInsetLeft == contentInsetRight && contentInsetTop == contentInsetBottom)
}

/** Left/Top over Right/Bottom percent inputs for asymmetric slice or opening insets. */
@Composable
private fun FourPercentFields(
    left: Float,
    top: Float,
    right: Float,
    bottom: Float,
    onLeft: (Float) -> Unit,
    onTop: (Float) -> Unit,
    onRight: (Float) -> Unit,
    onBottom: (Float) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        PercentField("Left", left, onLeft, Modifier.weight(1f))
        PercentField("Top", top, onTop, Modifier.weight(1f))
    }
    Spacer(Modifier.height(4.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        PercentField("Right", right, onRight, Modifier.weight(1f))
        PercentField("Bottom", bottom, onBottom, Modifier.weight(1f))
    }
}

/**
 * Integer-percent text input bound to a 0..1 [fraction]. Shows the fraction as a
 * whole percent, accepts digits only, and clamps to [MAX_SLICE_PERCENT] so
 * opposite insets can never sum past the edge.
 */
@Composable
private fun PercentField(
    label: String,
    fraction: Float,
    onFraction: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    var text by remember { mutableStateOf((fraction * 100f).roundToInt().toString()) }
    OutlinedTextField(
        value = text,
        onValueChange = { raw ->
            val digits = raw.filter { it.isDigit() }.take(2)
            val pct = digits.toIntOrNull()
            if (pct == null) {
                text = ""
                onFraction(0f)
            } else {
                val clamped = pct.coerceAtMost(MAX_SLICE_PERCENT)
                text = clamped.toString()
                onFraction(clamped / 100f)
            }
        },
        label = { Text("$label %") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier,
    )
}

// Default slice border for a new frame; ceiling keeps opposite insets from
// summing past the asset/node edge (49% + 49% < 100%).
private const val DEFAULT_SLICE_INSET = 0.25f
private const val MAX_SLICE_PERCENT = 49
private const val MAX_MANUAL_OFFSET = 1000f
private const val MIN_MANUAL_ZOOM = 0.1f
private const val MAX_MANUAL_ZOOM = 4f
private const val MAX_CAPTION_FONT = 64f

package com.mamton.zoomalbum.feature.ide_ui.ui.content

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import com.mamton.zoomalbum.domain.model.DecorationPlacement
import com.mamton.zoomalbum.domain.model.MediaColorAdjustments
import com.mamton.zoomalbum.domain.model.MediaDecoration
import com.mamton.zoomalbum.domain.model.MediaDecorationMode
import com.mamton.zoomalbum.domain.model.MediaOpening
import com.mamton.zoomalbum.feature.ide_ui.ui.color.ColorPicker
import com.mamton.zoomalbum.feature.ide_ui.ui.color.toHex
import java.util.UUID
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
 *  - [MediaOpeningEditor]      — `MediaOpening?` (rectangular content-area insets).
 *  - [DecorationListEditor]    — `List<MediaDecoration>` (a stack of visual layers,
 *                                each asset + mode + slice + placement + opacity).
 *                                Rendered (Stretch / NineSlice) by `MediaDecorationRenderer`.
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
    var tint by remember { mutableStateOf(initial?.tint ?: 0f) }
    var exposure by remember { mutableStateOf(initial?.exposure ?: 0f) }
    var vignette by remember { mutableStateOf(initial?.vignette ?: 0f) }

    fun emit() {
        onChange(
            if (enabled) MediaColorAdjustments(
                brightness = brightness,
                contrast = contrast,
                saturation = saturation,
                temperature = temperature,
                tint = tint,
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
        AdjustmentSlider("Brightness", brightness) { brightness = it; emit() }
        AdjustmentSlider("Contrast", contrast) { contrast = it; emit() }
        AdjustmentSlider("Saturation", saturation) { saturation = it; emit() }
        AdjustmentSlider("Temperature", temperature) { temperature = it; emit() }
        AdjustmentSlider("Tint", tint) { tint = it; emit() }
        AdjustmentSlider("Exposure", exposure) { exposure = it; emit() }
        AdjustmentSlider("Vignette", vignette) { vignette = it; emit() }
        Text(
            text = "Highlights, shadows, blur and sharpen are not rendered yet.",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(vertical = 4.dp),
        )
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

// ── Media opening (rectangular content-area slot) ──────────────────────────────

@Composable
fun MediaOpeningEditor(
    initial: MediaOpening?,
    onChange: (MediaOpening?) -> Unit,
) {
    var insetLeft by remember { mutableStateOf(initial?.insetLeft ?: 0f) }
    var insetTop by remember { mutableStateOf(initial?.insetTop ?: 0f) }
    var insetRight by remember { mutableStateOf(initial?.insetRight ?: 0f) }
    var insetBottom by remember { mutableStateOf(initial?.insetBottom ?: 0f) }
    var asymmetric by remember { mutableStateOf(initial.isAsymmetric()) }

    fun emit() {
        val any = insetLeft > 0f || insetTop > 0f || insetRight > 0f || insetBottom > 0f
        onChange(
            if (any) MediaOpening(insetLeft, insetTop, insetRight, insetBottom) else null,
        )
    }

    Column(Modifier.fillMaxWidth()) {
        Text(
            text = "Insets (% of the node edge) shrink the content area so frame / " +
                "mat decorations sit in the margin. Crop fits the media inside this " +
                "area. Arbitrary shapes go through the content mask.",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(vertical = 4.dp),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = asymmetric, onCheckedChange = { asymmetric = it; emit() })
            Text("Edit each edge separately")
        }
        if (asymmetric) {
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
}

// ── Media decorations (visual layer stack) ─────────────────────────────────────

/**
 * Editor for the ordered [MediaDecoration] stack: add / remove / reorder layers,
 * each editing its own asset / mode / slice / placement / opacity via
 * [MediaDecorationEditor]. (Visibility / hide-vs-delete polish is a follow-up.)
 */
@Composable
fun DecorationListEditor(
    initial: List<MediaDecoration>,
    onChange: (List<MediaDecoration>) -> Unit,
) {
    val context = LocalContext.current
    var items by remember { mutableStateOf(initial) }

    fun update(newItems: List<MediaDecoration>) {
        items = newItems
        onChange(newItems)
    }

    val addPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
        update(items + MediaDecoration(id = UUID.randomUUID().toString(), assetUri = uri.toString()))
    }

    Column(Modifier.fillMaxWidth()) {
        if (items.isEmpty()) {
            Text(
                text = "No decorations. Add a frame, tape, sticker, or label layer.",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(vertical = 4.dp),
            )
        }
        items.forEachIndexed { index, deco ->
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Layer ${index + 1}",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    enabled = index > 0,
                    onClick = { update(items.swapped(index, index - 1)) },
                ) { Text("↑") }
                TextButton(
                    enabled = index < items.lastIndex,
                    onClick = { update(items.swapped(index, index + 1)) },
                ) { Text("↓") }
                TextButton(
                    onClick = { update(items.toMutableList().also { it.removeAt(index) }) },
                ) { Text("Remove") }
            }
            MediaDecorationEditor(
                decoration = deco,
                onChange = { updated -> update(items.toMutableList().also { it[index] = updated }) },
            )
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = { addPicker.launch(arrayOf("image/*")) }) {
            Text("+ Add decoration")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MediaDecorationEditor(
    decoration: MediaDecoration,
    onChange: (MediaDecoration) -> Unit,
) {
    val context = LocalContext.current
    var modeOpen by remember { mutableStateOf(false) }
    var sliceAsymmetric by remember { mutableStateOf(decoration.isSliceAsymmetric()) }

    val replacePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
        onChange(decoration.copy(assetUri = uri.toString()))
    }

    AssetPickerRow(
        label = "Image",
        uri = decoration.assetUri,
        onPick = { replacePicker.launch(arrayOf("image/*")) },
    )

    Spacer(Modifier.height(8.dp))
    SectionLabel("Placement")
    Row(verticalAlignment = Alignment.CenterVertically) {
        DecorationPlacement.entries.forEach { p ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(
                    selected = decoration.placement == p,
                    onClick = { onChange(decoration.copy(placement = p)) },
                )
                Text(text = p.name, modifier = Modifier.padding(end = 8.dp))
            }
        }
    }

    SectionLabel("Mode")
    Box {
        OutlinedButton(onClick = { modeOpen = true }) { Text(decoration.mode.name) }
        DropdownMenu(expanded = modeOpen, onDismissRequest = { modeOpen = false }) {
            MediaDecorationMode.entries.forEach { m ->
                DropdownMenuItem(
                    text = { Text(m.name) },
                    onClick = { onChange(decoration.copy(mode = m)); modeOpen = false },
                )
            }
        }
    }

    if (decoration.mode == MediaDecorationMode.NineSlice) {
        Text(
            text = "Slice border marks the fixed corner zone (% of the asset edge). " +
                "Corners keep their shape; the edges between them stretch to fit any " +
                "media proportion.",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(vertical = 4.dp),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = sliceAsymmetric, onCheckedChange = { sliceAsymmetric = it })
            Text("Edit each edge separately")
        }
        if (sliceAsymmetric) {
            FourPercentFields(
                left = decoration.sliceLeft, top = decoration.sliceTop,
                right = decoration.sliceRight, bottom = decoration.sliceBottom,
                onLeft = { onChange(decoration.copy(sliceLeft = it)) },
                onTop = { onChange(decoration.copy(sliceTop = it)) },
                onRight = { onChange(decoration.copy(sliceRight = it)) },
                onBottom = { onChange(decoration.copy(sliceBottom = it)) },
            )
        } else {
            PercentField(
                label = "Slice border",
                fraction = decoration.sliceLeft,
                onFraction = {
                    onChange(
                        decoration.copy(sliceLeft = it, sliceTop = it, sliceRight = it, sliceBottom = it),
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    SectionLabel("Opacity: ${"%.2f".format(decoration.opacity)}")
    Slider(
        value = decoration.opacity.coerceIn(0f, 1f),
        onValueChange = { onChange(decoration.copy(opacity = it)) },
        valueRange = 0f..1f,
    )
}

private fun <T> List<T>.swapped(a: Int, b: Int): List<T> =
    toMutableList().also { val tmp = it[a]; it[a] = it[b]; it[b] = tmp }

/** Thumbnail + Pick/Replace/Clear row over a content-URI string. Shared by the decoration asset + opening-mask pickers. */
@Composable
private fun AssetPickerRow(
    label: String,
    uri: String,
    onPick: () -> Unit,
    onClear: (() -> Unit)? = null,
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
        if (uri.isNotBlank() && onClear != null) {
            Spacer(Modifier.width(8.dp))
            TextButton(onClick = onClear) { Text("Clear") }
        }
    }
}

/** True when the four slice insets aren't all equal — defaults the slice asymmetric toggle. */
private fun MediaDecoration.isSliceAsymmetric(): Boolean =
    !(sliceLeft == sliceTop && sliceTop == sliceRight && sliceRight == sliceBottom)

/** True when opening insets break left==right / top==bottom — defaults the opening asymmetric toggle. */
private fun MediaOpening?.isAsymmetric(): Boolean {
    this ?: return false
    return !(insetLeft == insetRight && insetTop == insetBottom)
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

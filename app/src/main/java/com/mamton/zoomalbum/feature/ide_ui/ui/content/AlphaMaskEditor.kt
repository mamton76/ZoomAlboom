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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.mamton.zoomalbum.domain.model.AlphaMask
import com.mamton.zoomalbum.domain.model.AlphaMaskSource
import com.mamton.zoomalbum.domain.model.AlphaStop
import com.mamton.zoomalbum.domain.model.MaskChannel
import com.mamton.zoomalbum.domain.model.MaskFitMode
import com.mamton.zoomalbum.domain.model.ProceduralPattern

/**
 * Per-concept editor body for [AlphaMask]. Source-type picker first (including
 * a `None` option that means "no mask"), then the per-source sub-editor, then
 * the [AlphaMask.invert] toggle. Mirrors `BackgroundEditor`'s
 * "None / Solid / Texture / Procedural" pattern — `None` is the off state
 * instead of a separate enable checkbox.
 *
 * Calls [onChange] on every tweak; emits `null` while the source is `None`.
 *
 * The Image source uses the SAF document picker
 * ([ActivityResultContracts.OpenDocument]) with an `"image/.*"` MIME filter
 * (rather than `PickVisualMedia`) so that PNGs in `/Downloads` / app folders
 * — common locations for mask assets exported from a design tool — are
 * reachable. Once `todo.md § 1.4` (media-library registry) ships, the
 * resolved content URI will deduplicate against other media + mask assets in
 * the registry.
 */
@Composable
fun AlphaMaskEditor(
    initial: AlphaMask?,
    onChange: (AlphaMask?) -> Unit,
) {
    var source by remember { mutableStateOf(initial?.source) }
    var invert by remember { mutableStateOf(initial?.invert ?: false) }

    fun emit() {
        onChange(source?.let { AlphaMask(source = it, invert = invert) })
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        SectionLabel("Source")
        AlphaMaskSourceTypeDropdown(
            current = source,
            onChange = { source = it; emit() },
        )

        val s = source ?: return

        Spacer(Modifier.height(8.dp))

        when (s) {
            is AlphaMaskSource.Image -> ImageMaskSourceEditor(s) { source = it; emit() }
            is AlphaMaskSource.LinearGradient -> LinearGradientMaskEditor(s) { source = it; emit() }
            is AlphaMaskSource.RadialGradient -> RadialGradientMaskEditor(s) { source = it; emit() }
            is AlphaMaskSource.Procedural -> ProceduralMaskSourceEditor(s) { source = it; emit() }
        }

        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = invert, onCheckedChange = { invert = it; emit() })
            Text("Invert (flip opaque ↔ transparent)")
        }
    }
}

// ── Source-type picker ──────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AlphaMaskSourceTypeDropdown(
    current: AlphaMaskSource?,
    onChange: (AlphaMaskSource?) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { open = true }) { Text(current.label()) }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            SOURCE_FACTORIES.forEach { (label, factory) ->
                DropdownMenuItem(
                    text = { Text(label) },
                    onClick = {
                        open = false
                        if (current.label() != label) onChange(factory())
                    },
                )
            }
        }
    }
}

private fun AlphaMaskSource?.label(): String = when (this) {
    null -> "None"
    is AlphaMaskSource.Image -> "Image"
    is AlphaMaskSource.LinearGradient -> "Linear gradient"
    is AlphaMaskSource.RadialGradient -> "Radial gradient"
    is AlphaMaskSource.Procedural -> "Procedural"
}

private val SOURCE_FACTORIES: List<Pair<String, () -> AlphaMaskSource?>> = listOf(
    "None" to { null },
    "Image" to { AlphaMaskSource.Image(maskRefId = "") },
    "Linear gradient" to {
        AlphaMaskSource.LinearGradient(
            angleDeg = 0f,
            stops = listOf(AlphaStop(0f, 1f), AlphaStop(1f, 0f)),
        )
    },
    "Radial gradient" to {
        AlphaMaskSource.RadialGradient(
            stops = listOf(AlphaStop(0f, 1f), AlphaStop(1f, 0f)),
        )
    },
    "Procedural" to { AlphaMaskSource.Procedural(pattern = ProceduralPattern.PaperGrain()) },
)

// ── Image source ────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ImageMaskSourceEditor(
    source: AlphaMaskSource.Image,
    onChange: (AlphaMaskSource.Image) -> Unit,
) {
    val context = LocalContext.current
    // SAF document picker rather than PickVisualMedia: mask PNGs are often
    // exported to /Downloads or app folders that aren't indexed by MediaStore,
    // so the Photo Picker would hide them. OpenDocument browses the whole
    // SAF tree. The MIME filter is "image/*" so the picker still scopes to
    // images, but anything reachable via a content provider shows up.
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
        onChange(source.copy(maskRefId = uri.toString()))
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        SectionLabel("Mask image")
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFF2A2A2A))
                    .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(6.dp)),
                contentAlignment = Alignment.Center,
            ) {
                if (source.maskRefId.isNotBlank()) {
                    AsyncImage(
                        model = source.maskRefId,
                        contentDescription = null,
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    Text("∅", style = MaterialTheme.typography.titleLarge)
                }
            }
            Spacer(Modifier.width(12.dp))
            OutlinedButton(
                onClick = { picker.launch(arrayOf("image/*")) },
            ) { Text(if (source.maskRefId.isBlank()) "Pick image" else "Replace") }
            if (source.maskRefId.isNotBlank()) {
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = { onChange(source.copy(maskRefId = "")) }) { Text("Clear") }
            }
        }
        Spacer(Modifier.height(8.dp))

        SectionLabel("Channel")
        EnumDropdown(
            current = source.channel,
            options = MaskChannel.entries.toList(),
            label = { it.label() },
            onChange = { onChange(source.copy(channel = it)) },
        )
        Spacer(Modifier.height(8.dp))

        SectionLabel("Fit mode")
        EnumDropdown(
            current = source.fitMode,
            options = MaskFitMode.entries.toList(),
            label = { it.label() },
            onChange = { onChange(source.copy(fitMode = it)) },
        )
    }
}

private fun MaskChannel.label(): String = when (this) {
    MaskChannel.Luminance -> "Luminance (BW image)"
    MaskChannel.Alpha -> "Alpha (transparent image)"
}

private fun MaskFitMode.label(): String = when (this) {
    MaskFitMode.Stretch -> "Stretch"
    MaskFitMode.Fit -> "Fit (letterbox)"
    MaskFitMode.Fill -> "Fill (crop)"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> EnumDropdown(
    current: T,
    options: List<T>,
    label: (T) -> String,
    onChange: (T) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { open = true }) { Text(label(current)) }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(label(option)) },
                    onClick = {
                        open = false
                        if (option != current) onChange(option)
                    },
                )
            }
        }
    }
}

// ── Linear gradient ────────────────────────────────────────────────────────

@Composable
private fun LinearGradientMaskEditor(
    source: AlphaMaskSource.LinearGradient,
    onChange: (AlphaMaskSource.LinearGradient) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        SectionLabel("Angle: ${"%.0f".format(source.angleDeg)}°")
        Slider(
            value = source.angleDeg,
            onValueChange = { onChange(source.copy(angleDeg = it)) },
            valueRange = -180f..180f,
        )
        Spacer(Modifier.height(8.dp))
        AlphaStopListEditor(
            stops = source.stops,
            onChange = { onChange(source.copy(stops = it)) },
        )
    }
}

// ── Radial gradient ─────────────────────────────────────────────────────────

@Composable
private fun RadialGradientMaskEditor(
    source: AlphaMaskSource.RadialGradient,
    onChange: (AlphaMaskSource.RadialGradient) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        SectionLabel("Center X: ${"%.2f".format(source.centerX)}")
        Slider(
            value = source.centerX,
            onValueChange = { onChange(source.copy(centerX = it)) },
            valueRange = 0f..1f,
        )
        SectionLabel("Center Y: ${"%.2f".format(source.centerY)}")
        Slider(
            value = source.centerY,
            onValueChange = { onChange(source.copy(centerY = it)) },
            valueRange = 0f..1f,
        )
        SectionLabel("Radius X: ${"%.2f".format(source.radiusX)}")
        Slider(
            value = source.radiusX,
            onValueChange = { onChange(source.copy(radiusX = it)) },
            valueRange = 0.01f..1f,
        )
        SectionLabel("Radius Y: ${"%.2f".format(source.radiusY)}")
        Slider(
            value = source.radiusY,
            onValueChange = { onChange(source.copy(radiusY = it)) },
            valueRange = 0.01f..1f,
        )
        Spacer(Modifier.height(8.dp))
        AlphaStopListEditor(
            stops = source.stops,
            onChange = { onChange(source.copy(stops = it)) },
        )
    }
}

// ── Procedural ──────────────────────────────────────────────────────────────

@Composable
private fun ProceduralMaskSourceEditor(
    source: AlphaMaskSource.Procedural,
    onChange: (AlphaMaskSource.Procedural) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        SectionLabel("Pattern")
        PatternTypeDropdown(
            current = source.pattern,
            onChange = { onChange(source.copy(pattern = it)) },
        )
        Spacer(Modifier.height(8.dp))
        ProceduralPatternEditor(
            pattern = source.pattern,
            onChange = { onChange(source.copy(pattern = it)) },
        )
    }
}

// ── Shared: AlphaStop list editor ───────────────────────────────────────────

@Composable
private fun AlphaStopListEditor(
    stops: List<AlphaStop>,
    onChange: (List<AlphaStop>) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        SectionLabel("Stops")
        stops.forEachIndexed { index, stop ->
            AlphaStopRow(
                stop = stop,
                canRemove = stops.size > 2,
                onValueChange = { updated ->
                    onChange(stops.toMutableList().also { it[index] = updated })
                },
                onRemove = {
                    onChange(stops.toMutableList().also { it.removeAt(index) })
                },
            )
            Spacer(Modifier.height(4.dp))
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            horizontalArrangement = Arrangement.Start,
        ) {
            TextButton(
                onClick = {
                    val newPos = stops.lastOrNull()?.position?.coerceAtMost(0.9f)?.let { it + 0.1f } ?: 0.5f
                    onChange(stops + AlphaStop(position = newPos.coerceIn(0f, 1f), alpha = 1f))
                },
            ) { Text("+ Add stop") }
        }
    }
}

@Composable
private fun AlphaStopRow(
    stop: AlphaStop,
    canRemove: Boolean,
    onValueChange: (AlphaStop) -> Unit,
    onRemove: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text("Position: ${"%.2f".format(stop.position)} • Alpha: ${"%.2f".format(stop.alpha)}")
        Slider(
            value = stop.position,
            onValueChange = { onValueChange(stop.copy(position = it)) },
            valueRange = 0f..1f,
        )
        Slider(
            value = stop.alpha,
            onValueChange = { onValueChange(stop.copy(alpha = it)) },
            valueRange = 0f..1f,
        )
        if (canRemove) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onRemove) { Text("Remove stop") }
            }
        }
    }
}

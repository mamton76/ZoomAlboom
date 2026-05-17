package com.mamton.zoomalbum.feature.ide_ui.ui.content

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.graphics.toColorInt
import coil3.compose.AsyncImage
import com.mamton.zoomalbum.domain.model.AlbumBackground
import com.mamton.zoomalbum.domain.model.AnchorMode
import com.mamton.zoomalbum.domain.model.BackgroundData
import com.mamton.zoomalbum.domain.model.ProceduralPattern
import com.mamton.zoomalbum.domain.model.TileData
import com.mamton.zoomalbum.domain.model.TileMode
import com.mamton.zoomalbum.feature.ide_ui.ui.color.ColorPicker
import com.mamton.zoomalbum.feature.ide_ui.ui.color.toHex
import kotlin.math.roundToInt

@Composable
fun BackgroundEditorContent(
    initialData: BackgroundData?,
    initialAnchorMode: AnchorMode,
    onDismiss: () -> Unit,
    onApply: (AlbumBackground?) -> Unit,
) {
    // Wrapper owns the state. Earlier this used plain `var data = data` locals
    // which weren't Compose state, so the anchor toggle never recomposed and
    // the apply emit captured stale values across recomposition.
    var data by remember { mutableStateOf(initialData) }
    var anchorMode by remember { mutableStateOf(initialAnchorMode) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        Text(
            text = "Album background",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(vertical = 8.dp),
        )

        BackgroundEditor(
            initial = data,
            onValueChange = { data = it },
            tileSizeUnitLabel = when (anchorMode) {
                AnchorMode.CameraLocked -> "screen px"
                AnchorMode.WorldLocked -> "world units"
            },
        )

        if (data != null) {
            SectionLabel("Anchor")
            Row {
                listOf(
                    AnchorMode.CameraLocked to "Camera-locked",
                    AnchorMode.WorldLocked to "World-locked",
                ).forEach { (m, label) ->
                    val selected = anchorMode == m
                    OutlinedButton(
                        onClick = { anchorMode = m },
                        border = if (selected) {
                            BorderStroke(
                                2.dp,
                                MaterialTheme.colorScheme.primary,
                            )
                        } else null,
                        modifier = Modifier.padding(end = 8.dp),
                    ) { Text(label) }
                }
            }
        }

        Spacer(Modifier.height(20.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            TextButton(onClick = onDismiss) { Text("Cancel") }
            Spacer(Modifier.width(8.dp))
            Button(onClick = {
                val emitted: AlbumBackground? = data?.let { d ->
                    AlbumBackground(data = d, anchorMode = anchorMode)
                }
                onApply(emitted)
            }) { Text("Apply") }
        }
        Spacer(Modifier.height(16.dp))
    }
}

/**
 * Shared background editor: source radio + per-source controls + opacity slider.
 * Emits a [BackgroundData]? (null when "None" is selected) via [onValueChange]
 * on every tweak.
 *
 * Stateful: holds per-source drafts so switching radio doesn't wipe the user's
 * earlier choices. The drafts are local to a single invocation — closing and
 * reopening the sheet starts fresh from [initial].
 *
 * @param choices subset of [BackgroundSourceChoice] to expose. Default: all four.
 *   Useful e.g. if a future caller wants "Solid only".
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackgroundEditor(
    initial: BackgroundData?,
    onValueChange: (BackgroundData?) -> Unit,
    modifier: Modifier = Modifier,
    choices: List<BackgroundSourceChoice> = BackgroundSourceChoice.entries,
    // Suffix shown next to the tile-size slider (e.g. "screen px", "world units").
    // Tile-size numbers are interpreted in whatever space the renderer is drawing
    // into — the editor itself doesn't know, so the caller names the unit.
    tileSizeUnitLabel: String? = null,
) {
    val context = LocalContext.current

    var choice by remember { mutableStateOf(initial.toChoice()) }
    var solidBackgroundDataColor by remember {
        mutableStateOf(
            parseHexOrBlack((initial as? BackgroundData.SolidBackgroundData)?.color ?: "#000000"),
        )
    }
    var textureBackgroundDataRefId by remember {
        mutableStateOf((initial as? BackgroundData.TextureBackgroundData)?.textureRefId)
    }
    var tileMode by remember {
        // Default to Repeat so a fresh world-locked texture tiles across the canvas
        // rather than appearing only inside a tiny anchored world rect.
        mutableStateOf(
            (initial as? BackgroundData.TextureBackgroundData)?.tile?.tileMode ?: TileMode.Repeat
        )
    }
    var tileSize by remember {
        mutableFloatStateOf(
            (initial as? BackgroundData.TextureBackgroundData)?.tile?.tileWidth ?: 200f
        )
    }
    var proceduralBackgroundData by remember {
        mutableStateOf<ProceduralPattern>(
            (initial as? BackgroundData.ProceduralBackgroundData)?.pattern ?: ProceduralPattern.Grid(),
        )
    }
    // Optional solid fill behind the procedural pattern. null = no fill (default,
    // current behavior). Editor exposes a checkbox + ColorPicker; when the box is
    // unchecked we drop the color but remember the user's last picked value so it
    // re-applies if they toggle it back on.
    var proceduralFillEnabled by remember {
        mutableStateOf((initial as? BackgroundData.ProceduralBackgroundData)?.fillColor != null)
    }
    var proceduralFillColor by remember {
        mutableStateOf(
            parseHexOrBlack((initial as? BackgroundData.ProceduralBackgroundData)?.fillColor ?: "#FFFFFF"),
        )
    }
    var opacity by remember { mutableFloatStateOf(initial?.opacity ?: 1f) }

    fun emit() {
        val result: BackgroundData? = when (choice) {
            BackgroundSourceChoice.None -> null
            BackgroundSourceChoice.Solid -> BackgroundData.SolidBackgroundData(
                color = toHex(solidBackgroundDataColor.copy(alpha = 1f)),
                opacity = opacity,
            )

            BackgroundSourceChoice.Texture -> {
                val ref = textureBackgroundDataRefId
                if (ref.isNullOrBlank()) null
                else BackgroundData.TextureBackgroundData(
                    textureRefId = ref,
                    tile = TileData(
                        tileMode = tileMode,
                        tileWidth = tileSize,
                        tileHeight = tileSize,
                    ),
                    opacity = opacity,
                )
            }

            BackgroundSourceChoice.Procedural -> BackgroundData.ProceduralBackgroundData(
                pattern = proceduralBackgroundData,
                fillColor = if (proceduralFillEnabled) toHex(proceduralFillColor) else null,
                opacity = opacity,
            )
        }
        onValueChange(result)
    }

    val texturePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
        textureBackgroundDataRefId = uri.toString()
        emit()
    }

    Column(modifier = modifier.fillMaxWidth()) {
        // Source radio
        SectionLabel("Source")
        Row(verticalAlignment = Alignment.CenterVertically) {
            choices.forEach { c ->
                RadioButton(
                    selected = choice == c,
                    onClick = { choice = c; emit() },
                )
                Text(c.label(), modifier = Modifier.padding(end = 12.dp))
            }
        }

        when (choice) {
            BackgroundSourceChoice.None -> { /* nothing */
            }

            BackgroundSourceChoice.Solid -> {
                Spacer(Modifier.height(8.dp))
                SectionLabel("Color")
                ColorPicker(
                    initial = solidBackgroundDataColor,
                    onChange = { solidBackgroundDataColor = it; emit() },
                )
            }

            BackgroundSourceChoice.Texture -> {
                Spacer(Modifier.height(8.dp))
                SectionLabel("Texture")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFF2A2A2A))
                            .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(6.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        val ref = textureBackgroundDataRefId
                        if (ref != null) {
                            AsyncImage(
                                model = ref,
                                contentDescription = null,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        } else {
                            Text("∅", style = MaterialTheme.typography.titleLarge)
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    OutlinedButton(
                        onClick = {
                            texturePicker.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                            )
                        },
                    ) { Text(if (textureBackgroundDataRefId == null) "Pick image" else "Replace") }
                    if (textureBackgroundDataRefId != null) {
                        Spacer(Modifier.width(8.dp))
                        TextButton(onClick = {
                            textureBackgroundDataRefId = null; emit()
                        }) { Text("Clear") }
                    }
                }

                Spacer(Modifier.height(12.dp))
                SectionLabel("Tile mode")
                TileModeDropdown(tileMode = tileMode, onChange = { tileMode = it; emit() })

                if (tileMode == TileMode.Repeat) {
                    Spacer(Modifier.height(8.dp))
                    val unitSuffix = tileSizeUnitLabel?.let { " $it" } ?: ""
                    Text("Tile size: ${tileSize.roundToInt()}$unitSuffix")
                    Slider(
                        value = tileSize,
                        onValueChange = { tileSize = it; emit() },
                        valueRange = 50f..10000f,
                    )
                }
            }

            BackgroundSourceChoice.Procedural -> {
                Spacer(Modifier.height(8.dp))
                SectionLabel("Pattern type")
                PatternTypeDropdown(current = proceduralBackgroundData, onChange = { proceduralBackgroundData = it; emit() })
                Spacer(Modifier.height(4.dp))
                ProceduralPatternEditor(
                    pattern = proceduralBackgroundData,
                    onChange = { proceduralBackgroundData = it; emit() })

                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = proceduralFillEnabled,
                        onCheckedChange = { proceduralFillEnabled = it; emit() },
                    )
                    Text("Solid fill behind pattern")
                }
                if (proceduralFillEnabled) {
                    SectionLabel("Fill color")
                    ColorPicker(
                        initial = proceduralFillColor,
                        onChange = { proceduralFillColor = it; emit() },
                    )
                }
            }
        }

        if (choice != BackgroundSourceChoice.None) {
            Spacer(Modifier.height(12.dp))
            Text("Opacity: ${"%.2f".format(opacity)}")
            Slider(value = opacity, onValueChange = { opacity = it; emit() }, valueRange = 0f..1f)
        }
    }
}

/** Local "source" enum — None is a UI-only choice that collapses to `null` on emit. */
enum class BackgroundSourceChoice { None, Solid, Texture, Procedural }


private fun BackgroundSourceChoice.label(): String = when (this) {
    BackgroundSourceChoice.None -> "None"
    BackgroundSourceChoice.Solid -> "Color"
    BackgroundSourceChoice.Texture -> "Texture"
    BackgroundSourceChoice.Procedural -> "Pattern"
}

private fun BackgroundData?.toChoice(): BackgroundSourceChoice = when (this) {
    null -> BackgroundSourceChoice.None
    is BackgroundData.SolidBackgroundData -> BackgroundSourceChoice.Solid
    is BackgroundData.TextureBackgroundData -> BackgroundSourceChoice.Texture
    is BackgroundData.ProceduralBackgroundData -> BackgroundSourceChoice.Procedural
}

private fun parseHexOrBlack(hex: String): Color =
    runCatching { Color(hex.toColorInt()) }.getOrNull() ?: Color.Black

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TileModeDropdown(tileMode: TileMode, onChange: (TileMode) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { open = true }) { Text(tileMode.name) }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            TileMode.entries.forEach { m ->
                DropdownMenuItem(
                    text = { Text(m.name) },
                    onClick = { onChange(m); open = false },
                )
            }
        }
    }
}

@Composable
internal fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        modifier = Modifier.padding(bottom = 4.dp, top = 8.dp),
    )
}

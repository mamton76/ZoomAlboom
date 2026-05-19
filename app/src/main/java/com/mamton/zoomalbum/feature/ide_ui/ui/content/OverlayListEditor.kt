package com.mamton.zoomalbum.feature.ide_ui.ui.content

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mamton.zoomalbum.domain.model.BackgroundData
import com.mamton.zoomalbum.domain.model.NodeBlendMode
import com.mamton.zoomalbum.domain.model.OverlaySource
import com.mamton.zoomalbum.domain.model.OverlayStyle

/**
 * Editor for an ordered `List<OverlayStyle>` — the shape carried by
 * `NodeAppearance.overlays` (inherited by both `MediaAppearance` and
 * `FrameAppearance`).
 *
 * Compositing is declaration-order: entry `[i]` draws above entry `[i-1]`.
 * The UI exposes per-entry source / opacity / blend mode, plus up / down /
 * remove controls and an "Add overlay" button.
 *
 * Source picker reuses [BackgroundEditor] with `choices` restricted to
 * Solid + Procedural — Texture overlays are intentionally hidden until the
 * bitmap loader behind `rememberOverlayTextureBitmaps` ships. Texture-source
 * entries that round-trip through the model still appear (rendered as
 * placeholders by the renderer) but the editor doesn't let users *create* one.
 */
@Composable
fun OverlayListEditor(
    overlays: List<OverlayStyle>,
    onChange: (List<OverlayStyle>) -> Unit,
    modifier: Modifier = Modifier,
    tileSizeUnitLabel: String? = null,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        SectionLabel("Content overlays")

        if (overlays.isEmpty()) {
            Text(
                text = "No overlays. Add one below to tint, texture, or pattern this frame's contents.",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(vertical = 4.dp),
            )
        }

        overlays.forEachIndexed { index, overlay ->
            OverlayEntryCard(
                index = index,
                total = overlays.size,
                overlay = overlay,
                tileSizeUnitLabel = tileSizeUnitLabel,
                onUpdate = { next -> onChange(overlays.replaceAt(index, next)) },
                onRemove = { onChange(overlays.removeAt(index)) },
                onMoveUp = { if (index > 0) onChange(overlays.swap(index, index - 1)) },
                onMoveDown = { if (index < overlays.lastIndex) onChange(overlays.swap(index, index + 1)) },
            )
            Spacer(Modifier.height(8.dp))
        }

        OutlinedButton(
            onClick = { onChange(overlays + defaultOverlay()) },
            modifier = Modifier.padding(top = 4.dp),
        ) { Text("+ Add overlay") }
    }
}

@Composable
private fun OverlayEntryCard(
    index: Int,
    total: Int,
    overlay: OverlayStyle,
    tileSizeUnitLabel: String?,
    onUpdate: (OverlayStyle) -> Unit,
    onRemove: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Layer ${index + 1} of $total",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                IconButton(
                    onClick = onMoveUp,
                    enabled = index > 0,
                ) { Text("↑") }
                IconButton(
                    onClick = onMoveDown,
                    enabled = index < total - 1,
                ) { Text("↓") }
                IconButton(onClick = onRemove) { Text("✕") }
            }

            // Source picker — reuses BackgroundEditor by round-tripping
            // OverlaySource ↔ BackgroundData. Texture is enabled because
            // rememberOverlayTextureBitmaps now loads bitmaps via Coil.
            BackgroundEditor(
                initial = overlay.source.toBackgroundData(),
                onValueChange = { bgData ->
                    val nextSource = bgData?.toOverlaySource() ?: overlay.source
                    onUpdate(overlay.copy(source = nextSource))
                },
                choices = listOf(
                    BackgroundSourceChoice.Solid,
                    BackgroundSourceChoice.Texture,
                    BackgroundSourceChoice.Procedural,
                ),
                tileSizeUnitLabel = tileSizeUnitLabel,
            )

            SectionLabel("Opacity")
            Slider(
                value = overlay.opacity.coerceIn(0f, 1f),
                onValueChange = { onUpdate(overlay.copy(opacity = it)) },
                valueRange = 0f..1f,
            )

            SectionLabel("Blend mode")
            BlendModeDropdown(
                value = overlay.blendMode,
                onChange = { onUpdate(overlay.copy(blendMode = it)) },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BlendModeDropdown(
    value: NodeBlendMode,
    onChange: (NodeBlendMode) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { open = true }) { Text(value.name) }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            NodeBlendMode.entries.forEach { mode ->
                DropdownMenuItem(
                    text = { Text(mode.name) },
                    onClick = {
                        onChange(mode)
                        open = false
                    },
                )
            }
        }
    }
}

// ── OverlaySource ↔ BackgroundData round-trip ─────────────────────────────────
// The two families mirror each other intentionally; the renderer's
// `drawOverlayStack` already routes overlays through `drawBackgroundData` via
// the same mapping. Editor reuses it so we don't ship a second editor.

private fun OverlaySource.toBackgroundData(): BackgroundData = when (this) {
    is OverlaySource.SolidColor ->
        BackgroundData.SolidBackgroundData(color = color, opacity = 1f)
    is OverlaySource.Texture ->
        BackgroundData.TextureBackgroundData(textureRefId = textureRefId, tile = tile, opacity = 1f)
    is OverlaySource.Procedural ->
        BackgroundData.ProceduralBackgroundData(pattern = pattern, fillColor = fillColor, opacity = 1f)
}

private fun BackgroundData.toOverlaySource(): OverlaySource = when (this) {
    is BackgroundData.SolidBackgroundData -> OverlaySource.SolidColor(color = color)
    is BackgroundData.TextureBackgroundData ->
        OverlaySource.Texture(textureRefId = textureRefId, tile = tile)
    is BackgroundData.ProceduralBackgroundData ->
        OverlaySource.Procedural(pattern = pattern, fillColor = fillColor)
}

private fun defaultOverlay(): OverlayStyle = OverlayStyle(
    source = OverlaySource.SolidColor(color = "#40FFFFFF"),
    opacity = 0.25f,
    blendMode = NodeBlendMode.SoftLight,
)

private fun <T> List<T>.replaceAt(index: Int, value: T): List<T> =
    toMutableList().also { it[index] = value }

private fun <T> List<T>.removeAt(index: Int): List<T> =
    toMutableList().also { it.removeAt(index) }

private fun <T> List<T>.swap(i: Int, j: Int): List<T> =
    toMutableList().also { val tmp = it[i]; it[i] = it[j]; it[j] = tmp }

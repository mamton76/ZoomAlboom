package com.mamton.zoomalbum.feature.ide_ui.ui.sheets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.mamton.zoomalbum.domain.model.AppearanceSection
import com.mamton.zoomalbum.domain.model.MediaAppearance
import com.mamton.zoomalbum.domain.model.MediaStylePreset
import com.mamton.zoomalbum.feature.ide_ui.ui.content.AlphaMaskEditor
import com.mamton.zoomalbum.feature.ide_ui.ui.content.BorderStyleEditor
import com.mamton.zoomalbum.feature.ide_ui.ui.content.CaptionStyleEditor
import com.mamton.zoomalbum.feature.ide_ui.ui.content.ColorAdjustmentsEditor
import com.mamton.zoomalbum.feature.ide_ui.ui.content.CornerRadiusSlider
import com.mamton.zoomalbum.feature.ide_ui.ui.content.CropEditor
import com.mamton.zoomalbum.feature.ide_ui.ui.content.DecorationListEditor
import com.mamton.zoomalbum.feature.ide_ui.ui.content.MediaOpeningEditor
import com.mamton.zoomalbum.feature.ide_ui.ui.content.OpacitySlider
import com.mamton.zoomalbum.feature.ide_ui.ui.content.OverlayListEditor
import com.mamton.zoomalbum.feature.ide_ui.ui.content.ShadowStyleEditor

/**
 * Aggregate editor for a [MediaStylePreset] definition: name + per-section
 * governs-checkbox + (when governed) the matching concept editor, all bound to a
 * working [MediaAppearance]. Reuses the existing per-concept editors. Saving an
 * edited preset propagates to every bound node (resolution re-runs on the store
 * flow). See `media-presets.md`.
 */
@Composable
fun PresetDefinitionEditor(
    preset: MediaStylePreset,
    onSave: (MediaStylePreset) -> Unit,
    onCancel: () -> Unit,
) {
    var name by remember { mutableStateOf(preset.name) }
    var working by remember { mutableStateOf(preset.appearance) }
    var sections by remember { mutableStateOf(preset.sections) }

    fun toggle(section: AppearanceSection, on: Boolean) {
        sections = if (on) sections + section else sections - section
    }

    Dialog(onDismissRequest = onCancel) {
        Surface(shape = MaterialTheme.shapes.large, tonalElevation = 4.dp) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 560.dp)
                    .padding(16.dp),
            ) {
                Text("Edit preset", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    "Tick the sections this preset controls; untick to leave a target's own value.",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(vertical = 4.dp),
                )

                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState()),
                ) {
                    Section("Opacity", AppearanceSection.Opacity, sections, ::toggle) {
                        OpacitySlider(working.opacity) { working = working.copy(opacity = it) }
                    }
                    Section("Corner radius", AppearanceSection.CornerRadius, sections, ::toggle) {
                        CornerRadiusSlider(
                            value = working.cornerRadius,
                            onChange = { working = working.copy(cornerRadius = it) },
                        )
                    }
                    Section("Crop", AppearanceSection.Crop, sections, ::toggle) {
                        CropEditor(working.crop) { working = working.copy(crop = it) }
                    }
                    Section("Color adjustments", AppearanceSection.ColorAdjustments, sections, ::toggle) {
                        ColorAdjustmentsEditor(working.colorAdjustments) { working = working.copy(colorAdjustments = it) }
                    }
                    Section("Overlays", AppearanceSection.Overlays, sections, ::toggle) {
                        OverlayListEditor(working.overlays, { working = working.copy(overlays = it) })
                    }
                    Section("Content mask", AppearanceSection.ContentMask, sections, ::toggle) {
                        AlphaMaskEditor(working.contentMask) { working = working.copy(contentMask = it) }
                    }
                    Section("Opening", AppearanceSection.Opening, sections, ::toggle) {
                        MediaOpeningEditor(working.opening) { working = working.copy(opening = it) }
                    }
                    Section("Decorations", AppearanceSection.Decorations, sections, ::toggle) {
                        DecorationListEditor(working.decorations) { working = working.copy(decorations = it) }
                    }
                    Section("Border", AppearanceSection.Border, sections, ::toggle) {
                        BorderStyleEditor(working.border) { working = working.copy(border = it) }
                    }
                    Section("Shadow", AppearanceSection.Shadow, sections, ::toggle) {
                        ShadowStyleEditor(working.shadow) { working = working.copy(shadow = it) }
                    }
                    Section("Caption", AppearanceSection.Caption, sections, ::toggle) {
                        CaptionStyleEditor(working.caption) { working = working.copy(caption = it) }
                    }
                }

                Spacer(Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onCancel) { Text("Cancel") }
                    Button(onClick = {
                        onSave(
                            preset.copy(
                                name = name.ifBlank { preset.name },
                                appearance = working,
                                sections = sections,
                            ),
                        )
                    }) { Text("Save") }
                }
            }
        }
    }
}

@Composable
private fun Section(
    label: String,
    section: AppearanceSection,
    sections: Set<AppearanceSection>,
    onToggle: (AppearanceSection, Boolean) -> Unit,
    content: @Composable () -> Unit,
) {
    val governed = section in sections
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = governed, onCheckedChange = { onToggle(section, it) })
            Text(label, style = MaterialTheme.typography.titleSmall)
        }
        if (governed) {
            Column(modifier = Modifier.padding(start = 12.dp, bottom = 8.dp)) { content() }
        }
    }
}

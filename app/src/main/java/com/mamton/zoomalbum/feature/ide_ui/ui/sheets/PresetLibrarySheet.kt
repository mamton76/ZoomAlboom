package com.mamton.zoomalbum.feature.ide_ui.ui.sheets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import com.mamton.zoomalbum.domain.model.MediaStylePreset

/**
 * App-level preset library for the current media selection. Slice 1 UI: save the
 * selection as a preset, and per-preset Apply / Duplicate / Delete. Card previews
 * (rendering the preset on the selection's media) + inherited/overridden styling
 * are Slice 1b — here a card shows the name + governed-section count.
 *
 * `Apply` over a node that already has overrides prompts Replace-vs-Keep.
 */
@Composable
fun PresetLibrarySheet(
    presets: List<MediaStylePreset>,
    targetCount: Int,
    targetBound: Boolean,
    hasOverrides: Boolean,
    previewUri: String?,
    onApply: (presetId: String, keepOverrides: Boolean) -> Unit,
    onSaveAs: (name: String) -> Unit,
    onDuplicate: (presetId: String) -> Unit,
    onDelete: (presetId: String) -> Unit,
    onEdit: (MediaStylePreset) -> Unit,
    onUnlink: () -> Unit,
    onDismiss: () -> Unit,
) {
    var pendingApplyId by remember { mutableStateOf<String?>(null) }
    var editingPreset by remember { mutableStateOf<MediaStylePreset?>(null) }
    var newName by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = MaterialTheme.shapes.large, tonalElevation = 4.dp) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Text("Presets", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))

                // Save the current selection as a new preset.
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        label = { Text("New preset name") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                    OutlinedButton(
                        enabled = targetCount >= 1,
                        onClick = { onSaveAs(newName); newName = "" },
                    ) { Text("Save") }
                }

                Spacer(Modifier.height(12.dp))

                if (presets.isEmpty()) {
                    Text(
                        "No presets yet. Style a photo, then Save it here.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                } else {
                    presets.forEach { preset ->
                        Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (!previewUri.isNullOrBlank()) {
                                        MediaPresetPreview(previewUri, preset.appearance, 64.dp)
                                        Spacer(Modifier.width(12.dp))
                                    }
                                    Column {
                                        Text(preset.name, style = MaterialTheme.typography.titleSmall)
                                        Text(
                                            "${preset.sections.size} section(s)",
                                            style = MaterialTheme.typography.bodySmall,
                                        )
                                    }
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    TextButton(
                                        enabled = targetCount >= 1,
                                        onClick = {
                                            if (hasOverrides) pendingApplyId = preset.id
                                            else { onApply(preset.id, false); onDismiss() }
                                        },
                                    ) { Text("Apply") }
                                    TextButton(onClick = { editingPreset = preset }) { Text("Edit") }
                                    TextButton(onClick = { onDuplicate(preset.id) }) { Text("Duplicate") }
                                    TextButton(onClick = { onDelete(preset.id) }) { Text("Delete") }
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    if (targetBound) {
                        TextButton(onClick = { onUnlink(); onDismiss() }) { Text("Unlink preset") }
                    }
                    TextButton(onClick = onDismiss) { Text("Close") }
                }
            }
        }
    }

    val editing = editingPreset
    if (editing != null) {
        PresetDefinitionEditor(
            preset = editing,
            onSave = { onEdit(it); editingPreset = null },
            onCancel = { editingPreset = null },
        )
    }

    val applyId = pendingApplyId
    if (applyId != null) {
        AlertDialog(
            onDismissRequest = { pendingApplyId = null },
            title = { Text("This object has local changes") },
            text = { Text("Replace them with the preset, or keep your changes on top?") },
            confirmButton = {
                TextButton(onClick = {
                    onApply(applyId, false); pendingApplyId = null; onDismiss()
                }) { Text("Replace look") }
            },
            dismissButton = {
                TextButton(onClick = {
                    onApply(applyId, true); pendingApplyId = null; onDismiss()
                }) { Text("Keep my changes") }
            },
        )
    }
}

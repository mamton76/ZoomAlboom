package com.mamton.zoomalbum.feature.ide_ui.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mamton.zoomalbum.feature.ide_ui.viewmodel.IdeAction
import com.mamton.zoomalbum.feature.ide_ui.viewmodel.IdeUiState

private data class PanelDefinition(val id: String, val title: String)

private val availablePanels = listOf(
    PanelDefinition("left_top", "Left Top Panel"),
    PanelDefinition("left_bottom", "Left Bottom Panel"),
    PanelDefinition("right_top", "Right Top Panel"),
    PanelDefinition("right_bottom", "Right Bottom Panel"),
    PanelDefinition("top", "Top Panel"),
    PanelDefinition("bottom", "Bottom Panel"),
)

@Composable
fun PanelConfigDialog(
    state: IdeUiState,
    onAction: (IdeAction) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Panel Configuration") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Enable IDE panels for power-user workflow.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                for (panel in availablePanels) {
                    val enabled = panel.id in state.panelConfig.enabledPanelIds
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = panel.title,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Switch(
                            checked = enabled,
                            onCheckedChange = {
                                onAction(IdeAction.TogglePanelEnabled(panel.id))
                            },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Done") }
        },
        dismissButton = {
            TextButton(onClick = {
                onAction(IdeAction.ResetPanelConfig)
                onDismiss()
            }) {
                Text("Reset")
            }
        },
    )
}

package com.mamton.zoomalbum.feature.ide_ui.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mamton.zoomalbum.domain.model.CanvasNode

/**
 * Dialog shown on long-press when multiple nodes overlap at the same point.
 * Lists each node so the user can pick which one to select.
 */
@Composable
fun OverlapPickerDialog(
    nodes: List<CanvasNode>,
    onSelectNode: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select node") },
        text = {
            Column {
                for (node in nodes) {
                    val label = when (node) {
                        is CanvasNode.Frame -> "Frame: ${node.label.ifEmpty { node.id }}"
                        is CanvasNode.Media -> "Media: ${node.mediaType.name.lowercase()} (${node.id})"
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelectNode(node.id) }
                            .padding(vertical = 10.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val icon = when (node) {
                            is CanvasNode.Frame -> "\u25A1" // □
                            is CanvasNode.Media -> "\u25A0" // ■
                        }
                        Text(icon, style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.width(12.dp))
                        Text(label, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

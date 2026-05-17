package com.mamton.zoomalbum.feature.ide_ui.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
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
import com.mamton.zoomalbum.domain.model.CanvasNode

/**
 * Dialog shown on long-press when multiple nodes overlap at the same point.
 * Lets the user check one or more nodes; OK adds them to the current selection.
 *
 * Long-press is the additive-selection gesture in this codebase, so the picker's
 * confirm path unions into the current selection rather than replacing it.
 */
@Composable
fun OverlapPickerDialog(
    nodes: List<CanvasNode>,
    onConfirm: (Set<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    // Default-check the top-most node (first in the list — `hitTestAll` returns
    // descending z-order). Matches single-hit long-press behaviour: you get the thing
    // visually on top, and can extend the selection by ticking more rows.
    var checked by remember(nodes) {
        mutableStateOf(setOfNotNull(nodes.firstOrNull()?.id))
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add to selection") },
        text = {
            Column {
                for (node in nodes) {
                    val isChecked = node.id in checked
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                checked = if (isChecked) checked - node.id else checked + node.id
                            }
                            .padding(vertical = 6.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = isChecked,
                            onCheckedChange = {
                                checked = if (it) checked + node.id else checked - node.id
                            },
                        )
                        Spacer(Modifier.width(8.dp))
                        when (node) {
                            is CanvasNode.Frame -> FrameNameLabel(
                                frame = node,
                                prefix = "Frame: ",
                            )
                            is CanvasNode.Media -> Text(
                                text = "Media: ${node.mediaType.name.lowercase()} (${node.id})",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = checked.isNotEmpty(),
                onClick = { onConfirm(checked) },
            ) { Text("OK") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

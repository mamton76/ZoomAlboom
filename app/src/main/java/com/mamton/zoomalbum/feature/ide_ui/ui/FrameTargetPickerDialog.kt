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
import androidx.compose.material3.RadioButton
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
import com.mamton.zoomalbum.feature.canvas.viewmodel.CanvasAction

/**
 * User intent captured when the contextual bar's Pin/Detach button is pressed.
 * Carries the action constructor so the scaffold doesn't have to switch on the intent
 * type a second time after the target frame is chosen.
 */
sealed class FrameMembershipIntent(val title: String) {
    abstract fun toAction(frameId: String, nodeIds: Set<String>): CanvasAction

    object Pin : FrameMembershipIntent("Pin to which frame?") {
        override fun toAction(frameId: String, nodeIds: Set<String>) =
            CanvasAction.PinToFrame(frameId, nodeIds)
    }

    object Detach : FrameMembershipIntent("Detach from which frame?") {
        override fun toAction(frameId: String, nodeIds: Set<String>) =
            CanvasAction.DetachFromFrame(frameId, nodeIds)
    }

    object Reset : FrameMembershipIntent("Reset overrides on which frame?") {
        override fun toAction(frameId: String, nodeIds: Set<String>) =
            CanvasAction.ClearFrameOverrides(frameId, nodeIds)
    }
}

/**
 * Dialog shown when the user triggers Pin/Detach with multiple frames in the selection.
 * Lists the selected frames (in selection order) and lets the user pick which one is
 * the target — the chosen frame's `overrides` map will receive the new entries; every
 * other selected node (frame or not) becomes a candidate member.
 *
 * The largest-AABB frame is pre-selected as the discoverable default (matches the
 * "container is the bigger one" mental model).
 */
@Composable
fun FrameTargetPickerDialog(
    title: String,
    frames: List<CanvasNode.Frame>,
    defaultFrameId: String,
    onConfirm: (frameId: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var selectedId by remember { mutableStateOf(defaultFrameId) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                Text(
                    "Pick the target frame. Other selected nodes will be the members.",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                for (frame in frames) {
                    val isSelected = frame.id == selectedId
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedId = frame.id }
                            .padding(vertical = 4.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = isSelected,
                            onClick = { selectedId = frame.id },
                        )
                        Spacer(Modifier.width(4.dp))
                        FrameNameLabel(frame = frame)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(selectedId) }) { Text("OK") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

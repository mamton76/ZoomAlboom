package com.mamton.zoomalbum.feature.ide_ui.ui.content

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.core.graphics.toColorInt
import com.mamton.zoomalbum.domain.model.CanvasNode
import com.mamton.zoomalbum.feature.ide_ui.ui.FrameNameLabel

/**
 * Reusable frame list content — rendered inside both
 * [com.mamton.zoomalbum.feature.ide_ui.ui.panels.FrameListPanel] (docked panel)
 * and [com.mamton.zoomalbum.feature.ide_ui.ui.sheets.FrameListBottomSheet].
 *
 * [visibleFrameIds] — IDs of frames currently visible in the camera viewport.
 * Items whose ID is in the set are highlighted with a colored border.
 */
@Composable
fun FrameListContent(
    frames: List<CanvasNode.Frame>,
    onDeleteFrame: (String) -> Unit,
    modifier: Modifier = Modifier,
    visibleFrameIds: Set<String> = emptySet(),
    onFocusFrame: ((String) -> Unit)? = null,
) {
    if (frames.isEmpty()) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "No frames in this album.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    } else {
        LazyColumn(
            modifier = modifier.fillMaxWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                horizontal = 16.dp,
                vertical = 8.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items(frames, key = { it.id }) { frame ->
                FrameListItem(
                    frame = frame,
                    isVisible = frame.id in visibleFrameIds,
                    onDelete = { onDeleteFrame(frame.id) },
                    onFocus = onFocusFrame?.let { focus -> { focus(frame.id) } },
                )
            }
        }
    }
}

@Composable
private fun FrameListItem(
    frame: CanvasNode.Frame,
    isVisible: Boolean,
    onDelete: () -> Unit,
    onFocus: (() -> Unit)? = null,
) {
    val frameColor = Color(frame.color.toColorInt())
    val transform = frame.transform

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .then(
                if (isVisible) Modifier.border(1.dp, frameColor.copy(alpha = 0.7f), RoundedCornerShape(6.dp))
                else Modifier
            )
            .padding(horizontal = if (isVisible) 8.dp else 0.dp)
            .then(if (onFocus != null) Modifier.clickable { onFocus() } else Modifier),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Frame name + color dot — shared widget so all list/picker surfaces match.
        FrameNameLabel(
            frame = frame,
            modifier = Modifier.weight(1f),
            dotSize = 12.dp,
        )

        // Frame transform — render dims, not base; aspect from rendered size
        Text(
            text = "center: ${"%.0f".format(transform.cx)},${"%.0f".format(transform.cy)} " +
                    "wh: ${"%.0f".format(transform.renderW)},${"%.0f".format(transform.renderH)}=${"%.2f".format(transform.renderW/transform.renderH)} " +
                    "rot: ${"%.0f".format(transform.rotation)}",
            style = MaterialTheme.typography.bodyMedium,
        )

        // Delete button
        IconButton(onClick = onDelete) {
            Text(
                text = "\u2715",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

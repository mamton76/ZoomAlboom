package com.mamton.zoomalbum.feature.ide_ui.ui.content

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.core.graphics.toColorInt
import com.mamton.zoomalbum.domain.model.CanvasNode
import kotlin.math.roundToLong

/**
 * Reusable frame list content — rendered inside both
 * [com.mamton.zoomalbum.feature.ide_ui.ui.panels.FrameListPanel] (docked panel)
 * and [com.mamton.zoomalbum.feature.ide_ui.ui.sheets.FrameListBottomSheet].
 */
@Composable
fun FrameListContent(
    frames: List<CanvasNode.Frame>,
    onDeleteFrame: (String) -> Unit,
    modifier: Modifier = Modifier,
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
                    onDelete = { onDeleteFrame(frame.id) },
                )
            }
        }
    }
}

@Composable
private fun FrameListItem(
    frame: CanvasNode.Frame,
    onDelete: () -> Unit,
) {
    val frameColor = Color(frame.color.toColorInt())
    val displayName = frame.label.ifEmpty { frame.id }
    val transform = frame.transform

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Color dot
        Surface(
            modifier = Modifier.size(12.dp),
            shape = CircleShape,
            color = frameColor,
        ) {}

        // Frame name in frame color
        Text(
            text = displayName,
            style = MaterialTheme.typography.bodyMedium,
            color = frameColor,
            modifier = Modifier.weight(1f),
        )

        // Frame transform
        Text(
            text = "XY: ${transform.x},${transform.y} rot: ${transform.rotation}, scale: ${transform.scale}",
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

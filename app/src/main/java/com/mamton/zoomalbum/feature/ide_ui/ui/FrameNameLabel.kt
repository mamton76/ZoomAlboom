package com.mamton.zoomalbum.feature.ide_ui.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.toColorInt
import com.mamton.zoomalbum.domain.model.CanvasNode

/**
 * Shared widget for displaying a frame's name with its color as a visual cue.
 *
 * Renders: `[●] <name>` as a Row — the dot is the frame's color, the name is rendered
 * in the same color so users can correlate the label with the frame on the canvas.
 *
 * Use everywhere a frame name appears in chrome: dialogs, list rows, picker rows.
 * Caller controls the surrounding layout via [modifier] (e.g. `Modifier.weight(1f)`
 * inside a parent Row).
 */
@Composable
fun FrameNameLabel(
    frame: CanvasNode.Frame,
    modifier: Modifier = Modifier,
    prefix: String? = null,
    style: TextStyle = MaterialTheme.typography.bodyMedium,
    dotSize: Dp = 10.dp,
    spacing: Dp = 8.dp,
) {
    val frameColor = remember(frame.color) {
        runCatching { Color(frame.color.toColorInt()) }.getOrDefault(Color.Gray)
    }
    val displayName = frame.label.ifEmpty { frame.id }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            modifier = Modifier.size(dotSize),
            shape = CircleShape,
            color = frameColor,
        ) {}
        Spacer(Modifier.width(spacing))
        if (prefix != null) {
            Text(text = prefix, style = style)
        }
        Text(
            text = displayName,
            style = style,
            color = frameColor,
        )
    }
}

package com.mamton.zoomalbum.feature.canvas.view

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mamton.zoomalbum.core.designsystem.PanelBackground
import com.mamton.zoomalbum.core.designsystem.TextSecondary
import com.mamton.zoomalbum.core.math.Camera
import com.mamton.zoomalbum.core.math.TransformUtils
import com.mamton.zoomalbum.domain.model.CanvasNode

/**
 * Small debug overlay showing details about the currently selected node(s).
 * Visible in debug builds or when explicitly toggled on.
 */
@Composable
fun SelectionDebugPanel(
    selectedNodes: List<CanvasNode>,
    camera: Camera,
    modifier: Modifier = Modifier,
) {
    if (selectedNodes.isEmpty()) return

    Column(
        modifier = modifier
            .background(PanelBackground.copy(alpha = 0.85f), RoundedCornerShape(8.dp))
            .padding(8.dp),
    ) {
        val mono = androidx.compose.ui.text.TextStyle(
            fontSize = 10.sp,
            color = TextSecondary,
            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
        )

        if (selectedNodes.size > 1) {
            val (gcx, gcy) = TransformUtils.groupCenter(selectedNodes)
            val bbox = TransformUtils.selectionBoundingBox(selectedNodes)
            Text("Group: ${selectedNodes.size} nodes", style = mono)
            Text("Center: (%.0f, %.0f)".format(gcx, gcy), style = mono)
            Text("AABB: %.0fx%.0f".format(bbox.width, bbox.height), style = mono)
            Text("---", style = mono)
        }

        for (node in selectedNodes.take(5)) {
            val t = node.transform
            val type = when (node) {
                is CanvasNode.Frame -> "Frame"
                is CanvasNode.Media -> "Media"
            }
            val label = when (node) {
                is CanvasNode.Frame -> node.label.ifEmpty { "" }
                is CanvasNode.Media -> node.mediaType.name.lowercase()
            }
            Text("$type ${label.take(12)} id=${node.id.takeLast(8)}", style = mono)
            Text(
                "  pos=(%.0f,%.0f) w=%.0f h=%.0f".format(t.cx, t.cy, t.w, t.h),
                style = mono,
            )
            Text(
                "  scale=%.2f rot=%.1f z=%.0f render=%.0fx%.0f".format(
                    t.scale, t.rotation, t.zIndex, t.renderW, t.renderH,
                ),
                style = mono,
            )
        }
        if (selectedNodes.size > 5) {
            Text("... +${selectedNodes.size - 5} more", style = mono)
        }
    }
}

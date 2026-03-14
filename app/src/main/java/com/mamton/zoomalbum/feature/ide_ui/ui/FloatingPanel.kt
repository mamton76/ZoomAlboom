package com.mamton.zoomalbum.feature.ide_ui.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.mamton.zoomalbum.core.designsystem.AccentCyan
import com.mamton.zoomalbum.core.designsystem.PanelBackground
import com.mamton.zoomalbum.core.designsystem.PanelBorder
import com.mamton.zoomalbum.core.designsystem.TextPrimary
import com.mamton.zoomalbum.core.designsystem.TextSecondary
import com.mamton.zoomalbum.feature.ide_ui.viewmodel.IdeAction
import com.mamton.zoomalbum.feature.ide_ui.viewmodel.PanelPosition
import com.mamton.zoomalbum.feature.ide_ui.viewmodel.PanelState
import kotlin.math.roundToInt

private val DOCK_THRESHOLD_DP = 80.dp

@Composable
fun FloatingPanel(
    panel: PanelState,
    onAction: (IdeAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val screenWidthDp = LocalConfiguration.current.screenWidthDp.dp

    Surface(
        modifier = modifier
            .zIndex(panel.zIndex)
            .offset { IntOffset(panel.offset.x.roundToInt(), panel.offset.y.roundToInt()) }
            .width(panel.width)
            .height(panel.height),
        color = PanelBackground,
        tonalElevation = 8.dp,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, PanelBorder),
    ) {
        Column {
            // Drag handle row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(36.dp)
                    .pointerInput(panel.id) {
                        detectDragGestures(
                            onDragStart = { onAction(IdeAction.BringToFront(panel.id)) },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                val newOffset = panel.offset + Offset(dragAmount.x, dragAmount.y)
                                val dockPx = DOCK_THRESHOLD_DP.toPx()
                                val rightEdgePx = (screenWidthDp - DOCK_THRESHOLD_DP).toPx()
                                when {
                                    newOffset.x < dockPx ->
                                        onAction(IdeAction.DockPanel(panel.id, PanelPosition.LeftTop))
                                    newOffset.x > rightEdgePx ->
                                        onAction(IdeAction.DockPanel(panel.id, PanelPosition.RightTop))
                                    else ->
                                        onAction(IdeAction.MovePanel(panel.id, newOffset))
                                }
                            },
                        )
                    }
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(text = "⠿", color = TextSecondary, fontSize = 14.sp)
                Text(
                    text = panel.title,
                    color = TextPrimary,
                    fontSize = 13.sp,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 6.dp),
                )
            }

            // Content area
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            )

            // Resize handle (bottom-right corner)
            Box(
                modifier = Modifier
                    .align(Alignment.End)
                    .size(16.dp)
                    .pointerInput(panel.id) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            val newWidth = (panel.width + dragAmount.x.toDp()).coerceAtLeast(120.dp)
                            val newHeight = (panel.height + dragAmount.y.toDp()).coerceAtLeast(80.dp)
                            onAction(IdeAction.ResizePanel(panel.id, newWidth, newHeight))
                        }
                    }
                    .background(AccentCyan.copy(alpha = 0.4f), RoundedCornerShape(topStart = 4.dp)),
            )
        }
    }
}
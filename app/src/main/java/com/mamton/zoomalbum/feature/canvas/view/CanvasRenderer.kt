package com.mamton.zoomalbum.feature.canvas.view

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.sp
import com.mamton.zoomalbum.domain.model.CanvasNode
import androidx.core.graphics.toColorInt

/**
 * Renders a single [CanvasNode] inside the camera-transformed container.
 * Positions and sizes are in world-coordinate dp; the parent graphicsLayer
 * handles all camera pan/zoom, so these composables stay stable across gestures.
 */
@Composable
fun CanvasNodeRenderer(node: CanvasNode) {
    when (node) {
        is CanvasNode.Frame -> FrameRenderer(node)
        is CanvasNode.Media -> { /* Stage 2 — Coil image loading */ }
    }
}

@Composable
private fun FrameRenderer(frame: CanvasNode.Frame) {
    val density = LocalDensity.current
    val t = frame.transform
    val shape = RoundedCornerShape(size = with(density) { 4f.toDp() })
    val fillColor = Color(frame.color.toColorInt())
    val borderColor = fillColor.copy(alpha = 0.6f)

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .offset(
                x = with(density) { t.x.toDp() },
                y = with(density) { t.y.toDp() },
            )
            .size(
                width = with(density) { (t.w * t.scale).toDp() },
                height = with(density) { (t.h * t.scale).toDp() },
            )
            .rotate(t.rotation)
            .clip(shape)
            .background(fillColor.copy(alpha = 0.35f))
            .border(width = with(density) { 1.5f.toDp() }, color = borderColor, shape = shape),
    ) {
        if (frame.label.isNotEmpty()) {
            Text(
                text = frame.label,
                color = Color.White.copy(alpha = 0.8f),
                fontSize = 10.sp,
            )
        }
    }
}

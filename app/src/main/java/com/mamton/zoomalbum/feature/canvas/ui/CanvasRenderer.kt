package com.mamton.zoomalbum.feature.canvas.ui

import androidx.compose.runtime.Composable
import com.mamton.zoomalbum.domain.model.CanvasNode

/**
 * Renders a single [CanvasNode] on the infinite canvas using
 * [Modifier.graphicsLayer] for GPU-accelerated transformations.
 */
@Composable
fun CanvasNodeRenderer(node: CanvasNode) {
    // TODO: render Media (image/video via Coil) or Frame (labeled rectangle)
}

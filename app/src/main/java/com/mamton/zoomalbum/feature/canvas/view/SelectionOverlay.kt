package com.mamton.zoomalbum.feature.canvas.view

import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import com.mamton.zoomalbum.core.designsystem.AccentCyan
import com.mamton.zoomalbum.core.math.BoundingBox
import com.mamton.zoomalbum.domain.model.CanvasNode
import com.mamton.zoomalbum.domain.model.Transform
import com.mamton.zoomalbum.feature.canvas.viewmodel.CanvasViewModel

/**
 * Draws selection chrome around the selected node(s).
 *
 * - **Single node**: border + corner handles + rotation handle on that node.
 * - **Multiple nodes**: individual border on each node, plus a group
 *   rectangle (with handles + rotation handle) that rotates as a rigid body.
 */
@Composable
fun SelectionOverlay(
    selectedNodes: List<CanvasNode>,
    cameraScale: Float,
    rotationHandleEnabled: Boolean,
    groupTransform: Transform?,
) {
    if (selectedNodes.isEmpty()) return

    if (selectedNodes.size == 1) {
        // Single node: full chrome (border + handles + rotation handle)
        NodeChrome(
            transform = selectedNodes.first().transform,
            cameraScale = cameraScale,
            showHandles = true,
            rotationHandleEnabled = rotationHandleEnabled,
        )
    } else {
        // Multi-select: individual border on each node (no handles)
        for (node in selectedNodes) {
            NodeChrome(
                transform = node.transform,
                cameraScale = cameraScale,
                showHandles = false,
                rotationHandleEnabled = false,
            )
        }
        // Group rectangle: handles + rotation handle, rotates as rigid body
        if (groupTransform != null) {
            NodeChrome(
                transform = groupTransform,
                cameraScale = cameraScale,
                showHandles = true,
                rotationHandleEnabled = rotationHandleEnabled,
            )
        }
    }
}

/**
 * Draws selection border (and optionally handles + rotation handle) for a single transform.
 */
@Composable
private fun NodeChrome(
    transform: Transform,
    cameraScale: Float,
    showHandles: Boolean,
    rotationHandleEnabled: Boolean,
) {
    val renderW = transform.renderW
    val renderH = transform.renderH
    val handleSize = CanvasViewModel.HANDLE_SCREEN_PX / cameraScale
    val strokeWidth = 2f / cameraScale
    val rotHandleOffset = CanvasViewModel.ROTATION_HANDLE_OFFSET_PX / cameraScale
    val rotHandleRadius = handleSize / 2f

    Spacer(
        modifier = Modifier
            .graphicsLayer {
                translationX = transform.cx
                translationY = transform.cy
                rotationZ = transform.rotation
                transformOrigin = TransformOrigin(0f, 0f)
                clip = false
            }
            .drawBehind {
                val halfW = renderW / 2f
                val halfH = renderH / 2f
                val topLeft = Offset(-halfW, -halfH)
                val nodeSize = Size(renderW, renderH)

                // Selection border
                drawRect(
                    color = AccentCyan,
                    topLeft = topLeft,
                    size = nodeSize,
                    style = Stroke(width = strokeWidth),
                )

                if (!showHandles) return@drawBehind

                // Corner handles
                val hs = handleSize / 2f
                val corners = listOf(
                    Offset(-halfW - hs, -halfH - hs),
                    Offset(halfW - hs, -halfH - hs),
                    Offset(-halfW - hs, halfH - hs),
                    Offset(halfW - hs, halfH - hs),
                )
                for (corner in corners) {
                    drawRect(
                        color = AccentCyan,
                        topLeft = corner,
                        size = Size(handleSize, handleSize),
                    )
                }

                // Rotation handle
                if (rotationHandleEnabled) {
                    val handleCenter = Offset(0f, -halfH - rotHandleOffset)
                    drawLine(
                        color = AccentCyan,
                        start = Offset(0f, -halfH),
                        end = handleCenter,
                        strokeWidth = strokeWidth,
                    )
                    drawCircle(
                        color = AccentCyan,
                        radius = rotHandleRadius,
                        center = handleCenter,
                    )
                }
            },
    )
}

/**
 * Semi-transparent rectangle drawn during drag-to-select.
 */
@Composable
fun SelectionRectOverlay(rect: BoundingBox) {
    Spacer(
        modifier = Modifier
            .graphicsLayer {
                translationX = rect.centerX
                translationY = rect.centerY
                transformOrigin = TransformOrigin(0f, 0f)
                clip = false
            }
            .drawBehind {
                val halfW = rect.width / 2f
                val halfH = rect.height / 2f
                drawRect(
                    color = AccentCyan.copy(alpha = 0.15f),
                    topLeft = Offset(-halfW, -halfH),
                    size = Size(rect.width, rect.height),
                )
                drawRect(
                    color = AccentCyan.copy(alpha = 0.6f),
                    topLeft = Offset(-halfW, -halfH),
                    size = Size(rect.width, rect.height),
                    style = Stroke(width = 1f),
                )
            },
    )
}

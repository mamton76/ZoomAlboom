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
 *
 * When [anchorNodeId] is non-null and present in [selectedNodes], that node
 * also gets an outer halo communicating which node anchor-scoped context
 * menu items operate on (see `docs/architecture/context-menu.md § 2`).
 */
@Composable
fun SelectionOverlay(
    selectedNodes: List<CanvasNode>,
    cameraScale: Float,
    rotationHandleEnabled: Boolean,
    groupTransform: Transform?,
    anchorNodeId: String? = null,
) {
    if (selectedNodes.isEmpty()) return

    // Anchor halo draws first so the regular selection border overlays on top —
    // halo sits outside the border, not behind it visually.
    if (anchorNodeId != null) {
        selectedNodes.firstOrNull { it.id == anchorNodeId }?.let { anchor ->
            AnchorHalo(transform = anchor.transform, cameraScale = cameraScale)
        }
    }

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
 * Outer halo drawn around the context-menu anchor node — a soft ring offset
 * outside the node's bounds. Visible only while the popup is open.
 */
@Composable
private fun AnchorHalo(
    transform: Transform,
    cameraScale: Float,
) {
    val haloOffset = 6f / cameraScale
    val haloStroke = 4f / cameraScale
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
                val halfW = transform.renderW / 2f + haloOffset
                val halfH = transform.renderH / 2f + haloOffset
                drawRect(
                    color = AccentCyan.copy(alpha = 0.55f),
                    topLeft = Offset(-halfW, -halfH),
                    size = Size(halfW * 2, halfH * 2),
                    style = Stroke(width = haloStroke),
                )
            },
    )
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
 * Draws thin borders around every effective member of the currently-selected frame.
 *
 * Two visual tiers communicate *why* a node is a member:
 *  - [autoMembers] — pure geometric membership (no override). Lighter, thinner.
 *  - [manualMembers] — explicit `Included` override (User / BatchImport / Wizard /
 *    RebindSuppressed). Darker, slightly thicker — signals "this is sticky, clearing
 *    the override changes membership."
 *
 * See `docs/architecture/frame-membership.md`.
 */
@Composable
fun MembershipBorderOverlay(
    autoMembers: List<CanvasNode>,
    manualMembers: List<CanvasNode>,
    cameraScale: Float,
) {
    val autoStroke = 1.5f / cameraScale
    val manualStroke = 2.5f / cameraScale

    for (member in autoMembers) {
        MembershipBorder(
            transform = member.transform,
            color = AccentCyan.copy(alpha = 0.40f),
            strokeWidth = autoStroke,
        )
    }
    for (member in manualMembers) {
        MembershipBorder(
            transform = member.transform,
            color = AccentCyan.copy(alpha = 0.95f),
            strokeWidth = manualStroke,
        )
    }
}

@Composable
private fun MembershipBorder(
    transform: Transform,
    color: androidx.compose.ui.graphics.Color,
    strokeWidth: Float,
) {
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
                val halfW = transform.renderW / 2f
                val halfH = transform.renderH / 2f
                drawRect(
                    color = color,
                    topLeft = Offset(-halfW, -halfH),
                    size = Size(transform.renderW, transform.renderH),
                    style = Stroke(width = strokeWidth),
                )
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

package com.mamton.zoomalbum.core.math

import com.mamton.zoomalbum.domain.model.CanvasNode

/**
 * Brute-force spatial query over all nodes.
 * Will be replaced by a grid / R-tree when node counts exceed ~2 000.
 */
object ViewportCuller {

    fun visibleNodes(
        allNodes: List<CanvasNode>,
        viewport: BoundingBox,
    ): List<CanvasNode> = allNodes.filter { node ->
        TransformUtils.toBoundingBox(node.transform).intersects(viewport)
    }
}

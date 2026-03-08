package com.mamton.zoomalbum.domain.usecase

import com.mamton.zoomalbum.core.math.BoundingBox
import com.mamton.zoomalbum.core.math.TransformUtils
import com.mamton.zoomalbum.domain.model.CanvasNode
import javax.inject.Inject

class CalculateViewportIntersectionsUseCase @Inject constructor() {

    operator fun invoke(
        viewport: BoundingBox,
        nodes: List<CanvasNode>,
    ): List<CanvasNode> = nodes.filter { node ->
        TransformUtils.toBoundingBox(node.transform).intersects(viewport)
    }
}

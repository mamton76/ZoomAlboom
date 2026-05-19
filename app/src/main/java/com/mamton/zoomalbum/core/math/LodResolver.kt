package com.mamton.zoomalbum.core.math

import com.mamton.zoomalbum.domain.model.CanvasNode
import com.mamton.zoomalbum.domain.model.RenderDetail
import com.mamton.zoomalbum.domain.model.VisibilityPolicy
import kotlin.math.max

/**
 * Resolves the [RenderDetail] for a node given the current camera state.
 *
 * Two-stage resolution:
 * 1. **Screen-size culling** — nodes smaller than [MIN_VISIBLE_PX] on screen are hidden.
 * 2. **Semantic zoom filtering** — uses the node's [VisibilityPolicy] (or type-based default)
 *    to determine detail level based on `camera.scale / referenceScale`.
 *
 * Pure functions, no Compose/Android dependencies.
 */
object LodResolver {

    /** Nodes whose largest screen dimension is below this are culled. */
    const val MIN_VISIBLE_PX = 2f

    /** Reference camera scale used for default policies — 1.0 = "viewed at canonical zoom." */
    const val DEFAULT_REFERENCE_SCALE = 1f

    /** Below this relative zoom, default frame policy renders as [RenderDetail.Stub]. */
    const val DEFAULT_FRAME_MIN_RELATIVE_ZOOM = 0.001f

    /** Above this relative zoom, default frame policy renders as [RenderDetail.Simplified]. */
    const val DEFAULT_FRAME_MAX_RELATIVE_ZOOM = 500f

    /** Below this relative zoom, default media policy renders as [RenderDetail.Hidden]. */
    const val DEFAULT_MEDIA_MIN_RELATIVE_ZOOM = 0.01f

    /** Above this relative zoom, default media policy renders as [RenderDetail.Full]. */
    const val DEFAULT_MEDIA_MAX_RELATIVE_ZOOM = 100f

    fun resolveRenderDetail(
        node: CanvasNode,
        camera: Camera,
    ): RenderDetail {
        // Stage 1: screen-size culling
        val screenSize = max(node.transform.renderW, node.transform.renderH) * camera.scale
        if (screenSize < MIN_VISIBLE_PX) return RenderDetail.Hidden

        // Stage 2: semantic zoom filtering
        val policy = node.visibilityPolicy ?: defaultPolicy(node)
        val relativeZoom = camera.scale / policy.referenceScale

        return when {
            relativeZoom < policy.minRelativeZoom -> policy.belowRangeMode
            relativeZoom > policy.maxRelativeZoom -> policy.aboveRangeMode
            else -> RenderDetail.Full
        }
    }

    fun defaultPolicy(node: CanvasNode): VisibilityPolicy = when (node) {
        is CanvasNode.Frame -> VisibilityPolicy(
            referenceScale = DEFAULT_REFERENCE_SCALE,
            minRelativeZoom = DEFAULT_FRAME_MIN_RELATIVE_ZOOM,
            maxRelativeZoom = DEFAULT_FRAME_MAX_RELATIVE_ZOOM,
            belowRangeMode = RenderDetail.Stub,
            aboveRangeMode = RenderDetail.Simplified,
        )
        is CanvasNode.Media -> VisibilityPolicy(
            referenceScale = DEFAULT_REFERENCE_SCALE,
            minRelativeZoom = DEFAULT_MEDIA_MIN_RELATIVE_ZOOM,
            maxRelativeZoom = DEFAULT_MEDIA_MAX_RELATIVE_ZOOM,
            belowRangeMode = RenderDetail.Hidden,
            aboveRangeMode = RenderDetail.Full,
        )
    }
}

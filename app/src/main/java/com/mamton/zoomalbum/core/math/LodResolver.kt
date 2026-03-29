package com.mamton.zoomalbum.core.math

import com.mamton.zoomalbum.domain.model.CanvasNode
import com.mamton.zoomalbum.domain.model.RenderDetail
import com.mamton.zoomalbum.domain.model.VisibilityPolicy
import android.util.Log
import kotlin.math.max

private const val TAG = "LodResolver"

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

    fun resolveRenderDetail(
        node: CanvasNode,
        camera: Camera,
    ): RenderDetail {
        // Stage 1: screen-size culling
        val screenSize = max(node.transform.renderW, node.transform.renderH) * camera.scale
        if (screenSize < MIN_VISIBLE_PX) {
            Log.d(TAG, "HIDDEN node=${node.id} reason=screen-size-cull " +
                "screenSize=$screenSize minPx=$MIN_VISIBLE_PX " +
                "renderW=${node.transform.renderW} renderH=${node.transform.renderH} " +
                "cameraScale=${camera.scale}")
            return RenderDetail.Hidden
        }

        // Stage 2: semantic zoom filtering
        val policy = node.visibilityPolicy ?: defaultPolicy(node)
        val relativeZoom = camera.scale / policy.referenceScale

        val result = when {
            relativeZoom < policy.minRelativeZoom -> policy.belowRangeMode
            relativeZoom > policy.maxRelativeZoom -> policy.aboveRangeMode
            else -> RenderDetail.Full
        }

        if (result != RenderDetail.Full) {
            Log.d(TAG, "HIDDEN node=${node.id} reason=semantic-zoom " +
                "result=$result relativeZoom=$relativeZoom " +
                "minRel=${policy.minRelativeZoom} maxRel=${policy.maxRelativeZoom} " +
                "refScale=${policy.referenceScale} cameraScale=${camera.scale}")
        }

        return result
    }

    fun defaultPolicy(node: CanvasNode): VisibilityPolicy = when (node) {
        is CanvasNode.Frame -> VisibilityPolicy(
            referenceScale = 1f,
            minRelativeZoom = 0.001f,
            maxRelativeZoom = 500f,
            belowRangeMode = RenderDetail.Stub,
            aboveRangeMode = RenderDetail.Simplified,
        )
        is CanvasNode.Media -> VisibilityPolicy(
            referenceScale = 1f,
            minRelativeZoom = 0.01f,
            maxRelativeZoom = 100f,
            belowRangeMode = RenderDetail.Hidden,
            aboveRangeMode = RenderDetail.Full,
        )
    }
}

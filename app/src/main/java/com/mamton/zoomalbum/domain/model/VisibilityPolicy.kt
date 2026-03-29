package com.mamton.zoomalbum.domain.model

import kotlinx.serialization.Serializable

/**
 * Controls how a node appears at different zoom levels.
 *
 * [referenceScale] is the camera zoom at which this node is "meant to be viewed" —
 * typically the zoom level when the node was created.
 *
 * The ratio `camera.scale / referenceScale` (relative zoom) determines detail level:
 * - Below [minRelativeZoom] → [belowRangeMode]
 * - Above [maxRelativeZoom] → [aboveRangeMode]
 * - In range → [RenderDetail.Full]
 */
@Serializable
data class VisibilityPolicy(
    val referenceScale: Float,
    val minRelativeZoom: Float = 0.025f,
    val maxRelativeZoom: Float = 10.0f,
    val belowRangeMode: RenderDetail = RenderDetail.Hidden,
    val aboveRangeMode: RenderDetail = RenderDetail.Full,
)

@Serializable
enum class RenderDetail {
    Hidden,
    Stub,
    Preview,
    Full,
    Simplified,
}

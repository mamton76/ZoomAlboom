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
    val minRelativeZoom: Float = DEFAULT_MIN_RELATIVE_ZOOM,
    val maxRelativeZoom: Float = DEFAULT_MAX_RELATIVE_ZOOM,
    val belowRangeMode: RenderDetail = RenderDetail.Hidden,
    val aboveRangeMode: RenderDetail = RenderDetail.Full,
) {
    companion object {
        /** Default lower-bound for [minRelativeZoom] — applied when none is provided. */
        const val DEFAULT_MIN_RELATIVE_ZOOM = 0.025f

        /** Default upper-bound for [maxRelativeZoom] — applied when none is provided. */
        const val DEFAULT_MAX_RELATIVE_ZOOM = 10.0f
    }
}

@Serializable
enum class RenderDetail {
    Hidden,
    Stub,
    Preview,
    Full,
    Simplified,
}

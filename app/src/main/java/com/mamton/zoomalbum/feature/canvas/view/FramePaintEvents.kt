package com.mamton.zoomalbum.feature.canvas.view

import com.mamton.zoomalbum.domain.model.CanvasNode
import com.mamton.zoomalbum.domain.model.RenderDetail
import com.mamton.zoomalbum.domain.usecase.FrameMembershipUseCase
import com.mamton.zoomalbum.feature.canvas.viewmodel.VisibleNode

/**
 * Discrete unit of work for the canvas paint loop.
 *
 * Most frames paint in a single [NodePass]. Frames with non-empty
 * `FrameAppearance.overlays` need their paint split so that their members
 * sandwich between the frame's surface (background) and its overlays +
 * border — see `docs/architecture/rendering.md § 6b`.
 *
 * Events carry a [sortKey] so the loop can mix them with regular node paints
 * in z-order: surface → members in their own z-order → overlay (just after
 * the last member). Built by [buildFramePaintEvents].
 *
 * Known limitation (MVP): the overlay event is clipped to the frame's
 * bounding rect, **not** masked to member pixels. A non-member node that
 * visually intersects the frame bounds and sorts below the overlay's z-slot
 * is covered by the overlay. Exact member-only compositing would require
 * an offscreen pass per layered frame — not justified for MVP. See
 * `docs/architecture/rendering.md § 6b — Known limitation`.
 */
internal sealed interface FramePaintEvent {

    val sortKey: Float

    data class NodePass(
        val node: CanvasNode,
        val detail: RenderDetail,
        override val sortKey: Float,
    ) : FramePaintEvent

    data class LayeredFrameSurface(
        val frame: CanvasNode.Frame,
        val detail: RenderDetail,
        override val sortKey: Float,
    ) : FramePaintEvent

    data class LayeredFrameOverlay(
        val frame: CanvasNode.Frame,
        val detail: RenderDetail,
        override val sortKey: Float,
    ) : FramePaintEvent
}

/**
 * Plans the paint order for [visibleNodes].
 *
 * Strategy:
 *  - Non-layered nodes paint as [FramePaintEvent.NodePass] at their own z-index.
 *  - A frame F that [CanvasNode.Frame.needsLayeredPaint] is true for splits into
 *    a [FramePaintEvent.LayeredFrameSurface] at F.zIndex and a
 *    [FramePaintEvent.LayeredFrameOverlay] just past `max(memberZ, F.zIndex)`.
 *  - Membership is computed by [FrameMembershipUseCase.effectiveMembers] over
 *    the current visible set (a member culled out of the viewport does not
 *    pull the overlay's z-slot upward).
 *
 * The returned list is sorted by `sortKey`, breaking ties via Kotlin's
 * stable sort (preserves [visibleNodes] insertion order at equal keys).
 */
internal fun buildFramePaintEvents(
    visibleNodes: List<VisibleNode>,
    membershipUseCase: FrameMembershipUseCase,
): List<FramePaintEvent> {
    if (visibleNodes.isEmpty()) return emptyList()
    val events = ArrayList<FramePaintEvent>(visibleNodes.size + 4)
    val allNodes = visibleNodes.map { it.node }

    val layeredFrames = ArrayList<Pair<CanvasNode.Frame, RenderDetail>>(4)
    for (vn in visibleNodes) {
        val node = vn.node
        val z = node.transform.zIndex
        if (node is CanvasNode.Frame && node.needsLayeredPaint) {
            events += FramePaintEvent.LayeredFrameSurface(node, vn.detail, z)
            layeredFrames += node to vn.detail
        } else {
            events += FramePaintEvent.NodePass(node, vn.detail, z)
        }
    }

    for ((frame, detail) in layeredFrames) {
        val members = membershipUseCase.effectiveMembers(frame, allNodes)
        val memberMaxZ = visibleNodes
            .filter { it.node.id in members }
            .maxOfOrNull { it.node.transform.zIndex }
            ?: frame.transform.zIndex
        val overlayKey = maxOf(memberMaxZ, frame.transform.zIndex) + LAYERED_OVERLAY_EPSILON
        events += FramePaintEvent.LayeredFrameOverlay(frame, detail, overlayKey)
    }

    events.sortBy { it.sortKey }
    return events
}

/**
 * Tiny offset added to a layered frame's overlay sort key so it strictly follows
 * its highest-z member without crossing the next node's z-slot in typical scenes
 * (zIndex values are integer-like / well-separated in current usage).
 */
private const val LAYERED_OVERLAY_EPSILON = 1e-4f

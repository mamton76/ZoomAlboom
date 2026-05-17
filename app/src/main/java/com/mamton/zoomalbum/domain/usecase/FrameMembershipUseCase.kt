package com.mamton.zoomalbum.domain.usecase

import com.mamton.zoomalbum.core.math.TransformUtils
import com.mamton.zoomalbum.domain.model.CanvasNode
import com.mamton.zoomalbum.domain.model.MembershipState

/**
 * Computes which nodes are logically members of a frame.
 *
 * Membership = geometry minus Excluded overrides plus Included overrides.
 * Geometry is implicit: an absent entry in `frame.overrides` means "geometry decides."
 *
 * See `docs/architecture/frame-membership.md`.
 *
 * Geometric intersection is AABB-based (rotation-loose). Acceptable for MVP — refine
 * to oriented-rect intersection if rotated frames produce visible mispredictions.
 */
class FrameMembershipUseCase {

    fun effectiveMembers(
        frame: CanvasNode.Frame,
        nodes: List<CanvasNode>,
    ): Set<String> {
        val frameBox = TransformUtils.toBoundingBox(frame.transform)

        val included = mutableSetOf<String>()
        for (node in nodes) {
            if (node.id == frame.id) continue
            if (!node.isFrameBindable) continue

            val override = frame.overrides[node.id]
            when (override?.state) {
                MembershipState.Excluded -> Unit
                MembershipState.Included -> included += node.id
                null -> {
                    if (TransformUtils.toBoundingBox(node.transform).intersects(frameBox)) {
                        included += node.id
                    }
                }
            }
        }
        // Included overrides for nodes not in the list are ignored — orphan refs are
        // tolerated on read and cleaned up by the corresponding delete commands.
        return included
    }
}

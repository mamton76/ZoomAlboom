package com.mamton.zoomalbum.domain.usecase

import com.mamton.zoomalbum.domain.model.CanvasNode
import com.mamton.zoomalbum.domain.model.FrameEditOptions
import com.mamton.zoomalbum.domain.model.FrameMembershipOverride
import com.mamton.zoomalbum.domain.model.MembershipOrigin
import com.mamton.zoomalbum.domain.model.MembershipState

/**
 * Post-gesture suppression logic for a frame transform.
 *
 * When [FrameEditOptions.rebindAfterEdit] is false, preserves the pre-edit logical
 * membership by writing `RebindSuppressed` overrides:
 *  - geometric members that were dropped by the edit  → `(Included, RebindSuppressed)`
 *  - geometric non-members captured by the edit       → `(Excluded, RebindSuppressed)`
 *
 * When `rebindAfterEdit` is true, returns [frameAfter] unchanged — geometry decides
 * the new membership live via [FrameMembershipUseCase.effectiveMembers].
 *
 * Caller responsibilities:
 *  - [allNodesBefore] / [allNodesAfter] must contain the pre- and post-transform
 *    versions of every node that participated in the gesture (frame + members for
 *    move-with-content; just the frame for frame-only edits).
 *  - [frameBefore] / [frameAfter] must be the same `id` and be present in the lists.
 *
 * See `docs/architecture/frame-membership.md`.
 */
class ApplyFrameEditUseCase(
    private val membershipUseCase: FrameMembershipUseCase = FrameMembershipUseCase(),
) {

    fun applyFrameEdit(
        frameBefore: CanvasNode.Frame,
        frameAfter: CanvasNode.Frame,
        allNodesBefore: List<CanvasNode>,
        allNodesAfter: List<CanvasNode>,
        options: FrameEditOptions,
    ): CanvasNode.Frame {
        if (options.rebindAfterEdit) return frameAfter

        val beforeActual = membershipUseCase.effectiveMembers(frameBefore, allNodesBefore)
        val afterPotential = membershipUseCase.effectiveMembers(frameAfter, allNodesAfter)

        val dropped = beforeActual - afterPotential
        val newlyCaptured = afterPotential - beforeActual
        if (dropped.isEmpty() && newlyCaptured.isEmpty()) return frameAfter

        val next = frameAfter.overrides.toMutableMap()
        for (id in dropped) {
            next[id] = FrameMembershipOverride(MembershipState.Included, MembershipOrigin.RebindSuppressed)
        }
        for (id in newlyCaptured) {
            next[id] = FrameMembershipOverride(MembershipState.Excluded, MembershipOrigin.RebindSuppressed)
        }
        return frameAfter.copy(overrides = next)
    }
}

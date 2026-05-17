package com.mamton.zoomalbum.domain.usecase

import com.mamton.zoomalbum.domain.model.CanvasNode
import com.mamton.zoomalbum.domain.model.FrameMembershipOverride
import com.mamton.zoomalbum.domain.model.MembershipOrigin
import com.mamton.zoomalbum.domain.model.MembershipState

/**
 * Pure-function mutations of `Frame.overrides`.
 *
 * Setting an override for a node is mutually exclusive — writing `Included` replaces
 * any prior `Excluded` entry for the same node and vice versa. See
 * `docs/architecture/frame-membership.md`.
 */
class FrameOverrideUseCase {

    /**
     * Write `(state, origin)` overrides for [nodeIds] on [frame].
     *
     * Returns the same instance if [nodeIds] is empty or the resulting map equals the
     * existing one — callers should treat instance equality as "no change."
     *
     * Defensive write: produces an override even if [state] matches what geometry would
     * already imply, so explicit user intent survives future geometry changes.
     */
    fun applyOverride(
        frame: CanvasNode.Frame,
        nodeIds: Set<String>,
        state: MembershipState,
        origin: MembershipOrigin,
    ): CanvasNode.Frame {
        if (nodeIds.isEmpty()) return frame
        val next = frame.overrides.toMutableMap()
        val target = FrameMembershipOverride(state, origin)
        for (id in nodeIds) next[id] = target
        return if (next == frame.overrides) frame else frame.copy(overrides = next)
    }

    /** Drop entries for [nodeIds]. Used when geometry no longer needs an override at all. */
    fun clearOverrides(
        frame: CanvasNode.Frame,
        nodeIds: Set<String>,
    ): CanvasNode.Frame {
        if (nodeIds.isEmpty()) return frame
        val next = frame.overrides.toMutableMap()
        var changed = false
        for (id in nodeIds) {
            if (next.remove(id) != null) changed = true
        }
        return if (changed) frame.copy(overrides = next) else frame
    }
}

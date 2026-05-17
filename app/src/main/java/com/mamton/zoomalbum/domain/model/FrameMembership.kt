package com.mamton.zoomalbum.domain.model

import kotlinx.serialization.Serializable

/**
 * Manual override for whether a node belongs to a frame, on top of the geometric default.
 *
 * Membership semantics live in `domain/usecase/FrameMembershipUseCase`. See
 * `docs/architecture/frame-membership.md`.
 *
 * Geometry is implicit — an absent entry means "geometry decides." Stored entries are
 * always overrides of one of the cases enumerated in [MembershipOrigin].
 */
@Serializable
data class FrameMembershipOverride(
    val state: MembershipState,
    val origin: MembershipOrigin,
)

@Serializable
enum class MembershipState { Included, Excluded }

@Serializable
enum class MembershipOrigin {
    /** Explicit pin / detach by user action. */
    User,

    /** Node was imported into a selected frame. */
    BatchImport,

    /** Wizard or AI-generated content, accepted by the user. */
    Wizard,

    /** Preserved by a frame edit with rebindAfterEdit = false. Cleared by "reset suppressed." */
    RebindSuppressed,
}

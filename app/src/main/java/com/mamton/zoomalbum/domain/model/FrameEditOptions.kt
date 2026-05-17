package com.mamton.zoomalbum.domain.model

/**
 * Transient (per-session) toggles that shape how a frame transform gesture behaves.
 * Not persisted on the scene graph. See `docs/architecture/frame-membership.md`.
 *
 * | transformContents | rebindAfterEdit | Behaviour                                              |
 * |-------------------|-----------------|--------------------------------------------------------|
 * | true              | true            | Frame + members transform together; geometry rebinds.  |
 * | true              | false           | Frame + members transform together; membership frozen. |
 * | false             | true            | Only frame transforms; geometry rebinds.               |
 * | false             | false           | Only frame transforms; membership frozen.              |
 *
 * `transformContents` applies to all three gesture kinds (move / resize / rotate).
 * Rotation pivots around the frame center, not the centroid of (frame + members).
 */
data class FrameEditOptions(
    val transformContents: Boolean = true,
    val rebindAfterEdit: Boolean = true,
)

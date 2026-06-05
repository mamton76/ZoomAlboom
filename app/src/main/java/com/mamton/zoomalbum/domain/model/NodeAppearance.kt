package com.mamton.zoomalbum.domain.model

import kotlinx.serialization.Serializable

/**
 * Non-destructive visual styling shared by every styleable [CanvasNode] variant.
 *
 * The base owns the cross-cutting properties that mean the same thing on every
 * node: surface opacity, corner rounding of the node rect, an ordered overlay
 * stack painted above the node's own rendered output, a border, and a drop
 * shadow. Concrete subclasses ([FrameAppearance], [MediaAppearance]) add their
 * own fields — background, crop, decoration, etc.
 *
 * [overlays] is unified across node types: for a media node it paints above
 * the media's pixels; for a frame node it paints above the frame's combined
 * children + background output, clipped to the frame rect. The renderer
 * dispatches by node type — the data model is one field.
 * See `docs/architecture/appearance.md § 13`.
 */
@Serializable
sealed class NodeAppearance {
    abstract val opacity: Float
    abstract val cornerRadius: Float
    abstract val overlays: List<OverlayStyle>
    abstract val border: BorderStyle?
    abstract val shadow: ShadowStyle?

    /**
     * Soft per-pixel alpha mask applied inside the node's clip shape — the
     * renderer wraps the node draw in an offscreen layer and composites the
     * mask via `BlendMode.DstIn` (`docs/architecture/appearance.md § 12.4`).
     * `null` = no mask, fast path with no offscreen layer.
     *
     * Border and shadow follow the clip rect, not the mask silhouette (§ 12.5).
     */
    abstract val alphaMask: AlphaMask?
}

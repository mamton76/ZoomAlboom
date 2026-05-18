package com.mamton.zoomalbum.domain.model

import kotlinx.serialization.Serializable

/**
 * Non-destructive visual styling shared by every styleable [CanvasNode] variant.
 *
 * The base owns only the four properties that mean the same thing on every
 * node: surface opacity, corner rounding of the node rect, border on the node
 * rect, drop shadow cast by the node rect. Concrete subclasses ([FrameAppearance]
 * and the upcoming `MediaAppearance`) add their own fields — background,
 * overlays, content overlays, etc.
 *
 * [NodeAppearance] deliberately does not declare a generic `overlays` field.
 * Media-level and container-level overlays sit at different render-pipeline
 * positions and are kept as separate fields on each subclass — see
 * `docs/architecture/appearance.md § 4–5`.
 */
@Serializable
sealed class NodeAppearance {
    abstract val opacity: Float
    abstract val cornerRadius: Float
    abstract val border: BorderStyle?
    abstract val shadow: ShadowStyle?
}

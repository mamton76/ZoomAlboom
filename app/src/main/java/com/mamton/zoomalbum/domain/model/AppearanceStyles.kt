package com.mamton.zoomalbum.domain.model

import kotlinx.serialization.Serializable

/**
 * Border applied to a node's own rendered rectangle.
 *
 * Shared by [MediaAppearance] (when media gets an appearance container) and
 * [FrameAppearance]. When `null` on a [FrameAppearance], the renderer falls back
 * to the default outline derived from `Frame.color`.
 */
@Serializable
data class BorderStyle(
    /** Hex color (`#RRGGBB` or `#AARRGGBB`). */
    val color: String,
    /** Stroke width in world units. */
    val widthPx: Float,
    /** 0..1 multiplier applied to the parsed color's alpha. */
    val opacity: Float = 1f,
)

/**
 * Drop shadow cast by a node's own rectangle. Drawn behind the node's surface.
 */
@Serializable
data class ShadowStyle(
    val color: String = "#000000",
    /** 0..1 multiplier applied to the parsed color's alpha. */
    val opacity: Float = 0.5f,
    val offsetX: Float = 4f,
    val offsetY: Float = 4f,
    /** Blur radius in world units. 0 = sharp shadow. */
    val blurRadius: Float = 8f,
)

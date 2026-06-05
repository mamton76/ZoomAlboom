package com.mamton.zoomalbum.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Typography / visibility of the frame's label. `null` on a [FrameAppearance]
 * means "renderer default" (currently: no rendered label). Minimal MVP shape;
 * extended later as the title-rendering slice lands.
 */
@Serializable
data class FrameTitleStyle(
    val show: Boolean = true,
    val fontSize: Float = 14f,
    /** `null` = derive from `Frame.color`. */
    val color: String? = null,
)

/**
 * Off-screen filter pass applied to the *rendered* frame contents (sepia, blur,
 * grayscale of everything inside). Distinct from [FrameAppearance.overlays],
 * which only composite new layers on top.
 *
 * Field shape only — variants and rendering land with the off-screen renderer
 * slice (post-MVP). See `docs/architecture/appearance.md § 6` and § 10.
 */
@Serializable
sealed class FrameContentEffect
// Future variants (post-MVP):
//   @Serializable @SerialName("Sepia")     data object Sepia : FrameContentEffect()
//   @Serializable @SerialName("Grayscale") data object Grayscale : FrameContentEffect()
//   @Serializable @SerialName("Blur")      data class Blur(val radius: Float) : FrameContentEffect()

/**
 * Container/content-level styling for a [CanvasNode.Frame].
 *
 * Owns the [NodeAppearance] base fields plus frame-specific concerns:
 *
 * - [background] sits **behind** the frame's linked contents (Solid / Texture /
 *   Procedural via [BackgroundData]; the frame is implicitly its own anchor).
 * - [overlays] (inherited from base) sit **above** the frame's combined
 *   contents output, clipped to frame bounds. Ordered list: entry `[i]`
 *   composites above entry `[i-1]`. Rendering depends on the layered frame
 *   renderer and on frame–content binding. See
 *   `docs/architecture/rendering.md § 6b`.
 * - [contentEffect] (future) re-renders the contents through a filter pass.
 * - [titleStyle] controls the rendered frame label.
 *
 * `null` `appearance` on a [CanvasNode.Frame] means "default rendering" — the
 * renderer falls back to the existing color-derived outline and no background.
 */
@Serializable
@SerialName("FrameAppearance")
data class FrameAppearance(
    override val opacity: Float = 1f,
    override val cornerRadius: Float = 0f,
    override val overlays: List<OverlayStyle> = emptyList(),
    override val border: BorderStyle? = null,
    override val shadow: ShadowStyle? = null,
    override val alphaMask: AlphaMask? = null,
    val background: BackgroundData? = null,
    val contentEffect: FrameContentEffect? = null,
    val titleStyle: FrameTitleStyle? = null,
) : NodeAppearance() {

    companion object {
        /** Convenience for the common "only a background" case used today. */
        fun ofBackground(background: BackgroundData?): FrameAppearance? =
            if (background == null) null else FrameAppearance(background = background)
    }
}

package com.mamton.zoomalbum.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Compositing mode for an overlay or layered draw step.
 *
 * The model lists every mode we expect to need. The renderer ships [Normal]
 * first; additional modes light up incrementally as the renderer learns them.
 * See `docs/architecture/appearance.md § 7.2`.
 */
@Serializable
enum class NodeBlendMode {
    Normal,
    Multiply,
    Screen,
    Overlay,
    SoftLight,
    Darken,
    Lighten,
}

/**
 * Source of an [OverlayStyle] — the pixels (or shader) that get composited.
 *
 * Mirrors the [BackgroundData] hierarchy on purpose: a paper-grain pattern that
 * works as a background also works as an overlay, just with a different blend.
 *
 * @SerialName values are stable on-disk identifiers — do not rename without a migration.
 */
@Serializable
sealed class OverlaySource {

    @Serializable
    @SerialName("SolidColor")
    data class SolidColor(val color: String) : OverlaySource()

    @Serializable
    @SerialName("Texture")
    data class Texture(
        val textureRefId: String,
        val tile: TileData = TileData(),
    ) : OverlaySource()

    @Serializable
    @SerialName("Procedural")
    data class Procedural(
        val pattern: ProceduralPattern,
        /** Optional fill drawn under the pattern; same role as `ProceduralBackgroundData.fillColor`. */
        val fillColor: String? = null,
    ) : OverlaySource()
}

/**
 * One overlay layer.
 *
 * Reused by `MediaAppearance.overlays` (object-level) and
 * `FrameAppearance.contentOverlays` (container/content-level). Both fields are
 * `List<OverlayStyle>` with **declaration-order compositing** — entry `[i]`
 * draws on top of entry `[i-1]`. The two outer fields differ in *bounds* and
 * *pipeline position*; an individual stack draws the same way in both scopes.
 *
 * See `docs/architecture/appearance.md § 7.1`.
 */
@Serializable
data class OverlayStyle(
    val source: OverlaySource,
    /** 0..1, applied on top of the source's own alpha. */
    val opacity: Float = 0.2f,
    val blendMode: NodeBlendMode = NodeBlendMode.Normal,
)

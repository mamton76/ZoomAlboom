package com.mamton.zoomalbum.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class TileMode { None, Stretch, Cover, Contain, Repeat }

@Serializable
enum class AnchorMode {
    CameraLocked,
    WorldLocked,
    // FrameLocked — future: clip the album background to a specific frame and
    // transform it with that frame's local space.
}

/**
 * Tiling parameters shared by sources whose pixel content is external (textures).
 *
 * Procedural patterns own their own positioning (`cellSize`, `spacing`, `originX/Y`
 * on each variant) and do not carry a [TileData].
 */
@Serializable
data class TileData(
    val tileMode: TileMode = TileMode.None,
    val tileOriginX: Float = 0f,
    val tileOriginY: Float = 0f,
    val tileWidth: Float = 200f,
    val tileHeight: Float = 200f,
)

/**
 * Polymorphic payload of a background — the *what*, decoupled from the *where*.
 *
 * Used both for album backgrounds (via [AlbumBackground], which adds an anchor)
 * and for frame backgrounds (via `FrameAppearance.background`, where the frame
 * *is* the anchor — no [AnchorMode] is stored).
 *
 * @SerialName values are stable on-disk identifiers. Do not rename without a migration.
 */
@Serializable
sealed class BackgroundData {
    abstract val opacity: Float

    @Serializable
    @SerialName("Solid")
    data class SolidBackgroundData(
        val color: String = "#000000",
        override val opacity: Float = 1f,
    ) : BackgroundData()

    @Serializable
    @SerialName("Texture")
    data class TextureBackgroundData(
        val textureRefId: String,
        val tile: TileData = TileData(),
        override val opacity: Float = 1f,
    ) : BackgroundData()

    @Serializable
    @SerialName("Procedural")
    data class ProceduralBackgroundData(
        val pattern: ProceduralPattern,
        /**
         * Optional solid fill drawn under the pattern. Lets users set a
         * background color for patterns that have gaps (Grid lines, dots,
         * gradients, noise). `null` = no fill (current behavior — whatever is
         * behind the layer shows through). Alpha-aware via 8-char hex.
         * Watercolor's own `baseColor` will overwrite this since it draws its
         * own full-rect wash.
         */
        val fillColor: String? = null,
        override val opacity: Float = 1f,
    ) : BackgroundData()
}

/**
 * Album-level background: source data + anchor mode (camera-locked / world-locked).
 *
 * Frame-level backgrounds use [BackgroundData] via `FrameAppearance.background` —
 * the frame is implicitly the anchor (no anchor mode stored).
 */
@Serializable
data class AlbumBackground(
    val data: BackgroundData,
    val anchorMode: AnchorMode = AnchorMode.CameraLocked,
)

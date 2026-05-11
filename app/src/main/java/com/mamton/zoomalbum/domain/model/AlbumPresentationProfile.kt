package com.mamton.zoomalbum.domain.model

import kotlinx.serialization.Serializable

/**
 * Album-level declaration of the intended screen shape for viewing/presenting.
 *
 * The infinite canvas stays infinite — this profile only shapes new-frame
 * creation defaults, View-mode camera fit, and editor overlays.
 *
 * See docs/architecture/presentation-profile.md.
 */
@Serializable
data class AlbumPresentationProfile(
    val aspectRatio: AspectRatio = AspectRatio.R_16_9,
    val orientation: Orientation = Orientation.Landscape,
    val defaultFitMode: FrameFitMode = FrameFitMode.CONTAIN,
    val defaultOutsideMode: OutsideFrameMode = OutsideFrameMode.ALBUM_BACKGROUND,
    val safeAreaInset: Float = 0.1f,
)

@Serializable
sealed class AspectRatio {
    @Serializable object R_16_9 : AspectRatio()
    @Serializable object R_9_16 : AspectRatio()
    @Serializable object R_4_3 : AspectRatio()
    @Serializable object R_3_4 : AspectRatio()
    @Serializable object Square : AspectRatio()
    @Serializable object Free : AspectRatio()
    @Serializable data class Custom(val w: Int, val h: Int) : AspectRatio()
}

@Serializable
enum class Orientation { Landscape, Portrait }

/**
 * How a frame fits into the device viewport in View/Present mode.
 *
 * CONTAIN — fit whole frame; min(scaleX, scaleY); MVP default.
 * COVER   — fill viewport; max(scaleX, scaleY); may crop frame content.
 * STRETCH — independent X/Y scale; ignores aspect ratio.
 */
@Serializable
enum class FrameFitMode { CONTAIN, COVER, STRETCH }

/**
 * What fills the viewport outside the frame's content area in CONTAIN fit.
 *
 * ALBUM_BACKGROUND  — fall back to AlbumBackground (cheapest; MVP default).
 * SOLID_FILL        — single configurable color.
 * BLURRED_BACKDROP  — frame content sampled and blurred (post-MVP).
 */
@Serializable
enum class OutsideFrameMode { ALBUM_BACKGROUND, SOLID_FILL, BLURRED_BACKDROP }

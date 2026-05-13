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

    val defaultTransitionPreset: TransitionPreset = TransitionPreset.SOFT,
    val defaultEasing: EasingType = EasingType.EASE_IN_OUT,
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

/**
 * Numeric `w/h` ratio for the aspect, or `null` for [AspectRatio.Free]
 * (caller should skip aspect-ratio fitting).
 *
 * Canonical ratios (R_16_9, R_9_16, etc.) encode orientation in the value
 * itself, so [Orientation] is informational and does not affect the result.
 */
fun AspectRatio.numericRatio(): Float? = when (this) {
    AspectRatio.R_16_9 -> 16f / 9f
    AspectRatio.R_9_16 -> 9f / 16f
    AspectRatio.R_4_3 -> 4f / 3f
    AspectRatio.R_3_4 -> 3f / 4f
    AspectRatio.Square -> 1f
    AspectRatio.Free -> null
    is AspectRatio.Custom -> w.toFloat() / h.toFloat()
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

/**
 * Camera interpolation curve for frame-to-frame transitions.
 *
 * Shared between [AlbumPresentationProfile.defaultEasing] and the future
 * per-edge transition editor (see docs/architecture/future-features/transition-editor.md).
 */
@Serializable
enum class EasingType { LINEAR, EASE_IN, EASE_OUT, EASE_IN_OUT }

/**
 * Album-level motion style for frame-to-frame transitions. Combines an
 * easing curve, a duration multiplier on the distance-derived auto-duration,
 * and an optional mid-path camera "breath".
 *
 *  CALM    — ease-in-out, 1.2× auto duration, no zoom shift.
 *  SOFT    — ease-in-out, 1.0× auto duration, slight zoom-out at midpoint (MVP default).
 *  FAST    — ease-out, 0.5× auto duration, snappy.
 *  LINEAR  — linear, 1.0× auto duration.
 *  CUSTOM  — per-segment overrides active (post-MVP transition editor only).
 *
 * See docs/architecture/future-features/transition-editor.md.
 */
@Serializable
enum class TransitionPreset { CALM, SOFT, FAST, LINEAR, CUSTOM }

package com.mamton.zoomalbum.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// ── Crop ─────────────────────────────────────────────────────────────────────

/**
 * How the source media is fitted into the node's bounding rect.
 */
@Serializable
enum class CropMode {
    /** Whole image visible inside bounds; empty space allowed. */
    Fit,
    /** Fills entire bounds; parts of image may be cropped; respects focal point. */
    Fill,
    /** User pans/zooms inside bounds via [CropSettings.offsetX]/[CropSettings.offsetY]/[CropSettings.zoom]. */
    Manual,
    /** Stretch to bounds ignoring aspect ratio. */
    Stretch,
}

/**
 * Per-media crop / framing. `null` `colorAdjustments` / etc. mean "no transform";
 * this struct has sensible defaults so passing the default value is a no-op.
 */
@Serializable
data class CropSettings(
    val mode: CropMode = CropMode.Fit,
    /** Manual pan offset within bounds (Manual mode), in world units. */
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
    /** Manual zoom within bounds (Manual mode). */
    val zoom: Float = 1f,
    /** Focal point for auto-crop, 0..1 relative to source pixels. */
    val focalX: Float = 0.5f,
    val focalY: Float = 0.5f,
)

// ── Parametric color adjustments ─────────────────────────────────────────────

/**
 * Parametric color grading applied to the media pixels before overlays.
 * All fields are in `-1..1` and stack additively; `0` = identity.
 *
 * Rendering of every field is incremental — the data shape lands now; the
 * renderer can wire up brightness/contrast first and grow from there.
 */
@Serializable
data class MediaColorAdjustments(
    val brightness: Float = 0f,
    val contrast: Float = 0f,
    val saturation: Float = 0f,
    val temperature: Float = 0f,
    val tint: Float = 0f,
    val exposure: Float = 0f,
    val highlights: Float = 0f,
    val shadows: Float = 0f,
    val blur: Float = 0f,
    val sharpen: Float = 0f,
    val vignette: Float = 0f,
)

// ── Media frame decoration (the Polaroid-style picture-frame around one media) ──

/**
 * How a [MediaFrameDecoration] asset is stretched/laid out over the media's rect.
 */
@Serializable
enum class MediaFrameDecorationMode {
    /** PNG stretched over entire object — simple, fine for textures/vignettes. */
    Stretch,
    /** Corners unscaled; edges scaled one axis only — required for real photo frames. */
    NineSlice,
}

/**
 * Decorative picture-frame asset around a single media node (Polaroid border,
 * mat, wooden frame, etc.).
 *
 * Despite the word "frame", this is **not** a [CanvasNode.Frame] and **not** a
 * [FrameAppearance]. It's media-local decoration; see
 * `docs/architecture/media-appearance.md § Media frame decoration`.
 */
@Serializable
data class MediaFrameDecoration(
    val assetUri: String,
    val opacity: Float = 1f,
    val mode: MediaFrameDecorationMode = MediaFrameDecorationMode.Stretch,
    // Nine-slice insets — ignored in Stretch mode.
    val sliceLeft: Float = 0f,
    val sliceTop: Float = 0f,
    val sliceRight: Float = 0f,
    val sliceBottom: Float = 0f,
    // Usable content area inside the decoration (e.g. Polaroid caption strip).
    val contentInsetLeft: Float = 0f,
    val contentInsetTop: Float = 0f,
    val contentInsetRight: Float = 0f,
    val contentInsetBottom: Float = 0f,
)

// ── Caption ──────────────────────────────────────────────────────────────────

/**
 * Optional text caption rendered with the media (e.g. the printed strip under a
 * Polaroid). MVP shape; styling grows as the caption-rendering slice lands.
 */
@Serializable
data class CaptionStyle(
    val text: String = "",
    val show: Boolean = true,
    val fontSize: Float = 14f,
    val color: String = "#000000",
)

// ── MediaAppearance ──────────────────────────────────────────────────────────

/**
 * Non-destructive visual styling for a [CanvasNode.Media].
 *
 * One of the two concrete [NodeAppearance] subclasses (alongside [FrameAppearance]).
 * Owns the shared base fields plus media-specific concerns: crop, color
 * adjustments, an ordered overlay stack, an optional decorative photo-frame, and
 * an optional caption.
 *
 * `null` on a [CanvasNode.Media] means "default rendering" — the renderer paints
 * the cropped source with no extra layers.
 */
@Serializable
@SerialName("MediaAppearance")
data class MediaAppearance(
    override val opacity: Float = 1f,
    override val cornerRadius: Float = 0f,
    override val border: BorderStyle? = null,
    override val shadow: ShadowStyle? = null,
    val crop: CropSettings = CropSettings(),
    val colorAdjustments: MediaColorAdjustments? = null,
    /**
     * Object-level overlays above this media's pixels, bounded by the media rect.
     * Ordered list: entry `[i]` composites above entry `[i-1]`. Empty = no overlays.
     */
    val overlays: List<OverlayStyle> = emptyList(),
    /** Decorative picture-frame around this single media. NOT a [CanvasNode.Frame]. */
    val frameDecoration: MediaFrameDecoration? = null,
    val caption: CaptionStyle? = null,
) : NodeAppearance()

// ── Style presets ────────────────────────────────────────────────────────────

/**
 * A saved [MediaAppearance] recipe with a user-facing name.
 *
 * Persisted per-album (scene graph) or globally (app-level preferences) — the
 * preset library UI lands in a later slice.
 */
@Serializable
data class MediaStylePreset(
    val id: String,
    val name: String,
    val appearance: MediaAppearance,
)

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

// ── Media decorations (visual layer stack around one media node) ────────────────

/**
 * How a [MediaDecoration] asset is stretched/laid out over the media's rect.
 */
@Serializable
enum class MediaDecorationMode {
    /** PNG stretched over entire object — simple, fine for textures/vignettes. */
    Stretch,
    /** Corners unscaled; edges scaled one axis only — required for real photo frames. */
    NineSlice,
}

/** Where a [MediaDecoration] layer draws relative to the media content. */
@Serializable
enum class DecorationPlacement {
    /** Over the media + overlays (frame, tape, sticker, label…). */
    Above,
    /** Under the media (mat, backing paper — shows in the opening margins). */
    Below,
}

/**
 * One decorative visual layer for a media node — a frame, tape, bow, sticker,
 * handwritten label, torn/burnt-paper edge, ticket stub, etc. A media node owns
 * an ordered [MediaAppearance.decorations] stack of these.
 *
 * This is **not** a [CanvasNode.Frame] / [FrameAppearance]: it's a media-local
 * visual layer that carries **no** clip of its own — the media's shape is owned
 * by [MediaAppearance.opening] + [NodeAppearance.contentMask]. See
 * `docs/architecture/media-appearance.md` ("content-model refactor").
 */
@Serializable
data class MediaDecoration(
    /** Stable id — paint/reorder order + future item-level preset overrides. */
    val id: String,
    val assetUri: String,
    val opacity: Float = 1f,
    val mode: MediaDecorationMode = MediaDecorationMode.Stretch,
    val placement: DecorationPlacement = DecorationPlacement.Above,
    // Nine-slice insets (fractions 0..1 of the asset edge) — ignored in Stretch.
    val sliceLeft: Float = 0f,
    val sliceTop: Float = 0f,
    val sliceRight: Float = 0f,
    val sliceBottom: Float = 0f,
)

// ── Media opening (rectangular content-area slot) ──────────────────────────────

/**
 * The rectangular content-area slot inside the node rect: the media is drawn
 * only inside `[insetLeft..1-insetRight] × [insetTop..1-insetBottom]` (fractions
 * 0..1 of the node edge), so a frame/mat decoration can sit in the margin
 * around it. A *resize/inset*, distinct from [CropSettings] (which fits the
 * media **within** this area). All-zero / `null` = media fills the whole rect.
 *
 * Rectangular only — arbitrary opening shapes go through [NodeAppearance.contentMask].
 */
@Serializable
data class MediaOpening(
    val insetLeft: Float = 0f,
    val insetTop: Float = 0f,
    val insetRight: Float = 0f,
    val insetBottom: Float = 0f,
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
 * adjustments, an ordered overlay stack, a rectangular content opening, an
 * ordered decoration-layer stack, and an optional caption.
 *
 * `null` on a [CanvasNode.Media] means "default rendering" — the renderer paints
 * the cropped source with no extra layers.
 */
@Serializable
@SerialName("MediaAppearance")
data class MediaAppearance(
    override val opacity: Float = 1f,
    override val cornerRadius: Float = 0f,
    /**
     * Object-level overlays above this media's pixels, bounded by the media rect.
     * Ordered list: entry `[i]` composites above entry `[i-1]`. Empty = no overlays.
     */
    override val overlays: List<OverlayStyle> = emptyList(),
    override val border: BorderStyle? = null,
    override val shadow: ShadowStyle? = null,
    override val contentMask: AlphaMask? = null,
    val crop: CropSettings = CropSettings(),
    val colorAdjustments: MediaColorAdjustments? = null,
    /** Rectangular content-area slot — the media is resized into it; decorations fill the margin. */
    val opening: MediaOpening? = null,
    /** Ordered stack of decorative visual layers around this media. NOT [CanvasNode.Frame]s. */
    val decorations: List<MediaDecoration> = emptyList(),
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

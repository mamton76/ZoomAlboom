package com.mamton.zoomalbum.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Soft per-pixel alpha mask applied inside the node's clip shape.
 *
 * Composes with the node's existing rounded-rect clip via the renderer's
 * offscreen compositing layer + `BlendMode.DstIn` step (see
 * `docs/architecture/appearance.md § 12.4`). The clip determines hard bounds
 * (pixels outside the clip stay invisible); the mask attenuates the alpha
 * *inside* those bounds continuously between 0 and 1.
 *
 * `null` on a [NodeAppearance] means "no mask" — fast path, no offscreen layer.
 *
 * ### Semantics
 *
 * - **[source]** picks the alpha source (an image, a gradient, or a procedural
 *   pattern). The renderer reads alpha from the source per [AlphaMaskSource]
 *   docs and writes it into the destination layer via `DstIn`, so the node's
 *   own pixels are kept only where the source has non-zero alpha.
 *
 * - **[invert]** flips the mask sense. `false` (default) means "source alpha
 *   = result opacity" — where the source is opaque, the node is visible.
 *   `true` means "source alpha = result transparency" — where the source is
 *   opaque, the node becomes transparent (and vice versa). Mathematically:
 *   `effective_alpha = invert ? 1 - source_alpha : source_alpha`. Useful for
 *   re-using the same mask asset as either a cutout or a stencil.
 *
 * ### Knock-ons (see § 12.5)
 *
 * - Border and shadow follow the clip rect, not the mask silhouette. A node
 *   with an image mask still has a rectangular shadow.
 * - Hit-testing stays AABB — tapping a pixel the mask hides still selects the
 *   node. The mask is purely visual.
 * - LOD: at Preview / Simplified tiers the renderer drops the offscreen layer
 *   entirely and renders the unmasked node; mask only at Full.
 */
@Serializable
data class AlphaMask(
    val source: AlphaMaskSource,
    val invert: Boolean = false,
)

/**
 * Where the mask alpha comes from.
 *
 * Four variants by design, mirroring the shape of [BackgroundData] and
 * [OverlaySource]: a sealed `source` with `Image` / `Gradient` / `Procedural`
 * variants is the third place in the codebase using this pattern. The renderer
 * reads alpha per variant; see each variant's docs.
 *
 * `@SerialName` values are stable on-disk identifiers — do not rename without
 * a migration.
 */
@Serializable
sealed class AlphaMaskSource {

    /**
     * Pixel-data mask sourced from an image asset.
     *
     * @property maskRefId Stable identifier of the asset — same string scheme
     * as [CanvasNode.Media.mediaRefId]; the renderer resolves it via Coil.
     * Today this is a URI/path; once the `media_library` table lands (`todo.md
     * § 1.4`) it becomes a proper FK and the registry deduplicates assets
     * across mask + media usage.
     *
     * @property channel How to convert source pixels to alpha:
     * - [MaskChannel.Luminance] — average R/G/B luminance is the alpha. Use for
     *   black-and-white PNG / JPG masks (white = opaque, black = transparent).
     *   Source alpha channel is ignored.
     * - [MaskChannel.Alpha] — read the source's own alpha channel directly.
     *   Use for transparent PNGs where the shape is already encoded as alpha.
     *
     * @property fitMode How the source image is laid out inside the node's
     * bounding rect:
     * - [MaskFitMode.Stretch] — distort to fill the rect (ignore source aspect).
     * - [MaskFitMode.Fit] — scale uniformly to fit *inside* the rect (preserve
     *   aspect; pixels outside the scaled source area are alpha=0).
     * - [MaskFitMode.Fill] — scale uniformly to *cover* the rect (preserve
     *   aspect; source is cropped at the rect edges).
     */
    @Serializable
    @SerialName("Image")
    data class Image(
        val maskRefId: String,
        val channel: MaskChannel = MaskChannel.Luminance,
        val fitMode: MaskFitMode = MaskFitMode.Stretch,
    ) : AlphaMaskSource()

    /**
     * Linear gradient mask — alpha ramps along a vector through the node rect.
     *
     * @property angleDeg Direction of the ramp in degrees. `0` = left→right,
     * `90` = top→bottom, `180` = right→left, `270` = bottom→top. The renderer
     * derives `start` / `end` points from this angle plus the node's bounding
     * rect.
     *
     * @property stops Sorted-by-position [AlphaStop] list. Each carries an
     * `alpha` value the renderer interpolates between. Position is `0..1`
     * along the gradient axis. At least two stops are expected; the editor
     * enforces it, the renderer falls back to a constant alpha if there are
     * fewer.
     */
    @Serializable
    @SerialName("LinearGradient")
    data class LinearGradient(
        val angleDeg: Float = 0f,
        val stops: List<AlphaStop>,
    ) : AlphaMaskSource()

    /**
     * Radial gradient mask — alpha ramps outward from a centre point.
     *
     * Coordinates are normalised relative to the node's bounding rect: `0..1`
     * in both axes. `centerX = 0.5, centerY = 0.5` is the rect centre; `0, 0`
     * is the top-left corner.
     *
     * [radiusX] / [radiusY] are also normalised; the renderer multiplies by
     * the rect's width / height respectively. Different X/Y radii produce an
     * elliptical ramp (the renderer applies `scale(1f, radiusY/radiusX)`
     * around the centre to stretch a circular brush — see § 12.4).
     */
    @Serializable
    @SerialName("RadialGradient")
    data class RadialGradient(
        val centerX: Float = 0.5f,
        val centerY: Float = 0.5f,
        val radiusX: Float = 0.5f,
        val radiusY: Float = 0.5f,
        val stops: List<AlphaStop>,
    ) : AlphaMaskSource()

    /**
     * Procedural mask — alpha extracted from a [ProceduralPattern]'s rendered
     * output. The pattern's luminance becomes the mask alpha (per the same
     * "luminance → alpha" rule as [MaskChannel.Luminance] for images).
     *
     * This is the **same** [ProceduralPattern] sealed type already used by
     * [BackgroundData.ProceduralBackgroundData] and [OverlaySource.Procedural]
     * — no new procedural type. Anything that works as a paper grain
     * background or noise overlay also works as a stencil mask.
     */
    @Serializable
    @SerialName("Procedural")
    data class Procedural(val pattern: ProceduralPattern) : AlphaMaskSource()
}

/**
 * One alpha stop along an [AlphaMaskSource.LinearGradient] or
 * [AlphaMaskSource.RadialGradient].
 *
 * Distinct from the existing [GradientStop] (which carries an RGBA `color`
 * string for procedural colour gradients). Mask stops only carry alpha — the
 * renderer always builds a white brush and reads stop alpha into the
 * destination layer. Sharing the same name as [GradientStop] would invite
 * misuse, so this type is `AlphaStop`.
 *
 * Both fields are `0..1`. The editor sorts by [position] before commit; the
 * renderer sorts defensively.
 */
@Serializable
data class AlphaStop(
    val position: Float,
    val alpha: Float,
)

/**
 * How an image-source mask's pixel data is read.
 *
 * - [Luminance] — convert source RGB to grayscale (average R/G/B), use as alpha.
 *   Source alpha channel is ignored. Standard for black-and-white PNG masks.
 * - [Alpha] — use the source's own alpha channel directly. Standard for
 *   transparent PNG masks where the shape is already encoded as alpha.
 *
 * Has no meaning for gradient or procedural sources — those carry alpha
 * directly (gradients) or via luminance of their rendered output (procedural).
 */
@Serializable
enum class MaskChannel { Luminance, Alpha }

/**
 * How an image-source mask is laid out inside the node's bounding rect.
 *
 * - [Stretch] — distort to fill the rect; source aspect ignored.
 * - [Fit] — scale uniformly to fit inside the rect; preserves aspect, alpha=0
 *   in the letterboxed area outside the source.
 * - [Fill] — scale uniformly to cover the rect; preserves aspect, source is
 *   cropped at rect edges.
 *
 * Only meaningful for [AlphaMaskSource.Image]. Gradients are defined directly
 * in normalised-rect space; procedural patterns tile per their own
 * coordinate-space rules (`ProceduralPattern` docs).
 */
@Serializable
enum class MaskFitMode { Stretch, Fit, Fill }

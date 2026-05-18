package com.mamton.zoomalbum.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Parameter-only definition of a procedurally-rendered background pattern.
 *
 * Procedural patterns are NOT image files — they are drawn from parameters
 * by the renderer. They are the payload of [BackgroundData.ProceduralBackgroundData], the
 * third [BackgroundData] variant alongside `Solid` and `Texture`.
 *
 * Length parameters (`cellSize`, `lineWidth`, `spacing`, `dotRadius`, etc.)
 * are interpreted in the same coordinate space the renderer is currently
 * drawing into:
 *
 * - For an [AlbumBackground] with [AnchorMode.CameraLocked] → screen pixels.
 * - For an [AlbumBackground] with [AnchorMode.WorldLocked] → world units.
 * - For a frame background (`FrameAppearance.background`) → world units (frame-local).
 *
 * That way line thickness stays visually constant on screen for camera-locked
 * and scales with zoom for world-locked / frame backgrounds, which matches
 * user expectations.
 */
@Serializable
sealed class ProceduralPattern {

    @Serializable
    @SerialName("Grid")
    data class Grid(
        val lineColor: String = "#FFFFFF",
        val lineWidth: Float = 1f,
        val cellSize: Float = 50f,
        val originX: Float = 0f,
        val originY: Float = 0f,
    ) : ProceduralPattern()

    @Serializable
    @SerialName("DotGrid")
    data class DotGrid(
        val dotColor: String = "#FFFFFF",
        val dotRadius: Float = 2f,
        val spacing: Float = 40f,
        val originX: Float = 0f,
        val originY: Float = 0f,
    ) : ProceduralPattern()

    @Serializable
    @SerialName("RuledPaper")
    data class RuledPaper(
        val lineColor: String = "#A0C4FF",
        val lineSpacing: Float = 30f,
        val lineWidth: Float = 1f,
        val originY: Float = 0f,
        /** Hex color of the vertical margin line; null = no margin. */
        val marginColor: String? = "#FFB0B0",
        val marginX: Float = 60f,
    ) : ProceduralPattern()

    @Serializable
    @SerialName("GraphPaper")
    data class GraphPaper(
        val minorColor: String = "#C0E0FF",
        val majorColor: String = "#80B0FF",
        val minorSpacing: Float = 20f,
        /** Major line drawn every `majorEvery` minor lines. */
        val majorEvery: Int = 5,
        val lineWidth: Float = 1f,
        val originX: Float = 0f,
        val originY: Float = 0f,
    ) : ProceduralPattern()

    @Serializable
    @SerialName("PaperGrain")
    data class PaperGrain(
        val color: String = "#000000",
        val intensity: Float = 0.08f,
        /** Approximate dot size in coordinate-space units. */
        val grainSize: Float = 2f,
        /** Density: dots per (10 × 10) coordinate-space units. */
        val density: Float = 1.5f,
        val seed: Int = 1,
    ) : ProceduralPattern()

    @Serializable
    @SerialName("Noise")
    data class Noise(
        val color: String = "#FFFFFF",
        val intensity: Float = 0.2f,
        val grainSize: Float = 4f,
        val density: Float = 0.8f,
        val seed: Int = 1,
    ) : ProceduralPattern()

    @Serializable
    @SerialName("Gradient")
    data class Gradient(
        /**
         * Color stops along the gradient axis. Positions are in `[0..1]`.
         *
         * MVP invariant: at least 2 stops, and the first stop (sorted by
         * position) is at exactly `0f` and the last at exactly `1f`. The
         * editor enforces this; the renderer sorts defensively and falls back
         * to a solid fill / skip if fewer than 2 stops are present.
         *
         * Stops may carry alpha via the `color` hex (e.g. `#80FF0000` = 50%
         * red); the renderer preserves it through `Color.toArgb()`.
         */
        val stops: List<GradientStop> = listOf(
            GradientStop(0f, "#000000"),
            GradientStop(1f, "#444444"),
        ),
        val kind: GradientKind = GradientKind.Linear,
        /** Linear gradient angle in degrees. 0 = left→right, 90 = top→bottom. */
        val angleDeg: Float = 90f,
    ) : ProceduralPattern()

    @Serializable
    @SerialName("Watercolor")
    data class Watercolor(
        val baseColor: String = "#FFEEDD",
        val splotchColor: String = "#FFD9B5",
        val splotchCount: Int = 8,
        val splotchRadius: Float = 200f,
        val splotchAlpha: Float = 0.35f,
        val seed: Int = 1,
    ) : ProceduralPattern()
}

@Serializable
enum class GradientKind { Linear, Radial }

/**
 * One color stop along a [ProceduralPattern.Gradient].
 *
 * `position` is stored in `[0..1]` internally; the editor may display it as
 * `0..100%`. `color` is a hex string parsed via `toColorInt()` — supports
 * alpha as `#AARRGGBB` so stops can fade in/out.
 */
@Serializable
data class GradientStop(
    val position: Float,
    val color: String,
)

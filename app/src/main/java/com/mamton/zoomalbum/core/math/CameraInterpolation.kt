package com.mamton.zoomalbum.core.math

import com.mamton.zoomalbum.domain.model.EasingType
import com.mamton.zoomalbum.domain.model.TransitionPreset
import kotlin.math.abs
import kotlin.math.ln

/**
 * Snapshot of an in-flight camera focus animation. Transient — not persisted.
 *
 * The view does not interpolate; the ViewModel ticks [Camera] state directly on
 * a coroutine. Pan / pinch gestures cancel the animation and clear this field.
 */
data class CameraAnimation(
    val from: Camera,
    val to: Camera,
    val startTimeMs: Long,
    val durationMs: Long,
    val easing: EasingType,
)

/**
 * Camera interpolation utilities. Used by `FocusNode` to animate from the
 * current camera to a target camera derived from a frame's [Transform.toCamera].
 */
object CameraInterpolation {

    /**
     * Linear interpolate between two cameras at progress [t] in [0..1], applying [easing].
     * Rotation uses shortest-angular-path lerp so 350° → 10° rotates +20°, not -340°.
     */
    fun interpolate(from: Camera, to: Camera, t: Float, easing: EasingType): Camera {
        val eased = easing.apply(t.coerceIn(0f, 1f))
        return Camera(
            cx = lerp(from.cx, to.cx, eased),
            cy = lerp(from.cy, to.cy, eased),
            scale = lerp(from.scale, to.scale, eased),
            rotation = lerpAngle(from.rotation, to.rotation, eased),
        )
    }

    /**
     * Resolves a [TransitionPreset] (+ profile easing fallback) plus a distance/zoom
     * measurement into a concrete duration and easing for one transition.
     *
     * Auto-duration formula (see docs/architecture/future-features/transition-editor.md):
     *   base + worldDistance * 0.8 + |log2(scaleRatio)| * 400, clamped to [400, 5000] ms.
     */
    fun resolveTransition(
        preset: TransitionPreset,
        profileEasing: EasingType,
        from: Camera,
        to: Camera,
    ): Pair<Long, EasingType> {
        val dx = to.cx - from.cx
        val dy = to.cy - from.cy
        val distance = kotlin.math.sqrt(dx * dx + dy * dy)
        val scaleRatio = if (from.scale > 0f && to.scale > 0f) {
            abs(ln(to.scale / from.scale) / LN_2)
        } else 0f

        val auto = (BASE_MS + distance * DISTANCE_FACTOR + scaleRatio * ZOOM_FACTOR_MS)
            .coerceIn(MIN_DURATION_MS, MAX_DURATION_MS)

        val multiplier = when (preset) {
            TransitionPreset.CALM -> 1.2f
            TransitionPreset.SOFT -> 1.0f
            TransitionPreset.FAST -> 0.5f
            TransitionPreset.LINEAR -> 1.0f
            TransitionPreset.CUSTOM -> 1.0f
        }
        val easing = when (preset) {
            TransitionPreset.CALM, TransitionPreset.SOFT -> EasingType.EASE_IN_OUT
            TransitionPreset.FAST -> EasingType.EASE_OUT
            TransitionPreset.LINEAR -> EasingType.LINEAR
            TransitionPreset.CUSTOM -> profileEasing
        }
        return (auto * multiplier).toLong() to easing
    }

    private const val BASE_MS = 600f
    private const val DISTANCE_FACTOR = 0.8f
    private const val ZOOM_FACTOR_MS = 400f
    private const val MIN_DURATION_MS = 400f
    private const val MAX_DURATION_MS = 5000f
    private val LN_2 = ln(2f)
}

/** Easing curve applied to normalized progress t ∈ [0, 1]. */
fun EasingType.apply(t: Float): Float = when (this) {
    EasingType.LINEAR -> t
    EasingType.EASE_IN -> t * t
    EasingType.EASE_OUT -> 1f - (1f - t) * (1f - t)
    EasingType.EASE_IN_OUT -> if (t < 0.5f) 2f * t * t else 1f - 2f * (1f - t) * (1f - t)
}

private fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t

/** Shortest-path angular lerp in degrees. */
private fun lerpAngle(a: Float, b: Float, t: Float): Float {
    val delta = ((b - a) % 360f + 540f) % 360f - 180f
    return a + delta * t
}

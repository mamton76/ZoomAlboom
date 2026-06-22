package com.mamton.zoomalbum.domain.model

import kotlinx.serialization.Serializable

@Serializable
sealed class CanvasNode {
    abstract val id: String
    abstract val transform: Transform
    abstract val visibilityPolicy: VisibilityPolicy?

    /**
     * Whether this node participates in frame membership computation. Default `true`.
     * Override to `false` for backgrounds, camera-locked, or decoration-only nodes that
     * should never be reported as members of any frame.
     * See `docs/architecture/frame-membership.md`.
     */
    open val isFrameBindable: Boolean get() = true

    @Serializable
    data class Media(
        override val id: String,
        override val transform: Transform,
        val mediaRefId: String,
        val mediaType: MediaType = MediaType.IMAGE,
        val tags: List<String> = emptyList(),
        // Native source pixel dimensions (after EXIF rotation). 0 = unknown.
        // Used by LOD to compute source-px-per-screen-px; not used for layout.
        val intrinsicPixelWidth: Int = 0,
        val intrinsicPixelHeight: Int = 0,
        /**
         * Non-destructive visual styling for this media: overlays, border,
         * shadow, crop, color adjustments, decorative frame, caption.
         * `null` = renderer defaults (cropped source only).
         */
        val appearance: MediaAppearance? = null,
        /**
         * Link to an app-level [MediaStylePreset]. `null` = unbound (the node's
         * own [appearance] is used as-is). When set, the effective appearance is
         * `resolveMediaAppearance(...)` = preset ∪ per-node section overrides.
         */
        val presetBinding: PresetBinding? = null,
        override val visibilityPolicy: VisibilityPolicy? = null,
    ) : CanvasNode()

    @Serializable
    data class Frame(
        override val id: String,
        override val transform: Transform,
        val label: String = "",
        val color: String = "#888888",
        /**
         * Manual membership overrides keyed by node id. Absent entries mean
         * "geometry decides." See `docs/architecture/frame-membership.md`.
         */
        val overrides: Map<String, FrameMembershipOverride> = emptyMap(),
        /**
         * Non-destructive visual styling for the frame: background, content
         * overlays, border, shadow, title, etc. `null` = renderer defaults.
         *
         * The frame is implicitly its own anchor — the [BackgroundData] inside
         * [FrameAppearance.background] carries no [AnchorMode].
         *
         * Legacy scene-graph JSON stored `background` directly on the frame;
         * the deserializer migrates it into `appearance.background` on read —
         * see `SceneGraphSerializer`.
         */
        val appearance: FrameAppearance? = null,
        override val visibilityPolicy: VisibilityPolicy? = null,
    ) : CanvasNode() {

        /** Convenience accessor: the frame's background, if any. */
        val background: BackgroundData? get() = appearance?.background
    }
}

@Serializable
enum class MediaType {
    IMAGE,        // raster photo/image
    VIDEO,        // video clip
    AUDIO,        // audio clip
    TEXT,         // inline text block
    STICKER,      // static sticker/illustration
    VECTOR_SHAPE, // SVG or vector primitive

    // future
    ANIMATED_PHOTO, // Live Photo / animated GIF
}

/** Returns a copy of this node with the given [transform], preserving all other fields. */
fun CanvasNode.withTransform(transform: Transform): CanvasNode = when (this) {
    is CanvasNode.Frame -> copy(transform = transform)
    is CanvasNode.Media -> copy(transform = transform)
}

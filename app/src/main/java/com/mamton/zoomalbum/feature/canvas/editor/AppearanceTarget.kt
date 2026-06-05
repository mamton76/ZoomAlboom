package com.mamton.zoomalbum.feature.canvas.editor

import com.mamton.zoomalbum.domain.model.CanvasNode

/**
 * Editing-set discriminator for a *universal* appearance concept (opacity,
 * corner radius, border, shadow, overlays — fields that live on both
 * `FrameAppearance` and `MediaAppearance` via the `NodeAppearance` base).
 *
 * Per `docs/architecture/appearance.md § 14.3`, universal concepts are visible
 * whenever the selection is homogeneous (all-frames or all-media). The host
 * sets [Frames] or [Media] at popup-open time based on which homogeneity
 * predicate matched; the popup body itself is type-agnostic — it just edits a
 * value — but the dispatch path differs (`SetFrameAppearance` vs
 * `SetMediaAppearance`), which is why the target carries the typed node list.
 */
sealed interface AppearanceTarget {
    val isEmpty: Boolean
    data object None : AppearanceTarget { override val isEmpty: Boolean get() = true }
    data class Frames(val nodes: List<CanvasNode.Frame>) : AppearanceTarget {
        override val isEmpty: Boolean get() = nodes.isEmpty()
    }
    data class Media(val nodes: List<CanvasNode.Media>) : AppearanceTarget {
        override val isEmpty: Boolean get() = nodes.isEmpty()
    }
}

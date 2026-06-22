package com.mamton.zoomalbum.feature.canvas.actions

import com.mamton.zoomalbum.domain.model.CanvasNode
import com.mamton.zoomalbum.feature.canvas.viewmodel.CanvasAction
import com.mamton.zoomalbum.feature.ide_ui.ui.FrameMembershipIntent

/**
 * Selection-scoped editor action. Each action knows its visual representation,
 * its visibility / enable predicates over a [SelectionContext], and the effect
 * it produces. The long-press popup iterates the catalog and renders each
 * action as either a text row (`Edit appearance`, `Duplicate`, …) or as an
 * inline button row group keyed by [ActionCategory] (z-order, frame membership).
 *
 * See `docs/architecture/editor-actions.md` for the full model (why a sealed
 * interface + effect type, how to add a new action, what lives outside the
 * catalog) and `docs/architecture/context-menu.md § 4 + § 6` for the menu
 * rendering convention.
 */
sealed interface EditorAction {
    val id: String
    val icon: String?
    val category: ActionCategory

    /**
     * Display label. A function (not a property) because some actions read
     * differently in single- vs. multi-selection contexts ("Duplicate" vs.
     * "Duplicate selection"). Most implementations ignore the parameter.
     */
    fun label(ctx: SelectionContext): String

    fun isVisible(ctx: SelectionContext): Boolean = true
    fun isEnabled(ctx: SelectionContext): Boolean = true

    /**
     * Translate a tap into the side-effect to perform. Returns `null` only when
     * the action is fundamentally inapplicable for the given context — visibility
     * gating via [isVisible] should normally prevent that case.
     */
    fun effect(ctx: SelectionContext): EditorActionEffect?
}

enum class ActionCategory {
    Edit,
    Navigation,
    Transform,
    ZOrder,
    Membership,
    SelectionMeta,
    Lifecycle,
}

/**
 * Things an [EditorAction] tap produces. The host (`CanvasScaffold`) holds the
 * mutable UI state (open sheets, pending dialogs) and runs the effect via a
 * single dispatcher. Adding a new effect kind is a one-place change.
 */
sealed interface EditorActionEffect {
    data class Dispatch(val action: CanvasAction) : EditorActionEffect
    data class FrameMembership(val intent: FrameMembershipIntent) : EditorActionEffect
    data object OpenAddSheet : EditorActionEffect

    /**
     * Play/pause for a single selected video from the context menu, routed to
     * the `CanvasScaffold`-level [VideoPlaybackController]. A menu alternative to
     * the gesture path (`DoubleTapRoute.PlayPauseVideo`) — both toggle the same
     * controller (`video.md § 4`).
     */
    data class ToggleVideoPlayback(val nodeId: String, val uri: String) : EditorActionEffect

    // Per-concept appearance editors (`docs/architecture/appearance.md § 14.1`).
    // Each opens a single-concept bottom sheet that edits one field of
    // FrameAppearance/MediaAppearance across the entire selection — the rest
    // of each node's appearance is preserved per-node.
    data object OpenOpacityEditor : EditorActionEffect
    data object OpenCornerRadiusEditor : EditorActionEffect
    data object OpenBorderEditor : EditorActionEffect
    data object OpenShadowEditor : EditorActionEffect
    data object OpenOverlaysEditor : EditorActionEffect
    data object OpenAlphaMaskEditor : EditorActionEffect
    data object OpenBackgroundEditor : EditorActionEffect
    data object OpenCropEditor : EditorActionEffect
    data object OpenColorAdjustmentsEditor : EditorActionEffect
    data object OpenOpeningEditor : EditorActionEffect
    data object OpenDecorationsEditor : EditorActionEffect
    data object OpenCaptionEditor : EditorActionEffect

    /** Opens the app-level media preset library for the current media selection. */
    data object OpenPresetLibrary : EditorActionEffect
}

/**
 * Snapshot of the editor state that an action needs to decide visibility and
 * dispatch. Built once per popup / bar render from `CanvasUiState` + the helper
 * derivations already living in `CanvasScaffold` (`pinDetachEnabled`,
 * `anyOverrideExists`, single-frame / single-media projections).
 */
data class SelectionContext(
    val selectedNodeIds: Set<String>,
    val anchorNodeId: String? = null,
    val singleSelectedFrame: CanvasNode.Frame? = null,
    val singleSelectedMedia: CanvasNode.Media? = null,
    val selectedFramesInOrder: List<CanvasNode.Frame> = emptyList(),
    val selectedMediaInOrder: List<CanvasNode.Media> = emptyList(),
    val pinDetachEnabled: Boolean = false,
    val anyOverrideExists: Boolean = false,
) {
    /**
     * `true` when the selection is non-empty and every selected node is a Frame.
     * Gates type-applicable popups per `docs/architecture/appearance.md § 14.3`
     * (Background / Title style / Content effect).
     */
    val isAllFrames: Boolean
        get() = selectedNodeIds.isNotEmpty() &&
            selectedFramesInOrder.size == selectedNodeIds.size

    /**
     * `true` when the selection is non-empty and every selected node is a Media.
     * Gates type-applicable popups per `docs/architecture/appearance.md § 14.3`
     * (Crop / Color adjustments / Frame decoration / Caption).
     */
    val isAllMedia: Boolean
        get() = selectedNodeIds.isNotEmpty() &&
            selectedMediaInOrder.size == selectedNodeIds.size
}

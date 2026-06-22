package com.mamton.zoomalbum.feature.canvas.actions

import com.mamton.zoomalbum.domain.model.MediaType
import com.mamton.zoomalbum.feature.canvas.editor.EditorTool
import com.mamton.zoomalbum.feature.canvas.viewmodel.CanvasAction
import com.mamton.zoomalbum.feature.ide_ui.ui.FrameMembershipIntent

// ── Edit ─────────────────────────────────────────────────────────────────────

// ── Per-concept appearance editors (appearance.md § 14.1) ────────────────────
// Universal concepts (opacity / corner radius / border / shadow / overlays) are
// fields on `NodeAppearance` base, so they show whenever the selection is
// homogeneous (all-frame or all-media). Mixed-type selections hide the universal
// concepts in MVP — per `to_discuss.md` the cross-type dispatch (two snapshot
// commands in one user action) would need Compound undo, which we deliberately
// deferred. Frame-only / media-only concepts gate on `isAllFrames` /
// `isAllMedia` per § 14.3.

private fun labelWithCount(base: String, n: Int): String =
    if (n >= 2) "$base ($n)" else base

private val SelectionContext.homogeneousCount: Int
    get() = selectedFramesInOrder.size.coerceAtLeast(selectedMediaInOrder.size)

data object EditOpacityAction : EditorAction {
    override val id = "edit.opacity"
    override val icon = "◐"
    override val category = ActionCategory.Edit
    override fun label(ctx: SelectionContext) =
        labelWithCount("Edit opacity", ctx.homogeneousCount)
    override fun isVisible(ctx: SelectionContext) = ctx.isAllFrames || ctx.isAllMedia
    override fun effect(ctx: SelectionContext) = EditorActionEffect.OpenOpacityEditor
}

data object EditCornerRadiusAction : EditorAction {
    override val id = "edit.cornerRadius"
    override val icon = "▢"
    override val category = ActionCategory.Edit
    override fun label(ctx: SelectionContext) =
        labelWithCount("Edit corner radius", ctx.homogeneousCount)
    override fun isVisible(ctx: SelectionContext) = ctx.isAllFrames || ctx.isAllMedia
    override fun effect(ctx: SelectionContext) = EditorActionEffect.OpenCornerRadiusEditor
}

data object EditBorderAction : EditorAction {
    override val id = "edit.border"
    override val icon = "▭"
    override val category = ActionCategory.Edit
    override fun label(ctx: SelectionContext) =
        labelWithCount("Edit border", ctx.homogeneousCount)
    override fun isVisible(ctx: SelectionContext) = ctx.isAllFrames || ctx.isAllMedia
    override fun effect(ctx: SelectionContext) = EditorActionEffect.OpenBorderEditor
}

data object EditShadowAction : EditorAction {
    override val id = "edit.shadow"
    override val icon = "◯"
    override val category = ActionCategory.Edit
    override fun label(ctx: SelectionContext) =
        labelWithCount("Edit shadow", ctx.homogeneousCount)
    override fun isVisible(ctx: SelectionContext) = ctx.isAllFrames || ctx.isAllMedia
    override fun effect(ctx: SelectionContext) = EditorActionEffect.OpenShadowEditor
}

data object EditOverlaysAction : EditorAction {
    override val id = "edit.overlays"
    override val icon = "▦"
    override val category = ActionCategory.Edit
    override fun label(ctx: SelectionContext) =
        labelWithCount("Edit overlays", ctx.homogeneousCount)
    override fun isVisible(ctx: SelectionContext) = ctx.isAllFrames || ctx.isAllMedia
    override fun effect(ctx: SelectionContext) = EditorActionEffect.OpenOverlaysEditor
}

data object EditContentMaskAction : EditorAction {
    override val id = "edit.contentMask"
    override val icon = "◍"
    override val category = ActionCategory.Edit
    override fun label(ctx: SelectionContext) =
        labelWithCount("Edit content mask", ctx.homogeneousCount)
    override fun isVisible(ctx: SelectionContext) = ctx.isAllFrames || ctx.isAllMedia
    override fun effect(ctx: SelectionContext) = EditorActionEffect.OpenAlphaMaskEditor
}

data object EditBackgroundAction : EditorAction {
    override val id = "edit.background"
    override val icon = "█"
    override val category = ActionCategory.Edit
    override fun label(ctx: SelectionContext) =
        labelWithCount("Edit background", ctx.selectedFramesInOrder.size)
    override fun isVisible(ctx: SelectionContext) = ctx.isAllFrames
    override fun effect(ctx: SelectionContext) = EditorActionEffect.OpenBackgroundEditor
}

/**
 * Enters the in-canvas `CropEdit` tool — see `docs/architecture/editor-tools.md
 * § 4.8`. v1 is gated to exactly one media node; multi-media `CropEdit` is out
 * of scope. Once in `CropEdit`, the node's `crop.mode` snaps to `Manual`; a
 * separate "Crop mode…" action that re-exposes the Fit / Fill / Stretch picker
 * is post-v1 (the existing sheet wiring under `OpenCropEditor` is preserved
 * for that follow-up).
 */
data object EditCropAction : EditorAction {
    override val id = "edit.crop"
    override val icon = "✂"
    override val category = ActionCategory.Edit
    override fun label(ctx: SelectionContext) = "Edit crop"
    override fun isVisible(ctx: SelectionContext) =
        ctx.isAllMedia && ctx.selectedMediaInOrder.size == 1
    override fun effect(ctx: SelectionContext): EditorActionEffect =
        EditorActionEffect.Dispatch(CanvasAction.SetActiveTool(EditorTool.CropEdit))
}

data object EditColorAdjustmentsAction : EditorAction {
    override val id = "edit.colorAdjustments"
    override val icon = "✺"
    override val category = ActionCategory.Edit
    override fun label(ctx: SelectionContext) =
        labelWithCount("Edit color adjustments", ctx.selectedMediaInOrder.size)
    override fun isVisible(ctx: SelectionContext) = ctx.isAllMedia
    override fun effect(ctx: SelectionContext) = EditorActionEffect.OpenColorAdjustmentsEditor
}

data object EditOpeningAction : EditorAction {
    override val id = "edit.opening"
    override val icon = "▣"
    override val category = ActionCategory.Edit
    override fun label(ctx: SelectionContext) =
        labelWithCount("Edit opening", ctx.selectedMediaInOrder.size)
    override fun isVisible(ctx: SelectionContext) = ctx.isAllMedia
    override fun effect(ctx: SelectionContext) = EditorActionEffect.OpenOpeningEditor
}

data object EditDecorationsAction : EditorAction {
    override val id = "edit.decorations"
    override val icon = "❖"
    override val category = ActionCategory.Edit
    override fun label(ctx: SelectionContext) =
        labelWithCount("Edit decorations", ctx.selectedMediaInOrder.size)
    override fun isVisible(ctx: SelectionContext) = ctx.isAllMedia
    override fun effect(ctx: SelectionContext) = EditorActionEffect.OpenDecorationsEditor
}

data object EditCaptionAction : EditorAction {
    override val id = "edit.caption"
    override val icon = "T"
    override val category = ActionCategory.Edit
    override fun label(ctx: SelectionContext) =
        labelWithCount("Edit caption", ctx.selectedMediaInOrder.size)
    override fun isVisible(ctx: SelectionContext) = ctx.isAllMedia
    override fun effect(ctx: SelectionContext) = EditorActionEffect.OpenCaptionEditor
}

data object PresetsAction : EditorAction {
    override val id = "preset.library"
    override val icon = "✦"
    override val category = ActionCategory.Edit
    override fun label(ctx: SelectionContext) = "Presets…"
    override fun isVisible(ctx: SelectionContext) = ctx.isAllMedia
    override fun effect(ctx: SelectionContext) = EditorActionEffect.OpenPresetLibrary
}

// ── Navigation ───────────────────────────────────────────────────────────────

/**
 * Edit-mode play/pause for a single selected video. Lets the user start
 * playback without leaving Edit, as the explicit affordance required by
 * `video.md § 4` ("no accidental playback in Edit"). Visible only for a single
 * video node; toggles the shared [VideoPlaybackController] via the host.
 */
data object PlayVideoAction : EditorAction {
    override val id = "media.play_video"
    override val icon = "▶"
    override val category = ActionCategory.Navigation
    override fun label(ctx: SelectionContext) = "Play / Pause"
    override fun isVisible(ctx: SelectionContext) =
        ctx.singleSelectedMedia?.mediaType == MediaType.VIDEO
    override fun effect(ctx: SelectionContext): EditorActionEffect? =
        ctx.singleSelectedMedia?.let {
            EditorActionEffect.ToggleVideoPlayback(it.id, it.mediaRefId)
        }
}

data object NavigateToFrameAction : EditorAction {
    override val id = "navigation.focus_frame"
    override val icon = null
    override val category = ActionCategory.Navigation
    override fun label(ctx: SelectionContext) = "Navigate to frame"
    override fun isVisible(ctx: SelectionContext) = ctx.singleSelectedFrame != null
    override fun effect(ctx: SelectionContext): EditorActionEffect? =
        ctx.singleSelectedFrame?.let { EditorActionEffect.Dispatch(CanvasAction.FocusNode(it.id)) }
}

// ── Z-order — single-selection only until multi-selection support ships;
//    relaxing the `isVisible` predicates below is all that's needed once
//    `docs/architecture/z-order.md § 3` is implemented end-to-end.

data object BringToFrontAction : EditorAction {
    override val id = "zorder.to_front"
    override val icon = "⤒"
    override val category = ActionCategory.ZOrder
    override fun label(ctx: SelectionContext) = "Bring to Front"
    override fun isVisible(ctx: SelectionContext) = ctx.selectedNodeIds.size == 1
    override fun effect(ctx: SelectionContext): EditorActionEffect? =
        ctx.selectedNodeIds.firstOrNull()?.let { EditorActionEffect.Dispatch(CanvasAction.BringToFront(it)) }
}

data object BringForwardAction : EditorAction {
    override val id = "zorder.forward"
    override val icon = "▲"
    override val category = ActionCategory.ZOrder
    override fun label(ctx: SelectionContext) = "Bring Forward"
    override fun isVisible(ctx: SelectionContext) = ctx.selectedNodeIds.size == 1
    override fun effect(ctx: SelectionContext): EditorActionEffect? =
        ctx.selectedNodeIds.firstOrNull()?.let { EditorActionEffect.Dispatch(CanvasAction.BringForward(it)) }
}

data object SendBackwardAction : EditorAction {
    override val id = "zorder.backward"
    override val icon = "▼"
    override val category = ActionCategory.ZOrder
    override fun label(ctx: SelectionContext) = "Send Backward"
    override fun isVisible(ctx: SelectionContext) = ctx.selectedNodeIds.size == 1
    override fun effect(ctx: SelectionContext): EditorActionEffect? =
        ctx.selectedNodeIds.firstOrNull()?.let { EditorActionEffect.Dispatch(CanvasAction.SendBackward(it)) }
}

data object SendToBackAction : EditorAction {
    override val id = "zorder.to_back"
    override val icon = "⤓"
    override val category = ActionCategory.ZOrder
    override fun label(ctx: SelectionContext) = "Send to Back"
    override fun isVisible(ctx: SelectionContext) = ctx.selectedNodeIds.size == 1
    override fun effect(ctx: SelectionContext): EditorActionEffect? =
        ctx.selectedNodeIds.firstOrNull()?.let { EditorActionEffect.Dispatch(CanvasAction.SendToBack(it)) }
}

// ── Frame membership ────────────────────────────────────────────────────────
// Visibility matches today's bar gating (`pinDetachEnabled`). Auto is shown
// only when at least one override exists on the selection. The host resolves
// single-vs-multi target frame: direct dispatch for single, FrameTargetPickerDialog
// for multi (handled inside `dispatchFrameMembership` in CanvasScaffold).

data object PinAction : EditorAction {
    override val id = "membership.pin"
    override val icon = "⊕"
    override val category = ActionCategory.Membership
    override fun label(ctx: SelectionContext) = "Pin"
    override fun isVisible(ctx: SelectionContext) = ctx.pinDetachEnabled
    override fun effect(ctx: SelectionContext): EditorActionEffect =
        EditorActionEffect.FrameMembership(FrameMembershipIntent.Pin)
}

data object DetachAction : EditorAction {
    override val id = "membership.detach"
    override val icon = "⊖"
    override val category = ActionCategory.Membership
    override fun label(ctx: SelectionContext) = "Detach"
    override fun isVisible(ctx: SelectionContext) = ctx.pinDetachEnabled
    override fun effect(ctx: SelectionContext): EditorActionEffect =
        EditorActionEffect.FrameMembership(FrameMembershipIntent.Detach)
}

data object AutoAction : EditorAction {
    override val id = "membership.auto"
    override val icon = "⟲"
    override val category = ActionCategory.Membership
    override fun label(ctx: SelectionContext) = "Auto"
    override fun isVisible(ctx: SelectionContext) = ctx.pinDetachEnabled && ctx.anyOverrideExists
    override fun effect(ctx: SelectionContext): EditorActionEffect =
        EditorActionEffect.FrameMembership(FrameMembershipIntent.Reset)
}

// ── Lifecycle ───────────────────────────────────────────────────────────────

data object DuplicateSelectionAction : EditorAction {
    override val id = "lifecycle.duplicate"
    override val icon = "⎘"
    override val category = ActionCategory.Lifecycle
    override fun label(ctx: SelectionContext) =
        if (ctx.selectedNodeIds.size >= 2) "Duplicate selection" else "Duplicate"
    override fun isVisible(ctx: SelectionContext) = ctx.selectedNodeIds.isNotEmpty()
    override fun effect(ctx: SelectionContext): EditorActionEffect =
        EditorActionEffect.Dispatch(CanvasAction.DuplicateSelection)
}

data object DeleteSelectionAction : EditorAction {
    override val id = "lifecycle.delete"
    override val icon = "✕"
    override val category = ActionCategory.Lifecycle
    override fun label(ctx: SelectionContext) =
        if (ctx.selectedNodeIds.size >= 2) "Delete selection" else "Delete"
    override fun isVisible(ctx: SelectionContext) = ctx.selectedNodeIds.isNotEmpty()
    override fun effect(ctx: SelectionContext): EditorActionEffect =
        EditorActionEffect.Dispatch(CanvasAction.DeleteSelection)
}

// ── Selection meta ───────────────────────────────────────────────────────────

data object ClearSelectionAction : EditorAction {
    override val id = "selection.clear"
    override val icon = null
    override val category = ActionCategory.SelectionMeta
    override fun label(ctx: SelectionContext) = "Clear selection"
    override fun isVisible(ctx: SelectionContext) = ctx.selectedNodeIds.size >= 2
    override fun effect(ctx: SelectionContext): EditorActionEffect =
        EditorActionEffect.Dispatch(CanvasAction.DeselectAll)
}

// ── Catalog ─────────────────────────────────────────────────────────────────

/**
 * Registry of every catalog-driven [EditorAction] that exists today. The long-
 * press popup (`buildEditContextMenuItems`) consumes this — text rows for Edit
 * / Navigation / Lifecycle / SelectionMeta categories, inline button rows for
 * ZOrder / Membership. Multi-line / per-concept popups (`Edit frame appearance`
 * opens a bottom sheet, etc.) are routed via [EditorActionEffect] kinds the
 * host handles centrally.
 *
 * Anchor-scoped items (`Remove this from selection`, `Edit this only`) live
 * outside the catalog — they have idiosyncratic semantics (`keepOpenOnClick`,
 * an `onAnchorRemoved` callback) that don't fit the `(ctx) -> Effect` shape.
 */
object EditorActionCatalog {

    /** Catalog-declaration order. Consumers may filter or re-group. */
    val all: List<EditorAction> = listOf(
        EditOpacityAction,
        EditCornerRadiusAction,
        EditBorderAction,
        EditShadowAction,
        EditOverlaysAction,
        EditContentMaskAction,
        EditBackgroundAction,
        EditCropAction,
        EditColorAdjustmentsAction,
        EditOpeningAction,
        EditDecorationsAction,
        EditCaptionAction,
        PresetsAction,
        PlayVideoAction,
        NavigateToFrameAction,
        BringToFrontAction,
        BringForwardAction,
        SendBackwardAction,
        SendToBackAction,
        PinAction,
        DetachAction,
        AutoAction,
        DuplicateSelectionAction,
        DeleteSelectionAction,
        ClearSelectionAction,
    )

    fun visibleActions(ctx: SelectionContext): List<EditorAction> =
        all.filter { it.isVisible(ctx) }

    fun visibleByCategory(ctx: SelectionContext): Map<ActionCategory, List<EditorAction>> =
        visibleActions(ctx).groupBy { it.category }
}

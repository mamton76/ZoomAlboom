package com.mamton.zoomalbum.feature.canvas.actions

import com.mamton.zoomalbum.feature.canvas.viewmodel.CanvasAction
import com.mamton.zoomalbum.feature.ide_ui.ui.FrameMembershipIntent

// ── Edit ─────────────────────────────────────────────────────────────────────

data object EditMediaAppearanceAction : EditorAction {
    override val id = "edit.media.appearance"
    override val icon = "✦"
    override val category = ActionCategory.Edit
    override fun label(ctx: SelectionContext) = "Edit appearance"
    override fun isVisible(ctx: SelectionContext) = ctx.singleSelectedMedia != null
    override fun effect(ctx: SelectionContext): EditorActionEffect =
        EditorActionEffect.OpenMediaAppearance
}

data object EditFrameAppearanceAction : EditorAction {
    override val id = "edit.frame.appearance"
    override val icon = "▣"
    override val category = ActionCategory.Edit
    override fun label(ctx: SelectionContext) = "Edit frame appearance"
    override fun isVisible(ctx: SelectionContext) = ctx.singleSelectedFrame != null
    override fun effect(ctx: SelectionContext): EditorActionEffect =
        EditorActionEffect.OpenFrameBackground
}

// ── Navigation ───────────────────────────────────────────────────────────────

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
        EditMediaAppearanceAction,
        EditFrameAppearanceAction,
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

package com.mamton.zoomalbum.feature.canvas.view

import com.mamton.zoomalbum.feature.canvas.actions.ActionCategory
import com.mamton.zoomalbum.feature.canvas.actions.DeleteSelectionAction
import com.mamton.zoomalbum.feature.canvas.actions.EditorAction
import com.mamton.zoomalbum.feature.canvas.actions.EditorActionCatalog
import com.mamton.zoomalbum.feature.canvas.actions.EditorActionEffect
import com.mamton.zoomalbum.feature.canvas.actions.SelectionContext
import com.mamton.zoomalbum.feature.canvas.viewmodel.CanvasAction

/**
 * Builds the Edit-mode context menu items for a given long-press request.
 *
 * Menu shape (matches `docs/architecture/context-menu.md § 4`):
 *
 * ```
 * Header             — Edit / Navigation / Duplicate (text rows)
 * ─── divider ───
 * Z-order row        — inline button row (single-selection only today)
 * ─── divider ───
 * Frame-membership   — inline button row (Pin / Detach / Auto when applicable)
 * ─── divider ───
 * Delete             — text row
 * ─── divider ───
 * Anchor section     — Edit this only / Remove this from selection (group only)
 * ─── divider ───
 * Clear selection    — text row (group only)
 * ```
 *
 * Items / rows whose [EditorAction.isVisible] is false for the current context
 * are omitted; dividers are inserted only between non-empty groups.
 *
 * Anchor-scoped items (`Remove this from selection`, `Edit this only`) live
 * outside the catalog because they have idiosyncratic semantics —
 * [ContextMenuItem.keepOpenOnClick] + a custom `onAnchorRemoved` callback for
 * the former, anchor-id parameterization for the latter.
 *
 * Defensive guard: if a single-node selection projects to neither a frame nor
 * a media (the underlying node has been removed), returns an empty list so the
 * popup dismisses rather than rendering a stale menu over nothing.
 *
 * Pure (non-Composable) so it can be unit-tested directly. See
 * `EditContextMenuItemsTest`.
 */
internal fun buildEditContextMenuItems(
    request: ContextMenuRequest,
    ctx: SelectionContext,
    runEffect: (EditorActionEffect) -> Unit,
    /**
     * Called after `Remove this from selection` dispatches. The host clears
     * the request's `anchorNodeId` so anchor-scoped items disappear from the
     * still-open popup. See `docs/architecture/context-menu.md § 4.4`.
     */
    onAnchorRemoved: () -> Unit,
): List<ContextMenuItem> {
    if (ctx.selectedNodeIds.isEmpty()) {
        // No "Add Photo" / "Add Frame" direct items yet — open the existing
        // AddContentBottomSheet which already routes both via the photo picker
        // and `CanvasNodeFactory.createFrame`. Splitting into separate menu
        // items is a follow-up.
        return listOf(
            ContextMenuItem(
                label = "Add…",
                onClick = { runEffect(EditorActionEffect.OpenAddSheet) },
            ),
        )
    }

    // Missing-node guard: single-node selection that doesn't project to a known
    // node (defensive case — long-press hit an id that has since been removed).
    if (ctx.selectedNodeIds.size == 1 &&
        ctx.singleSelectedFrame == null &&
        ctx.singleSelectedMedia == null
    ) {
        return emptyList()
    }

    val byCategory = EditorActionCatalog.visibleByCategory(ctx)
    val items = mutableListOf<ContextMenuItem>()

    // ── Header section: Edit / Navigation / Transform + Duplicate ─────────────
    val headerActions: List<EditorAction> = buildList {
        addAll(byCategory[ActionCategory.Edit].orEmpty())
        addAll(byCategory[ActionCategory.Navigation].orEmpty())
        addAll(byCategory[ActionCategory.Transform].orEmpty())
        addAll(
            byCategory[ActionCategory.Lifecycle].orEmpty()
                .filter { it !is DeleteSelectionAction }
        )
    }
    items += headerActions.map { it.toMenuItem(ctx, runEffect) }

    // ── Z-order inline row ────────────────────────────────────────────────────
    val zOrderActions = byCategory[ActionCategory.ZOrder].orEmpty()
    if (zOrderActions.isNotEmpty()) {
        if (items.isNotEmpty()) items += ContextMenuItem.Divider
        items += zOrderActions.toInlineRowItem(ctx, runEffect)
    }

    // ── Frame-membership inline row ──────────────────────────────────────────
    val membershipActions = byCategory[ActionCategory.Membership].orEmpty()
    if (membershipActions.isNotEmpty()) {
        if (items.isNotEmpty()) items += ContextMenuItem.Divider
        items += membershipActions.toInlineRowItem(ctx, runEffect)
    }

    // ── Delete (the only Lifecycle item that lives below the divider) ────────
    if (DeleteSelectionAction.isVisible(ctx)) {
        if (items.isNotEmpty()) items += ContextMenuItem.Divider
        items += DeleteSelectionAction.toMenuItem(ctx, runEffect)
    }

    // ── Anchor-scoped block (group + anchor in selection) ─────────────────────
    val anchorId = request.anchorNodeId
    val showAnchorBlock = anchorId != null &&
        anchorId in request.selection &&
        request.selection.size >= 2
    if (showAnchorBlock) {
        if (items.isNotEmpty()) items += ContextMenuItem.Divider
        items += ContextMenuItem(
            label = "Edit this only",
            onClick = {
                runEffect(EditorActionEffect.Dispatch(CanvasAction.SelectNode(anchorId!!)))
            },
        )
        items += ContextMenuItem(
            label = "Remove this from selection",
            // Selection-management action: stays in the same popup so the user
            // can keep editing the selection. Anchor clears because the
            // removed node *was* the anchor (Option A — see
            // context-menu.md § 4.4).
            keepOpenOnClick = true,
            onClick = {
                runEffect(EditorActionEffect.Dispatch(CanvasAction.ToggleNodeSelection(anchorId!!)))
                onAnchorRemoved()
            },
        )
    }

    // ── Clear selection (multi-only, very bottom) ─────────────────────────────
    val clearActions = byCategory[ActionCategory.SelectionMeta].orEmpty()
    if (clearActions.isNotEmpty()) {
        if (items.isNotEmpty()) items += ContextMenuItem.Divider
        items += clearActions.map { it.toMenuItem(ctx, runEffect) }
    }

    return items
}

private fun EditorAction.toMenuItem(
    ctx: SelectionContext,
    runEffect: (EditorActionEffect) -> Unit,
): ContextMenuItem = ContextMenuItem(
    label = label(ctx),
    enabled = isEnabled(ctx),
    onClick = { effect(ctx)?.let(runEffect) },
)

private fun List<EditorAction>.toInlineRowItem(
    ctx: SelectionContext,
    runEffect: (EditorActionEffect) -> Unit,
): ContextMenuItem = ContextMenuItem(
    inlineRow = map { action ->
        InlineRowButton(
            icon = action.icon ?: action.label(ctx).take(2),
            label = action.label(ctx),
            enabled = action.isEnabled(ctx),
            onClick = { action.effect(ctx)?.let(runEffect) },
        )
    },
)

package com.mamton.zoomalbum.feature.canvas.view

import com.mamton.zoomalbum.domain.model.CanvasNode
import com.mamton.zoomalbum.feature.canvas.viewmodel.CanvasAction

/**
 * Builds the Edit-mode context menu items for a given long-press request.
 *
 * Empty selection → empty-space menu (Add Photo / Add Frame).
 * Single media   → Edit appearance / Duplicate / Delete.
 * Single frame   → Edit frame appearance / Navigate / Duplicate / Delete.
 * Group (≥ 2)    → Duplicate / Delete / Clear selection + (if anchor in selection)
 *                  Remove this from selection / Edit this only.
 *
 * Items in `context-menu.md § 4` that have no underlying action yet
 * (Add Text, Paste, Add Guideline, Replace media, Edit media — and the
 * type-specific clip / alpha mask / overlays / crop popups which currently
 * all share `MediaAppearanceBottomSheet`) are omitted rather than shown
 * disabled, and will appear once their actions / popups ship.
 *
 * Pure (non-Composable) so it can be unit-tested directly. See
 * `EditContextMenuItemsTest`.
 */
internal fun buildEditContextMenuItems(
    request: ContextMenuRequest,
    nodesById: Map<String, CanvasNode>,
    dispatch: (CanvasAction) -> Unit,
    openMediaAppearance: (CanvasNode.Media) -> Unit,
    openFrameAppearance: (CanvasNode.Frame) -> Unit,
    openAddSheet: () -> Unit,
    /**
     * Called after `Remove this from selection` dispatches. The host clears
     * the request's `anchorNodeId` so anchor-scoped items disappear from the
     * still-open popup. See `docs/architecture/context-menu.md § 4.4`.
     */
    onAnchorRemoved: () -> Unit,
): List<ContextMenuItem> {
    val divider = ContextMenuItem.Divider
    val selection = request.selection

    return when {
        selection.isEmpty() -> listOf(
            // No "Add Photo" / "Add Frame" direct items yet — open the existing
            // AddContentBottomSheet which already routes both via the photo picker
            // and `CanvasNodeFactory.createFrame`. Splitting into separate menu
            // items is a follow-up.
            ContextMenuItem(label = "Add…", onClick = openAddSheet),
        )

        selection.size == 1 -> {
            val node = nodesById[selection.first()] ?: return emptyList()
            when (node) {
                is CanvasNode.Media -> listOf(
                    ContextMenuItem(
                        label = "Edit appearance",
                        onClick = { openMediaAppearance(node) },
                    ),
                    divider,
                    ContextMenuItem(
                        label = "Duplicate",
                        onClick = { dispatch(CanvasAction.DuplicateSelection) },
                    ),
                    ContextMenuItem(
                        label = "Delete",
                        onClick = { dispatch(CanvasAction.DeleteSelection) },
                    ),
                )

                is CanvasNode.Frame -> listOf(
                    ContextMenuItem(
                        label = "Edit frame appearance",
                        onClick = { openFrameAppearance(node) },
                    ),
                    ContextMenuItem(
                        label = "Navigate to frame",
                        onClick = { dispatch(CanvasAction.FocusNode(node.id)) },
                    ),
                    divider,
                    ContextMenuItem(
                        label = "Duplicate",
                        onClick = { dispatch(CanvasAction.DuplicateSelection) },
                    ),
                    ContextMenuItem(
                        label = "Delete",
                        onClick = { dispatch(CanvasAction.DeleteSelection) },
                    ),
                )
            }
        }

        else -> {
            // Group menu. Selection-scoped items first; anchor-scoped items
            // appended only when the long-pressed node is in the selection
            // (the common case — long-press on a selected node from a group).
            val groupItems = mutableListOf(
                ContextMenuItem(
                    label = "Duplicate selection",
                    onClick = { dispatch(CanvasAction.DuplicateSelection) },
                ),
                ContextMenuItem(
                    label = "Delete selection",
                    onClick = { dispatch(CanvasAction.DeleteSelection) },
                ),
                ContextMenuItem(
                    label = "Clear selection",
                    onClick = { dispatch(CanvasAction.DeselectAll) },
                ),
            )
            val anchorId = request.anchorNodeId
            if (anchorId != null && anchorId in selection) {
                groupItems += divider
                groupItems += ContextMenuItem(
                    label = "Remove this from selection",
                    // Selection-management action: stays in the same popup
                    // (`keepOpenOnClick = true`) so the user can keep editing
                    // the selection. Anchor clears because the removed node
                    // *was* the anchor (Option A — see context-menu.md § 4.4).
                    keepOpenOnClick = true,
                    onClick = {
                        dispatch(CanvasAction.ToggleNodeSelection(anchorId))
                        onAnchorRemoved()
                    },
                )
                groupItems += ContextMenuItem(
                    label = "Edit this only",
                    onClick = { dispatch(CanvasAction.SelectNode(anchorId)) },
                )
            }
            groupItems
        }
    }
}

package com.mamton.zoomalbum.feature.canvas.view

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.mamton.zoomalbum.domain.model.CanvasNode
import com.mamton.zoomalbum.feature.ide_ui.ui.FrameNameLabel

/**
 * Long-press menu request — what to show and where.
 *
 * Carries both the post-resolution [selection] (what most items act on) and
 * the [anchorNodeId] (the node the user actually long-pressed, used by
 * anchor-scoped items like "Remove this from selection" / "Edit this only"
 * — see `docs/architecture/context-menu.md § 2`).
 *
 * [pickerNodes] is non-null when the long-press hit a stack of overlapping
 * nodes. The popup renders a checkbox row per node above the menu items so
 * the user can adjust which nodes are selected without leaving the popover.
 *
 * Touch coordinates are in *screen* pixels; the popup positions itself
 * with [IntOffset] in the same space.
 */
data class ContextMenuRequest(
    val selection: Set<String>,
    val anchorNodeId: String?,
    val anchorScreenX: Float,
    val anchorScreenY: Float,
    val pickerNodes: List<CanvasNode>? = null,
)

/**
 * One menu entry. Either a clickable action or a structural separator.
 *
 * Disabled entries render with reduced opacity and absorb taps without firing
 * [onClick] or dismissing the popup. Use sparingly — entries that don't apply
 * to the current selection should be omitted, not disabled.
 *
 * [keepOpenOnClick] suppresses auto-dismiss after [onClick]. Used by
 * selection-management items (e.g. `Remove this from selection`) where the
 * user is likely to keep working in the same popup. Anchor adjustments are
 * the [onClick]'s responsibility (see `docs/architecture/context-menu.md § 4.4`).
 */
data class ContextMenuItem(
    val label: String,
    val onClick: () -> Unit = {},
    val enabled: Boolean = true,
    val isDivider: Boolean = false,
    val keepOpenOnClick: Boolean = false,
) {
    companion object {
        val Divider = ContextMenuItem(label = "", isDivider = true)
    }
}

/**
 * Transient popover anchored at the long-press touch point.
 *
 * Dismissal is **driven externally**, not by the `Popup` itself:
 *
 * - Tap / double-tap / drag-start outside the popup → handled by
 *   `CanvasScreen.onCanvasGesture`, which the host (`CanvasScaffold`)
 *   reacts to by clearing `contextMenuRequest`. The same gestures' normal
 *   canvas actions (Select / DeselectAll / camera reset / rect-select) are
 *   suppressed for the same touch.
 * - Long-press on another node → produces a new `ContextMenuRequest` which
 *   replaces the popup in place (no intermediate dismiss frame).
 * - Camera pan/pinch/rotate (Layer 3) and node drag/resize/rotate (Layer 1)
 *   → dismiss the popup *and* still execute the gesture.
 * - Back-press → handled by a `BackHandler` in `CanvasScaffold` (gated on
 *   the menu being open). The popup window itself is not focusable, so
 *   `PopupProperties.dismissOnBackPress` cannot fire from inside the popup.
 * - Menu item tap → fires the action and clears `contextMenuRequest`.
 *
 * Why `focusable = false`: a focusable popup creates a separate focused
 * window that swallows touches anywhere on the screen and would force
 * "long-press on another node" through an outside-tap intermediate step
 * (visible flicker + selection loss). Keeping the popup non-focusable lets
 * the canvas gesture handlers below see the touch and route it correctly.
 *
 * Why `dismissOnClickOutside = false`: outside-tap dismissal already runs
 * through the canvas gesture handler so it can suppress the normal selection
 * action; enabling Popup's own outside-click would race with that path.
 *
 * Why `dismissOnBackPress = false`: a non-focusable popup never receives
 * key events, so this flag is a no-op. Back is intercepted by the host's
 * `BackHandler` instead (see `CanvasScaffold`).
 *
 * Items selected via [DropdownMenuItem] fire their own `onClick` and then the
 * caller is expected to clear the menu state, which auto-dismisses the Popup.
 *
 * When [ContextMenuRequest.pickerNodes] is non-null, a checkbox row appears
 * above the menu items for each overlapping node. Toggling a checkbox calls
 * [onTogglePickerNode] and does **not** dismiss the popup — the user keeps
 * the menu open while adjusting the selection.
 *
 * Known limitation: a non-member node visually inside frame bounds and below
 * the overlay pass may be covered by the frame's overlay (the overlay is
 * clipped to the frame rect, not masked to its members). See
 * `docs/architecture/rendering.md § 6b`.
 */
@Composable
fun ContextMenuPopup(
    request: ContextMenuRequest,
    items: List<ContextMenuItem>,
    onDismiss: () -> Unit,
    onTogglePickerNode: (CanvasNode) -> Unit = {},
) {
    if (items.isEmpty() && request.pickerNodes.isNullOrEmpty()) return
    Popup(
        offset = IntOffset(request.anchorScreenX.toInt(), request.anchorScreenY.toInt()),
        onDismissRequest = onDismiss,
        // See the kdoc above for the full dismissal/focusable rationale.
        // Back-press is handled by the host (`BackHandler` in CanvasScaffold)
        // because a non-focusable popup window cannot intercept key events.
        properties = PopupProperties(
            focusable = false,
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
        ),
    ) {
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 4.dp,
            shadowElevation = 8.dp,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(min = 200.dp, max = 320.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 4.dp),
            ) {
                val pickerNodes = request.pickerNodes
                if (!pickerNodes.isNullOrEmpty()) {
                    pickerNodes.forEach { node ->
                        ContextMenuPickerRow(
                            node = node,
                            checked = node.id in request.selection,
                            isAnchor = node.id == request.anchorNodeId,
                            onToggle = { onTogglePickerNode(node) },
                        )
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                }

                items.forEach { item ->
                    if (item.isDivider) {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    } else {
                        DropdownMenuItem(
                            text = { Text(item.label) },
                            onClick = {
                                item.onClick()
                                if (!item.keepOpenOnClick) onDismiss()
                            },
                            enabled = item.enabled,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ContextMenuPickerRow(
    node: CanvasNode,
    checked: Boolean,
    isAnchor: Boolean,
    onToggle: () -> Unit,
) {
    // Anchor highlight: tinted row background + bold label. The anchor is the
    // node whose id appears in anchor-scoped menu items ("Remove this from
    // selection" / "Edit this only"); tapping a different picker row makes it
    // the new anchor.
    val anchorTint = MaterialTheme.colorScheme.secondaryContainer
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (isAnchor) Modifier.background(anchorTint) else Modifier)
            .clickable { onToggle() }
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = { onToggle() })
        Spacer(modifier = Modifier.padding(horizontal = 4.dp))
        when (node) {
            is CanvasNode.Frame -> FrameNameLabel(
                frame = node,
                modifier = Modifier,
                dotSize = 12.dp,
            )
            is CanvasNode.Media -> Text(
                text = "Media ${node.id.take(6)}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isAnchor) FontWeight.SemiBold else FontWeight.Normal,
            )
        }
    }
}

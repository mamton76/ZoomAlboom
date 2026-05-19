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
 */
data class ContextMenuItem(
    val label: String,
    val onClick: () -> Unit = {},
    val enabled: Boolean = true,
    val isDivider: Boolean = false,
) {
    companion object {
        val Divider = ContextMenuItem(label = "", isDivider = true)
    }
}

/**
 * Transient popover anchored at the long-press touch point.
 *
 * Wraps a `Popup` with `dismissOnClickOutside` + `dismissOnBackPress` so any
 * touch outside the menu closes it without dispatching an item. Click-through
 * to the canvas behind the menu is **not** desired (a typical "tap outside to
 * dismiss" surface, not pass-through), so [PopupProperties.focusable] is true.
 *
 * Items selected via [DropdownMenuItem] fire their own `onClick` and then the
 * caller is expected to clear the menu state, which auto-dismisses the Popup.
 *
 * When [ContextMenuRequest.pickerNodes] is non-null, a checkbox row appears
 * above the menu items for each overlapping node. Toggling a checkbox calls
 * [onTogglePickerNode] and does **not** dismiss the popup — the user keeps
 * the menu open while adjusting the selection.
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
        // `focusable = false` makes the popup transparent to touches outside its
        // surface — a new long-press on another node immediately replaces the
        // popup without requiring an outside-tap-to-dismiss intermediate step.
        // Dismissal on outside-tap is driven externally by the canvas gesture
        // handler (`onCanvasGesture` in CanvasScreen). Back-press still dismisses.
        properties = PopupProperties(
            focusable = false,
            dismissOnBackPress = true,
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
                                onDismiss()
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

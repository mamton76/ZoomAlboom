package com.mamton.zoomalbum.feature.ide_ui.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private data class ActionItem(val icon: String, val label: String)

private val baseActions = listOf(
    ActionItem("\u2715", "Delete"),    // ✕
    ActionItem("\u2398", "Duplicate"), // ⎘
    ActionItem("\u270E", "Edit"),      // ✎
)

// Frame fill toggle. Short-term entry point — moves to Object Properties Panel later.
private val backgroundAction = ActionItem("▣", "Background") // ▣

// Media-only entry point for the MediaAppearance editor.
private val mediaAppearanceAction = ActionItem("✦", "Appearance") // ✦

// Z-order actions. Single-node selections only; multi-select would need group
// semantics that aren't worth designing yet.
private val zOrderActions = listOf(
    ActionItem("⤒", "ToFront"),   // ⤒  bring to front
    ActionItem("▲", "Forward"),   // ▲  bring forward one step
    ActionItem("▼", "Backward"),  // ▼  send backward one step
    ActionItem("⤓", "ToBack"),    // ⤓  send to back
)

// Frame membership — visible only when selection contains a frame + ≥1 other node.
// See docs/architecture/frame-membership.md.
private val frameMembershipActions = listOf(
    ActionItem("⊕", "Pin"),       // ⊕  pin selected nodes to the frame
    ActionItem("⊖", "Detach"),    // ⊖  detach selected nodes from the frame
)

// "Auto" — clear any override entries for the candidate nodes; reverts to pure geometry.
// Visible only when at least one selected candidate has an override on the target frame.
private val autoAction = ActionItem("⟲", "Auto") // ⟲

@Composable
fun ContextualActionBar(
    hasSelection: Boolean,
    modifier: Modifier = Modifier,
    showBackgroundAction: Boolean = false,
    showMediaAppearanceAction: Boolean = false,
    showZOrderActions: Boolean = false,
    showFrameMembershipActions: Boolean = false,
    showAutoAction: Boolean = false,
    onAction: (String) -> Unit = {},
) {
    AnimatedVisibility(
        visible = hasSelection,
        modifier = modifier,
        enter = slideInVertically { it },
        exit = slideOutVertically { it },
    ) {
        val actions = buildList {
            addAll(baseActions)
            if (showBackgroundAction) add(backgroundAction)
            if (showMediaAppearanceAction) add(mediaAppearanceAction)
            if (showZOrderActions) addAll(zOrderActions)
            if (showFrameMembershipActions) addAll(frameMembershipActions)
            if (showAutoAction) add(autoAction)
        }
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceVariant,
            tonalElevation = 4.dp,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                for (action in actions) {
                    IconButton(onClick = { onAction(action.label) }) {
                        Text(text = action.icon, fontSize = 20.sp)
                    }
                }
            }
        }
    }
}

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

// Z-order actions. Single-node selections only; multi-select would need group
// semantics that aren't worth designing yet.
private val zOrderActions = listOf(
    ActionItem("⤒", "ToFront"),   // ⤒  bring to front
    ActionItem("▲", "Forward"),   // ▲  bring forward one step
    ActionItem("▼", "Backward"),  // ▼  send backward one step
    ActionItem("⤓", "ToBack"),    // ⤓  send to back
)

@Composable
fun ContextualActionBar(
    hasSelection: Boolean,
    modifier: Modifier = Modifier,
    showBackgroundAction: Boolean = false,
    showZOrderActions: Boolean = false,
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
            if (showZOrderActions) addAll(zOrderActions)
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

package com.mamton.zoomalbum.feature.ide_ui.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mamton.zoomalbum.domain.model.FrameEditOptions

/**
 * Two-checkbox row that controls how the next frame transform gesture behaves.
 * Visible only when exactly one Frame is selected. Session-level state — not persisted.
 *
 * See `docs/architecture/frame-membership.md` for the four-combination behaviour matrix.
 */
@Composable
fun FrameEditOptionsBar(
    visible: Boolean,
    options: FrameEditOptions,
    onOptionsChange: (FrameEditOptions) -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = fadeIn(),
        exit = fadeOut(),
    ) {
        Surface(
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.surfaceVariant,
            tonalElevation = 2.dp,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Checkbox(
                    checked = options.transformContents,
                    onCheckedChange = { onOptionsChange(options.copy(transformContents = it)) },
                )
                Text("Transform w/ content", fontSize = 12.sp)
                Checkbox(
                    checked = options.rebindAfterEdit,
                    onCheckedChange = { onOptionsChange(options.copy(rebindAfterEdit = it)) },
                )
                Text("Rebind", fontSize = 12.sp)
            }
        }
    }
}

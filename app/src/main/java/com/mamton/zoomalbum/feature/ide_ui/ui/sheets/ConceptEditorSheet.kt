package com.mamton.zoomalbum.feature.ide_ui.ui.sheets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mamton.zoomalbum.feature.canvas.editor.MixedValue
import com.mamton.zoomalbum.feature.canvas.editor.toMixedValue

/**
 * Reusable bottom-sheet host for one appearance concept editor (per
 * `docs/architecture/appearance.md § 14.1` — "per-concept popup direction").
 *
 * Holds per-node drafts of one concept value `T` (opacity / corner radius /
 * border / etc.) across the editing set. Touching the [body]'s control
 * mutates *every* draft uniformly: the popup edits one field, never the
 * whole appearance, so untouched fields on each node keep their per-node
 * variation. On Apply the host receives the full per-id map and dispatches
 * the appropriate `SetFrameAppearance` / `SetMediaAppearance` action,
 * merging the concept value into each node's existing appearance.
 *
 * The [body] receives both a live [MixedValue] (for Mixed-aware controls
 * like `MixedAwareOpacitySlider`) and the first draft's value (for legacy
 * sub-editors that haven't been refactored to render Mixed states). The
 * wrapper makes no UI choice for Mixed — bodies do.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> ConceptEditorSheet(
    title: String,
    initialById: Map<String, T>,
    onApply: (Map<String, T>) -> Unit,
    onDismiss: () -> Unit,
    body: @Composable (mixed: MixedValue<T>, firstDraft: T, onChange: (T) -> Unit) -> Unit,
) {
    if (initialById.isEmpty()) return

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var draftsById by remember { mutableStateOf(initialById) }
    val multiCount = draftsById.size
    val mixed: MixedValue<T> = draftsById.values.toMixedValue()
    val firstDraft: T = draftsById.values.first()

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Text(
                text = if (multiCount > 1) "$title ($multiCount items)" else title,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(vertical = 8.dp),
            )

            body(mixed, firstDraft) { newValue ->
                draftsById = draftsById.mapValues { newValue }
            }

            Spacer(Modifier.height(20.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onDismiss) { Text("Cancel") }
                Spacer(Modifier.width(8.dp))
                Button(onClick = { onApply(draftsById) }) {
                    Text(if (multiCount > 1) "Apply to $multiCount" else "Apply")
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

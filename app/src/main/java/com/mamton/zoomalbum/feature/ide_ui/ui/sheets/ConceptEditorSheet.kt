package com.mamton.zoomalbum.feature.ide_ui.ui.sheets

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.mamton.zoomalbum.domain.model.AppearanceSection
import com.mamton.zoomalbum.domain.model.MediaAppearance
import com.mamton.zoomalbum.domain.model.withSection
import com.mamton.zoomalbum.feature.canvas.editor.MixedValue
import com.mamton.zoomalbum.feature.canvas.editor.toMixedValue
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Live preview for a [ConceptEditorSheet]: re-renders [uri] (the selected — or
 * first selected — media) through the real canvas media pipeline with the
 * in-progress draft applied on top of [base]. **Hold** (long-press) the preview
 * to peek the same photo with [section] stripped to its default, so the user can
 * compare the part being edited on/off; release restores the live edit.
 *
 * Only meaningful for media concepts — frame concept editors pass `null`.
 */
data class ConceptPreview<T>(
    val uri: String,
    val base: MediaAppearance,
    val section: AppearanceSection,
    /** Source node `renderW`/`renderH` — keeps the preview at the photo's real aspect + faithful crop. */
    val nodeW: Float,
    val nodeH: Float,
    /** Merge the draft value into [base] for this concept — same shape as the dispatch lambda. */
    val applyDraft: (base: MediaAppearance, draft: T) -> MediaAppearance,
)

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
 * When [preview] is non-null the sheet shows a live thumbnail of the edited
 * media at the top (hold to compare with/without this part).
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
    preview: ConceptPreview<T>? = null,
    body: @Composable (mixed: MixedValue<T>, firstDraft: T, onChange: (T) -> Unit) -> Unit,
) {
    if (initialById.isEmpty()) return

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var draftsById by remember { mutableStateOf(initialById) }
    val multiCount = draftsById.size
    val mixed: MixedValue<T> = draftsById.values.toMixedValue()
    val firstDraft: T = draftsById.values.first()
    // Preview reflects the LAST selected node (matches the preview's source node).
    val lastDraft: T = draftsById.values.last()

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

            if (preview != null) {
                ConceptPreviewThumbnail(preview, lastDraft)
                Spacer(Modifier.height(12.dp))
            }

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

/**
 * The live preview thumbnail + hold-to-compare gesture. Shows the photo WITH the
 * in-progress edit; while held, shows it WITHOUT this concept's contribution
 * (the section reset to default), then restores on release.
 */
@Composable
private fun <T> ConceptPreviewThumbnail(preview: ConceptPreview<T>, firstDraft: T) {
    var peeking by remember { mutableStateOf(false) }
    val edited = remember(preview, firstDraft) { preview.applyDraft(preview.base, firstDraft) }
    val shown = if (peeking) {
        remember(edited) { edited.withSection(preview.section, MediaAppearance()) }
    } else {
        edited
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    // No up within the long-press window → enter peek until release.
                    val up = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                        waitForUpOrCancellation()
                    }
                    if (up == null) {
                        peeking = true
                        waitForUpOrCancellation()
                        peeking = false
                    }
                }
            },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        MediaPresetPreview(
            uri = preview.uri,
            appearance = shown,
            size = 180.dp,
            sourceW = preview.nodeW,
            sourceH = preview.nodeH,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = if (peeking) "Without this — release to restore" else "Hold to compare without this",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

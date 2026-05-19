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
import androidx.compose.material3.HorizontalDivider
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
import com.mamton.zoomalbum.domain.model.FrameAppearance
import com.mamton.zoomalbum.feature.ide_ui.ui.content.BackgroundEditor
import com.mamton.zoomalbum.feature.ide_ui.ui.content.BorderStyleEditor
import com.mamton.zoomalbum.feature.ide_ui.ui.content.CornerRadiusSlider
import com.mamton.zoomalbum.feature.ide_ui.ui.content.OpacitySlider
import com.mamton.zoomalbum.feature.ide_ui.ui.content.OverlayListEditor
import com.mamton.zoomalbum.feature.ide_ui.ui.content.ShadowStyleEditor

/**
 * Editor sheet for the entire [FrameAppearance] of a single frame.
 *
 * Sections:
 *  - Background (reuses [BackgroundEditor]).
 *  - Content overlays (reuses [OverlayListEditor]).
 *
 * Border, shadow, title style, and content-effect editors are deferred — they
 * will land as additional sections in this same sheet (Slice D2+).
 *
 * Returns `null` from [onApply] when every field of the edited appearance is at
 * its default — the caller's `SetFrameAppearance` handler then drops the field
 * to keep the JSON tidy.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FrameAppearanceBottomSheet(
    initial: FrameAppearance?,
    onApply: (FrameAppearance?) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var draft by remember { mutableStateOf(initial ?: FrameAppearance()) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Text(
                text = "Frame appearance",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(vertical = 8.dp),
            )

            BackgroundEditor(
                initial = draft.background,
                onValueChange = { draft = draft.copy(background = it) },
                tileSizeUnitLabel = "world units",
            )

            Spacer(Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(Modifier.height(8.dp))

            OpacitySlider(
                value = draft.opacity,
                onChange = { draft = draft.copy(opacity = it) },
            )
            CornerRadiusSlider(
                value = draft.cornerRadius,
                onChange = { draft = draft.copy(cornerRadius = it) },
            )

            Spacer(Modifier.height(8.dp))
            BorderStyleEditor(
                initial = draft.border,
                onChange = { draft = draft.copy(border = it) },
            )

            Spacer(Modifier.height(8.dp))
            ShadowStyleEditor(
                initial = draft.shadow,
                onChange = { draft = draft.copy(shadow = it) },
            )

            Spacer(Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(Modifier.height(8.dp))

            OverlayListEditor(
                overlays = draft.overlays,
                onChange = { draft = draft.copy(overlays = it) },
                tileSizeUnitLabel = "world units",
            )

            Spacer(Modifier.height(20.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onDismiss) { Text("Cancel") }
                Spacer(Modifier.width(8.dp))
                Button(onClick = { onApply(draft.collapseEmpty()) }) { Text("Apply") }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

/** Collapse an all-default `FrameAppearance` back to `null` for tidy persistence. */
private fun FrameAppearance.collapseEmpty(): FrameAppearance? =
    if (this == FrameAppearance()) null else this

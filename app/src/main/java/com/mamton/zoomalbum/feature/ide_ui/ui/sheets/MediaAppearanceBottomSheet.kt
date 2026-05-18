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
import com.mamton.zoomalbum.domain.model.MediaAppearance
import com.mamton.zoomalbum.feature.ide_ui.ui.content.BorderStyleEditor
import com.mamton.zoomalbum.feature.ide_ui.ui.content.CaptionStyleEditor
import com.mamton.zoomalbum.feature.ide_ui.ui.content.ColorAdjustmentsEditor
import com.mamton.zoomalbum.feature.ide_ui.ui.content.CornerRadiusSlider
import com.mamton.zoomalbum.feature.ide_ui.ui.content.CropEditor
import com.mamton.zoomalbum.feature.ide_ui.ui.content.MediaFrameDecorationEditor
import com.mamton.zoomalbum.feature.ide_ui.ui.content.OpacitySlider
import com.mamton.zoomalbum.feature.ide_ui.ui.content.OverlayListEditor
import com.mamton.zoomalbum.feature.ide_ui.ui.content.ShadowStyleEditor

/**
 * Editor sheet for the full [MediaAppearance] of a single media node.
 *
 * Sections, top to bottom:
 *  1. Opacity / corner radius (shared `NodeAppearance` knobs).
 *  2. Crop (`CropSettings`, mode + focal / manual offsets).
 *  3. Color adjustments (persist-only today — see `MediaStyleEditors.kt`).
 *  4. Border / shadow.
 *  5. Overlays (`MediaAppearance.overlays`, ordered list).
 *  6. Picture-frame decoration (persist-only today).
 *  7. Caption (persist-only today).
 *
 * Returns `null` from `onApply` when every field is at its default — the
 * caller's `SetMediaAppearance` handler then drops the field for tidy JSON.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaAppearanceBottomSheet(
    initial: MediaAppearance?,
    onApply: (MediaAppearance?) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var draft by remember { mutableStateOf(initial ?: MediaAppearance()) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Text(
                text = "Media appearance",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(vertical = 8.dp),
            )

            OpacitySlider(
                value = draft.opacity,
                onChange = { draft = draft.copy(opacity = it) },
            )
            CornerRadiusSlider(
                value = draft.cornerRadius,
                onChange = { draft = draft.copy(cornerRadius = it) },
            )

            Spacer(Modifier.height(8.dp))
            HorizontalDivider()
            Spacer(Modifier.height(8.dp))

            CropEditor(
                initial = draft.crop,
                onChange = { draft = draft.copy(crop = it) },
            )

            Spacer(Modifier.height(8.dp))
            HorizontalDivider()
            Spacer(Modifier.height(8.dp))

            ColorAdjustmentsEditor(
                initial = draft.colorAdjustments,
                onChange = { draft = draft.copy(colorAdjustments = it) },
            )

            Spacer(Modifier.height(8.dp))
            HorizontalDivider()
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

            // Local override of OverlayListEditor's heading from "Content overlays"
            // to "Overlays" would need a parameter; reuse as-is — the label fits
            // either scope semantically.
            OverlayListEditor(
                overlays = draft.overlays,
                onChange = { draft = draft.copy(overlays = it) },
                tileSizeUnitLabel = "world units",
            )

            Spacer(Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(Modifier.height(8.dp))

            MediaFrameDecorationEditor(
                initial = draft.frameDecoration,
                onChange = { draft = draft.copy(frameDecoration = it) },
            )

            Spacer(Modifier.height(8.dp))
            HorizontalDivider()
            Spacer(Modifier.height(8.dp))

            CaptionStyleEditor(
                initial = draft.caption,
                onChange = { draft = draft.copy(caption = it) },
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

/** Collapse an all-default `MediaAppearance` back to `null` for tidy persistence. */
private fun MediaAppearance.collapseEmpty(): MediaAppearance? =
    if (this == MediaAppearance()) null else this

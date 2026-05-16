package com.mamton.zoomalbum.feature.ide_ui.ui.sheets

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import com.mamton.zoomalbum.domain.model.AlbumBackground
import com.mamton.zoomalbum.domain.model.AnchorMode
import com.mamton.zoomalbum.feature.ide_ui.ui.content.BackgroundEditorContent

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumSettingsBottomSheet(
    initial: AlbumBackground?,
    onApply: (AlbumBackground?) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        BackgroundEditorContent(
            initialData = initial?.data,
            initialAnchorMode = initial?.anchorMode ?: AnchorMode.WorldLocked,
            onDismiss = onDismiss,
            onApply = onApply,
        )
    }
}

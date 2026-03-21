package com.mamton.zoomalbum.feature.ide_ui.ui.sheets

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mamton.zoomalbum.domain.model.CanvasNode
import com.mamton.zoomalbum.feature.ide_ui.ui.content.FrameListContent

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FrameListBottomSheet(
    frames: List<CanvasNode.Frame>,
    onDeleteFrame: (String) -> Unit,
    onDismiss: () -> Unit,
    visibleFrameIds: Set<String> = emptySet(),
) {
    val sheetState = rememberModalBottomSheetState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Text(
            text = "Frames",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        )

        FrameListContent(
            frames = frames,
            onDeleteFrame = onDeleteFrame,
            visibleFrameIds = visibleFrameIds,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 100.dp, max = 400.dp),
        )
    }
}

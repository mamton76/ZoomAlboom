package com.mamton.zoomalbum.feature.ide_ui.ui.sheets

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mamton.zoomalbum.feature.ide_ui.ui.content.AddContentGrid

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddContentBottomSheet(
    onDismiss: () -> Unit,
    onContentTypeSelected: (String) -> Unit = { onDismiss() },
) {
    val sheetState = rememberModalBottomSheetState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Text(
            text = "Add Content",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        )

        AddContentGrid(onContentTypeSelected = onContentTypeSelected)

        Spacer(modifier = Modifier.padding(bottom = 32.dp))
    }
}

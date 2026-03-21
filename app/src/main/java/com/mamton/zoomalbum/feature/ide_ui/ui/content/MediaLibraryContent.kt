package com.mamton.zoomalbum.feature.ide_ui.ui.content

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Reusable media library content — rendered inside both
 * [com.mamton.zoomalbum.feature.ide_ui.ui.panels.MediaLibraryPanel] (docked panel)
 * and [com.mamton.zoomalbum.feature.ide_ui.ui.sheets.MediaLibraryBottomSheet].
 */
@Composable
fun MediaLibraryContent(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "No media imported yet.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

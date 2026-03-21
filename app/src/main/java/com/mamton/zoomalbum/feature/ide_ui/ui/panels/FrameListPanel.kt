package com.mamton.zoomalbum.feature.ide_ui.ui.panels

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.mamton.zoomalbum.domain.model.CanvasNode
import com.mamton.zoomalbum.feature.ide_ui.ui.content.FrameListContent

@Composable
fun FrameListPanel(
    frames: List<CanvasNode.Frame>,
    onDeleteFrame: (String) -> Unit,
    modifier: Modifier = Modifier,
    visibleFrameIds: Set<String> = emptySet(),
) {
    FrameListContent(
        frames = frames,
        onDeleteFrame = onDeleteFrame,
        visibleFrameIds = visibleFrameIds,
        modifier = modifier,
    )
}

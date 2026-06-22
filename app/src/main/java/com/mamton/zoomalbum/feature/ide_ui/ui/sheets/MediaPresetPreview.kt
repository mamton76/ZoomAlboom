package com.mamton.zoomalbum.feature.ide_ui.ui.sheets

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mamton.zoomalbum.domain.model.CanvasNode
import com.mamton.zoomalbum.domain.model.MediaAppearance
import com.mamton.zoomalbum.domain.model.RenderDetail
import com.mamton.zoomalbum.domain.model.Transform
import com.mamton.zoomalbum.feature.canvas.view.CanvasNodeRenderer

/**
 * A card-sized preview of a media [appearance] applied to [uri]. Reuses the
 * exact canvas media pipeline by rendering a synthetic card-sized node through
 * [CanvasNodeRenderer] (crop / opening / contentMask / overlays / decorations /
 * border / opacity all faithful). A video [uri] previews via its poster frame.
 */
@Composable
fun MediaPresetPreview(uri: String, appearance: MediaAppearance?, size: Dp) {
    val px = with(LocalDensity.current) { size.toPx() }
    val node = remember(uri, appearance, px) {
        CanvasNode.Media(
            id = "preset-preview",
            transform = Transform(cx = px / 2f, cy = px / 2f, w = px, h = px, scale = 1f),
            mediaRefId = uri,
            appearance = appearance,
        )
    }
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(6.dp))
            .clipToBounds(),
    ) {
        CanvasNodeRenderer(node, RenderDetail.Full)
    }
}

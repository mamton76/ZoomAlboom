package com.mamton.zoomalbum.feature.ide_ui.ui.sheets

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mamton.zoomalbum.domain.model.CanvasNode
import com.mamton.zoomalbum.domain.model.MediaAppearance
import com.mamton.zoomalbum.domain.model.RenderDetail
import com.mamton.zoomalbum.domain.model.Transform
import com.mamton.zoomalbum.feature.canvas.view.CanvasNodeRenderer

/**
 * A preview of a media [appearance] applied to [uri], reusing the exact canvas
 * media pipeline by rendering a synthetic node through [CanvasNodeRenderer]
 * (crop / opening / contentMask / overlays / decorations / border / opacity all
 * faithful). A video [uri] previews via its poster frame.
 *
 * [size] bounds the **longest** edge. When [sourceW]/[sourceH] (the source
 * node's `renderW`/`renderH`) are given, the preview keeps the **original
 * aspect ratio** and renders the synthetic node at those real dimensions, then
 * scales the result to fit — so crop math (Manual offset/zoom is in the node's
 * world units, Fit/Fill depend on the node aspect) renders exactly as on canvas.
 * When omitted the preview falls back to a square box (used by the small preset
 * cards, where the bound preset isn't tied to one node's geometry).
 */
@Composable
fun MediaPresetPreview(
    uri: String,
    appearance: MediaAppearance?,
    size: Dp,
    sourceW: Float? = null,
    sourceH: Float? = null,
) {
    val boxPx = with(LocalDensity.current) { size.toPx() }
    val hasAspect = sourceW != null && sourceH != null && sourceW > 0f && sourceH > 0f
    val aspect = if (hasAspect) sourceW!! / sourceH!! else 1f

    // Display box keeps the source aspect, longest edge == size.
    val dispWpx = if (aspect >= 1f) boxPx else boxPx * aspect
    val dispHpx = if (aspect >= 1f) boxPx / aspect else boxPx

    // Synthetic node at the source's real render dimensions (faithful crop), or
    // a square box-sized node in the legacy (no-aspect) case.
    val nodeW = if (hasAspect) sourceW!! else dispWpx
    val nodeH = if (hasAspect) sourceH!! else dispHpx
    val node = remember(uri, appearance, nodeW, nodeH) {
        CanvasNode.Media(
            id = "preset-preview",
            transform = Transform(cx = nodeW / 2f, cy = nodeH / 2f, w = nodeW, h = nodeH, scale = 1f),
            mediaRefId = uri,
            appearance = appearance,
        )
    }
    val fit = dispWpx / nodeW // == dispHpx / nodeH (aspect preserved)

    val dispW = with(LocalDensity.current) { dispWpx.toDp() }
    val dispH = with(LocalDensity.current) { dispHpx.toDp() }
    Box(
        modifier = Modifier
            .size(dispW, dispH)
            .clip(RoundedCornerShape(6.dp))
            .clipToBounds(),
    ) {
        Box(
            modifier = Modifier.graphicsLayer {
                scaleX = fit
                scaleY = fit
                transformOrigin = TransformOrigin(0f, 0f)
            },
        ) {
            CanvasNodeRenderer(node, RenderDetail.Full)
        }
    }
}

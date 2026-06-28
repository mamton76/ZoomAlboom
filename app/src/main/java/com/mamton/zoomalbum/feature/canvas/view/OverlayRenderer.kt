package com.mamton.zoomalbum.feature.canvas.view

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.platform.LocalContext
import com.mamton.zoomalbum.domain.model.BackgroundData
import com.mamton.zoomalbum.domain.model.NodeBlendMode
import com.mamton.zoomalbum.domain.model.OverlayStyle
import com.mamton.zoomalbum.domain.model.OverlaySource

/**
 * Paints an ordered list of [OverlayStyle] entries inside `[left-right] × [top-bottom]`.
 *
 * Compositing rule: entry `[i]` draws above entry `[i-1]`. Each entry uses its
 * own `blendMode` and `opacity`, applied via a `saveLayer` so the blend operates
 * on the destination pixels under the overlay's source rect.
 *
 * Shared between media and frame renderers — see
 * `docs/architecture/rendering.md § 6b`.
 *
 * @param overlays      ordered list; empty = no-op.
 * @param textureBitmaps optional `textureRefId → ImageBitmap` map. Texture
 *   overlays without an entry render nothing (the caller is responsible for
 *   resolving bitmaps via [rememberOverlayTextureBitmaps]).
 */
internal fun DrawScope.drawOverlayStack(
    overlays: List<OverlayStyle>,
    left: Float,
    top: Float,
    right: Float,
    bottom: Float,
    textureBitmaps: Map<String, ImageBitmap> = emptyMap(),
) {
    if (overlays.isEmpty()) return
    val w = right - left
    val h = bottom - top
    if (w <= 0f || h <= 0f) return

    val rect = Rect(left, top, right, bottom)
    for (overlay in overlays) {
        val paint = Paint().apply {
            alpha = overlay.opacity.coerceIn(0f, 1f)
            blendMode = overlay.blendMode.toComposeBlendMode()
        }
        drawIntoCanvas { canvas ->
            canvas.saveLayer(rect, paint)
            drawBackgroundData(
                data = overlay.source.toBackgroundData(),
                left = left, top = top, right = right, bottom = bottom,
                textureBitmap = (overlay.source as? OverlaySource.Texture)
                    ?.let { textureBitmaps[it.textureRefId] },
            )
            canvas.restore()
        }
    }
}

private fun OverlaySource.toBackgroundData(): BackgroundData = when (this) {
    is OverlaySource.SolidColor ->
        BackgroundData.SolidBackgroundData(color = color, opacity = 1f)
    is OverlaySource.Texture ->
        BackgroundData.TextureBackgroundData(textureRefId = textureRefId, tile = tile, opacity = 1f)
    is OverlaySource.Procedural ->
        BackgroundData.ProceduralBackgroundData(pattern = pattern, fillColor = fillColor, opacity = 1f)
}

private fun NodeBlendMode.toComposeBlendMode(): BlendMode = when (this) {
    NodeBlendMode.Normal -> BlendMode.SrcOver
    NodeBlendMode.Multiply -> BlendMode.Multiply
    NodeBlendMode.Screen -> BlendMode.Screen
    NodeBlendMode.Overlay -> BlendMode.Overlay
    NodeBlendMode.SoftLight -> BlendMode.Softlight
    NodeBlendMode.Darken -> BlendMode.Darken
    NodeBlendMode.Lighten -> BlendMode.Lighten
}

/**
 * Resolves the [ImageBitmap]s referenced by `Texture` entries in [overlays]
 * into a stable map keyed by `textureRefId`.
 *
 * Decoded via the shared [loadAppearanceBitmap] (ARGB_8888 software bitmap, so it
 * can back a [android.graphics.BitmapShader] for Repeat tile modes; stable
 * memory-cache key). The decode runs in a `LaunchedEffect` keyed on the set of
 * refIds; loads survive recomposition and missing entries simply remain absent
 * (the renderer skips a texture overlay whose bitmap hasn't arrived yet).
 */
@Composable
internal fun rememberOverlayTextureBitmaps(
    overlays: List<OverlayStyle>,
): Map<String, ImageBitmap> {
    val refIds: List<String> = overlays
        .mapNotNull { (it.source as? OverlaySource.Texture)?.textureRefId }
        .filter { it.isNotBlank() }
        .distinct()
    if (refIds.isEmpty()) return emptyMap()

    val context = LocalContext.current
    val refIdsKey = refIds.joinToString("|")
    var bitmaps by remember(refIdsKey) { mutableStateOf<Map<String, ImageBitmap>>(emptyMap()) }

    LaunchedEffect(refIdsKey) {
        val loaded = mutableMapOf<String, ImageBitmap>()
        for (refId in refIds) {
            runCatching {
                loadAppearanceBitmap(context, refId)?.let { loaded[refId] = it }
            }
        }
        bitmaps = loaded
    }
    return bitmaps
}

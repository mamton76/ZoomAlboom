package com.mamton.zoomalbum.feature.canvas.view

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.core.graphics.toColorInt
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import com.mamton.zoomalbum.core.math.Camera
import com.mamton.zoomalbum.core.math.TransformUtils
import com.mamton.zoomalbum.domain.model.AlbumBackground
import com.mamton.zoomalbum.domain.model.BackgroundData
import com.mamton.zoomalbum.domain.model.TileData
import com.mamton.zoomalbum.domain.model.TileMode
import kotlin.math.roundToInt

/**
 * Half-extent of the fixed world rect used as the anchor for Procedural
 * fill-rect patterns (Gradient / Watercolor / PaperGrain / Noise) in
 * WorldLocked album backgrounds. The pattern exists inside
 * `(-half, -half) → (+half, +half)` in world coords; outside that, fill-rect
 * patterns are blank (Gradient shows end-color, dot patterns show nothing).
 *
 * TODO §19.10: derive from album extent / `AlbumPresentationProfile` instead
 * of a hardcoded constant.
 */
private const val PROCEDURAL_WORLD_ANCHOR_HALF = 2500f

/**
 * Camera-locked album background — drawn OUTSIDE the camera `graphicsLayer`.
 * Screen-fixed: does not move/scale with pan/zoom.
 */
@Composable
fun CameraLockedAlbumBackground(background: AlbumBackground) {
    val textureBitmap = rememberBackgroundBitmap(background.data)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .drawBehind {
                drawBackgroundData(
                    data = background.data,
                    left = 0f, top = 0f,
                    right = size.width, bottom = size.height,
                    textureBitmap = textureBitmap,
                )
            },
    )
}

/**
 * World-locked album background — drawn INSIDE the camera `graphicsLayer`,
 * before any nodes. Moves and scales with pan/zoom/rotation.
 */
@Composable
fun WorldLockedAlbumBackground(
    background: AlbumBackground,
    camera: Camera,
    screenSize: IntSize,
) {
    if (screenSize.width <= 0 || screenSize.height <= 0) return
    val worldRect = TransformUtils.cameraViewport(
        cameraCx = camera.cx,
        cameraCy = camera.cy,
        cameraScale = camera.scale,
        cameraRotation = camera.rotation,
        screenWidth = screenSize.width.toFloat(),
        screenHeight = screenSize.height.toFloat(),
    )
    val textureBitmap = rememberBackgroundBitmap(background.data)
    // No inner graphicsLayer here: we don't need a separate render target — the
    // parent camera Box's graphicsLayer already supplies the world↔screen
    // transform. Putting one here would allocate a 0×0 RenderNode that clips
    // (or zero-samples) draws made at large world coordinates.
    Spacer(
        modifier = Modifier
            .drawBehind {
                val data = background.data
                // For a non-repeating texture in world-locked mode the draw rect
                // must be anchored to TileData's world coords — otherwise we'd
                // stretch the image across the visible viewport, which makes it
                // follow the camera (camera-locked, not world-locked). For
                // Repeat the shader brush handles tiling at GPU speed; we pass
                // the visible world rect as the bounds and the shader covers it
                // with a single draw call.
                val (l, t, r, b) = textureWorldRect(data) ?: worldRect.asQuad()
                drawBackgroundData(
                    data = data,
                    left = l, top = t, right = r, bottom = b,
                    textureBitmap = textureBitmap,
                    // Fixed world anchor for Procedural fill-rect patterns
                    // (Gradient/Watercolor/Grain/Noise) so they don't slide
                    // with the camera. TODO §19.10: wire this to album extent /
                    // AlbumPresentationProfile instead of a hardcoded constant.
                    anchorLeft = -PROCEDURAL_WORLD_ANCHOR_HALF,
                    anchorTop = -PROCEDURAL_WORLD_ANCHOR_HALF,
                    anchorRight = PROCEDURAL_WORLD_ANCHOR_HALF,
                    anchorBottom = PROCEDURAL_WORLD_ANCHOR_HALF,
                )
            },
    )
}

/**
 * For a [BackgroundData.TextureBackgroundData] in a non-tiling mode, returns
 * the world rect the texture occupies — derived from [TileData.tileOriginX]/Y/Width/Height.
 * Returns null for all other shapes (caller should fall back to the viewport rect).
 */
private fun textureWorldRect(data: BackgroundData): FloatQuad? {
    if (data !is BackgroundData.TextureBackgroundData) return null
    return when (data.tile.tileMode) {
        TileMode.None, TileMode.Stretch, TileMode.Cover, TileMode.Contain -> FloatQuad(
            left = data.tile.tileOriginX,
            top = data.tile.tileOriginY,
            right = data.tile.tileOriginX + data.tile.tileWidth,
            bottom = data.tile.tileOriginY + data.tile.tileHeight,
        )
        TileMode.Repeat -> null
    }
}

private data class FloatQuad(val left: Float, val top: Float, val right: Float, val bottom: Float)

private fun com.mamton.zoomalbum.core.math.BoundingBox.asQuad(): FloatQuad =
    FloatQuad(left, top, right, bottom)

// ── Shared draw entry point (used by both album and frame renderers) ────

/**
 * Paints [data] into the rect (`left`,`top`)→(`right`,`bottom`) in whatever
 * coordinate space the caller is currently drawing into.
 *
 * @param textureBitmap must be non-null when [data] is [BackgroundData.TextureBackgroundData],
 *   provided by the caller via [rememberBackgroundBitmap] at the Composable level.
 * @param anchorLeft, anchorTop, anchorRight, anchorBottom — pattern anchor rect
 *   for Procedural backgrounds. Default = viewport (correct for CameraLocked
 *   album + Frame). [WorldLockedAlbumBackground] overrides with a fixed world
 *   rect so fill-rect patterns (Gradient/Watercolor/Grain/Noise) stay anchored
 *   in world coords. Unused by Solid and Texture.
 */
internal fun DrawScope.drawBackgroundData(
    data: BackgroundData,
    left: Float,
    top: Float,
    right: Float,
    bottom: Float,
    textureBitmap: ImageBitmap? = null,
    anchorLeft: Float = left,
    anchorTop: Float = top,
    anchorRight: Float = right,
    anchorBottom: Float = bottom,
) {
    val w = right - left
    val h = bottom - top
    if (w <= 0f || h <= 0f) return

    when (data) {
        is BackgroundData.SolidBackgroundData -> {
            val color = parseHex(data.color) ?: return
            drawRect(
                color = color.copy(alpha = (color.alpha * data.opacity).coerceIn(0f, 1f)),
                topLeft = Offset(left, top),
                size = Size(w, h),
            )
        }
        is BackgroundData.TextureBackgroundData -> {
            val bitmap = textureBitmap ?: return
            drawTextureBitmap(
                bitmap = bitmap,
                left = left, top = top, right = right, bottom = bottom,
                tile = data.tile,
                opacity = data.opacity,
            )
        }
        is BackgroundData.ProceduralBackgroundData -> {
            drawProceduralPattern(
                pattern = data.pattern,
                left = left, top = top, right = right, bottom = bottom,
                opacity = data.opacity,
                anchorLeft = anchorLeft, anchorTop = anchorTop,
                anchorRight = anchorRight, anchorBottom = anchorBottom,
            )
        }
    }
}

/**
 * Composable-level helper: returns the decoded texture bitmap when [data] is a
 * [BackgroundData.TextureBackgroundData], otherwise null.
 *
 * Loaded once per URI via Coil's [SingletonImageLoader]. We deliberately do not
 * use `rememberAsyncImagePainter` — that returns a Painter which the per-tile
 * loop has to invoke once per tile. With the [ImageBitmap] path we can build a
 * single `BitmapShader` for Repeat modes and let the GPU handle tiling in one
 * draw call, which makes zoom-out cost constant instead of O(tile_count).
 */
@Composable
internal fun rememberBackgroundBitmap(data: BackgroundData?): ImageBitmap? {
    val refId = (data as? BackgroundData.TextureBackgroundData)?.textureRefId
    if (refId.isNullOrBlank()) return null
    val context = LocalContext.current
    var bitmap by remember(refId) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(refId) {
        runCatching {
            // allowHardware(false): Coil's default Bitmap.Config.HARDWARE cannot
            // back a BitmapShader — the shader silently samples transparent.
            // We also defensively re-copy non-ARGB_8888 configs (RGB_565, F16, …)
            // since BitmapShader's behavior on those is device-dependent.
            val request = ImageRequest.Builder(context)
                .data(refId)
                .allowHardware(false)
                .build()
            val result = SingletonImageLoader.get(context).execute(request)
            val image = (result as? SuccessResult)?.image
            if (image != null) {
                val raw = image.toBitmap()
                val safe = if (raw.config == android.graphics.Bitmap.Config.ARGB_8888) raw
                else raw.copy(android.graphics.Bitmap.Config.ARGB_8888, false) ?: raw
                bitmap = safe.asImageBitmap()
            }
        }
    }
    return bitmap
}

// ── Texture rendering (BitmapShader for Repeat modes; single draw otherwise) ──

private fun DrawScope.drawTextureBitmap(
    bitmap: ImageBitmap,
    left: Float, top: Float, right: Float, bottom: Float,
    tile: TileData,
    opacity: Float,
) {
    val w = right - left
    val h = bottom - top
    if (w <= 0f || h <= 0f) return

    when (tile.tileMode) {
        TileMode.None, TileMode.Stretch, TileMode.Cover, TileMode.Contain -> {
            // Single draw filling the anchored rect.
            // Cover/Contain aspect-preservation deferred; behave as Stretch for MVP.
            drawImage(
                image = bitmap,
                dstOffset = IntOffset(left.roundToInt(), top.roundToInt()),
                dstSize = IntSize(w.roundToInt().coerceAtLeast(1), h.roundToInt().coerceAtLeast(1)),
                alpha = opacity,
            )
        }
        TileMode.Repeat -> drawTiledShader(
            bitmap = bitmap,
            rectLeft = left, rectTop = top, rectW = w, rectH = h,
            tile = tile, opacity = opacity,
        )
    }
}

/**
 * Single-draw tiling via [android.graphics.BitmapShader] painted through the
 * native [android.graphics.Canvas]. We deliberately bypass Compose's
 * `ShaderBrush` wrapper — when applied inside a `graphicsLayer`, that wrapper
 * has produced silently-transparent output for pre-built shaders.
 *
 * The shader's `localMatrix` scales the source bitmap so one copy spans
 * `tileWidth × tileHeight` of the caller's coordinate space, then translates
 * so bitmap pixel (0, 0) lands at `(tileOriginX, tileOriginY)`. `TileMode.REPEAT`
 * extends the pattern infinitely; the destination rect bounds what's
 * rasterised. Total cost is one `drawRect` call regardless of zoom level.
 *
 * `isFilterBitmap` enables bilinear sampling so non-1:1 `tileWidth/bw` ratios
 * don't pixelate. `isDither` reduces banding on low-bit panels.
 */
private fun DrawScope.drawTiledShader(
    bitmap: ImageBitmap,
    rectLeft: Float, rectTop: Float, rectW: Float, rectH: Float,
    tile: TileData,
    opacity: Float,
) {
    if (rectW <= 0f || rectH <= 0f) return
    val tw = tile.tileWidth
    val th = tile.tileHeight
    if (tw <= 0f || th <= 0f) return

    val androidBitmap = bitmap.asAndroidBitmap()
    val bw = androidBitmap.width.toFloat()
    val bh = androidBitmap.height.toFloat()
    if (bw <= 0f || bh <= 0f) return

    val shader = android.graphics.BitmapShader(
        androidBitmap,
        android.graphics.Shader.TileMode.REPEAT,
        android.graphics.Shader.TileMode.REPEAT,
    )
    val matrix = android.graphics.Matrix().apply {
        setScale(tw / bw, th / bh)
        postTranslate(tile.tileOriginX, tile.tileOriginY)
    }
    shader.setLocalMatrix(matrix)

    val paint = android.graphics.Paint().apply {
        this.shader = shader
        alpha = (opacity.coerceIn(0f, 1f) * 255f).roundToInt()
        isFilterBitmap = true
        isDither = true
    }

    drawIntoCanvas { canvas ->
        canvas.nativeCanvas.drawRect(
            rectLeft,
            rectTop,
            rectLeft + rectW,
            rectTop + rectH,
            paint,
        )
    }
}

private fun parseHex(hex: String): Color? = runCatching { Color(hex.toColorInt()) }.getOrNull()

package com.mamton.zoomalbum.feature.canvas.view

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.request.bitmapConfig
import coil3.toBitmap
import coil3.video.videoFrameMillis

/**
 * Shared loader for non-content **appearance assets** — decoration PNGs, alpha-mask
 * images, and overlay textures. See `docs/to_discuss.md § 28` / `docs/todo.md § 28.1`.
 *
 * These three previously each ran their own `SingletonImageLoader.execute(...)` with
 * `allowHardware(false)` followed by a per-load `copy(ARGB_8888)`. § 28.1 unifies
 * that into one request shape and fixes two things:
 *  - **Config:** the copy existed because `allowHardware(false)` alone can still hand
 *    back an RGB_565 (alpha-less) bitmap, which `DstIn` masks and transparent
 *    decorations need `ARGB_8888` for. We now request `bitmapConfig(ARGB_8888)`
 *    directly, so Coil decodes into the right config — the copy is gone from the
 *    common path (one defensive guard remains for the rare decoder downgrade).
 *  - **Cache:** an explicit stable [memoryCacheKey] per asset URI lets repeated loads
 *    of the same asset hit Coil's memory cache instead of re-decoding.
 *
 * This slice does **not** change *where* or *how often* callers load (that residency
 * work is § 28.2) — only the request shape, in one place. Callers keep their own
 * `runCatching` / state plumbing.
 *
 * [videoFrameMillis] (non-null) loads a frame from a video URI instead of an image
 * — the video-poster path, which differs from the image path only by this request
 * param. It also distinguishes the memory-cache key, so a future "pick poster
 * frame" at a different time won't collide.
 *
 * Returns `null` for a blank URI or a non-success result. May throw on a hard Coil
 * failure — callers wrap in `runCatching`, mirroring the prior per-loader behavior.
 */
internal suspend fun loadAppearanceBitmap(
    context: Context,
    assetUri: String,
    videoFrameMillis: Long? = null,
): ImageBitmap? {
    if (assetUri.isBlank()) return null
    val frameSuffix = videoFrameMillis?.let { "@frame=$it" }.orEmpty()
    val request = ImageRequest.Builder(context)
        .data(assetUri)
        .allowHardware(false)
        .bitmapConfig(Bitmap.Config.ARGB_8888)
        .memoryCacheKey(APPEARANCE_ASSET_CACHE_KEY_PREFIX + assetUri + frameSuffix)
        .apply { if (videoFrameMillis != null) videoFrameMillis(videoFrameMillis) }
        .build()
    val image = (SingletonImageLoader.get(context).execute(request) as? SuccessResult)?.image
        ?: return null
    val bitmap = image.toBitmap()
    // Defensive: bitmapConfig is a decode hint. If a decoder still returned a
    // non-ARGB_8888 (alpha-less) bitmap, copy once so masks / transparent PNGs keep
    // their alpha channel. Expected to be a no-op on the common path.
    val argb = if (bitmap.config == Bitmap.Config.ARGB_8888) bitmap
    else bitmap.copy(Bitmap.Config.ARGB_8888, false) ?: bitmap
    return argb.asImageBitmap()
}

/**
 * Prefix for the explicit appearance-asset memory-cache key so these software,
 * original-size, ARGB_8888 decodes never collide with the content painter's
 * default (size-suffixed) key for the same URI.
 */
private const val APPEARANCE_ASSET_CACHE_KEY_PREFIX = "appearance-asset:"

/** Frame time used for a video node's idle poster (first frame). */
const val VIDEO_POSTER_FRAME_MILLIS = 0L

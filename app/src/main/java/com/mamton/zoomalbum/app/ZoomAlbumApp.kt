package com.mamton.zoomalbum.app

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.video.VideoFrameDecoder
import dagger.hilt.android.HiltAndroidApp

/**
 * Registers Coil's [VideoFrameDecoder] on the singleton [ImageLoader] so the
 * existing Coil rendering path can extract a poster frame from a video URI
 * (see `docs/architecture/video.md § 3` — zero-storage poster). All other
 * media keeps loading through the default decoders unchanged.
 */
@HiltAndroidApp
class ZoomAlbumApp : Application(), SingletonImageLoader.Factory {
    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components { add(VideoFrameDecoder.Factory()) }
            .build()
}

package com.mamton.zoomalbum.feature.canvas.playback

import android.media.MediaCodecList

/**
 * Derives the bounded player-pool size *K* from a device decoder-capability
 * probe (`video.md § 5`, `todo.md § 27.5`). Hardware `MediaCodec` decoders are
 * capped per device, so "simultaneous" playback can never be unbounded.
 *
 * There is no portable API for "max concurrent video decoders across all
 * codecs at this resolution" — `getMaxSupportedInstances()` is per-codec and
 * resolution-independent, and real concurrency is lower. So the probe reads the
 * best per-codec instance count and clamps it hard to a conservative
 * [POOL_CEILING]. On any failure it returns [POOL_DEFAULT].
 */
object VideoDecoderProbe {

    /** Hard upper bound on simultaneous players regardless of what the probe reports. */
    const val POOL_CEILING = 4

    /** Fallback when the probe can't read codec capabilities. */
    const val POOL_DEFAULT = 2

    fun maxConcurrentVideoPlayers(): Int = runCatching {
        val list = MediaCodecList(MediaCodecList.REGULAR_CODECS)
        val best = list.codecInfos
            .filterNot { it.isEncoder }
            .flatMap { info ->
                info.supportedTypes
                    .filter { it.startsWith("video/", ignoreCase = true) }
                    .mapNotNull { type ->
                        runCatching { info.getCapabilitiesForType(type).maxSupportedInstances }
                            .getOrNull()
                    }
            }
            .filter { it > 0 }
            .maxOrNull() ?: POOL_DEFAULT
        best.coerceIn(1, POOL_CEILING)
    }.getOrDefault(POOL_DEFAULT)
}

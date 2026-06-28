package com.mamton.zoomalbum.feature.canvas.view

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalContext
import com.mamton.zoomalbum.domain.model.AlphaMaskSource
import com.mamton.zoomalbum.domain.model.CanvasNode
import com.mamton.zoomalbum.domain.model.MediaAppearance
import com.mamton.zoomalbum.domain.model.MediaType
import com.mamton.zoomalbum.domain.model.OverlaySource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Which kind of appearance asset a cached bitmap is. Part of the cache key so
 * retain/prewarm bookkeeping stays per-kind and future per-kind decode tweaks
 * have somewhere to hang. NOT a decode discriminator today — all three decode
 * identically via [loadAppearanceBitmap]. See `docs/todo.md § 28.2`.
 */
enum class AppearanceAssetKind { Decoration, ContentMask, OverlayTexture, VideoPoster }

/**
 * Storage key for [AppearanceAssetCache]. Keyed by **asset identity**, never by
 * node — one decoration/mask/overlay reused by many nodes resolves to a single
 * shared bitmap entry (one decode, one allocation). A `sizeBucket` may be added
 * later for size-aware loading; intentionally absent in this slice.
 */
data class AppearanceAssetKey(
    val assetUri: String,
    val kind: AppearanceAssetKind,
)

/**
 * Session-level residency cache for non-content appearance assets — decoration
 * PNGs, alpha-mask images, overlay textures. Lives **above** the per-node LOD
 * switch (provided from `CanvasScaffold` via [LocalAppearanceAssetCache]), so a
 * node crossing `Full ↔ Simplified` during zoom re-reads a resident bitmap
 * synchronously instead of reloading from scratch — the fix for the
 * "content first, decoration pops in later" flicker (`docs/to_discuss.md § 28`,
 * `docs/todo.md § 28.2`).
 *
 * Decode goes through the shared [loadAppearanceBitmap] (ARGB_8888 + stable
 * memory-cache key, § 28.1). Mutation happens on the main thread: [ensure]
 * launches on a main-dispatched [scope] and writes the snapshot map from there,
 * so [get] reads are consistent during composition.
 *
 * Eviction is a bounded **LRU cold tail** ([retainOnly]) — non-retained entries
 * aren't dropped immediately, so a node that briefly leaves and re-enters the
 * near-viewport window during a fast zoom/pan keeps its bitmap (no re-flicker).
 */
@Stable
class AppearanceAssetCache(
    private val context: Context,
    private val scope: CoroutineScope,
    private val coldTailCap: Int = DEFAULT_COLD_TAIL_CAP,
) {
    private val bitmaps = mutableStateMapOf<AppearanceAssetKey, ImageBitmap>()
    private val inFlight = HashSet<AppearanceAssetKey>()

    /** Resident keys not currently retained, eldest-cooled first — eviction order. */
    private val coldTail = LinkedHashSet<AppearanceAssetKey>()

    /** Resident bitmap for [key], or `null` if not loaded yet. A snapshot read. */
    fun get(key: AppearanceAssetKey): ImageBitmap? = bitmaps[key]

    /**
     * Kick off loads for any of [keys] that aren't already resident or in flight.
     * Idempotent and cheap to call repeatedly (loader composables + the canvas
     * prewarm both call it).
     */
    fun ensure(keys: Collection<AppearanceAssetKey>) {
        for (key in keys) {
            if (key.assetUri.isBlank()) continue
            if (bitmaps.containsKey(key) || key in inFlight) continue
            inFlight += key
            scope.launch {
                // Video posters load a frame from the video URI; the rest are images.
                val frameMillis =
                    if (key.kind == AppearanceAssetKind.VideoPoster) VIDEO_POSTER_FRAME_MILLIS else null
                val bitmap = runCatching {
                    loadAppearanceBitmap(context, key.assetUri, frameMillis)
                }.getOrNull()
                inFlight -= key
                if (bitmap != null) bitmaps[key] = bitmap
            }
        }
    }

    /**
     * Keep [retained] hot; move every other resident key to the LRU cold tail and
     * evict only from the eldest end once the tail exceeds [coldTailCap]. Drive
     * this off the *set* of near-visible asset keys (not every frame) so it fires
     * only on actual visibility changes — avoids per-frame churn.
     */
    fun retainOnly(retained: Set<AppearanceAssetKey>) {
        for (key in planColdTailEviction(bitmaps.keys.toSet(), retained, coldTail, coldTailCap)) {
            bitmaps.remove(key)
        }
    }

    companion object {
        /** Max resident-but-not-retained entries before eviction kicks in. */
        const val DEFAULT_COLD_TAIL_CAP = 64
    }
}

/**
 * Pure LRU cold-tail step for [AppearanceAssetCache.retainOnly], extracted so the
 * eviction rule is unit-testable. Keeps [retained] hot (never evicted), moves the
 * remaining [resident] keys to the [coldTail] (eldest-first, mutated in place,
 * preserving each key's cool-time order), and returns the keys to drop from storage
 * once the tail exceeds [cap]. See `todo.md § 28.2`.
 */
internal fun planColdTailEviction(
    resident: Set<AppearanceAssetKey>,
    retained: Set<AppearanceAssetKey>,
    coldTail: LinkedHashSet<AppearanceAssetKey>,
    cap: Int,
): List<AppearanceAssetKey> {
    // Re-retained keys leave the cold tail (hot again → not eviction candidates).
    coldTail.removeAll(retained)
    // Newly-cooled resident keys join the newest end, preserving cool-time order.
    for (key in resident) {
        if (key !in retained && key !in coldTail) coldTail.add(key)
    }
    // Evict the eldest-cooled beyond the cap.
    val evicted = ArrayList<AppearanceAssetKey>()
    while (coldTail.size > cap) {
        val eldest = coldTail.iterator().next()
        coldTail.remove(eldest)
        evicted += eldest
    }
    return evicted
}

/**
 * Provides the canvas-session [AppearanceAssetCache] down to the per-node loaders.
 * `null` default: composables rendered outside the canvas (e.g. preset-preview
 * sheets) fall back to a local, non-resident cache — see the loaders.
 */
val LocalAppearanceAssetCache = staticCompositionLocalOf<AppearanceAssetCache?> { null }

/**
 * Creates an [AppearanceAssetCache] scoped to the current composition. Hoist this
 * at `CanvasScaffold` (above the LOD switch) and provide it via
 * [LocalAppearanceAssetCache]; loads cancel when the composition leaves.
 */
@Composable
fun rememberAppearanceAssetCache(): AppearanceAssetCache {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    return remember { AppearanceAssetCache(context, scope) }
}

/**
 * The [AppearanceAssetKey]s referenced by a node — decoration assets (media only),
 * an image content-mask, texture overlays (both node types), and a video node's
 * idle poster frame. Used by the canvas prewarm/retain pass so these survive LOD
 * remounts during zoom. Procedural / gradient / solid sources carry no asset and
 * are skipped.
 */
fun appearanceAssetKeys(node: CanvasNode): Set<AppearanceAssetKey> {
    val keys = HashSet<AppearanceAssetKey>()

    // A video's poster frame is keyed by the video URI — kept resident so the
    // poster doesn't re-extract (flash) when the node re-crosses the LOD boundary.
    if (node is CanvasNode.Media && node.mediaType == MediaType.VIDEO) {
        node.mediaRefId.takeIf { it.isNotBlank() }
            ?.let { keys += AppearanceAssetKey(it, AppearanceAssetKind.VideoPoster) }
    }

    val appearance = when (node) {
        is CanvasNode.Media -> node.appearance
        is CanvasNode.Frame -> node.appearance
    } ?: return keys

    appearance.overlays.forEach { overlay ->
        (overlay.source as? OverlaySource.Texture)?.textureRefId
            ?.takeIf { it.isNotBlank() }
            ?.let { keys += AppearanceAssetKey(it, AppearanceAssetKind.OverlayTexture) }
    }
    (appearance.contentMask?.source as? AlphaMaskSource.Image)?.maskRefId
        ?.takeIf { it.isNotBlank() }
        ?.let { keys += AppearanceAssetKey(it, AppearanceAssetKind.ContentMask) }
    if (appearance is MediaAppearance) {
        appearance.decorations.forEach { decoration ->
            decoration.assetUri
                .takeIf { it.isNotBlank() }
                ?.let { keys += AppearanceAssetKey(it, AppearanceAssetKind.Decoration) }
        }
    }
    return keys
}

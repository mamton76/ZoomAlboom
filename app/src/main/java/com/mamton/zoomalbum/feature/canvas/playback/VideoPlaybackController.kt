package com.mamton.zoomalbum.feature.canvas.playback

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import java.io.File

/**
 * `CanvasScaffold`-level playback holder, keyed by `nodeId`. Owns a **bounded
 * pool** of at most [poolSize] [ExoPlayer]s (`video.md § 5`, `todo.md § 27.5`):
 * playback is simultaneous (multiple atmospheric clips at once) but capped,
 * because hardware decoders are capped. Playback state lives here and never on
 * a domain model — a video node carries no `isPlaying` field (`video.md § 6`).
 *
 * Model:
 * - [playingNodeIds] — the set of videos the user has toggled on (tap in View,
 *   or the Edit "Play / Pause" menu item).
 * - [reconcile] assigns the pool's players to the top-[poolSize] *candidates*
 *   (playing AND currently a `RenderDetail.Full` visible node), ranked by most
 *   recently started. Nodes that want to play but can't get a player — because
 *   they're off-screen or lost the eviction race — simply show their poster.
 * - [assignments] — the live nodeId → player bindings the render loop mounts.
 *
 * MVP notes: tapping a playing video stops it (drops it from [playingNodeIds]
 * and restarts on the next play — no resume-from-position); an ended clip keeps
 * its player until evicted. Loop / mute / start-position are out of scope.
 */
@Stable
class VideoPlaybackController(
    context: Context,
    private val poolSize: Int,
) {
    private val appContext = context.applicationContext

    private val freePlayers = ArrayDeque<ExoPlayer>()
    private var createdPlayers = 0

    private val _assignments = mutableStateMapOf<String, ExoPlayer>()
    /** Live nodeId → player bindings; the render loop mounts a surface per entry. */
    val assignments: Map<String, ExoPlayer> get() = _assignments

    // Plain (non-state) bookkeeping read only inside reconcile / toggle.
    private val uris = mutableMapOf<String, String>()
    private val startOrder = mutableMapOf<String, Long>()
    private var orderCounter = 0L

    /** Videos the user has requested to play (may exceed [poolSize]). */
    var playingNodeIds by mutableStateOf<Set<String>>(emptySet())
        private set

    /** True iff this node currently holds a pooled player (i.e. is actually playing). */
    fun isPlaying(nodeId: String): Boolean = nodeId in _assignments

    /**
     * Toggle a video in/out of the playing set. Adding it makes it the most
     * recently started candidate (so it wins the next eviction). The actual
     * player assignment happens in [reconcile].
     */
    fun togglePlayback(nodeId: String, uri: String) {
        uris[nodeId] = uri
        playingNodeIds = if (nodeId in playingNodeIds) {
            startOrder.remove(nodeId)
            playingNodeIds - nodeId
        } else {
            startOrder[nodeId] = orderCounter++
            playingNodeIds + nodeId
        }
    }

    /**
     * Bind pooled players to the top-[poolSize] candidates. [fullVisibleIds] is
     * the set of video nodes currently at `RenderDetail.Full` — the LOD bound on
     * candidacy. Call reactively whenever that set or [playingNodeIds] changes.
     */
    fun reconcile(fullVisibleIds: Set<String>) {
        val keep = selectPlaybackKeepSet(playingNodeIds, fullVisibleIds, startOrder, poolSize)

        // Evict players no longer kept (off-screen / lost the recency race /
        // toggled off) → returns the player to the pool; the node falls back to
        // its poster.
        for (id in _assignments.keys.toList()) {
            if (id !in keep) {
                _assignments.remove(id)?.let { recycle(it) }
            }
        }
        // Acquire players for newly-kept nodes, up to the pool ceiling.
        for (id in keep) {
            if (id in _assignments) continue
            val uri = uris[id] ?: continue
            val player = obtainPlayer() ?: break
            player.setMediaItem(MediaItem.fromUri(Uri.fromFile(File(uri))))
            player.prepare()
            player.playWhenReady = true
            _assignments[id] = player
        }
    }

    private fun recycle(player: ExoPlayer) {
        player.pause()
        player.clearMediaItems()
        freePlayers.addLast(player)
    }

    private fun obtainPlayer(): ExoPlayer? {
        freePlayers.removeFirstOrNull()?.let { return it }
        if (createdPlayers < poolSize) {
            createdPlayers++
            return ExoPlayer.Builder(appContext).build()
        }
        return null
    }

    fun release() {
        _assignments.values.forEach { it.release() }
        freePlayers.forEach { it.release() }
        _assignments.clear()
        freePlayers.clear()
        createdPlayers = 0
    }
}

/**
 * Pure eviction/ranking rule for the bounded pool, extracted so it is unit-
 * testable without a real [ExoPlayer] (`todo.md § 27.7`). Candidates are the
 * playing videos that are currently `RenderDetail.Full` ([fullVisibleIds]) —
 * off-screen / low-LOD nodes are excluded outright. Among candidates the most
 * recently started win the [poolSize] slots; the rest fall back to poster.
 */
internal fun selectPlaybackKeepSet(
    playingNodeIds: Set<String>,
    fullVisibleIds: Set<String>,
    startOrder: Map<String, Long>,
    poolSize: Int,
): Set<String> =
    playingNodeIds
        .filter { it in fullVisibleIds }
        .sortedByDescending { startOrder[it] ?: 0L }
        .take(poolSize.coerceAtLeast(0))
        .toSet()

/**
 * Creates a pool-backed [VideoPlaybackController] scoped to the current
 * composition (released on dispose). Pool size comes from the device decoder
 * probe ([VideoDecoderProbe]). Hoist this at the `CanvasScaffold` level and pass
 * it into the canvas so tap routing, the Edit menu, and the render loop share
 * one holder.
 */
@Composable
fun rememberVideoPlaybackController(): VideoPlaybackController {
    val context = LocalContext.current
    val controller = remember {
        VideoPlaybackController(
            context = context.applicationContext,
            poolSize = VideoDecoderProbe.maxConcurrentVideoPlayers(),
        )
    }
    DisposableEffect(controller) {
        onDispose { controller.release() }
    }
    return controller
}

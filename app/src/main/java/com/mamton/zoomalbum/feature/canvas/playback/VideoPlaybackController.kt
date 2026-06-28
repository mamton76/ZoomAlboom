package com.mamton.zoomalbum.feature.canvas.playback

import android.content.Context
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
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer

/**
 * `CanvasScaffold`-level playback holder, keyed by `nodeId`. Owns a **bounded
 * pool** of at most [poolSize] [ExoPlayer]s (`video.md § 5`, `todo.md § 27.5`):
 * playback is simultaneous (multiple atmospheric clips at once) but capped,
 * because hardware decoders are capped. Playback state lives here and never on
 * a domain model — a video node carries no `isPlaying` field (`video.md § 6`).
 *
 * Model:
 * - [playingNodeIds] — the set of videos the user has activated (double-tap, or
 *   the Edit "Play / Pause" menu item).
 * - [pausedNodeIds] — activated videos paused in place (keep their player +
 *   position + frozen frame).
 * - [reconcile] assigns the pool's players to the top-[poolSize] *candidates*
 *   (active AND currently a `RenderDetail.Full` visible node), ranked by most
 *   recently started. Nodes that want a player but can't get one — off-screen or
 *   lost the eviction race — show their poster.
 * - [assignments] — the live nodeId → player bindings the render loop mounts.
 *
 * MVP notes: [togglePlayback] is pause/resume-in-place (a video never *stops*
 * via gesture — it leaves only by eviction or node delete); an ended clip
 * replays from 0 on the next toggle. Loop / mute / start-position are out of
 * scope, and a paused video evicted under pool pressure loses its position.
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

    /** Videos the user has activated (may exceed [poolSize]). */
    var playingNodeIds by mutableStateOf<Set<String>>(emptySet())
        private set

    /**
     * Active videos that are paused **in place** — they keep their pooled player
     * and current position, showing the frozen frame (+ a play badge) rather than
     * the poster. A node here is also in [playingNodeIds]. Compose state so the
     * render loop can show/hide the paused badge.
     */
    var pausedNodeIds by mutableStateOf<Set<String>>(emptySet())
        private set

    /**
     * While true (a camera gesture is active), playback is **frozen**: every
     * assigned player is paused and [reconcile] won't start/resume any player.
     * This makes the gesture pause authoritative. Without it, a `reconcile` that
     * re-acquires or newly assigns a player mid-gesture (a slight pan, an
     * evict→reacquire) would set `playWhenReady = true` and the video would advance
     * *under* the offscreen gesture freeze — the "timing shifts during camera
     * transform" bug. See `todo.md § 27.11`.
     */
    private var frozen = false

    /**
     * Freeze (true) / unfreeze (false) all playback for the duration of a camera
     * gesture. Freeze pauses every assigned player; unfreeze restores each to its
     * normal state (playing unless the user paused it in place). Idempotent.
     */
    fun setFrozen(value: Boolean) {
        if (value == frozen) return
        frozen = value
        for ((id, player) in _assignments) {
            player.playWhenReady = shouldVideoPlay(id, pausedNodeIds, frozen)
        }
    }

    /**
     * Double-tap / menu toggle:
     * - has a player & **playing** → **pause** in place (keep position + frame);
     * - has a player & **paused**  → **resume** from where it left off;
     * - no player (stopped / poster) → become the most-recent active candidate
     *   so [reconcile] assigns a player and starts it from the top.
     *
     * This never *stops* a video (drops it from [playingNodeIds]); a video leaves
     * only via pool eviction (off-screen / lost the recency race) or node delete.
     * That's the "living media" intent — see `video.md § 4`.
     */
    fun togglePlayback(nodeId: String, uri: String) {
        uris[nodeId] = uri
        val player = _assignments[nodeId]
        if (player != null) {
            if (player.playbackState == Player.STATE_ENDED) {
                // No loop in MVP — an ended clip replays from the top instead of
                // being stuck on its final frame.
                player.seekTo(0)
                player.playWhenReady = true
                pausedNodeIds = pausedNodeIds - nodeId
            } else {
                val pausing = player.playWhenReady
                player.playWhenReady = !pausing
                pausedNodeIds = if (pausing) pausedNodeIds + nodeId else pausedNodeIds - nodeId
            }
        } else {
            pausedNodeIds = pausedNodeIds - nodeId
            startOrder[nodeId] = orderCounter++
            playingNodeIds = playingNodeIds + nodeId
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
            // mediaRefId may be a content:// / file:// URI or a bare path — convert
            // safely so an already-formed URI isn't corrupted (see mediaRefToUri).
            player.setMediaItem(MediaItem.fromUri(mediaRefToUri(uri)))
            player.prepare()
            // Honor a pre-existing pause so a paused node reacquiring a player
            // (e.g. after returning on-screen) doesn't auto-resume; and never
            // start playback while frozen by an active camera gesture.
            player.playWhenReady = shouldVideoPlay(id, pausedNodeIds, frozen)
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
/**
 * Whether a pooled video should be playing: not user-paused **in place**, and not
 * [frozen] by an active camera gesture. The single source of truth for the
 * `playWhenReady` decision, shared by [VideoPlaybackController.setFrozen] and
 * [VideoPlaybackController.reconcile] so it stays consistent and unit-testable
 * (`todo.md § 27.11`).
 */
internal fun shouldVideoPlay(
    nodeId: String,
    pausedNodeIds: Set<String>,
    frozen: Boolean,
): Boolean = nodeId !in pausedNodeIds && !frozen

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

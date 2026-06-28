package com.mamton.zoomalbum.feature.canvas.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers the pure eviction/ranking rule of the bounded player pool
 * (`video.md § 5`, `todo.md § 27.5/27.7`). The ExoPlayer-coupled parts of
 * [VideoPlaybackController] need instrumentation and are not exercised here.
 */
class VideoPlaybackKeepSetTest {

    @Test
    fun `keeps all playing candidates when under the pool size`() {
        val keep = selectPlaybackKeepSet(
            playingNodeIds = setOf("a", "b"),
            fullVisibleIds = setOf("a", "b", "c"),
            startOrder = mapOf("a" to 0L, "b" to 1L),
            poolSize = 3,
        )
        assertEquals(setOf("a", "b"), keep)
    }

    @Test
    fun `evicts least recently started when over the pool size`() {
        // c started last, then b, then a (oldest). Pool of 2 keeps the two most
        // recent (c, b); a is evicted to poster.
        val keep = selectPlaybackKeepSet(
            playingNodeIds = setOf("a", "b", "c"),
            fullVisibleIds = setOf("a", "b", "c"),
            startOrder = mapOf("a" to 0L, "b" to 1L, "c" to 2L),
            poolSize = 2,
        )
        assertEquals(setOf("b", "c"), keep)
    }

    @Test
    fun `off-screen playing nodes are not candidates`() {
        // b is playing but not Full-visible → excluded even though a slot is free.
        val keep = selectPlaybackKeepSet(
            playingNodeIds = setOf("a", "b"),
            fullVisibleIds = setOf("a"),
            startOrder = mapOf("a" to 0L, "b" to 5L),
            poolSize = 4,
        )
        assertEquals(setOf("a"), keep)
    }

    @Test
    fun `empty when nothing is playing or pool size is zero`() {
        assertEquals(
            emptySet<String>(),
            selectPlaybackKeepSet(emptySet(), setOf("a"), emptyMap(), 4),
        )
        assertEquals(
            emptySet<String>(),
            selectPlaybackKeepSet(setOf("a"), setOf("a"), mapOf("a" to 0L), 0),
        )
    }

    // ── shouldVideoPlay: the playWhenReady rule shared by setFrozen + reconcile ──

    @Test
    fun `a normal video plays when not paused and not frozen`() {
        assertTrue(shouldVideoPlay("a", pausedNodeIds = emptySet(), frozen = false))
    }

    @Test
    fun `a camera gesture freeze stops playback`() {
        assertFalse(shouldVideoPlay("a", pausedNodeIds = emptySet(), frozen = true))
    }

    @Test
    fun `a user-paused video never plays, frozen or not`() {
        assertFalse(shouldVideoPlay("a", pausedNodeIds = setOf("a"), frozen = false))
        assertFalse(shouldVideoPlay("a", pausedNodeIds = setOf("a"), frozen = true))
    }
}

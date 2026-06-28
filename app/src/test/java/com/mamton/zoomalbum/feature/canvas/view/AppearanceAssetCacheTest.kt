package com.mamton.zoomalbum.feature.canvas.view

import com.mamton.zoomalbum.domain.model.AlphaMask
import com.mamton.zoomalbum.domain.model.AlphaMaskSource
import com.mamton.zoomalbum.domain.model.CanvasNode
import com.mamton.zoomalbum.domain.model.FrameAppearance
import com.mamton.zoomalbum.domain.model.MediaAppearance
import com.mamton.zoomalbum.domain.model.MediaDecoration
import com.mamton.zoomalbum.domain.model.MediaType
import com.mamton.zoomalbum.domain.model.OverlaySource
import com.mamton.zoomalbum.domain.model.OverlayStyle
import com.mamton.zoomalbum.domain.model.ProceduralPattern
import com.mamton.zoomalbum.domain.model.Transform
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure-logic coverage for the appearance-asset residency cache (`todo.md § 28.2`):
 * the asset-key discovery used by the canvas prewarm/retain pass, and the LRU
 * cold-tail eviction rule. The Coil/Compose-coupled parts of [AppearanceAssetCache]
 * (loading, the snapshot map) need instrumentation and aren't exercised here.
 */
class AppearanceAssetCacheTest {

    private fun media(
        appearance: MediaAppearance? = null,
        mediaType: MediaType = MediaType.IMAGE,
        mediaRefId: String = "media-uri",
    ) = CanvasNode.Media(
        id = "m",
        transform = Transform(),
        mediaRefId = mediaRefId,
        mediaType = mediaType,
        appearance = appearance,
    )

    private fun key(uri: String, kind: AppearanceAssetKind) = AppearanceAssetKey(uri, kind)

    // ── appearanceAssetKeys ──────────────────────────────────────────────────

    @Test
    fun `media decorations, image mask and texture overlays all yield keys`() {
        val node = media(
            appearance = MediaAppearance(
                decorations = listOf(
                    MediaDecoration(id = "d1", assetUri = "deco-a"),
                    MediaDecoration(id = "d2", assetUri = "deco-b"),
                ),
                contentMask = AlphaMask(source = AlphaMaskSource.Image(maskRefId = "mask-a")),
                overlays = listOf(
                    OverlayStyle(source = OverlaySource.Texture(textureRefId = "tex-a")),
                ),
            ),
        )

        assertEquals(
            setOf(
                key("deco-a", AppearanceAssetKind.Decoration),
                key("deco-b", AppearanceAssetKind.Decoration),
                key("mask-a", AppearanceAssetKind.ContentMask),
                key("tex-a", AppearanceAssetKind.OverlayTexture),
            ),
            appearanceAssetKeys(node),
        )
    }

    @Test
    fun `a video node yields a poster key from its mediaRefId`() {
        val node = media(mediaType = MediaType.VIDEO, mediaRefId = "clip.mp4")
        assertEquals(
            setOf(key("clip.mp4", AppearanceAssetKind.VideoPoster)),
            appearanceAssetKeys(node),
        )
    }

    @Test
    fun `an image node with no appearance yields no keys`() {
        assertTrue(appearanceAssetKeys(media()).isEmpty())
    }

    @Test
    fun `non-asset overlay and mask sources are skipped`() {
        val node = media(
            appearance = MediaAppearance(
                overlays = listOf(OverlayStyle(source = OverlaySource.SolidColor("#ff0000"))),
                contentMask = AlphaMask(source = AlphaMaskSource.Procedural(pattern = ProceduralPattern.Grid())),
            ),
        )
        assertTrue(appearanceAssetKeys(node).isEmpty())
    }

    @Test
    fun `blank asset uris are skipped`() {
        val node = media(
            appearance = MediaAppearance(
                decorations = listOf(MediaDecoration(id = "d", assetUri = "")),
            ),
        )
        assertTrue(appearanceAssetKeys(node).isEmpty())
    }

    @Test
    fun `frame nodes yield mask and overlay keys but never decoration or poster`() {
        val frame = CanvasNode.Frame(
            id = "f",
            transform = Transform(),
            appearance = FrameAppearance(
                contentMask = AlphaMask(source = AlphaMaskSource.Image(maskRefId = "fmask")),
                overlays = listOf(OverlayStyle(source = OverlaySource.Texture(textureRefId = "ftex"))),
            ),
        )
        assertEquals(
            setOf(
                key("fmask", AppearanceAssetKind.ContentMask),
                key("ftex", AppearanceAssetKind.OverlayTexture),
            ),
            appearanceAssetKeys(frame),
        )
    }

    // ── planColdTailEviction ─────────────────────────────────────────────────

    @Test
    fun `nothing is evicted while the cold tail is within the cap`() {
        val cold = LinkedHashSet<AppearanceAssetKey>()
        val evicted = planColdTailEviction(
            resident = setOf(key("a", AppearanceAssetKind.Decoration)),
            retained = emptySet(),
            coldTail = cold,
            cap = 4,
        )
        assertTrue(evicted.isEmpty())
        assertEquals(1, cold.size)
    }

    @Test
    fun `retained keys are never evicted even over the cap`() {
        val hot = (1..10).map { key("h$it", AppearanceAssetKind.Decoration) }.toSet()
        val cold = LinkedHashSet<AppearanceAssetKey>()
        val evicted = planColdTailEviction(
            resident = hot,
            retained = hot,
            coldTail = cold,
            cap = 2,
        )
        assertTrue(evicted.isEmpty())
        assertTrue(cold.isEmpty())
    }

    @Test
    fun `eldest-cooled keys are evicted first once over the cap`() {
        val cold = LinkedHashSet<AppearanceAssetKey>()
        val a = key("a", AppearanceAssetKind.Decoration)
        val b = key("b", AppearanceAssetKind.Decoration)
        val c = key("c", AppearanceAssetKind.Decoration)
        // a cooled first, then b, then c; cap=1 → a and b evicted, c kept.
        val evicted = planColdTailEviction(
            resident = setOf(a, b, c),
            retained = emptySet(),
            coldTail = cold,
            cap = 1,
        )
        assertEquals(listOf(a, b), evicted)
        assertEquals(linkedSetOf(c), cold)
    }

    @Test
    fun `a key re-retained after cooling is promoted out of the cold tail`() {
        val a = key("a", AppearanceAssetKind.Decoration)
        val b = key("b", AppearanceAssetKind.Decoration)
        val cold = LinkedHashSet<AppearanceAssetKey>()
        // First pass: both cool (cap big enough, nothing evicted).
        planColdTailEviction(setOf(a, b), retained = emptySet(), coldTail = cold, cap = 10)
        assertEquals(linkedSetOf(a, b), cold)
        // Second pass: `a` is retained again → leaves the cold tail.
        planColdTailEviction(setOf(a, b), retained = setOf(a), coldTail = cold, cap = 10)
        assertEquals(linkedSetOf(b), cold)
    }
}

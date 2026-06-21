package com.mamton.zoomalbum.domain.model

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Round-trip tests for [AlphaMask] and its source variants, plus the
 * persistence interaction with [MediaAppearance] / [FrameAppearance].
 *
 * Mirrors the production Json config (`ignoreUnknownKeys = true`, defaults
 * stripped from output) so test JSON matches what hits disk.
 */
class AlphaMaskSerializationTest {

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    private fun <T> roundTrip(value: T, serializer: kotlinx.serialization.KSerializer<T>): T {
        val encoded = json.encodeToString(serializer, value)
        return json.decodeFromString(serializer, encoded)
    }

    // ── Per-source round trips ──────────────────────────────────────────────

    @Test
    fun `Image source round-trips with explicit fields`() {
        val mask = AlphaMask(
            source = AlphaMaskSource.Image(
                maskRefId = "ref-abc",
                channel = MaskChannel.Alpha,
                fitMode = MaskFitMode.Fill,
            ),
            invert = true,
        )
        val restored = roundTrip(mask, AlphaMask.serializer())
        assertEquals(mask, restored)
    }

    @Test
    fun `Image source round-trips with defaults`() {
        val mask = AlphaMask(source = AlphaMaskSource.Image(maskRefId = "r1"))
        val restored = roundTrip(mask, AlphaMask.serializer())
        // Defaults survive the round-trip — decoded value equals the
        // constructed one regardless of whether defaults were written.
        assertEquals(mask, restored)
        val img = restored.source as AlphaMaskSource.Image
        assertEquals(MaskChannel.Luminance, img.channel)
        assertEquals(MaskFitMode.Stretch, img.fitMode)
        assertFalse(restored.invert)
    }

    @Test
    fun `LinearGradient round-trips with stops in order`() {
        val mask = AlphaMask(
            source = AlphaMaskSource.LinearGradient(
                angleDeg = 45f,
                stops = listOf(AlphaStop(0f, 0f), AlphaStop(0.5f, 0.7f), AlphaStop(1f, 1f)),
            ),
        )
        val restored = roundTrip(mask, AlphaMask.serializer())
        assertEquals(mask, restored)
    }

    @Test
    fun `RadialGradient round-trips with elliptical radii`() {
        val mask = AlphaMask(
            source = AlphaMaskSource.RadialGradient(
                centerX = 0.4f,
                centerY = 0.6f,
                radiusX = 0.5f,
                radiusY = 0.2f,
                stops = listOf(AlphaStop(0f, 1f), AlphaStop(1f, 0f)),
            ),
        )
        val restored = roundTrip(mask, AlphaMask.serializer())
        assertEquals(mask, restored)
    }

    @Test
    fun `Procedural source reuses ProceduralPattern unchanged`() {
        val pattern = ProceduralPattern.PaperGrain(intensity = 0.15f, seed = 42)
        val mask = AlphaMask(source = AlphaMaskSource.Procedural(pattern = pattern))
        val restored = roundTrip(mask, AlphaMask.serializer())
        assertEquals(mask, restored)
        val proc = restored.source as AlphaMaskSource.Procedural
        assertTrue(proc.pattern is ProceduralPattern.PaperGrain)
    }

    @Test
    fun `invert flag round-trips both true and false`() {
        val src = AlphaMaskSource.LinearGradient(stops = listOf(AlphaStop(0f, 0f), AlphaStop(1f, 1f)))
        assertTrue(roundTrip(AlphaMask(source = src, invert = true), AlphaMask.serializer()).invert)
        assertFalse(roundTrip(AlphaMask(source = src, invert = false), AlphaMask.serializer()).invert)
    }

    // ── Polymorphic wire form ───────────────────────────────────────────────

    @Test
    fun `source variants serialize under their declared SerialName discriminator`() {
        val image = json.encodeToString(
            AlphaMask.serializer(),
            AlphaMask(source = AlphaMaskSource.Image(maskRefId = "x")),
        )
        assertTrue("Image variant tagged \"Image\"", image.contains("\"type\": \"Image\""))

        val lg = json.encodeToString(
            AlphaMask.serializer(),
            AlphaMask(source = AlphaMaskSource.LinearGradient(stops = listOf(AlphaStop(0f, 0f), AlphaStop(1f, 1f)))),
        )
        assertTrue("LinearGradient variant tagged \"LinearGradient\"", lg.contains("\"type\": \"LinearGradient\""))

        val rg = json.encodeToString(
            AlphaMask.serializer(),
            AlphaMask(source = AlphaMaskSource.RadialGradient(stops = listOf(AlphaStop(0f, 0f), AlphaStop(1f, 1f)))),
        )
        assertTrue("RadialGradient variant tagged \"RadialGradient\"", rg.contains("\"type\": \"RadialGradient\""))

        val proc = json.encodeToString(
            AlphaMask.serializer(),
            AlphaMask(source = AlphaMaskSource.Procedural(pattern = ProceduralPattern.PaperGrain())),
        )
        assertTrue("Procedural variant tagged \"Procedural\"", proc.contains("\"type\": \"Procedural\""))
    }

    // ── Embedded in MediaAppearance / FrameAppearance ──────────────────────

    @Test
    fun `MediaAppearance round-trips with contentMask present`() {
        val appearance = MediaAppearance(
            contentMask = AlphaMask(
                source = AlphaMaskSource.Image(maskRefId = "mref"),
            ),
        )
        val restored = roundTrip(appearance, MediaAppearance.serializer())
        assertEquals(appearance, restored)
    }

    @Test
    fun `FrameAppearance round-trips with contentMask present`() {
        val appearance = FrameAppearance(
            contentMask = AlphaMask(
                source = AlphaMaskSource.RadialGradient(
                    stops = listOf(AlphaStop(0f, 1f), AlphaStop(1f, 0f)),
                ),
            ),
        )
        val restored = roundTrip(appearance, FrameAppearance.serializer())
        assertEquals(appearance, restored)
    }

    @Test
    fun `default null contentMask is omitted from MediaAppearance JSON`() {
        val encoded = json.encodeToString(MediaAppearance.serializer(), MediaAppearance())
        assertFalse(
            "Default-null contentMask should not appear in MediaAppearance output",
            encoded.contains("contentMask"),
        )
    }

    @Test
    fun `default null contentMask is omitted from FrameAppearance JSON`() {
        val encoded = json.encodeToString(FrameAppearance.serializer(), FrameAppearance())
        assertFalse(
            "Default-null contentMask should not appear in FrameAppearance output",
            encoded.contains("contentMask"),
        )
    }

    @Test
    fun `legacy JSON without contentMask still deserializes`() {
        // No `contentMask` key — exercises the new optional field on existing
        // round-trips (defensive: the default `null` must apply on read).
        val raw = """{"opacity":0.8,"cornerRadius":4.0}"""
        val restored = json.decodeFromString(MediaAppearance.serializer(), raw)
        assertNull(restored.contentMask)
        assertEquals(0.8f, restored.opacity, 1e-6f)
        assertEquals(4f, restored.cornerRadius, 1e-6f)
    }
}

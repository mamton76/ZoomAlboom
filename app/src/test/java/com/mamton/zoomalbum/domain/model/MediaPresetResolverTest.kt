package com.mamton.zoomalbum.domain.model

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaPresetResolverTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun media(appearance: MediaAppearance?, binding: PresetBinding?) = CanvasNode.Media(
        id = "m1",
        transform = Transform(cx = 0f, cy = 0f, w = 100f, h = 100f),
        mediaRefId = "ref",
        appearance = appearance,
        presetBinding = binding,
    )

    private val preset = MediaStylePreset(
        id = "p1",
        name = "Vintage",
        appearance = MediaAppearance(
            opacity = 0.2f,
            border = BorderStyle(color = "#FF0000", widthPx = 5f),
        ),
        sections = setOf(AppearanceSection.Opacity, AppearanceSection.Border),
    )
    private val presetsById = mapOf(preset.id to preset)

    @Test
    fun `unbound node resolves to its own appearance`() {
        val own = MediaAppearance(opacity = 0.5f)
        val node = media(own, binding = null)
        assertSame(own, resolveMediaAppearance(node, presetsById))
    }

    @Test
    fun `governed non-overridden section comes from the preset, others from the node`() {
        val own = MediaAppearance(
            opacity = 0.5f,
            cornerRadius = 9f, // ungoverned
            border = BorderStyle(color = "#00FF00", widthPx = 1f), // governed
        )
        val node = media(own, PresetBinding(preset.id, overridden = emptySet()))
        val r = resolveMediaAppearance(node, presetsById)!!
        assertEquals(0.2f, r.opacity)               // governed → preset
        assertEquals("#FF0000", r.border?.color)    // governed → preset
        assertEquals(9f, r.cornerRadius)            // ungoverned → own
    }

    @Test
    fun `overridden governed section keeps the node's own value`() {
        val own = MediaAppearance(
            opacity = 0.5f,
            border = BorderStyle(color = "#00FF00", widthPx = 1f),
        )
        val node = media(own, PresetBinding(preset.id, overridden = setOf(AppearanceSection.Border)))
        val r = resolveMediaAppearance(node, presetsById)!!
        assertEquals(0.2f, r.opacity)               // governed, not overridden → preset
        assertEquals("#00FF00", r.border?.color)    // overridden → own
    }

    @Test
    fun `dangling binding falls back to the node's own appearance`() {
        val own = MediaAppearance(opacity = 0.5f)
        val node = media(own, PresetBinding("missing", emptySet()))
        assertEquals(own, resolveMediaAppearance(node, presetsById))
    }

    @Test
    fun `nonDefaultSections reports only the sections that differ from default`() {
        val a = MediaAppearance(opacity = 0.3f, border = BorderStyle(color = "#000000", widthPx = 2f))
        assertEquals(setOf(AppearanceSection.Opacity, AppearanceSection.Border), a.nonDefaultSections())
        assertTrue(MediaAppearance().nonDefaultSections().isEmpty())
    }

    @Test
    fun `MediaStylePreset and PresetBinding round-trip through JSON`() {
        val backPreset = json.decodeFromString(
            MediaStylePreset.serializer(),
            json.encodeToString(MediaStylePreset.serializer(), preset),
        )
        assertEquals(preset, backPreset)

        val binding = PresetBinding("p1", setOf(AppearanceSection.Border, AppearanceSection.Opacity))
        val backBinding = json.decodeFromString(
            PresetBinding.serializer(),
            json.encodeToString(PresetBinding.serializer(), binding),
        )
        assertEquals(binding, backBinding)
    }
}

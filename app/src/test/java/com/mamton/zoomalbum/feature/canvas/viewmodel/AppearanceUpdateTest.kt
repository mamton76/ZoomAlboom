package com.mamton.zoomalbum.feature.canvas.viewmodel

import com.mamton.zoomalbum.domain.model.CanvasNode
import com.mamton.zoomalbum.domain.model.FrameAppearance
import com.mamton.zoomalbum.domain.model.MediaAppearance
import com.mamton.zoomalbum.domain.model.Transform
import com.mamton.zoomalbum.domain.undo.CommandKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the multi-id appearance fan-out helper. Targets the pure
 * `computeAppearanceUpdate` function so we don't need to construct a full
 * `CanvasViewModel` (which depends on Hilt-injected repositories).
 *
 * Behaviour under test:
 *   - one snapshot command covers every changed node;
 *   - non-existent ids and wrong-type ids are silently skipped;
 *   - per-node no-op skip (no command when nothing actually changes);
 *   - partial no-op only commits the changed subset.
 */
class AppearanceUpdateTest {

    private fun mediaNode(id: String, appearance: MediaAppearance? = null) = CanvasNode.Media(
        id = id,
        transform = Transform(),
        mediaRefId = "m-$id",
        appearance = appearance,
    )

    private fun frameNode(id: String, appearance: FrameAppearance? = null) = CanvasNode.Frame(
        id = id,
        transform = Transform(),
        appearance = appearance,
    )

    private val nextMedia = MediaAppearance(opacity = 0.5f)
    private val nextFrame = FrameAppearance(opacity = 0.5f)

    @Test
    fun `multi-media update produces one command covering all targets`() {
        val nodes = listOf(mediaNode("a"), mediaNode("b"), mediaNode("c"))

        val result = computeAppearanceUpdate<CanvasNode.Media>(
            currentNodes = nodes,
            ids = setOf("a", "b", "c"),
            kind = CommandKind.SET_MEDIA_APPEARANCE,
            timestampMs = 1L,
        ) { it.copy(appearance = nextMedia) }

        val cmd = result.command
        assertNotNull(cmd)
        assertEquals(CommandKind.SET_MEDIA_APPEARANCE, cmd!!.kind)
        assertEquals(setOf("a", "b", "c"), cmd.before?.map { it.id }?.toSet())
        assertEquals(setOf("a", "b", "c"), cmd.after?.map { it.id }?.toSet())
        // All three nodes in the resulting list now carry the new appearance.
        val updatedAppearances = result.updatedNodes
            .filterIsInstance<CanvasNode.Media>()
            .associate { it.id to it.appearance }
        assertEquals(nextMedia, updatedAppearances["a"])
        assertEquals(nextMedia, updatedAppearances["b"])
        assertEquals(nextMedia, updatedAppearances["c"])
    }

    @Test
    fun `multi-frame update produces one command covering both targets`() {
        val nodes = listOf(frameNode("f1"), frameNode("f2"))

        val result = computeAppearanceUpdate<CanvasNode.Frame>(
            currentNodes = nodes,
            ids = setOf("f1", "f2"),
            kind = CommandKind.SET_FRAME_APPEARANCE,
            timestampMs = 2L,
        ) { it.copy(appearance = nextFrame) }

        val cmd = result.command
        assertNotNull(cmd)
        assertEquals(CommandKind.SET_FRAME_APPEARANCE, cmd!!.kind)
        assertEquals(2, cmd.before?.size)
        assertEquals(2, cmd.after?.size)
        assertEquals(setOf("f1", "f2"), cmd.after?.map { it.id }?.toSet())
        result.updatedNodes.filterIsInstance<CanvasNode.Frame>().forEach {
            assertEquals(nextFrame, it.appearance)
        }
    }

    @Test
    fun `non-existent and wrong-type ids are silently skipped`() {
        val realFrame = frameNode("f1")
        val realMedia = mediaNode("m1")
        val nodes = listOf(realFrame, realMedia)

        val result = computeAppearanceUpdate<CanvasNode.Frame>(
            currentNodes = nodes,
            ids = setOf("f1", "ghost", "m1"),
            kind = CommandKind.SET_FRAME_APPEARANCE,
            timestampMs = 3L,
        ) { it.copy(appearance = nextFrame) }

        val cmd = result.command
        assertNotNull(cmd)
        assertEquals(1, cmd!!.before?.size)
        assertEquals("f1", cmd.before?.single()?.id)
        // Media is unchanged in the resulting list.
        val mediaAfter = result.updatedNodes.filterIsInstance<CanvasNode.Media>().single()
        assertNull(mediaAfter.appearance)
    }

    @Test
    fun `no-op update pushes no command`() {
        val nodes = listOf(
            mediaNode("a", appearance = nextMedia),
            mediaNode("b", appearance = nextMedia),
        )

        val result = computeAppearanceUpdate<CanvasNode.Media>(
            currentNodes = nodes,
            ids = setOf("a", "b"),
            kind = CommandKind.SET_MEDIA_APPEARANCE,
            timestampMs = 4L,
        ) { it.copy(appearance = nextMedia) }

        assertNull(result.command)
        // Returns the same list reference — caller can early-return.
        assertSame(nodes, result.updatedNodes)
    }

    @Test
    fun `partial no-op only commits the changed subset`() {
        val nodes = listOf(
            frameNode("f1", appearance = nextFrame), // already at target
            frameNode("f2", appearance = nextFrame), // already at target
            frameNode("f3", appearance = null),      // will change
        )

        val result = computeAppearanceUpdate<CanvasNode.Frame>(
            currentNodes = nodes,
            ids = setOf("f1", "f2", "f3"),
            kind = CommandKind.SET_FRAME_APPEARANCE,
            timestampMs = 5L,
        ) { it.copy(appearance = nextFrame) }

        val cmd = result.command
        assertNotNull(cmd)
        assertEquals(1, cmd!!.before?.size)
        assertEquals("f3", cmd.before?.single()?.id)
        // before/after lists paired positionally and share ids.
        assertEquals(cmd.before?.map { it.id }, cmd.after?.map { it.id })
    }

    @Test
    fun `empty id set is a no-op`() {
        val nodes = listOf(mediaNode("a"))

        val result = computeAppearanceUpdate<CanvasNode.Media>(
            currentNodes = nodes,
            ids = emptySet(),
            kind = CommandKind.SET_MEDIA_APPEARANCE,
            timestampMs = 6L,
        ) { it.copy(appearance = nextMedia) }

        assertNull(result.command)
        assertSame(nodes, result.updatedNodes)
    }

    @Test
    fun `per-id divergent appearances land as different after values in one command`() {
        val nodes = listOf(mediaNode("a"), mediaNode("b"), mediaNode("c"))
        // Per-id map — each target gets its own appearance. Mirrors how the
        // multi-edit sheet (Phase C2+) will dispatch when untouched fields stay
        // per-node-different.
        val perId = mapOf(
            "a" to MediaAppearance(opacity = 0.25f),
            "b" to MediaAppearance(opacity = 0.50f),
            "c" to MediaAppearance(opacity = 0.75f),
        )

        val result = computeAppearanceUpdate<CanvasNode.Media>(
            currentNodes = nodes,
            ids = perId.keys,
            kind = CommandKind.SET_MEDIA_APPEARANCE,
            timestampMs = 8L,
        ) { media -> media.copy(appearance = perId[media.id]) }

        val cmd = result.command
        assertNotNull(cmd)
        assertEquals(3, cmd!!.after?.size)
        // Each node carries its own distinct appearance in the snapshot's after side.
        val afterById = cmd.after!!.filterIsInstance<CanvasNode.Media>()
            .associate { it.id to it.appearance }
        assertEquals(MediaAppearance(opacity = 0.25f), afterById["a"])
        assertEquals(MediaAppearance(opacity = 0.50f), afterById["b"])
        assertEquals(MediaAppearance(opacity = 0.75f), afterById["c"])
        // before/after lists paired positionally, same ids.
        assertEquals(cmd.before?.map { it.id }, cmd.after?.map { it.id })
    }

    @Test
    fun `clearing appearance with null is a real change`() {
        val nodes = listOf(mediaNode("a", appearance = nextMedia))

        val result = computeAppearanceUpdate<CanvasNode.Media>(
            currentNodes = nodes,
            ids = setOf("a"),
            kind = CommandKind.SET_MEDIA_APPEARANCE,
            timestampMs = 7L,
        ) { it.copy(appearance = null) }

        val cmd = result.command
        assertNotNull(cmd)
        assertEquals(nextMedia, (cmd!!.before?.single() as CanvasNode.Media).appearance)
        assertNull((cmd.after?.single() as CanvasNode.Media).appearance)
        val updated = result.updatedNodes.filterIsInstance<CanvasNode.Media>().single()
        assertNull(updated.appearance)
        assertTrue(result.updatedNodes !== nodes)
    }
}

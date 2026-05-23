package com.mamton.zoomalbum.feature.canvas.view

import com.mamton.zoomalbum.domain.model.CanvasNode
import com.mamton.zoomalbum.domain.model.Transform
import com.mamton.zoomalbum.feature.canvas.viewmodel.CanvasAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EditContextMenuItemsTest {

    private fun media(id: String): CanvasNode.Media = CanvasNode.Media(
        id = id,
        transform = Transform(cx = 0f, cy = 0f, w = 50f, h = 50f),
        mediaRefId = "asset-$id",
    )

    private fun frame(id: String): CanvasNode.Frame = CanvasNode.Frame(
        id = id,
        transform = Transform(cx = 0f, cy = 0f, w = 100f, h = 100f),
        label = id,
    )

    private fun req(
        selection: Set<String>,
        anchorNodeId: String? = null,
    ) = ContextMenuRequest(
        selection = selection,
        anchorNodeId = anchorNodeId,
        anchorScreenX = 0f,
        anchorScreenY = 0f,
    )

    /** Sink to capture dispatched actions / lambda calls. */
    private class Sink {
        val actions = mutableListOf<CanvasAction>()
        var openedMedia: CanvasNode.Media? = null
        var openedFrame: CanvasNode.Frame? = null
        var addSheetOpened: Int = 0
        var anchorRemovedCalls: Int = 0
    }

    private fun build(
        request: ContextMenuRequest,
        nodes: List<CanvasNode> = emptyList(),
    ): Pair<List<ContextMenuItem>, Sink> {
        val sink = Sink()
        val items = buildEditContextMenuItems(
            request = request,
            nodesById = nodes.associateBy { it.id },
            dispatch = { sink.actions += it },
            openMediaAppearance = { sink.openedMedia = it },
            openFrameAppearance = { sink.openedFrame = it },
            openAddSheet = { sink.addSheetOpened++ },
            onAnchorRemoved = { sink.anchorRemovedCalls++ },
        )
        return items to sink
    }

    private fun labels(items: List<ContextMenuItem>): List<String> =
        items.filterNot { it.isDivider }.map { it.label }

    @Test
    fun `empty selection shows Add… and opens the add sheet`() {
        val (items, sink) = build(req(selection = emptySet()))

        assertEquals(listOf("Add…"), labels(items))
        items.single { it.label == "Add…" }.onClick()
        assertEquals(1, sink.addSheetOpened)
    }

    @Test
    fun `single media selection shows Edit appearance Duplicate Delete`() {
        val m = media("m1")
        val (items, sink) = build(req(selection = setOf("m1")), nodes = listOf(m))

        assertEquals(
            listOf("Edit appearance", "Duplicate", "Delete"),
            labels(items),
        )
        // Sanity: hooks fire correctly.
        items.single { it.label == "Edit appearance" }.onClick()
        assertEquals(m, sink.openedMedia)
        items.single { it.label == "Duplicate" }.onClick()
        assertTrue(sink.actions.contains(CanvasAction.DuplicateSelection))
        items.single { it.label == "Delete" }.onClick()
        assertTrue(sink.actions.contains(CanvasAction.DeleteSelection))
    }

    @Test
    fun `single frame selection shows Edit frame appearance Navigate Duplicate Delete`() {
        val f = frame("f1")
        val (items, sink) = build(req(selection = setOf("f1")), nodes = listOf(f))

        assertEquals(
            listOf("Edit frame appearance", "Navigate to frame", "Duplicate", "Delete"),
            labels(items),
        )
        items.single { it.label == "Edit frame appearance" }.onClick()
        assertEquals(f, sink.openedFrame)
        items.single { it.label == "Navigate to frame" }.onClick()
        assertTrue(sink.actions.any { it is CanvasAction.FocusNode && it.nodeId == "f1" })
    }

    @Test
    fun `single selection with missing node returns empty list`() {
        // Defensive case — long-press selected an id that has since been removed.
        val (items, _) = build(req(selection = setOf("missing")), nodes = emptyList())
        assertTrue(items.isEmpty())
    }

    @Test
    fun `group selection without anchor shows group items only`() {
        val m1 = media("m1")
        val m2 = media("m2")
        val (items, _) = build(
            req(selection = setOf("m1", "m2"), anchorNodeId = null),
            nodes = listOf(m1, m2),
        )

        assertEquals(
            listOf("Duplicate selection", "Delete selection", "Clear selection"),
            labels(items),
        )
        // No anchor-scoped items.
        assertNull(items.firstOrNull { it.label == "Remove this from selection" })
        assertNull(items.firstOrNull { it.label == "Edit this only" })
    }

    @Test
    fun `group selection with anchor outside selection shows no anchor items`() {
        val m1 = media("m1")
        val m2 = media("m2")
        // Anchor "x" isn't in selection — anchor-scoped items must be hidden.
        val (items, _) = build(
            req(selection = setOf("m1", "m2"), anchorNodeId = "x"),
            nodes = listOf(m1, m2),
        )

        assertEquals(
            listOf("Duplicate selection", "Delete selection", "Clear selection"),
            labels(items),
        )
    }

    @Test
    fun `group selection with anchor in selection shows anchor-scoped items`() {
        val m1 = media("m1")
        val m2 = media("m2")
        val (items, _) = build(
            req(selection = setOf("m1", "m2"), anchorNodeId = "m1"),
            nodes = listOf(m1, m2),
        )

        assertEquals(
            listOf(
                "Duplicate selection", "Delete selection", "Clear selection",
                "Remove this from selection", "Edit this only",
            ),
            labels(items),
        )
    }

    @Test
    fun `Remove this from selection keeps popup open and notifies host`() {
        val m1 = media("m1")
        val m2 = media("m2")
        val (items, sink) = build(
            req(selection = setOf("m1", "m2"), anchorNodeId = "m1"),
            nodes = listOf(m1, m2),
        )

        val removeItem = items.single { it.label == "Remove this from selection" }
        assertTrue(
            "Remove this from selection must set keepOpenOnClick = true",
            removeItem.keepOpenOnClick,
        )
        // No other item keeps the popup open.
        for (other in items.filterNot { it.isDivider || it === removeItem }) {
            assertFalse(
                "Item '${other.label}' should not keep the popup open",
                other.keepOpenOnClick,
            )
        }

        removeItem.onClick()
        assertTrue(
            "Click must dispatch ToggleNodeSelection on the anchor",
            sink.actions.any { it is CanvasAction.ToggleNodeSelection && it.nodeId == "m1" },
        )
        assertEquals(
            "Click must notify the host so it can clear the anchor (Option A)",
            1, sink.anchorRemovedCalls,
        )
    }

    @Test
    fun `Edit this only replaces selection with the anchor`() {
        val m1 = media("m1")
        val m2 = media("m2")
        val (items, sink) = build(
            req(selection = setOf("m1", "m2"), anchorNodeId = "m1"),
            nodes = listOf(m1, m2),
        )

        val edit = items.single { it.label == "Edit this only" }
        assertFalse(
            "Edit this only should dismiss the popup (no keepOpen)",
            edit.keepOpenOnClick,
        )
        edit.onClick()
        assertTrue(
            sink.actions.any { it is CanvasAction.SelectNode && it.nodeId == "m1" },
        )
    }

    @Test
    fun `divider entries appear between sections`() {
        val (singleMediaItems, _) = build(
            req(selection = setOf("m")),
            nodes = listOf(media("m")),
        )
        // Edit appearance / divider / Duplicate / Delete
        assertEquals(4, singleMediaItems.size)
        assertTrue(singleMediaItems[1].isDivider)

        val (groupWithAnchor, _) = build(
            req(selection = setOf("m1", "m2"), anchorNodeId = "m1"),
            nodes = listOf(media("m1"), media("m2")),
        )
        // 3 group items, divider, then 2 anchor items = 6 entries
        assertEquals(6, groupWithAnchor.size)
        assertTrue(
            "Divider must sit between group and anchor sections",
            groupWithAnchor[3].isDivider,
        )
    }

    @Test
    fun `divider singleton entry is non-clickable structural marker`() {
        val divider = ContextMenuItem.Divider
        assertTrue(divider.isDivider)
        assertEquals("", divider.label)
        // Sanity — no-op onClick by default; not enabled is irrelevant for renderer.
        assertNotNull(divider.onClick)
    }
}

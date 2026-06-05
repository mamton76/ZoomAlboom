package com.mamton.zoomalbum.feature.canvas.view

import com.mamton.zoomalbum.domain.model.CanvasNode
import com.mamton.zoomalbum.domain.model.Transform
import com.mamton.zoomalbum.feature.canvas.actions.EditorActionEffect
import com.mamton.zoomalbum.feature.canvas.actions.SelectionContext
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

    private fun ctxFromNodes(
        selection: Set<String>,
        anchorNodeId: String? = null,
        nodes: List<CanvasNode> = emptyList(),
        pinDetachEnabled: Boolean = false,
        anyOverrideExists: Boolean = false,
    ): SelectionContext {
        val byId = nodes.associateBy { it.id }
        val singleNode = if (selection.size == 1) byId[selection.first()] else null
        return SelectionContext(
            selectedNodeIds = selection,
            anchorNodeId = anchorNodeId,
            singleSelectedFrame = singleNode as? CanvasNode.Frame,
            singleSelectedMedia = singleNode as? CanvasNode.Media,
            selectedFramesInOrder = selection.mapNotNull { byId[it] as? CanvasNode.Frame },
            selectedMediaInOrder = selection.mapNotNull { byId[it] as? CanvasNode.Media },
            pinDetachEnabled = pinDetachEnabled,
            anyOverrideExists = anyOverrideExists,
        )
    }

    /** Sink to capture effects produced by tapped menu items. */
    private class Sink {
        val effects = mutableListOf<EditorActionEffect>()
        var anchorRemovedCalls: Int = 0

        val dispatchedActions: List<CanvasAction>
            get() = effects.filterIsInstance<EditorActionEffect.Dispatch>().map { it.action }
        val addSheetOpened: Int
            get() = effects.count { it == EditorActionEffect.OpenAddSheet }
    }

    private fun build(
        request: ContextMenuRequest,
        nodes: List<CanvasNode> = emptyList(),
        pinDetachEnabled: Boolean = false,
        anyOverrideExists: Boolean = false,
    ): Pair<List<ContextMenuItem>, Sink> {
        val sink = Sink()
        val items = buildEditContextMenuItems(
            request = request,
            ctx = ctxFromNodes(
                selection = request.selection,
                anchorNodeId = request.anchorNodeId,
                nodes = nodes,
                pinDetachEnabled = pinDetachEnabled,
                anyOverrideExists = anyOverrideExists,
            ),
            runEffect = { sink.effects += it },
            onAnchorRemoved = { sink.anchorRemovedCalls++ },
        )
        return items to sink
    }

    /** Text-row labels only — skips dividers and inline rows. */
    private fun textLabels(items: List<ContextMenuItem>): List<String> =
        items.filterNot { it.isDivider || it.inlineRow != null }.map { it.label }

    private fun inlineRows(items: List<ContextMenuItem>): List<List<InlineRowButton>> =
        items.mapNotNull { it.inlineRow }

    @Test
    fun `empty selection shows Add… and opens the add sheet`() {
        val (items, sink) = build(req(selection = emptySet()))

        assertEquals(listOf("Add…"), textLabels(items))
        items.single { it.label == "Add…" }.onClick()
        assertEquals(1, sink.addSheetOpened)
    }

    @Test
    fun `single media selection — per-concept editors, z-order row, Delete at bottom`() {
        val m = media("m1")
        val (items, sink) = build(req(selection = setOf("m1")), nodes = listOf(m))

        // 5 universal concepts (opacity / corner radius / border / shadow / overlays)
        // + 4 media-only (crop / color adj / decoration / caption) + Duplicate + Delete.
        assertEquals(
            listOf(
                "Edit opacity",
                "Edit corner radius",
                "Edit border",
                "Edit shadow",
                "Edit overlays",
                "Edit crop",
                "Edit color adjustments",
                "Edit frame decoration",
                "Edit caption",
                "Duplicate",
                "Delete",
            ),
            textLabels(items),
        )
        // Exactly one inline row — the z-order four-button row.
        val rows = inlineRows(items)
        assertEquals(1, rows.size)
        assertEquals(
            listOf("Bring to Front", "Bring Forward", "Send Backward", "Send to Back"),
            rows.single().map { it.label },
        )

        // Per-concept editors route to their own effects.
        items.single { it.label == "Edit opacity" }.onClick()
        assertTrue(sink.effects.contains(EditorActionEffect.OpenOpacityEditor))
        items.single { it.label == "Edit crop" }.onClick()
        assertTrue(sink.effects.contains(EditorActionEffect.OpenCropEditor))
        items.single { it.label == "Duplicate" }.onClick()
        assertTrue(sink.dispatchedActions.contains(CanvasAction.DuplicateSelection))
        items.single { it.label == "Delete" }.onClick()
        assertTrue(sink.dispatchedActions.contains(CanvasAction.DeleteSelection))
        rows.single()[0].onClick()
        assertTrue(sink.dispatchedActions.any { it is CanvasAction.BringToFront && it.nodeId == "m1" })
    }

    @Test
    fun `single frame selection — per-concept editors + Navigate + Duplicate, z-order row, Delete`() {
        val f = frame("f1")
        val (items, sink) = build(req(selection = setOf("f1")), nodes = listOf(f))

        // 5 universal concepts + Background (frame-only) + Navigate + Duplicate + Delete.
        assertEquals(
            listOf(
                "Edit opacity",
                "Edit corner radius",
                "Edit border",
                "Edit shadow",
                "Edit overlays",
                "Edit background",
                "Navigate to frame",
                "Duplicate",
                "Delete",
            ),
            textLabels(items),
        )
        assertEquals(1, inlineRows(items).size) // z-order row

        items.single { it.label == "Edit background" }.onClick()
        assertTrue(sink.effects.contains(EditorActionEffect.OpenBackgroundEditor))
        items.single { it.label == "Navigate to frame" }.onClick()
        assertTrue(sink.dispatchedActions.any { it is CanvasAction.FocusNode && it.nodeId == "f1" })
    }

    @Test
    fun `single selection with missing node returns empty list`() {
        // Defensive case — long-press selected an id that has since been removed.
        val (items, _) = build(req(selection = setOf("missing")), nodes = emptyList())
        assertTrue(items.isEmpty())
    }

    @Test
    fun `frame-membership row appears when pinDetachEnabled with single target frame`() {
        // Selection is one frame + one media — the frame is the target. Pin/Detach
        // applies; Auto only when an override exists.
        val f = frame("f1")
        val m = media("m1")
        val (items, sink) = build(
            req(selection = setOf("f1", "m1")),
            nodes = listOf(f, m),
            pinDetachEnabled = true,
            anyOverrideExists = false,
        )

        val rows = inlineRows(items)
        // Single z-order row hidden (size != 1); only membership row present.
        assertEquals(1, rows.size)
        assertEquals(listOf("Pin", "Detach"), rows.single().map { it.label })

        // Tap Pin — produces a FrameMembership effect (host dispatches to the picker
        // or directly per selection shape).
        rows.single()[0].onClick()
        assertTrue(
            "Pin must produce a FrameMembership effect",
            sink.effects.any { it is EditorActionEffect.FrameMembership },
        )
    }

    @Test
    fun `frame-membership row includes Auto when an override exists`() {
        val f = frame("f1")
        val m = media("m1")
        val (items, _) = build(
            req(selection = setOf("f1", "m1")),
            nodes = listOf(f, m),
            pinDetachEnabled = true,
            anyOverrideExists = true,
        )

        val rows = inlineRows(items)
        assertEquals(listOf("Pin", "Detach", "Auto"), rows.single().map { it.label })
    }

    @Test
    fun `group selection without anchor — per-concept editors + Duplicate selection, Delete selection, Clear selection`() {
        val m1 = media("m1")
        val m2 = media("m2")
        val (items, _) = build(
            req(selection = setOf("m1", "m2"), anchorNodeId = null),
            nodes = listOf(m1, m2),
        )

        // Homogeneous-all-media selection: per-concept editors visible per
        // `appearance.md § 14.3`, with "(N)" suffix in their label.
        assertEquals(
            listOf(
                "Edit opacity (2)",
                "Edit corner radius (2)",
                "Edit border (2)",
                "Edit shadow (2)",
                "Edit overlays (2)",
                "Edit crop (2)",
                "Edit color adjustments (2)",
                "Edit frame decoration (2)",
                "Edit caption (2)",
                "Duplicate selection",
                "Delete selection",
                "Clear selection",
            ),
            textLabels(items),
        )
        // No inline rows (z-order multi-select is blocked until §13.5).
        assertEquals(0, inlineRows(items).size)
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
            listOf(
                "Edit opacity (2)",
                "Edit corner radius (2)",
                "Edit border (2)",
                "Edit shadow (2)",
                "Edit overlays (2)",
                "Edit crop (2)",
                "Edit color adjustments (2)",
                "Edit frame decoration (2)",
                "Edit caption (2)",
                "Duplicate selection",
                "Delete selection",
                "Clear selection",
            ),
            textLabels(items),
        )
    }

    @Test
    fun `group selection with anchor in selection inserts anchor block between Delete and Clear`() {
        val m1 = media("m1")
        val m2 = media("m2")
        val (items, _) = build(
            req(selection = setOf("m1", "m2"), anchorNodeId = "m1"),
            nodes = listOf(m1, m2),
        )

        assertEquals(
            listOf(
                "Edit opacity (2)",
                "Edit corner radius (2)",
                "Edit border (2)",
                "Edit shadow (2)",
                "Edit overlays (2)",
                "Edit crop (2)",
                "Edit color adjustments (2)",
                "Edit frame decoration (2)",
                "Edit caption (2)",
                "Duplicate selection",
                "Delete selection",
                "Edit this only",
                "Remove this from selection",
                "Clear selection",
            ),
            textLabels(items),
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
            sink.dispatchedActions.any { it is CanvasAction.ToggleNodeSelection && it.nodeId == "m1" },
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
            sink.dispatchedActions.any { it is CanvasAction.SelectNode && it.nodeId == "m1" },
        )
    }

    @Test
    fun `dividers separate sections in the single-media menu`() {
        val (items, _) = build(req(selection = setOf("m")), nodes = listOf(media("m")))
        // 9 per-concept edits (5 universal + 4 media-only) + Duplicate, divider,
        // [z-order row], divider, Delete.
        assertEquals(14, items.size)
        assertEquals("Edit opacity", items[0].label)
        assertEquals("Edit caption", items[8].label)
        assertEquals("Duplicate", items[9].label)
        assertTrue(items[10].isDivider)
        assertNotNull(items[11].inlineRow)
        assertTrue(items[12].isDivider)
        assertEquals("Delete", items[13].label)
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

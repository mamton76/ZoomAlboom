package com.mamton.zoomalbum.feature.canvas.editor.gestures

import com.mamton.zoomalbum.domain.model.CanvasInteractionMode
import com.mamton.zoomalbum.feature.canvas.editor.EditorTool
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Routing-matrix coverage for [GestureRouter]. Covers:
 *  - router foundation, mode-aware transform gating, Eraser
 *    long-press-on-empty suppression;
 *  - locked per-tool tap + double-tap semantics: Selection keeps
 *    select / deselect, Eraser tap deletes the hit node, Edit-mode
 *    double-tap is `Ignore`, and View / Presentation preserve the
 *    reset-camera affordance;
 *  - drag-start routes for marquee and Eraser scrub;
 *  - long-press selection-extension policy (Selection only).
 *
 * Conventions for context constructors:
 *  - `editCtx(...)` defaults to Edit + Selection + has selection + menu closed.
 *  - `viewCtx(...)` defaults to View + Selection (the stored "nil tool" value).
 *  - `presentCtx(...)` defaults to Presentation + Selection.
 */
class GestureRouterTest {

    private fun editCtx(
        tool: EditorTool = EditorTool.Selection,
        hasSelection: Boolean = true,
        isContextMenuOpen: Boolean = false,
    ) = GestureRoutingContext(
        mode = CanvasInteractionMode.Edit,
        activeTool = tool,
        hasSelection = hasSelection,
        isContextMenuOpen = isContextMenuOpen,
    )

    private fun viewCtx(
        isContextMenuOpen: Boolean = false,
    ) = GestureRoutingContext(
        mode = CanvasInteractionMode.View,
        activeTool = EditorTool.Selection,
        hasSelection = false,
        isContextMenuOpen = isContextMenuOpen,
    )

    private fun presentCtx(
        isContextMenuOpen: Boolean = false,
    ) = GestureRoutingContext(
        mode = CanvasInteractionMode.Presentation,
        activeTool = EditorTool.Selection,
        hasSelection = false,
        isContextMenuOpen = isContextMenuOpen,
    )

    // ── routeTap ──────────────────────────────────────────────────────────

    @Test
    fun `tap with popup open dismisses regardless of mode or tool`() {
        assertEquals(
            TapRoute.DismissContextMenuOnly,
            GestureRouter.routeTap(editCtx(isContextMenuOpen = true), hitNodeId = "n1"),
        )
        assertEquals(
            TapRoute.DismissContextMenuOnly,
            GestureRouter.routeTap(
                editCtx(tool = EditorTool.Eraser, isContextMenuOpen = true),
                hitNodeId = null,
            ),
        )
        assertEquals(
            TapRoute.DismissContextMenuOnly,
            GestureRouter.routeTap(viewCtx(isContextMenuOpen = true), hitNodeId = "n1"),
        )
    }

    @Test
    fun `Edit + Selection + tap on node selects the node`() {
        assertEquals(
            TapRoute.EditSelect("n1"),
            GestureRouter.routeTap(editCtx(), hitNodeId = "n1"),
        )
    }

    @Test
    fun `Edit + Selection + tap on empty clears selection`() {
        assertEquals(
            TapRoute.EditDeselectAll,
            GestureRouter.routeTap(editCtx(), hitNodeId = null),
        )
    }

    @Test
    fun `Edit + Eraser + tap on node deletes that node`() {
        // Object-mode Eraser tap (`editor-tools.md § 4.6`). Single tap =
        // degenerate scrub: one node, one undo entry. See routeEraserScrubStart
        // for the multi-node drag path.
        assertEquals(
            TapRoute.EraserDeleteNode("n1"),
            GestureRouter.routeTap(editCtx(tool = EditorTool.Eraser), hitNodeId = "n1"),
        )
    }

    @Test
    fun `Edit + Eraser + tap on empty is a no-op`() {
        assertEquals(
            TapRoute.Ignore,
            GestureRouter.routeTap(editCtx(tool = EditorTool.Eraser), hitNodeId = null),
        )
    }

    @Test
    fun `View tap on node focuses, tap on empty is no-op`() {
        assertEquals(
            TapRoute.ViewFocus("n1"),
            GestureRouter.routeTap(viewCtx(), hitNodeId = "n1"),
        )
        assertEquals(
            TapRoute.Ignore,
            GestureRouter.routeTap(viewCtx(), hitNodeId = null),
        )
    }

    @Test
    fun `Presentation tap on node focuses, tap on empty is no-op`() {
        assertEquals(
            TapRoute.ViewFocus("n1"),
            GestureRouter.routeTap(presentCtx(), hitNodeId = "n1"),
        )
        assertEquals(
            TapRoute.Ignore,
            GestureRouter.routeTap(presentCtx(), hitNodeId = null),
        )
    }

    // ── routeDoubleTap ────────────────────────────────────────────────────

    @Test
    fun `double-tap with popup open dismisses`() {
        assertEquals(
            DoubleTapRoute.DismissContextMenuOnly,
            GestureRouter.routeDoubleTap(editCtx(isContextMenuOpen = true)),
        )
        assertEquals(
            DoubleTapRoute.DismissContextMenuOnly,
            GestureRouter.routeDoubleTap(viewCtx(isContextMenuOpen = true)),
        )
    }

    @Test
    fun `Edit + Selection double-tap is a no-op per locked tool map`() {
        // editor-tools.md § 4.1 doesn't list double-tap for Selection.
        // The legacy reset-camera affordance moved to View / Presentation.
        assertEquals(DoubleTapRoute.Ignore, GestureRouter.routeDoubleTap(editCtx()))
    }

    @Test
    fun `Edit + Eraser double-tap is a no-op`() {
        assertEquals(
            DoubleTapRoute.Ignore,
            GestureRouter.routeDoubleTap(editCtx(tool = EditorTool.Eraser)),
        )
    }

    @Test
    fun `View and Presentation double-tap preserve the reset-camera affordance`() {
        // Nil-tool modes — reset-camera is a mode-level convenience that the
        // tool-axis migration explicitly preserves.
        assertEquals(DoubleTapRoute.ResetCamera, GestureRouter.routeDoubleTap(viewCtx()))
        assertEquals(DoubleTapRoute.ResetCamera, GestureRouter.routeDoubleTap(presentCtx()))
    }

    // ── routeLongPress ────────────────────────────────────────────────────

    @Test
    fun `long-press in View is suppressed`() {
        // View-mode long-press popups (per editor-tools.md) are an existing
        // unimplemented mismatch tracked separately — Slice A preserves the
        // current suppression to avoid bundling a UX behavior change into
        // the routing-foundation refactor.
        assertEquals(LongPressRoute.Suppress, GestureRouter.routeLongPress(viewCtx(), listOf("n1")))
        assertEquals(LongPressRoute.Suppress, GestureRouter.routeLongPress(viewCtx(), emptyList()))
    }

    @Test
    fun `long-press in Presentation is suppressed`() {
        assertEquals(
            LongPressRoute.Suppress,
            GestureRouter.routeLongPress(presentCtx(), listOf("n1")),
        )
    }

    @Test
    fun `Edit long-press on empty is always suppressed (every tool)`() {
        // The long-press-then-drag rect-select fall-through is retired.
        // Marquee selection is reached via direct drag-on-empty
        // (`routeMarqueeStart`); long-press on empty is a no-op for every
        // Edit tool, matching the locked Selection map in `editor-tools.md
        // § 4.1`.
        assertEquals(
            LongPressRoute.Suppress,
            GestureRouter.routeLongPress(editCtx(), emptyList()),
        )
        assertEquals(
            LongPressRoute.Suppress,
            GestureRouter.routeLongPress(editCtx(tool = EditorTool.Eraser), emptyList()),
        )
    }

    @Test
    fun `Edit + Selection long-press on one node resolves anchor and extends selection`() {
        // "Add-or-keep" affordance per selection.md § 2 — Selection's
        // long-press both opens the popup and adds the anchor to selection
        // so multi-select grows discoverably.
        assertEquals(
            LongPressRoute.ResolveAnchor(
                anchorNodeId = "n1",
                hasPicker = false,
                extendsSelection = true,
            ),
            GestureRouter.routeLongPress(editCtx(), listOf("n1")),
        )
    }

    @Test
    fun `Edit + Eraser long-press resolves anchor but does NOT extend selection`() {
        // Non-destructive bailout per editor-tools.md § 4.6 — Eraser's
        // long-press opens the popup without mutating selection state.
        // (Tap and scrub are the destructive paths in Eraser.)
        assertEquals(
            LongPressRoute.ResolveAnchor(
                anchorNodeId = "n1",
                hasPicker = false,
                extendsSelection = false,
            ),
            GestureRouter.routeLongPress(editCtx(tool = EditorTool.Eraser), listOf("n1")),
        )
    }

    @Test
    fun `Edit long-press on stacked nodes resolves topmost anchor with picker`() {
        // hitIds[0] is the topmost; the detector hands the list in z-desc order.
        assertEquals(
            LongPressRoute.ResolveAnchor(
                anchorNodeId = "top",
                hasPicker = true,
                extendsSelection = true,
            ),
            GestureRouter.routeLongPress(editCtx(), listOf("top", "mid", "bot")),
        )
    }

    @Test
    fun `Edit long-press anchor + picker invariants do not depend on activeTool`() {
        // `extendsSelection` is the only tool-sensitive field. anchorNodeId
        // and hasPicker are derived from the hit list only — the popup
        // invocation gesture is universal across tools.
        val selectionRoute = GestureRouter.routeLongPress(editCtx(), listOf("top", "mid"))
            as LongPressRoute.ResolveAnchor
        val eraserRoute = GestureRouter.routeLongPress(
            editCtx(tool = EditorTool.Eraser),
            listOf("top", "mid"),
        ) as LongPressRoute.ResolveAnchor
        assertEquals(selectionRoute.anchorNodeId, eraserRoute.anchorNodeId)
        assertEquals(selectionRoute.hasPicker, eraserRoute.hasPicker)
    }

    @Test
    fun `Edit long-press with popup open still resolves anchor (single-gesture popup replace)`() {
        // Replacing one popup with another via long-press on a different node
        // is the documented behavior (context-menu.md § 3). Popup-open does
        // not suppress long-press at press time.
        assertEquals(
            LongPressRoute.ResolveAnchor(
                anchorNodeId = "n1",
                hasPicker = false,
                extendsSelection = true,
            ),
            GestureRouter.routeLongPress(editCtx(isContextMenuOpen = true), listOf("n1")),
        )
    }

    // ── routeLongPressLift ────────────────────────────────────────────────

    @Test
    fun `long-press lift opens menu when press resolved anchor in Edit`() {
        val press = LongPressRoute.ResolveAnchor(
            anchorNodeId = "n1",
            hasPicker = false,
            extendsSelection = true,
        )
        assertEquals(
            LongPressLiftRoute.OpenMenu,
            GestureRouter.routeLongPressLift(editCtx(), press),
        )
    }

    @Test
    fun `long-press lift suppresses menu when press was suppressed`() {
        assertEquals(
            LongPressLiftRoute.Suppress,
            GestureRouter.routeLongPressLift(viewCtx(), LongPressRoute.Suppress),
        )
    }

    // ── routeSelectedNodeTransformStart ───────────────────────────────────

    @Test
    fun `Edit + Selection + has selection allows transform`() {
        assertEquals(
            SelectedNodeTransformRoute.Allow,
            GestureRouter.routeSelectedNodeTransformStart(editCtx()),
        )
    }

    @Test
    fun `Edit + Eraser blocks transform even when selection persists across switch`() {
        // The whole reason this route exists. Selection persistence across
        // tool switches must not let Eraser users accidentally move the last
        // selected node by dragging its body.
        assertEquals(
            SelectedNodeTransformRoute.Block,
            GestureRouter.routeSelectedNodeTransformStart(editCtx(tool = EditorTool.Eraser)),
        )
    }

    @Test
    fun `no selection blocks transform regardless of mode or tool`() {
        assertEquals(
            SelectedNodeTransformRoute.Block,
            GestureRouter.routeSelectedNodeTransformStart(editCtx(hasSelection = false)),
        )
        assertEquals(
            SelectedNodeTransformRoute.Block,
            GestureRouter.routeSelectedNodeTransformStart(viewCtx()),
        )
    }

    @Test
    fun `View blocks selected-node transform even with stored Selection tool`() {
        // activeTool is meaningful only in Edit; in View / Presentation the
        // stored value may still be Selection (the default). The router must
        // block transforms based on mode regardless of the stored tool —
        // otherwise a node selected in Edit and then switched to View would
        // remain draggable. viewCtx() defaults activeTool to Selection so
        // this test covers exactly that case.
        val viewWithSelection = viewCtx().copy(hasSelection = true)
        assertEquals(EditorTool.Selection, viewWithSelection.activeTool) // sanity
        assertEquals(
            SelectedNodeTransformRoute.Block,
            GestureRouter.routeSelectedNodeTransformStart(viewWithSelection),
        )
    }

    @Test
    fun `Presentation blocks selected-node transform even with stored Selection tool`() {
        val presentWithSelection = presentCtx().copy(hasSelection = true)
        assertEquals(EditorTool.Selection, presentWithSelection.activeTool) // sanity
        assertEquals(
            SelectedNodeTransformRoute.Block,
            GestureRouter.routeSelectedNodeTransformStart(presentWithSelection),
        )
    }

    // ── routeCameraTransformStart ─────────────────────────────────────────

    @Test
    fun `camera transform dismisses popup when open`() {
        assertEquals(
            CameraTransformRoute.DismissContextMenuAndProceed,
            GestureRouter.routeCameraTransformStart(editCtx(isContextMenuOpen = true)),
        )
    }

    @Test
    fun `camera transform allowed in every mode and tool when popup closed`() {
        assertEquals(
            CameraTransformRoute.Allow,
            GestureRouter.routeCameraTransformStart(editCtx()),
        )
        assertEquals(
            CameraTransformRoute.Allow,
            GestureRouter.routeCameraTransformStart(editCtx(tool = EditorTool.Eraser)),
        )
        assertEquals(
            CameraTransformRoute.Allow,
            GestureRouter.routeCameraTransformStart(viewCtx()),
        )
        assertEquals(
            CameraTransformRoute.Allow,
            GestureRouter.routeCameraTransformStart(presentCtx()),
        )
    }

    // ── routeMarqueeStart ─────────────────────────────────────────────────

    @Test
    fun `Edit + Selection + popup closed starts marquee`() {
        assertEquals(
            MarqueeStartRoute.Start,
            GestureRouter.routeMarqueeStart(editCtx()),
        )
    }

    @Test
    fun `Edit + Selection + popup open dismisses without starting marquee`() {
        // Discrete-gesture dismissal rule. Dragging on empty with the popup
        // open closes the popup and does NOT also create a rectangle on the
        // same gesture; the next gesture starts fresh.
        assertEquals(
            MarqueeStartRoute.DismissContextMenuOnly,
            GestureRouter.routeMarqueeStart(editCtx(isContextMenuOpen = true)),
        )
    }

    @Test
    fun `Edit + Eraser does not start marquee`() {
        // Marquee selection is Selection's gesture. Eraser must not inherit
        // any rectangle-selection behavior, even when a leftover selection
        // exists from a prior Selection session.
        assertEquals(
            MarqueeStartRoute.Suppress,
            GestureRouter.routeMarqueeStart(editCtx(tool = EditorTool.Eraser)),
        )
        assertEquals(
            MarqueeStartRoute.Suppress,
            GestureRouter.routeMarqueeStart(
                editCtx(tool = EditorTool.Eraser, isContextMenuOpen = true),
            ),
        )
    }

    @Test
    fun `View does not start marquee even with stored Selection tool`() {
        // Drag-on-empty in View is reserved for single-finger pan;
        // marquee selection is Edit-only.
        assertEquals(EditorTool.Selection, viewCtx().activeTool) // sanity
        assertEquals(
            MarqueeStartRoute.Suppress,
            GestureRouter.routeMarqueeStart(viewCtx()),
        )
    }

    @Test
    fun `Presentation does not start marquee even with stored Selection tool`() {
        assertEquals(EditorTool.Selection, presentCtx().activeTool) // sanity
        assertEquals(
            MarqueeStartRoute.Suppress,
            GestureRouter.routeMarqueeStart(presentCtx()),
        )
    }

    @Test
    fun `marquee start ignores hasSelection — additive flag is decided at drag end`() {
        // The router doesn't read hasSelection here; whether the marquee
        // unions with or replaces the current selection is a caller-side
        // decision evaluated at `SelectNodesInRect(rect, additive = ...)`
        // time. Keeping the routing decision orthogonal lets a future tool
        // (e.g. anchor-level marquee in VectorEdit) share the route without
        // re-encoding selection state.
        val empty = editCtx(hasSelection = false)
        val nonEmpty = editCtx(hasSelection = true)
        assertEquals(
            GestureRouter.routeMarqueeStart(empty),
            GestureRouter.routeMarqueeStart(nonEmpty),
        )
    }

    // ── routeEraserScrubStart ─────────────────────────────────────────────

    @Test
    fun `Edit + Eraser + popup closed starts scrub`() {
        assertEquals(
            EraserScrubStartRoute.Start,
            GestureRouter.routeEraserScrubStart(editCtx(tool = EditorTool.Eraser)),
        )
    }

    @Test
    fun `Edit + Eraser + popup open dismisses without starting scrub`() {
        // Discrete-gesture dismissal: drag past slop with menu open closes
        // the menu and does NOT also delete nodes on the same gesture.
        assertEquals(
            EraserScrubStartRoute.DismissContextMenuOnly,
            GestureRouter.routeEraserScrubStart(
                editCtx(tool = EditorTool.Eraser, isContextMenuOpen = true),
            ),
        )
    }

    @Test
    fun `Edit + Selection does not start eraser scrub`() {
        // Scrub-delete is Eraser's gesture. Selection mode owns marquee
        // (`routeMarqueeStart`) on the same drag-on-empty pattern; the two
        // detectors are mutually exclusive by their `enabled` gates.
        assertEquals(
            EraserScrubStartRoute.Suppress,
            GestureRouter.routeEraserScrubStart(editCtx()),
        )
    }

    @Test
    fun `View and Presentation do not start eraser scrub`() {
        // No tool axis outside Edit; scrub-delete is unreachable.
        assertEquals(
            EraserScrubStartRoute.Suppress,
            GestureRouter.routeEraserScrubStart(viewCtx()),
        )
        assertEquals(
            EraserScrubStartRoute.Suppress,
            GestureRouter.routeEraserScrubStart(presentCtx()),
        )
    }

    // ── routeViewPanStart ─────────────────────────────────────────────────

    @Test
    fun `View + popup closed allows single-finger pan`() {
        assertEquals(
            ViewPanRoute.Allow,
            GestureRouter.routeViewPanStart(viewCtx()),
        )
    }

    @Test
    fun `View + popup open dismisses popup and proceeds with pan`() {
        // Continuous gesture — camera intent isn't lost when the menu was
        // open. Same rule as multi-finger camera nav
        // (`CameraTransformRoute.DismissContextMenuAndProceed`).
        assertEquals(
            ViewPanRoute.DismissContextMenuAndProceed,
            GestureRouter.routeViewPanStart(viewCtx(isContextMenuOpen = true)),
        )
    }

    @Test
    fun `Edit suppresses single-finger pan regardless of tool`() {
        // Single-finger in Edit is reserved for the active tool
        // (`editor-tools.md § 2`). Pan must use two fingers.
        assertEquals(
            ViewPanRoute.Suppress,
            GestureRouter.routeViewPanStart(editCtx()),
        )
        assertEquals(
            ViewPanRoute.Suppress,
            GestureRouter.routeViewPanStart(editCtx(tool = EditorTool.Eraser)),
        )
    }

    @Test
    fun `Presentation suppresses single-finger pan`() {
        // Presentation has its own gesture vocabulary; not routed through
        // the View pan path.
        assertEquals(
            ViewPanRoute.Suppress,
            GestureRouter.routeViewPanStart(presentCtx()),
        )
    }

    @Test
    fun `View pan ignores activeTool — tool axis is meaningless outside Edit`() {
        // viewCtx() defaults activeTool to Selection (the stored "nil tool"
        // value in non-Edit modes). The route should be identical with any
        // stored tool, since the tool axis is gated to Edit per
        // editor-tools.md § 1.2.
        assertEquals(EditorTool.Selection, viewCtx().activeTool) // sanity
        assertEquals(ViewPanRoute.Allow, GestureRouter.routeViewPanStart(viewCtx()))
        // If we hypothetically had a stored Eraser in View, route still allows:
        val viewWithEraserStored = viewCtx().copy(activeTool = EditorTool.Eraser)
        assertEquals(
            ViewPanRoute.Allow,
            GestureRouter.routeViewPanStart(viewWithEraserStored),
        )
    }

    // ── exhaustiveness guard ──────────────────────────────────────────────

    @Test
    fun `route types are exhaustive when matched`() {
        // Trivial smoke check: matching every variant compiles. Catches the
        // case where a route variant is added without wiring router cases.
        val tap: TapRoute = TapRoute.Ignore
        val tapExhaustive: String = when (tap) {
            TapRoute.DismissContextMenuOnly -> "dismiss"
            is TapRoute.EditSelect -> "select"
            TapRoute.EditDeselectAll -> "deselect"
            is TapRoute.EraserDeleteNode -> "eraser-delete"
            is TapRoute.ViewFocus -> "focus"
            TapRoute.Ignore -> "ignore"
        }
        assertTrue(tapExhaustive.isNotEmpty())
    }
}

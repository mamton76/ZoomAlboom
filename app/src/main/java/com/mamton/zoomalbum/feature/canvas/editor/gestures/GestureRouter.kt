package com.mamton.zoomalbum.feature.canvas.editor.gestures

import com.mamton.zoomalbum.domain.model.CanvasInteractionMode
import com.mamton.zoomalbum.feature.canvas.editor.EditorTool

/**
 * Semantic gesture routing.
 *
 * Pointer-level detectors (`tapAndLongPressGestures`, `nodeInteractionGestures`,
 * `infiniteCanvasGestures`) recognize gestures; this object decides *who owns
 * them* given the current editor session. The split keeps low-level pointer
 * mechanics (touch slop, multi-finger arbitration, event consumption passes)
 * separate from policy that has to evolve with the editor model.
 *
 * Design:
 *  - Pure / testable. No Compose, no ViewModel, no hit-testing.
 *  - All recognized gestures go through this router. Long-press is routed
 *    here as a global popup policy — it is **not** delegated to `activeTool`.
 *    Active tool influences derived popup contents (see
 *    `docs/architecture/editor-tools.md § 5`), never whether the popup is
 *    owned by long-press.
 *  - Mode-first. The tool axis is consulted only in `Edit`. `View` and
 *    `Presentation` have no active tool — the field is set but the router
 *    does not branch on it.
 *  - Context-menu rules are encoded as routes (`DismissContextMenuOnly`),
 *    not as a flag the caller has to remember to short-circuit on.
 *
 * Callers translate router decisions into the appropriate `CanvasAction`
 * dispatches / overlay state updates. The router never produces side effects.
 */
data class GestureRoutingContext(
    val mode: CanvasInteractionMode,
    val activeTool: EditorTool,
    val hasSelection: Boolean,
    val isContextMenuOpen: Boolean,
)

/** Result of routing a single-tap gesture. */
sealed interface TapRoute {
    /** Popup is open. Dismiss it; do not run any other action. */
    data object DismissContextMenuOnly : TapRoute

    /** Edit + Selection: replace selection with `{nodeId}`. */
    data class EditSelect(val nodeId: String) : TapRoute

    /** Edit + Selection: tap on empty space → clear selection. */
    data object EditDeselectAll : TapRoute

    /**
     * Edit + Eraser tap on a node → delete that node. One `CanvasCommand`
     * entry per tap (matches the scrub-undo granularity rule by degenerate
     * case — a single-node "scrub"). See `editor-tools.md § 4.6` Object mode.
     */
    data class EraserDeleteNode(val nodeId: String) : TapRoute

    /** View / Presentation: tap on a node → focus it. */
    data class ViewFocus(val nodeId: String) : TapRoute

    /**
     * Tap is owned by the current tool / mode but produces no effect.
     * (Eraser tap on empty; View tap on empty.)
     */
    data object Ignore : TapRoute
}

/** Result of routing a double-tap gesture. */
sealed interface DoubleTapRoute {
    /** Popup is open. Dismiss it; do not run any other action. */
    data object DismissContextMenuOnly : DoubleTapRoute

    /**
     * Reset the camera to its default fit. Today a dev/test affordance —
     * preserved in non-Edit modes (the Edit-mode rule is "tool owns
     * double-tap" per the locked per-tool gesture maps).
     */
    data object ResetCamera : DoubleTapRoute

    /** Owned by the active tool but no current tool defines double-tap. */
    data object Ignore : DoubleTapRoute
}

/**
 * Result of routing a long-press at *press* time, when the anchor is being
 * resolved. The lift step is routed separately via [routeLongPressLift].
 *
 * Long-press-on-empty has no defined behavior in the locked Selection map
 * (`editor-tools.md § 4.1`) — every empty long-press routes to [Suppress],
 * regardless of active tool. The transitional rect-select fall-through is
 * gone; marquee selection is reached via direct drag-on-empty
 * (see [routeMarqueeStart]).
 */
sealed interface LongPressRoute {
    /**
     * Long-press produces no selection or popup state change. Reached in
     * View / Presentation, when the popup is *not* open and long-press is
     * suppressed by mode, and when long-press lands on empty canvas in any
     * Edit tool.
     */
    data object Suppress : LongPressRoute

    /**
     * Edit + long-press on one or more nodes. The detector remembers
     * [anchorNodeId] as the popup's anchor and opens the menu on lift; if
     * [hasPicker] is true, the popup also renders an overlap-picker row.
     *
     * When [extendsSelection] is true, the caller also dispatches
     * `AddNodeToSelection(anchorNodeId)` — Selection's "add-or-keep"
     * affordance per `selection.md § 2`. Other tools (Eraser today) leave
     * the selection untouched per `editor-tools.md § 4.6`: their long-press
     * is a **non-destructive** bailout to the popup, never a side-effecting
     * selection mutation.
     */
    data class ResolveAnchor(
        val anchorNodeId: String,
        val hasPicker: Boolean,
        val extendsSelection: Boolean,
    ) : LongPressRoute
}

/** Result of routing the lift event after a confirmed long-press. */
sealed interface LongPressLiftRoute {
    /** Open the context menu scoped to the resolved selection + anchor. */
    data object OpenMenu : LongPressLiftRoute

    /** Long-press was suppressed at press time, or another route blocks the menu. */
    data object Suppress : LongPressLiftRoute
}

/**
 * Result of routing a selected-node transform start (move / resize / rotate)
 * recognized by `nodeInteractionGestures`. Today the detector is enabled
 * solely on selection non-emptiness — this route adds tool-awareness so
 * Selection's persistent selection cannot be moved while another tool is
 * active (required correctness before functional `Eraser` lands).
 */
sealed interface SelectedNodeTransformRoute {
    /** Allow the transform; the detector proceeds. */
    data object Allow : SelectedNodeTransformRoute

    /** Block — current tool does not own selected-node manipulation. */
    data object Block : SelectedNodeTransformRoute
}

/**
 * Result of routing an Object-mode Eraser scrub start, recognized by
 * `eraserScrubGestures` after a single-finger drag past touch slop. The
 * scrub fires `CanvasAction.DeleteNodes(setOf(id))` **per newly crossed
 * node** — one undo entry per node, symmetric with tap-on-node Eraser. The
 * detector dedupes within the gesture so re-crossing a node is a no-op.
 * See `editor-tools.md § 4.6` Object mode.
 */
sealed interface EraserScrubStartRoute {
    /** Start the scrub — caller hit-tests each event and dispatches per cross. */
    data object Start : EraserScrubStartRoute

    /**
     * Popup is open. Dismiss it and **do not** start a scrub on this
     * gesture. Same discrete-gesture dismissal rule used by the marquee
     * detector (`MarqueeStartRoute.DismissContextMenuOnly`).
     */
    data object DismissContextMenuOnly : EraserScrubStartRoute

    /** Current tool / mode does not own scrub. Detector is gated off. */
    data object Suppress : EraserScrubStartRoute
}

/**
 * Result of routing a View-mode single-finger pan, recognized by
 * `viewModePanGestures` after a single-finger drag past touch slop. View
 * relaxes the strict "two-finger nav only" rule that holds in Edit
 * (`editor-tools.md § 2`) — there's no active tool claiming single-finger
 * input, so one finger drives the camera directly.
 */
sealed interface ViewPanRoute {
    /** Pan the camera; popup wasn't open. */
    data object Allow : ViewPanRoute

    /**
     * Popup is open. Dismiss it AND proceed with panning — same
     * dismissal-on-proceed rule used by the multi-finger camera path
     * (`CameraTransformRoute.DismissContextMenuAndProceed`). Camera input
     * shouldn't be lost just because the menu was open.
     */
    data object DismissContextMenuAndProceed : ViewPanRoute

    /** Current mode does not own single-finger pan. Detector is gated off. */
    data object Suppress : ViewPanRoute
}

/**
 * Result of routing a Selection-tool marquee start, recognized by
 * `selectionMarqueeGestures` after a single-finger drag-on-empty past touch
 * slop. The detector is the marquee's only entry point — the long-press-
 * then-drag rect-select path is retired.
 */
sealed interface MarqueeStartRoute {
    /** Start the marquee — caller dispatches `UpdateSelectionRect`. */
    data object Start : MarqueeStartRoute

    /**
     * Popup is open. Dismiss it and **do not** start a marquee on this
     * gesture. The detector still consumes the rest of the pointer stream
     * (otherwise the gesture would re-enter on continued movement); the
     * caller short-circuits update / end.
     */
    data object DismissContextMenuOnly : MarqueeStartRoute

    /**
     * The current tool / mode does not own marquee selection. Used as a
     * defensive route — the detector itself is gated off when the route
     * would be [Suppress], so this branch is rarely taken at runtime.
     */
    data object Suppress : MarqueeStartRoute
}

/**
 * Result of routing a multi-finger camera gesture start. Camera always
 * belongs to global navigation per `editor-tools.md § 1.3`; the route exists
 * for symmetry and to give callers a single funnel for context-menu dismissal.
 */
sealed interface CameraTransformRoute {
    /** Dismiss the open popup and proceed with the camera gesture. */
    data object DismissContextMenuAndProceed : CameraTransformRoute

    /** Allow the camera gesture; no popup to dismiss. */
    data object Allow : CameraTransformRoute
}

object GestureRouter {

    fun routeTap(ctx: GestureRoutingContext, hitNodeId: String?): TapRoute {
        if (ctx.isContextMenuOpen) return TapRoute.DismissContextMenuOnly
        return when (ctx.mode) {
            CanvasInteractionMode.Edit -> routeEditTap(ctx.activeTool, hitNodeId)
            CanvasInteractionMode.View,
            CanvasInteractionMode.Presentation,
            -> if (hitNodeId != null) TapRoute.ViewFocus(hitNodeId) else TapRoute.Ignore
        }
    }

    private fun routeEditTap(tool: EditorTool, hitNodeId: String?): TapRoute = when (tool) {
        EditorTool.Selection ->
            if (hitNodeId != null) TapRoute.EditSelect(hitNodeId) else TapRoute.EditDeselectAll
        EditorTool.Eraser ->
            // Object-mode Eraser: tap on a node deletes it; tap on empty is
            // a no-op (`editor-tools.md § 4.6`). Scrub is handled separately
            // by `routeEraserScrubStart` + the dedicated scrub detector.
            if (hitNodeId != null) TapRoute.EraserDeleteNode(hitNodeId) else TapRoute.Ignore
    }

    fun routeDoubleTap(ctx: GestureRoutingContext): DoubleTapRoute {
        if (ctx.isContextMenuOpen) return DoubleTapRoute.DismissContextMenuOnly
        return when (ctx.mode) {
            // Edit: per the locked per-tool gesture maps in editor-tools.md § 4,
            // no MVP tool claims double-tap. (`VectorEdit` will, when it lands.)
            // For Selection / Eraser, double-tap is tool-owned but no-op.
            CanvasInteractionMode.Edit -> when (ctx.activeTool) {
                EditorTool.Selection,
                EditorTool.Eraser,
                -> DoubleTapRoute.Ignore
            }
            // View / Presentation are nil-tool modes — the reset-camera
            // affordance is kept here as a mode-level convenience.
            CanvasInteractionMode.View,
            CanvasInteractionMode.Presentation,
            -> DoubleTapRoute.ResetCamera
        }
    }

    fun routeLongPress(ctx: GestureRoutingContext, hitIds: List<String>): LongPressRoute {
        // Popup-open is *not* a suppression at press time — long-press fires
        // normally so a new long-press on another node replaces the popup
        // in one gesture. See `context-menu.md § 3 — Dismissal rules`.
        return when (ctx.mode) {
            CanvasInteractionMode.View,
            CanvasInteractionMode.Presentation,
            -> LongPressRoute.Suppress
            CanvasInteractionMode.Edit -> {
                if (hitIds.isEmpty()) {
                    // Long-press-then-drag rect-select is retired: marquee
                    // selection is reached via direct drag-on-empty (see
                    // `routeMarqueeStart`). Empty long-press has no defined
                    // tool behavior in the locked Selection map
                    // (`editor-tools.md § 4.1`), so every tool routes to
                    // Suppress here.
                    LongPressRoute.Suppress
                } else {
                    LongPressRoute.ResolveAnchor(
                        anchorNodeId = hitIds.first(),
                        hasPicker = hitIds.size > 1,
                        // Only Selection extends the selection on long-press
                        // ("add-or-keep" per `selection.md § 2`). Other tools
                        // open the popup as a non-destructive bailout
                        // (`editor-tools.md § 4.6` for Eraser) and must not
                        // mutate selection state.
                        extendsSelection = ctx.activeTool == EditorTool.Selection,
                    )
                }
            }
        }
    }

    fun routeLongPressLift(
        ctx: GestureRoutingContext,
        pressRoute: LongPressRoute,
    ): LongPressLiftRoute = when (pressRoute) {
        is LongPressRoute.Suppress -> LongPressLiftRoute.Suppress
        is LongPressRoute.ResolveAnchor ->
            if (ctx.mode == CanvasInteractionMode.Edit) LongPressLiftRoute.OpenMenu
            else LongPressLiftRoute.Suppress
    }

    fun routeSelectedNodeTransformStart(
        ctx: GestureRoutingContext,
    ): SelectedNodeTransformRoute {
        // Selected-node body / handle drag is owned by the Selection tool.
        // Other tools never claim it, even when selection persists across the
        // tool switch — otherwise Eraser-while-something-was-selected would
        // still move/resize that node.
        if (!ctx.hasSelection) return SelectedNodeTransformRoute.Block
        return when (ctx.mode) {
            CanvasInteractionMode.Edit -> when (ctx.activeTool) {
                EditorTool.Selection -> SelectedNodeTransformRoute.Allow
                EditorTool.Eraser -> SelectedNodeTransformRoute.Block
            }
            CanvasInteractionMode.View,
            CanvasInteractionMode.Presentation,
            -> SelectedNodeTransformRoute.Block
        }
    }

    fun routeCameraTransformStart(ctx: GestureRoutingContext): CameraTransformRoute =
        if (ctx.isContextMenuOpen) CameraTransformRoute.DismissContextMenuAndProceed
        else CameraTransformRoute.Allow

    fun routeViewPanStart(ctx: GestureRoutingContext): ViewPanRoute {
        // Only View claims single-finger pan. Edit reserves single-finger
        // for the active tool (`editor-tools.md § 2`); Presentation has its
        // own gestures (`presentation-profile.md`) and isn't routed here.
        if (ctx.mode != CanvasInteractionMode.View) return ViewPanRoute.Suppress
        if (ctx.isContextMenuOpen) return ViewPanRoute.DismissContextMenuAndProceed
        return ViewPanRoute.Allow
    }

    fun routeEraserScrubStart(ctx: GestureRoutingContext): EraserScrubStartRoute {
        if (ctx.mode != CanvasInteractionMode.Edit) return EraserScrubStartRoute.Suppress
        if (ctx.activeTool != EditorTool.Eraser) return EraserScrubStartRoute.Suppress
        if (ctx.isContextMenuOpen) return EraserScrubStartRoute.DismissContextMenuOnly
        return EraserScrubStartRoute.Start
    }

    fun routeMarqueeStart(ctx: GestureRoutingContext): MarqueeStartRoute {
        // Marquee is Selection's drag-on-empty gesture, owned exclusively
        // in Edit mode. Other modes / tools don't claim drag-on-empty so
        // the route returns Suppress — callers should also key the detector
        // off this case so its `pointerInput` short-circuits.
        if (ctx.mode != CanvasInteractionMode.Edit) return MarqueeStartRoute.Suppress
        if (ctx.activeTool != EditorTool.Selection) return MarqueeStartRoute.Suppress
        // Popup-open + drag-on-empty: same discrete-gesture dismissal rule
        // used for tap — dismissing the menu should not also create a
        // selection rect on the same gesture (see `context-menu.md § 3 —
        // Dismissal rules`).
        if (ctx.isContextMenuOpen) return MarqueeStartRoute.DismissContextMenuOnly
        return MarqueeStartRoute.Start
    }
}

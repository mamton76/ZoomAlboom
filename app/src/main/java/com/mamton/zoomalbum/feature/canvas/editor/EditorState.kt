package com.mamton.zoomalbum.feature.canvas.editor

import com.mamton.zoomalbum.core.math.BoundingBox
import com.mamton.zoomalbum.domain.model.CanvasInteractionMode
import com.mamton.zoomalbum.domain.model.FrameEditOptions
import com.mamton.zoomalbum.domain.model.MediaAppearance
import com.mamton.zoomalbum.domain.model.Transform

/**
 * Editor-session state. Everything the canvas needs to interpret single-finger
 * gestures and draw editor overlays — and nothing more.
 *
 * Three categories of editor concern are deliberately kept separate:
 *
 *  1. **Editor-session state** — this class. Owned by `CanvasViewModel`,
 *     observed by the canvas + overlays + action catalog.
 *  2. **Tool settings + transient interaction state** — added on demand
 *     when a tool actually requires them. Introduce `toolSettings` /
 *     `activeInteraction` fields here at that point; do not predeclare
 *     speculative state for tools that don't yet exist, and do not encode
 *     tool settings or active-gesture state inside [EditorTool] variants.
 *  3. **UI-surface state** — open popup / bottom sheet / dialog. Stays
 *     local to `CanvasScaffold`. The same editor operation can later
 *     surface as a phone bottom sheet, a tablet docked panel, or a future
 *     desktop sidebar; coupling that decision to editor-session state would
 *     leak presentation concerns into the model.
 *
 * `contextAnchorNodeId` is an intentional exception to (3): the popup itself
 * is UI-surface state, but the anchor id is consumed by canvas rendering
 * (`SelectionOverlay` draws a halo around the anchor node), so it belongs
 * here — see `docs/architecture/context-menu.md § 2`.
 *
 * Selection is kept flat for now. A dedicated `SelectionState` will be
 * extracted when `SelectionTool` accumulates its own substantial state
 * (selection mode rectangle vs lasso, selection rule, in-progress lasso
 * path, hover, additive flag, anchor-level selection). See
 * `docs/to_discuss.md § 11` resolution notes.
 */
data class EditorState(
    val mode: CanvasInteractionMode = CanvasInteractionMode.Edit,
    val activeTool: EditorTool = EditorTool.Selection,
    val selectedNodeIds: Set<String> = emptySet(),
    /**
     * Live marquee rectangle while the user is drawing a rect-select. Stored
     * in **screen** coordinates so it stays axis-aligned to the screen even
     * when the camera has been rotated. `null` when no marquee is in flight.
     */
    val selectionRect: BoundingBox? = null,
    /** Group selection bounding rect — computed on selection change, rotation accumulated. */
    val groupSelectionTransform: Transform? = null,
    /**
     * Transient toggles for frame transform gestures. See `frame-membership.md`.
     * Defaults are session-level, not persisted with the album.
     */
    val frameEditOptions: FrameEditOptions = FrameEditOptions(),
    /**
     * Node id of the current long-press anchor — the node the user just
     * long-pressed (or last toggled in the overlap picker). Non-null only
     * while the context menu popup is open. Drawn with an outer halo by
     * `SelectionOverlay` to communicate which node anchor-scoped menu items
     * (`Remove this from selection`, `Edit this only`) operate on.
     * See `docs/architecture/context-menu.md § 2`.
     */
    val contextAnchorNodeId: String? = null,
    /**
     * Topbar settings for `EditorTool.CropEdit`. Session-only — not persisted
     * with the album. See `docs/architecture/editor-tools.md § 4.8`.
     */
    val cropEdit: CropEditToolState = CropEditToolState(),
)

/**
 * Topbar-driven settings for `EditorTool.CropEdit`, plus the entry snapshot
 * used by the topbar Cancel button. Session-scoped (not persisted).
 */
data class CropEditToolState(
    /**
     * When `true`, corner-handle drags preserve the viewport's aspect ratio
     * captured at gesture start; when `false`, corners resize freely. Edge
     * handles ignore this — they're inherently one-axis. Default `true`
     * (locked) per `editor-tools.md § 4.8`.
     */
    val aspectLocked: Boolean = true,
    /**
     * Snapshot of the edited media's transform + appearance captured the
     * moment the user entered `CropEdit`. The topbar `Cancel` button
     * restores this; `Leave crop edit` keeps the current state. `null`
     * outside an active `CropEdit` session.
     */
    val entrySnapshot: CropEditEntrySnapshot? = null,
)

/** Snapshot of one media node for the Cancel revert path. */
data class CropEditEntrySnapshot(
    val nodeId: String,
    val transform: Transform,
    val appearance: MediaAppearance?,
)

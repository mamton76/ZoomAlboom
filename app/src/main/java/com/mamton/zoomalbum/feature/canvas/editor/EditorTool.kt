package com.mamton.zoomalbum.feature.canvas.editor

/**
 * The currently-selected editor tool. Owns single-finger / stylus interpretation
 * in Edit mode (two-finger / pinch / rotate always belongs to global navigation).
 *
 * See `docs/architecture/editor-tools.md`. MVP exposes only [Selection] and
 * [Eraser] — the broader vocabulary (`FreeDraw`, `Shape`, `Text`, `VectorEdit`,
 * `MaskEdit`) is added one variant at a time as each tool actually ships.
 *
 * Tool identity is intentionally separate from tool settings and from any
 * in-progress gesture. When per-tool settings or transient interaction state
 * become necessary, introduce dedicated `toolSettings` / `activeInteraction`
 * fields on [EditorState] — do not encode them inside the [EditorTool] variants.
 */
sealed interface EditorTool {
    /** Default tool. Selects, moves, resizes, rotates, marquees. */
    data object Selection : EditorTool

    /**
     * Erases canvas objects. Gesture map locked in
     * `docs/architecture/editor-tools.md § 4.6`; behavior lands as a separate
     * implementation slice after the [EditorState] extraction.
     */
    data object Eraser : EditorTool

    /**
     * Pan / zoom the source pixels of one media node behind its world rect
     * (the "viewport"), and resize the viewport via corner / edge handles.
     * Context-gated: valid only when the selection is exactly one
     * `CanvasNode.Media`. Auto-exits to [Selection] if that invariant breaks
     * (deletion, multi-select, frame selected, etc.).
     *
     * Mutates `MediaAppearance.crop.{offsetX, offsetY, zoom}` and the media
     * node's `Transform`. Entry snaps `crop.mode` to `Manual` if it isn't
     * already; v1 keeps existing `offsetX / offsetY / zoom` values (`(0, 0, 1)`
     * for never-edited nodes — renders as centered Fill). See
     * `docs/architecture/editor-tools.md § 4.8`.
     */
    data object CropEdit : EditorTool
}

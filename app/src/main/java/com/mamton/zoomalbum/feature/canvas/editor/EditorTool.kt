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
}

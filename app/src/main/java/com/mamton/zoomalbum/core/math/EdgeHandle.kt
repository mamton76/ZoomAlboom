package com.mamton.zoomalbum.core.math

/**
 * One-axis resize handle placed at the midpoint of a node rect's edge. Used by
 * `EditorTool.CropEdit` (`docs/architecture/editor-tools.md § 4.8`) to resize
 * the crop viewport along a single axis. Selection does not use edges.
 */
enum class EdgeHandle { TOP, RIGHT, BOTTOM, LEFT }

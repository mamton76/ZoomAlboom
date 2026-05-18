package com.mamton.zoomalbum.domain.undo

import com.mamton.zoomalbum.domain.model.AlbumBackground
import com.mamton.zoomalbum.domain.model.CanvasNode
import kotlinx.serialization.Serializable

@Serializable
enum class CommandKind {
    ADD, REMOVE, DELETE, DUPLICATE, MOVE, RESIZE, ROTATE,
    SET_ALBUM_BACKGROUND,
    /**
     * Frame appearance mutation — covers the entire `FrameAppearance` (background,
     * contentOverlays, border, shadow, titleStyle, opacity, cornerRadius).
     * Wire name kept as `SET_FRAME_BACKGROUND` so existing on-disk history files
     * keep deserializing; semantics broadened with the appearance system.
     */
    SET_FRAME_BACKGROUND,
    /** Media appearance mutation — the entire `MediaAppearance`. */
    SET_MEDIA_APPEARANCE,
    SET_FRAME_OVERRIDES, // Pin / Detach — mutates Frame.overrides only (no transform change).
    REORDER, // BringToFront / SendToBack / BringForward / SendBackward — zIndex mutations.
}

/** Album-level change captured in a command. Distinguishes "set to null" from "no change". */
@Serializable
data class AlbumBackgroundChange(
    val before: AlbumBackground?,
    val after: AlbumBackground?,
)

/**
 * Snapshot-based undoable operation. Holds the affected slice of nodes pre/post,
 * and optionally an album-level change.
 *
 * Invariants:
 * - `before == null && after != null` → pure insert (ADD, DUPLICATE).
 * - `before != null && after == null` → pure delete (REMOVE, DELETE).
 *   `beforeIndices` is populated to restore deleted nodes at their original positions.
 * - `before != null && after != null` → node mutation (MOVE, RESIZE, ROTATE, SET_FRAME_BACKGROUND, SET_FRAME_OVERRIDES).
 *   Same length, same ids, paired positionally.
 * - `albumBackgroundChange != null` → album-level mutation (SET_ALBUM_BACKGROUND). May
 *   coexist with node sides being null.
 * - At least one of (before/after/albumBackgroundChange) must be non-null.
 */
@Serializable
data class CanvasCommand(
    val before: List<CanvasNode>?,
    val after: List<CanvasNode>?,
    val beforeIndices: List<Int>? = null,
    val albumBackgroundChange: AlbumBackgroundChange? = null,
    val kind: CommandKind,
    val timestampMs: Long,
)

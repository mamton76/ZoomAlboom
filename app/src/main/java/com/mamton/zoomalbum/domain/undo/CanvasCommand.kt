package com.mamton.zoomalbum.domain.undo

import com.mamton.zoomalbum.domain.model.CanvasNode
import kotlinx.serialization.Serializable

@Serializable
enum class CommandKind { ADD, REMOVE, DELETE, DUPLICATE, MOVE, RESIZE, ROTATE }

/**
 * Snapshot-based undoable operation. Holds the affected slice of nodes pre/post.
 *
 * Invariants:
 * - `before == null && after != null` → pure insert (ADD, DUPLICATE).
 * - `before != null && after == null` → pure delete (REMOVE, DELETE).
 *   `beforeIndices` is populated to restore deleted nodes at their original positions.
 * - `before != null && after != null` → mutation (MOVE, RESIZE, ROTATE).
 *   Same length, same ids, paired positionally.
 * - At least one side must be non-null.
 */
@Serializable
data class CanvasCommand(
    val before: List<CanvasNode>?,
    val after: List<CanvasNode>?,
    val beforeIndices: List<Int>? = null,
    val kind: CommandKind,
    val timestampMs: Long,
)

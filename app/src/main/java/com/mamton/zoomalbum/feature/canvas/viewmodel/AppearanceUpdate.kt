package com.mamton.zoomalbum.feature.canvas.viewmodel

import com.mamton.zoomalbum.domain.model.CanvasNode
import com.mamton.zoomalbum.domain.undo.CanvasCommand
import com.mamton.zoomalbum.domain.undo.CommandKind

/**
 * Outcome of a fan-out appearance update across a multi-node selection.
 *
 * - [updatedNodes] is the full node list with the change set applied — always
 *   safe to assign back to `_allNodes`; when [command] is `null` it is the same
 *   list reference passed in.
 * - [command] is the single snapshot to commit, or `null` when nothing
 *   actually changed (empty id set, no candidate matched the expected type, or
 *   every candidate already had the target appearance).
 *
 * Phase A foundation for `docs/architecture/appearance.md § 14` (multi-edit).
 * One ordinary `CanvasCommand` carries N before / N after entries — no new
 * `CommandKind`, no session API.
 */
internal data class AppearanceUpdateResult(
    val updatedNodes: List<CanvasNode>,
    val command: CanvasCommand?,
)

/**
 * Pure helper: compute the next node list + one snapshot command for a
 * homogeneous appearance update across [ids].
 *
 * Behavior:
 * - Filters [currentNodes] down to ids in the requested set whose type matches
 *   the reified [N]; ids that miss either filter are silently skipped.
 * - Applies [update] to each remaining candidate; drops entries where the
 *   updated node is structurally equal to the original (per-node no-op skip).
 * - Returns the same node list and a `null` command if no candidate actually
 *   changed (so callers can early-return without touching state or history).
 *
 * The returned [CanvasCommand.before] / `after` lists are positionally paired
 * and share the same ids — matches the mutation invariant declared in
 * `CanvasCommand`.
 */
internal inline fun <reified N : CanvasNode> computeAppearanceUpdate(
    currentNodes: List<CanvasNode>,
    ids: Set<String>,
    kind: CommandKind,
    timestampMs: Long,
    crossinline update: (N) -> N,
): AppearanceUpdateResult {
    if (ids.isEmpty()) return AppearanceUpdateResult(currentNodes, command = null)

    val changes: List<Pair<N, N>> = currentNodes.asSequence()
        .filter { it.id in ids }
        .mapNotNull { it as? N }
        .map { original -> original to update(original) }
        .filter { (original, next) -> original != next }
        .toList()

    if (changes.isEmpty()) return AppearanceUpdateResult(currentNodes, command = null)

    val updatedById: Map<String, CanvasNode> = changes.associate { (_, next) -> next.id to next }
    val newNodes = currentNodes.map { updatedById[it.id] ?: it }

    val command = CanvasCommand(
        before = changes.map { it.first },
        after = changes.map { it.second },
        kind = kind,
        timestampMs = timestampMs,
    )
    return AppearanceUpdateResult(newNodes, command)
}

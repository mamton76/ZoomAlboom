package com.mamton.zoomalbum.feature.canvas.editor

/**
 * Pure predicate for the `CropEdit` **snapshot-bound** invariant.
 *
 * `CropEdit` may be active only when the current selection is exactly the one
 * media node captured in the entry snapshot. The invariant breaks — and the
 * tool must exit to `Selection` — when:
 *
 *  - no entry snapshot exists (`entrySnapshotNodeId == null`); or
 *  - the selection is not exactly one media node (`selectedMediaId == null` —
 *    empty, multi-node, or a non-media single selection); or
 *  - the single selected media is a *different* node than the snapshot's.
 *
 * Returns `false` (nothing to enforce) whenever `CropEdit` is not the active
 * tool. Kept pure and free of `CanvasViewModel` state so it is unit-testable;
 * the view model calls it from `enforceCropEditInvariant()`.
 *
 * See `docs/architecture/editor-tools.md § 4.8` "Persistence + invariant".
 *
 * @param activeTool the current active tool
 * @param entrySnapshotNodeId node id captured at `CropEdit` entry, or `null`
 * @param selectedMediaId id of the single selected media node, or `null` when
 *   the selection is empty, multi-node, or not a single media node
 */
fun cropEditInvariantBroken(
    activeTool: EditorTool,
    entrySnapshotNodeId: String?,
    selectedMediaId: String?,
): Boolean {
    if (activeTool !== EditorTool.CropEdit) return false
    return entrySnapshotNodeId == null ||
        selectedMediaId == null ||
        selectedMediaId != entrySnapshotNodeId
}

package com.mamton.zoomalbum.feature.canvas.editor

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the pure `CropEdit` snapshot-bound invariant predicate
 * (`cropEditInvariantBroken`). Targets the pure function so we don't need to
 * construct a full `CanvasViewModel` (Hilt-injected repositories + coroutine
 * dispatchers). The view model's `enforceCropEditInvariant()` delegates to this
 * predicate, so these cases cover the production exit decision.
 *
 * See `docs/architecture/editor-tools.md § 4.8`.
 */
class CropEditInvariantTest {

    // ── Not in CropEdit → never broken (nothing to enforce) ──────────────

    @Test
    fun `selection tool is never broken`() {
        assertFalse(
            cropEditInvariantBroken(
                activeTool = EditorTool.Selection,
                entrySnapshotNodeId = null,
                selectedMediaId = null,
            ),
        )
    }

    @Test
    fun `eraser tool is never broken even with a stale snapshot`() {
        assertFalse(
            cropEditInvariantBroken(
                activeTool = EditorTool.Eraser,
                entrySnapshotNodeId = "a",
                selectedMediaId = "b",
            ),
        )
    }

    // ── In CropEdit, invariant holds ─────────────────────────────────────

    @Test
    fun `holds when the single selected media matches the snapshot`() {
        assertFalse(
            cropEditInvariantBroken(
                activeTool = EditorTool.CropEdit,
                entrySnapshotNodeId = "photo-1",
                selectedMediaId = "photo-1",
            ),
        )
    }

    // ── In CropEdit, invariant breaks → must exit ────────────────────────

    @Test
    fun `breaks when selection moves to a different media node`() {
        // Gap 2: a *different* single media must exit (snapshot would be stale).
        assertTrue(
            cropEditInvariantBroken(
                activeTool = EditorTool.CropEdit,
                entrySnapshotNodeId = "photo-1",
                selectedMediaId = "photo-2",
            ),
        )
    }

    @Test
    fun `breaks when selection is empty, multi, or non-media (selectedMediaId null)`() {
        // singleSelectedMedia() returns null for empty / multi / frame selection.
        assertTrue(
            cropEditInvariantBroken(
                activeTool = EditorTool.CropEdit,
                entrySnapshotNodeId = "photo-1",
                selectedMediaId = null,
            ),
        )
    }

    @Test
    fun `breaks when there is no entry snapshot`() {
        // Defensive: CropEdit active without a captured snapshot is inconsistent.
        assertTrue(
            cropEditInvariantBroken(
                activeTool = EditorTool.CropEdit,
                entrySnapshotNodeId = null,
                selectedMediaId = "photo-1",
            ),
        )
    }

    @Test
    fun `breaks when both snapshot and selection are absent`() {
        assertTrue(
            cropEditInvariantBroken(
                activeTool = EditorTool.CropEdit,
                entrySnapshotNodeId = null,
                selectedMediaId = null,
            ),
        )
    }
}

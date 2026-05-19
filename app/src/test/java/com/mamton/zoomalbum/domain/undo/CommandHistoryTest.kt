package com.mamton.zoomalbum.domain.undo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CommandHistoryTest {

    private fun cmd(label: Long): CanvasCommand = CanvasCommand(
        before = null,
        after = emptyList(),
        kind = CommandKind.ADD,
        timestampMs = label,
    )

    @Test
    fun `empty history can neither undo nor redo`() {
        val h = CommandHistory()
        assertFalse(h.canUndo)
        assertFalse(h.canRedo)
        assertNull(h.undo())
        assertNull(h.redo())
    }

    @Test
    fun `push enables undo and disables redo`() {
        val h = CommandHistory()
        h.push(cmd(1))
        assertTrue(h.canUndo)
        assertFalse(h.canRedo)
    }

    @Test
    fun `undo returns last pushed and enables redo`() {
        val h = CommandHistory()
        h.push(cmd(1))
        h.push(cmd(2))
        val popped = h.undo()
        assertEquals(2L, popped?.timestampMs)
        assertTrue(h.canUndo)
        assertTrue(h.canRedo)
    }

    @Test
    fun `redo returns last undone and re-enables undo`() {
        val h = CommandHistory()
        h.push(cmd(1))
        h.undo()
        val redone = h.redo()
        assertEquals(1L, redone?.timestampMs)
        assertTrue(h.canUndo)
        assertFalse(h.canRedo)
    }

    @Test
    fun `push clears redo stack`() {
        val h = CommandHistory()
        h.push(cmd(1))
        h.push(cmd(2))
        h.undo() // now redo has cmd(2)
        assertTrue(h.canRedo)
        h.push(cmd(3))
        assertFalse(h.canRedo)
        assertNull(h.redo())
    }

    @Test
    fun `capacity caps undo deque, dropping oldest`() {
        val h = CommandHistory(capacity = 3)
        h.push(cmd(1))
        h.push(cmd(2))
        h.push(cmd(3))
        h.push(cmd(4))  // pushes 1 out
        // Undo should pop 4, 3, 2 — never 1.
        assertEquals(4L, h.undo()?.timestampMs)
        assertEquals(3L, h.undo()?.timestampMs)
        assertEquals(2L, h.undo()?.timestampMs)
        assertNull(h.undo())
    }

    @Test
    fun `clear empties both stacks`() {
        val h = CommandHistory()
        h.push(cmd(1))
        h.undo()
        h.clear()
        assertFalse(h.canUndo)
        assertFalse(h.canRedo)
    }

    @Test
    fun `snapshot and restore round-trip preserves stacks`() {
        val h = CommandHistory()
        h.push(cmd(1))
        h.push(cmd(2))
        h.push(cmd(3))
        h.undo() // undo: [1, 2], redo: [3]

        val snap = h.snapshot()
        assertEquals(2, snap.undo.size)
        assertEquals(1, snap.redo.size)

        val restored = CommandHistory()
        restored.restore(snap)
        // Undo stack: pop 2 then 1 (LIFO). Each undo pushes onto redo.
        assertEquals(2L, restored.undo()?.timestampMs) // undo: [1],  redo: [3, 2]
        assertEquals(1L, restored.undo()?.timestampMs) // undo: [],   redo: [3, 2, 1]
        // Redo pops the top of redo (LIFO) — the most recently undone command (1),
        // not the original snapshot entry (3). This verifies the round-trip plus the
        // LIFO contract; the original `3` remains at the bottom of the redo stack.
        assertEquals(1L, restored.redo()?.timestampMs) // undo: [1],  redo: [3, 2]
    }

    @Test
    fun `restore with oversize snapshot trims to capacity`() {
        val h = CommandHistory(capacity = 2)
        val snap = HistorySnapshot(
            undo = listOf(cmd(1), cmd(2), cmd(3), cmd(4)),
            redo = emptyList(),
        )
        h.restore(snap)
        // Only the most recent 2 should survive.
        assertEquals(4L, h.undo()?.timestampMs)
        assertEquals(3L, h.undo()?.timestampMs)
        assertNull(h.undo())
    }
}

package com.mamton.zoomalbum.domain.undo

/**
 * Pure in-memory undo/redo history.
 *
 * - [push] appends to the undo stack and clears the redo stack.
 * - [undo] pops from the undo stack and pushes onto the redo stack.
 * - [redo] pops from the redo stack and pushes onto the undo stack.
 * - Capacity-capped: oldest undo entries are dropped when [CAPACITY] is exceeded.
 */
class CommandHistory(
    private val capacity: Int = CAPACITY,
) {
    private val undoStack: ArrayDeque<CanvasCommand> = ArrayDeque()
    private val redoStack: ArrayDeque<CanvasCommand> = ArrayDeque()

    val canUndo: Boolean get() = undoStack.isNotEmpty()
    val canRedo: Boolean get() = redoStack.isNotEmpty()

    fun push(command: CanvasCommand) {
        undoStack.addLast(command)
        while (undoStack.size > capacity) undoStack.removeFirst()
        redoStack.clear()
    }

    fun undo(): CanvasCommand? {
        val cmd = undoStack.removeLastOrNull() ?: return null
        redoStack.addLast(cmd)
        return cmd
    }

    fun redo(): CanvasCommand? {
        val cmd = redoStack.removeLastOrNull() ?: return null
        undoStack.addLast(cmd)
        return cmd
    }

    fun clear() {
        undoStack.clear()
        redoStack.clear()
    }

    fun snapshot(): HistorySnapshot =
        HistorySnapshot(undo = undoStack.toList(), redo = redoStack.toList())

    fun restore(snapshot: HistorySnapshot) {
        undoStack.clear()
        redoStack.clear()
        snapshot.undo.forEach { undoStack.addLast(it) }
        snapshot.redo.forEach { redoStack.addLast(it) }
        while (undoStack.size > capacity) undoStack.removeFirst()
    }

    companion object {
        const val CAPACITY: Int = 50
    }
}

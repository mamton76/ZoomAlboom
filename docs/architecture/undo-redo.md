# Undo/Redo Model (Snapshot-Based)

> Related: [data-model.md](data-model.md) | [overview.md](overview.md) | [todo.md §2](../todo.md#2-undoredo)

Implemented. Two `ArrayDeque<CanvasCommand>` (undo + redo) inside `CommandHistory`, capped at 50 entries. Persisted to `filesDir/history_{albumId}.json` on `ViewModel.onCleared()`, loaded on album open.

---

## Domain Types

```kotlin
@Serializable
enum class CommandKind { ADD, REMOVE, DELETE, DUPLICATE, MOVE, RESIZE, ROTATE }

@Serializable
data class CanvasCommand(
    val before: List<CanvasNode>?,        // null = pure insert
    val after: List<CanvasNode>?,         // null = pure delete
    val beforeIndices: List<Int>? = null, // positions of `before` at capture (used for delete restore)
    val kind: CommandKind,
    val timestampMs: Long,
)

@Serializable
data class HistorySnapshot(
    val version: Int = 1,
    val undo: List<CanvasCommand> = emptyList(),
    val redo: List<CanvasCommand> = emptyList(),
)
```

The list-shape of `before`/`after` collapses single- and multi-node operations into one command type — no separate `Single | Compound` variants needed. Every undoable user action emits exactly one `CanvasCommand`.

---

## Invariants

| Shape | Kind | Meaning |
|-------|------|---------|
| `before == null, after != null` | ADD, DUPLICATE | Pure insert — undo removes by id |
| `before != null, after == null` | REMOVE, DELETE | Pure delete — `beforeIndices` records original positions so undo restores list order |
| `before != null, after != null` | MOVE, RESIZE, ROTATE | Mutate — same length, same ids, paired positionally |

At least one side must be non-null (no-op commands are dropped before commit).

---

## Gesture Grouping

**Drag / resize / rotate gestures** use a three-phase protocol:

1. `BeginInteraction(kind)` — fired by the gesture detector when the gesture starts. Snapshots the selected nodes' current state into `pendingSnapshot`.
2. Per-frame `MoveSelection` / `ResizeSelection` / `RotateSelection` — mutate `_allNodes` as usual. No history entry yet.
3. `FinishInteraction` — fired when the gesture ends. Emits one `CanvasCommand` with `before = pendingSnapshot`, `after = current selected slice in matching id order`. No-op gestures (where `before == after` structurally) are dropped.

**Discrete mutations** (`addNode`, `removeNode`, `DeleteSelection`, `DuplicateSelection`) commit immediately — no begin/finish lifecycle.

**Begin-before-delta invariant:** `BeginInteraction` is always dispatched before the first mutation action of a gesture. `NodeInteractionGestureDetector` guarantees this for drag/resize/rotation handles. Two-finger node rotation was removed; the `inNodeRotation` flag pattern is no longer needed.

---

## Mechanics

```
commit(cmd)         — push to undo deque, clear redo deque, refresh canUndo/canRedo flows
Undo action         — defensively commits any pending interaction first, pops undo, applies in reverse
Redo action         — symmetric
```

`applyCommand(cmd, reverse)` preserves list order:
- **Mutate** (`both != null`): replace nodes by id in-place — list order of non-mutated nodes preserved.
- **Pure insert** (`from == null`): append `to` to the end of `_allNodes`.
- **Pure delete** (`to == null`): filter out ids in `from`.
- **Restoring a delete** (`reverse == true && before != null && after == null`): re-insert nodes at their `beforeIndices` positions (ascending order so each insertion index is valid).

**Selection policy after apply:**
- Pure insert → select inserted nodes
- Pure delete → clear selection
- Mutation → select mutated nodes

---

## Capacity & Storage

- **Cap:** 50 undo entries. When full, oldest entry is evicted. Push clears redo.
- **Disk estimate:** ~10 nodes × ~400 B JSON per command × 50 entries ≈ 200 KB upper bound.
- **File:** `filesDir/history_{albumId}.json`
- **Serializer:** `HistorySerializer` (mirrors `SceneGraphSerializer` shape — `prettyPrint = true`, `ignoreUnknownKeys = true`)
- **Repository:** `HistoryRepository` / `HistoryRepositoryImpl` (Hilt-bound)

---

## Persistence Limitation

History saves on `onCleared()` only — survives normal app close/reopen (user backs out of album, swipes activity away) but **not** process death (OS-initiated kill, force-stop, crash between saves). The same caveat applies to the scene graph itself today. A debounced post-commit autosave for both is a separate, future concern.

---

## What Is NOT Undoable

Selection state mutations (`SelectNode`, `ToggleNodeSelection`, `DeselectAll`, `SelectNodesInRect`) do not push `CanvasCommand` entries. They are transient UI state, not scene graph mutations.

---

## File Map

| File | Role |
|------|------|
| `domain/undo/CommandKind.kt` | `@Serializable enum class CommandKind` |
| `domain/undo/CanvasCommand.kt` | `@Serializable data class CanvasCommand` |
| `domain/undo/HistorySnapshot.kt` | `@Serializable data class HistorySnapshot` |
| `domain/undo/InteractionKind.kt` | Gesture-side enum MOVE/RESIZE/ROTATE; maps to CommandKind at commit |
| `domain/undo/CommandHistory.kt` | Two `ArrayDeque<CanvasCommand>`, push/undo/redo/snapshot/restore |
| `domain/repository/HistoryRepository.kt` | Interface: `load(albumId)` / `save(albumId, snapshot)` |
| `data/repository/HistoryRepositoryImpl.kt` | Hilt impl using `HistorySerializer` + `FileStorageHelper` |
| `data/local/file/HistorySerializer.kt` | `serialize(HistorySnapshot)` / `deserialize(raw)` |
| `feature/canvas/viewmodel/CanvasViewModel.kt` | `history: CommandHistory`, `pendingSnapshot`, `canUndo`/`canRedo` flows, `BeginInteraction`/`FinishInteraction`/`Undo`/`Redo` actions |
| `feature/ide_ui/ui/CanvasTopBar.kt` | Undo/Redo buttons (↶ ↷ Unicode chars, enabled state) |
| `feature/ide_ui/ui/CanvasScaffold.kt` | Collects `canUndo`/`canRedo` flows, wires buttons to actions |

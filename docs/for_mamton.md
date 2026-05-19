# Notes for Mamton — things to revisit

Personal study/reference notes Claude saved on request. Not source-of-truth; not part of the architecture docs. Move/rename/delete freely.

---

## 1. Why `rememberUpdatedState` was needed in `CanvasScreen` (2026-05-19)

### Symptom

When the context menu popup was open and the user tapped outside, the popup did **not** dismiss — the tap appeared to do nothing, even though the code clearly had:

```kotlin
onTap = { offset ->
    if (isContextMenuOpen) {
        onCanvasGesture()
        return@tapAndLongPressGestures
    }
    // …normal Select/Deselect…
}
```

### Root cause — Compose's `pointerInput(Unit)` captures lambdas once

`Modifier.tapAndLongPressGestures` is implemented like this:

```kotlin
fun Modifier.tapAndLongPressGestures(
    onTap: (Offset) -> Unit,
    /* ... other callbacks ... */
): Modifier = this.pointerInput(Unit) {
    /* awaitEachGesture { ... onTap(...) ... } */
}
```

`pointerInput(Unit)` means: *start a coroutine once; restart it only if the key (`Unit`) changes.* Since `Unit` is constant, the coroutine **never restarts**.

When that coroutine first runs (first composition), it captures the lambda references you passed in. Those references become "frozen" inside the coroutine. On every subsequent recomposition, Kotlin builds new lambda instances (with fresh captured values like `isContextMenuOpen`), passes them to `tapAndLongPressGestures(...)`, but the running coroutine inside doesn't know — it keeps using the **old** lambda references from the first composition.

So when the popup opens and `isContextMenuOpen` becomes `true` on a recomposition, the gesture coroutine is still calling the lambda that captured `isContextMenuOpen = false`. The early-return path never fires.

### Why some captures didn't have this problem

Inside those lambdas, references to things like `state.mode` or `contextMenuRequest = null` worked correctly even when the lambda itself was stale. Why?

- `state` is a `State<CanvasState>` delegate (`val state by viewModel.state.collectAsStateWithLifecycle()`). Reading `state.mode` goes through the delegate's `.value` getter, which fetches the current value at the moment of read. The lambda captures the **delegate** (a stable object), not the underlying value, so reads stay fresh.
- `contextMenuRequest = null` writes through a `MutableState<...>` delegate (`var contextMenuRequest by remember { mutableStateOf(...) }`). The setter is on the same `MutableState` object across recompositions. Writing via the old lambda still hits the latest state holder.
- `viewModel` is a Hilt-managed reference — stable across recompositions.

The only stale reads are for **plain primitive parameters** like `isContextMenuOpen: Boolean`. Those are captured **by value** at lambda-creation time. The lambda has no way to "re-read" them later because there's no delegate to go through.

### The fix — `rememberUpdatedState`

```kotlin
val isContextMenuOpenLatest by rememberUpdatedState(isContextMenuOpen)

// …inside the captured lambda…
if (isContextMenuOpenLatest) { … }
```

`rememberUpdatedState(value)` does two things:

1. On first composition, creates a `MutableState<T>` and sets its value to `value`.
2. On every subsequent recomposition, writes the new `value` into the same `MutableState`.

The returned `State<T>` delegate (used via `by`) is a stable object. Lambdas capture the delegate, not the underlying value. Each lambda invocation reads `delegate.value`, which is always the latest pass-through value.

It's effectively a one-line "make this primitive live-reactive inside a long-lived closure."

### When you need this pattern

You need `rememberUpdatedState` (or another stable-holder pattern) whenever:
- You have a long-running coroutine / side-effect (`LaunchedEffect(key)`, `DisposableEffect(key)`, `pointerInput(key)` — anything that runs in a coroutine keyed on something that doesn't change with the value you care about).
- You want code inside that coroutine to use the **latest** value of some parameter that changes during recomposition.
- The parameter isn't already a Compose `State<T>` (because if it were, it'd already be live-reactive).

Common cases:
- `isContextMenuOpen: Boolean` parameter consumed inside `pointerInput(Unit)`.
- A click callback inside a `LaunchedEffect`.
- Animation parameters where the animation key shouldn't change but the value should.

### Alternatives

- **Key the `pointerInput` on the changing value:** `pointerInput(isContextMenuOpen) { … }`. Works, but restarts the gesture coroutine every time the value flips. Mid-gesture restart can drop in-flight gestures, so it's bad for an interaction-sensitive modifier like this one.
- **Mutable holder object:** `val ref = remember { booleanArrayOf(false) }; ref[0] = isContextMenuOpen; /* lambdas read ref[0] */`. Equivalent in effect, less idiomatic. The existing `menuCtx` holder in `CanvasScreen` uses this pattern for the anchor / pickerNodes / skipMenu — kept because those are mutated *across multiple sibling lambdas* (one lambda writes, another reads), which `rememberUpdatedState` doesn't model as well.

### Mental model

> Inside a `pointerInput(Unit)` block, treat every captured value as if it were frozen on first composition. To unfreeze a value, route it through a `State<T>` delegate (built-in via `mutableStateOf` / `derivedStateOf`, or synthesized via `rememberUpdatedState`).

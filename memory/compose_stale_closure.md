# Compose: stale closure in pointerInput

`Modifier.pointerInput(key) { block }` only restarts the running coroutine when `key` changes. Lambdas captured inside the block (callbacks like `onDrag`, `onResizeDrag`) keep references to whatever they closed over when the block started. They do NOT see values from later recompositions — unless they read through a stable `State<T>` holder.

**Never do this:**

```kotlin
val state by viewModel.state.collectAsStateWithLifecycle()
val cam = state.camera   // local snapshot — STALE in gesture callbacks

Modifier.nodeInteractionGestures(
    onResizeDrag = { dx, dy ->
        val (wdx, wdy) = screenDeltaToWorld(dx, dy, cam) // uses stale cam
    },
)
```

After zoom/pan/rotate without selection change, gesture callbacks still use the OLD `cam` — resize/rotate feel frozen at the old scale.

**Do this:**

```kotlin
val state by viewModel.state.collectAsStateWithLifecycle()

Modifier.nodeInteractionGestures(
    onResizeDrag = { dx, dy ->
        val (wdx, wdy) = screenDeltaToWorld(dx, dy, state.camera) // fresh via State delegate
    },
)
```

`state.camera` goes through the `State<T>` delegate's `getValue()` on every access, so reads are always current.

## Why

`collectAsStateWithLifecycle()` returns a `State<T>` object that is remembered across recompositions and whose `.value` updates with the Flow. Reading `state.foo` inside a captured lambda dereferences the shared `State` and yields the current value; assigning `val foo = state.foo` snapshots the value into a plain Kotlin local which is then frozen in the closure.

## How to apply

In `CanvasScreen` and any future gesture layer — do NOT hoist `state.X` into a local `val` if it's read inside `pointerInput` callbacks. Either read `state.X` directly each time, or use `rememberUpdatedState` for callbacks. Applies equally to `pointerInput(Unit)` (never restarts) and `pointerInput(someKey)` (restarts only on key change).

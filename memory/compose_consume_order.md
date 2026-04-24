# Compose: consume order in pointerInput

`PointerInputChange.changedToUp()` and `changedToDown()` are **consumption-aware**:

```kotlin
fun PointerInputChange.changedToUp()   = !isConsumed && previousPressed && !pressed
fun PointerInputChange.changedToDown() = !isConsumed && !previousPressed && pressed
```

So this order is **broken**:

```kotlin
event.changes.forEach { it.consume() }      // marks consumed = true
if (event.changes.all { it.changedToUp() }) // returns false for all consumed → never matches
    break
```

The loop never exits. If this is inside a `pointerInput` coroutine (e.g. `consumeUntilAllUp`), the whole `awaitEachGesture` is stuck — subsequent gestures never get picked up.

**Correct order: read status first, consume second:**

```kotlin
val allUp = event.changes.all { it.changedToUp() }
event.changes.forEach { it.consume() }
if (allUp) break
```

Or use the `*IgnoreConsumed()` variants when you explicitly don't care:

```kotlin
event.changes.forEach { it.consume() }
if (event.changes.all { it.changedToUpIgnoreConsumed() }) break
```

## Why this is easy to miss

Debug logs often capture status into a local val (for log formatting), accidentally reading it before consumption. Removing the logs "introduces" the bug, making the causal link counterintuitive. Past incident in `TapAndLongPressGestureDetector.consumeUntilAllUp`.

## How to apply

Any time a loop in `pointerInput` calls `consume()` on events AND checks `changedToDown()` / `changedToUp()` in the same iteration — read the status into a local val first, consume second. Or explicitly pick the `IgnoreConsumed` variant.

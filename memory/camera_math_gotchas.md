# Camera & Resize Math — Subtle Traps

Discovered while wiring `FocusNode` and reviewing resize-by-handle. Both bugs only manifest on rotated frames, which is why they survived for many commits. Read this before touching anything that computes a `Camera` from a `Transform` (or a scale pivot from a handle).

## 1. `Transform.toCamera` — two pitfalls

The graphicsLayer transform with `transformOrigin = (0, 0)` is:

```
screen = rotate(world * scale, cameraRotation) + (camera.cx, camera.cy)
```

To make a focused frame appear axis-aligned **and** centered:

### Trap A — rotation direction

`camera.rotation = transform.rotation` is wrong. The per-node renderer also rotates by `transform.rotation`, so the visible rotation is `cameraRotation + transformRotation`. Copying the value doubles it. The correct value is `camera.rotation = -transform.rotation`, which cancels the frame's own rotation visually.

### Trap B — translation must respect rotation

`camera.cx = screenW/2 - cx * scale` is wrong whenever `camera.rotation != 0`. The screen formula above shows the rotation is applied to the scaled-world vector *before* translation, so the centering formula has to mirror that:

```kotlin
val (rotX, rotY) = rotateVector(cx * scale, cy * scale, cameraRotation)
val cameraCx = screenW / 2f - rotX
val cameraCy = screenH / 2f - rotY
```

Both traps were once latent in `Transform.toCamera` and only surfaced once rotated-frame focus shipped. There is no test for the non-rotated case that catches them (in fact at `rotation = 0` the wrong formula reduces to the right one).

If you add another "compute a camera from a target" function (group focus, rect zoom, present mode), use the same rotate-then-translate pattern. See [coordinates § 8.1](../docs/architecture/coordinates.md#81-frame-focus-camera) for the canonical formula.

## 2. Resize-by-handle — pivot and scale-factor

Two issues that hid each other on non-rotated frames:

### Trap A — pivot

Center-anchored resize (`pivot = node center`) is mathematically valid but doesn't match Figma/Sketch/PowerPoint conventions, where dragging a corner keeps the **opposite corner fixed**. ZoomAlboom uses the opposite-corner pivot.

The pivot is the local opposite-corner offset rotated by `t.rotation` and translated by `(t.cx, t.cy)`. Compute it **once on the gesture side** (`CanvasScreen.onResizeDrag`) and pass it through the action as `(pivotX, pivotY)`. The pivot is geometrically invariant during the gesture — recomputing it from the current transform every event returns the same world point.

### Trap B — scale factor magnitude

With a center pivot, the pivot-to-corner distance is `diagonalLen = sqrt(halfW² + halfH²)`. With an opposite-corner pivot it's `2 * diagonalLen` (the full diagonal). So the same drag projection produces a smaller multiplier under the corner-pivot model:

```kotlin
// Wrong (over-scales by 2×):
val scaleFactor = (diagonalLen + projection) / diagonalLen

// Right:
val fullDiag = 2f * diagonalLen
val scaleFactor = (fullDiag + projection) / fullDiag
```

The two traps masked each other in early testing: with the old code, dragging a corner of a non-rotated rect by `(d, d)` produced `factor = 2`, which made the rect grow at twice the visual drag speed — but symmetric around the center, so the misbehavior felt like a tuning issue rather than a geometry bug. Rotated frames are where the wrongness becomes obvious because the visible diagonal direction no longer aligns with the screen axes.

## 3. Rule of thumb when changing any transform code

- Forward order is **rotate → scale → translate**. Inverse is the reverse. See [coordinates § 3](../docs/architecture/coordinates.md#3-forward-transform-world--screen) / [§ 4](../docs/architecture/coordinates.md#4-inverse-transform-screen--world).
- `effectiveScreenRotation = cameraRotation + nodeRotation`. To "cancel" something visually, negate.
- For pivot-based scale or rotation, compute the pivot once in world space at gesture start (or rely on per-event invariance, but verify the math first). Don't pass scale factors without their pivots.
- If you "fixed" a sign and one direction now works but another doesn't, you broke a different invariant. The pipeline is internally consistent — see [coordinates § 5](../docs/architecture/coordinates.md#5-rotation-sign-convention).

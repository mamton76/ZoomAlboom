# Coordinate Systems & Transform Math

> Related: [overview](overview.md) | [rendering](rendering.md) | [data-model](data-model.md)

Canvas engine math lives in `core/math/`. This doc is the **contract** — the set of conventions, sign rules, and invariants every coordinate-related change must respect. Implementation details (formulas) live in `TransformUtils.kt`; this doc captures the WHY and the invariants so future contributors (and the planned `:canvas-engine` extraction, see [todo § 10](../todo.md#10-canvas-engine-extraction-canvas-engine-module)) have a single source of truth.

## 1. Coordinate Spaces

| Space | Units | Origin | Used by |
|-------|-------|--------|---------|
| **Screen** | pixels | top-left of the canvas composable | gesture input, rendering output |
| **World** | world units (abstract, 1:1 with pixels at `scale = 1`, `rotation = 0`) | user-defined; nodes are placed here | domain model, scene graph JSON |
| **Node-local** | world units relative to the node's own center, un-rotated | node center | hit-testing, handle placement |

All nodes live in **world** space. The **camera** is the single mapping from world to screen.

## 2. Camera

```kotlin
data class Camera(
    val cx: Float = 0f,       // graphicsLayer translationX — screen pixels
    val cy: Float = 0f,       // graphicsLayer translationY — screen pixels
    val scale: Float = 1f,    // 1 = 100%, 2 = zoomed in 2×
    val rotation: Float = 0f, // degrees, positive = CCW in math convention, CW on screen
)
```

- `cx`, `cy` are **graphicsLayer translation values**, not the world point under the screen center. They are in **screen-pixel units**, applied AFTER scale and rotation.
- `transformOrigin = (0, 0)` (top-left) on the canvas graphicsLayer — never `(0.5, 0.5)`. Changing the origin would change the meaning of every other equation here.
- `scale` is clamped to `[0.00005, 10000]` by the gesture handler.

## 3. Forward Transform (world → screen)

Defined by the single `graphicsLayer` on `CanvasScreen`'s inner `Box`:

```
screen = translate(scale(rotate(world)))
```

Concretely:

```kotlin
fun worldToScreen(x, y, cam) =
    rotateVector(x, y, cam.rotation) * cam.scale + (cam.cx, cam.cy)
```

Order is **rotate → scale → translate**. Any other order produces a different visual and breaks the inverse below.

## 4. Inverse Transform (screen → world)

```
world = un-rotate(un-scale(un-translate(screen)))
```

```kotlin
fun screenToWorld(sx, sy, cam) =
    rotateVector((sx - cam.cx) / cam.scale, (sy - cam.cy) / cam.scale, -cam.rotation)
```

**Critical:** the inversion order is the forward order reversed — un-translate first, then un-scale, then un-rotate. Doing it any other way produces coordinates that only match at `rotation = 0`.

### Position-independent deltas

Screen drag deltas have no translation component — only scale and rotation invert:

```kotlin
fun screenDeltaToWorld(dx, dy, cam) =
    rotateVector(dx / cam.scale, dy / cam.scale, -cam.rotation)
```

## 5. Rotation Sign Convention

`rotateVector(x, y, angleDeg)` uses the standard math convention:

```
rotated.x = x·cos(θ) − y·sin(θ)
rotated.y = x·sin(θ) + y·cos(θ)
```

With screen Y pointing **down**, `rotateVector(..., +30°)` appears as a **clockwise** rotation on screen. This is a quirk of flipped-Y screen space, not the math. Do not "fix" it by negating angles — the whole pipeline (camera, node rotation, gesture rotation, handle placement) is internally consistent and flipping the sign in one place breaks the others.

## 6. `Transform` (Node Placement)

```kotlin
data class Transform(
    val cx: Float,      // world-space CENTER x (not top-left)
    val cy: Float,      // world-space CENTER y
    val w: Float,       // base width in world units (rebasable; not native pixels)
    val h: Float,       // base height in world units (rebasable; not native pixels)
    val scale: Float,   // current multiplier; resize mutates this. At creation = 1/camera.scale.
    val rotation: Float,// degrees
    val zIndex: Float,
) {
    val renderW get() = w * scale   // actual rendered width
    val renderH get() = h * scale
}
```

Key conventions:

- `(cx, cy)` is the node's **center** in world coords. The rendered rect's top-left is at `(cx − renderW/2, cy − renderH/2)`.
- Per-node `graphicsLayer` uses `translationX = cx`, `translationY = cy`, `transformOrigin = (0, 0)`, and `drawBehind` paints at `Offset(−renderW/2, −renderH/2)`. See [rendering.md § 6](rendering.md#6-node-rendering) for why `Spacer` + `TransformOrigin(0, 0)` is required.
- `renderW`, `renderH` are computed — use them (not `w`, `h`) for visual size in hit-tests and layout math. Exception: the group selection rect (§9) stores size directly in `w`/`h` and leaves `scale = 1`.

## 7. Hit-Testing (Point-in-OBB)

Nodes are rendered as oriented rectangles, not AABBs. Hit-test transforms the world point **into the node's local frame** (un-rotate around the node center), then checks half-extents:

```kotlin
val (lx, ly) = rotateVector(worldX - t.cx, worldY - t.cy, -t.rotation)
return abs(lx) <= t.renderW / 2 && abs(ly) <= t.renderH / 2
```

Same pattern is used for resize-handle hit-testing: the 4 corner offsets are defined in local space, and the world point is un-rotated to compare.

## 8. Composition Order: Camera + Node Rotation

The inner graphicsLayer applies the camera transform; each node's own graphicsLayer applies the node transform. The final on-screen rotation of a node is the sum:

```
effectiveScreenRotation = cameraRotation + nodeRotation
```

Two consequences:

- A frame created with `nodeRotation = −cameraRotation` appears axis-aligned on screen (see [rendering § 7](rendering.md#7-node-creation)).
- A group selection rect stored with `rotation = −cameraRotation` at formation time appears screen-aligned, then pins to the world as the camera rotates further (§9).

## 8.1 Frame-Focus Camera (`Transform.toCamera`)

`Transform.toCamera(screenW, screenH, fitMode, safeAreaInset): Camera` produces the camera state that centers a node on screen with the frame appearing axis-aligned. Used by `FocusNode` ([navigation.md § Animated Frame Focus](navigation.md#animated-frame-focus)).

```kotlin
val fill = 1f - safeAreaInset * 2f
val sx = (screenW * fill) / renderW
val sy = (screenH * fill) / renderH
val scale = when (fitMode) {
    CONTAIN -> minOf(sx, sy)
    COVER   -> maxOf(sx, sy)
    STRETCH -> sx
}
// Cancel the frame's rotation — visible rotation = cameraRotation + frameRotation.
val cameraRotation = -rotation
// Camera transform: screen = rotate(world * scale, cameraRotation) + (cx, cy).
// To land world point (cx, cy) at screen center, rotate the scaled-world vector
// FIRST, then subtract from the screen-center vector.
val (rotX, rotY) = rotateVector(cx * scale, cy * scale, cameraRotation)
Camera(
    cx = screenW / 2f - rotX,
    cy = screenH / 2f - rotY,
    scale = scale,
    rotation = cameraRotation,
)
```

Two traps in this formula, both easy to get wrong:

1. **`camera.rotation = transform.rotation`** would *double* the visible rotation (canvas rotates by R, frame's own R applies on top → 2R). The inverse — `-rotation` — is what cancels it.
2. **`camera.cx = screenW/2 − cx*scale`** is wrong whenever `camera.rotation ≠ 0`. graphicsLayer applies translate *after* scale+rotate, so the scaled-world vector must be rotated through `cameraRotation` before subtracting.

Both traps were once present in the implementation; they only manifest on rotated frames, so they survived until rotated-frame focus actually shipped. If you add another "compute a camera from a target" function, follow the same pattern (rotate, then translate).

## 9. Group Selection Rect — Invariants

The multi-selection rectangle (`CanvasState.groupSelectionTransform`, built by `TransformUtils.screenAlignedGroupTransform`) is a **rigid-body handle** around the selection, not a tight recomputed bounding box:

1. **Formation (only on selection membership change):** compute an AABB in the screen-aligned frame. Store with `rotation = −cameraRotation`, so it appears axis-aligned on screen at that moment.
2. **After formation, the rect is pinned to world.** Camera pan/zoom/rotate do not mutate it — they appear to rotate/scale it on screen because rendering applies the camera transform on top.
3. **Move:** translate the rect's `(cx, cy)` by the same world delta as the nodes.
4. **Rotate (handle or two-finger):** rigid-body. `rect.rotation += Δ`. `w`, `h` unchanged. All selected nodes rotate around `(rect.cx, rect.cy)` and their own `rotation` accumulates by `Δ`.
5. **Resize (corner handle):** rigid-body uniform scale, **corner-anchored**. Pivot = world position of the corner *opposite* the dragged handle (computed from the rect's local opposite-corner offset rotated by `rect.rotation` and translated by `(rect.cx, rect.cy)`). `rect.cx`, `rect.cy` move with the pivot too — `newCx = pivotX + (rect.cx − pivotX) * factor`, same for `cy`. `rect.w *= factor`, `rect.h *= factor`. Each node's `(cx, cy)` translates away from the pivot by `factor`, and its `scale *= factor`. Shape is preserved.

   Scale-factor derivation on the screen side: the dragged corner sits at `2 × diagonalLen` from the opposite-corner pivot (the full diagonal). The drag delta projected onto the local diagonal direction gives `projection`, and `factor = (2*diag + projection) / (2*diag)`. Using `diag` (center-to-corner) here would over-scale by 2×.
6. **Never** recompute the rect at the end of a gesture — that would snap it back to screen-aligned and destroy the user's rotation/resize.

Add/remove from selection is the *only* event that recomputes the rect, and recomputation always re-anchors it to the current screen axes.

## 10. Viewport-Independent Selection Operations

Selection operations (hit-tests on handles, effective selection transform, move/rotate/resize) must **not** depend on which nodes are currently in `state.visibleNodes`. A zoomed-out camera can cull most of the selection, but the user still needs to drag the handles.

- Single-select transform lookups read `_allNodes` directly.
- Multi-select uses the stored `groupSelectionTransform` (which doesn't depend on node positions at render time).
- `CanvasViewModel.selectionTransform()` is the single entry point — use it instead of iterating `visibleNodes`.

## 11. Summary of Invariants (Cheat Sheet)

| Invariant | Enforced by |
|-----------|-------------|
| `graphicsLayer.transformOrigin = (0, 0)` on both camera and node layers | `CanvasScreen`, `CanvasNodeRenderer` |
| Forward order: rotate → scale → translate | `worldToScreen`, `graphicsLayer` setup |
| Inverse order: un-translate → un-scale → un-rotate | `screenToWorld`, `cameraViewport` |
| `Transform.cx/cy` = center, not top-left | `Transform` model, `CanvasNodeFactory` |
| Node hit-test = un-rotate to local + half-extents | `pointInNode`, `hitTestHandle`, `hitTestRotationHandle` |
| Group rect: screen-aligned at formation, pinned to world after | `screenAlignedGroupTransform`, `ResizeSelection`, `RotateSelection` |
| Group rect only recomputed on selection membership change | `recomputeGroupTransform` call sites |
| Selection operations ignore viewport culling | `selectionTransform()`, `isOnSelectedNode` |

When adding new coordinate math, grep `TransformUtils` for the closest existing function and follow its conventions — do not invent a new sign rule or inversion order locally.

# Rendering Architecture

> Related: [overview](overview.md) | [data-model](data-model.md) | [navigation](navigation.md)

## Canvas Rendering Pipeline

### 1. Gesture Input

`InfiniteCanvasGestureDetector` (`feature/canvas/gestures/`) is a `Modifier.pointerInput` extension that wraps `detectTransformGestures`. It emits four values per gesture frame:

| Parameter | Type | Meaning |
|-----------|------|---------|
| `centroid` | `Offset` | Screen-space pinch midpoint |
| `pan` | `Offset` | Screen-space translation delta |
| `zoom` | `Float` | Scale multiplier (1.0 = no change) |
| `rotation` | `Float` | Rotation delta in degrees |

Double-tap resets the camera to origin.

### 2. Camera State

`Camera` in `core/math/Camera.kt`:

```kotlin
data class Camera(
    val cx: Float = 0f,       // graphicsLayer translationX (screen-pixel units)
    val cy: Float = 0f,       // graphicsLayer translationY (screen-pixel units)
    val scale: Float = 1f,    // zoom level: 1.0 = 100%, 2.0 = zoomed in 2×
    val rotation: Float = 0f, // degrees
)
```

Scale clamped to `[0.00005, 10000]`. `cx`/`cy` are graphicsLayer translation values — see [data-model.md](data-model.md) for the Camera.cx/cy → world coordinate formula.

### 3. Camera Math (Centroid-Anchored)

On each gesture frame, the world point under the pinch centroid must remain fixed:

1. Compute vector from centroid to current camera origin.
2. Scale that vector by the zoom ratio (`newScale / oldScale`).
3. Rotate the scaled vector by the rotation delta (`TransformUtils.rotateVector`).
4. New camera origin = centroid + rotated vector + pan delta.

This keeps the point under the user's fingers stationary during pinch-zoom and rotation.

### 4. Viewport Culling

After each camera update, `recalculateVisibleNodes()` runs on `Dispatchers.Default`:

1. `TransformUtils.cameraViewport()` maps screen corners through the inverse camera matrix to get a world-space AABB.
2. `ViewportCuller.visibleNodes()` filters `allNodes` by AABB intersection with the viewport.
3. Result sorted ascending by `Transform.zIndex` (lowest first → highest on top, matching Compose's draw-in-iteration-order convention).
4. Sorted result stored in `CanvasState.visibleNodes`.

Render correctness depends purely on `Transform.zIndex` — not on insertion order in `_allNodes`. The four z-order actions (`BringToFront` / `SendToBack` / `BringForward` / `SendBackward`) mutate `zIndex` directly and either re-sort `visibleNodes` in place (cheap path) or rely on the next `recalculateVisibleNodes()` to re-sort (for undo/redo, which calls it).

**Current:** brute-force O(n). **Planned:** spatial index (grid or R-tree) for >2k nodes.

### 4b. Level-of-Detail Resolution (LOD)

After viewport culling, each visible node is assigned a `RenderDetail` level by `LodResolver` (`core/math/LodResolver.kt`). This determines **how** (or whether) to render a node at the current zoom.

**Two-stage resolution:**

1. **Screen-size culling** — if the node's largest rendered dimension × `camera.scale` is below `MIN_VISIBLE_PX` (2px), the node is `Hidden`. This prevents rendering sub-pixel objects.

2. **Semantic zoom filtering** — uses the node's `VisibilityPolicy` (or a type-based default) to compute `relativeZoom = camera.scale / policy.referenceScale`:
   - Below `minRelativeZoom` → `belowRangeMode` (e.g. `Hidden` for media, `Stub` for frames)
   - Above `maxRelativeZoom` → `aboveRangeMode` (e.g. `Simplified` for frames, `Full` for media)
   - In range → `Full`

**`RenderDetail`** enum: `Hidden`, `Stub`, `Preview`, `Full`, `Simplified`.

**`VisibilityPolicy`** (per-node, optional — falls back to type defaults):

| Field | Type | Meaning |
|-------|------|---------|
| `referenceScale` | Float | Camera zoom at which the node is "meant to be viewed" |
| `minRelativeZoom` | Float | Below this ratio → `belowRangeMode` |
| `maxRelativeZoom` | Float | Above this ratio → `aboveRangeMode` |
| `belowRangeMode` | RenderDetail | What to show when zoomed too far out |
| `aboveRangeMode` | RenderDetail | What to show when zoomed too far in |

**Default policies:**

| Node type | minRelativeZoom | maxRelativeZoom | belowRangeMode | aboveRangeMode |
|-----------|-----------------|-----------------|----------------|----------------|
| Frame | 0.001 | 500 | Stub | Simplified |
| Media | 0.01 | 100 | Hidden | Full |

**Debug logging:** `LodResolver` emits `Log.d("LodResolver", ...)` whenever a node is hidden or downgraded, logging the node ID, reason (screen-size-cull or semantic-zoom), all relevant values (screen size, relative zoom, policy bounds, camera scale). Filter with `adb logcat -s LodResolver:D`.

### 5. graphicsLayer Transform

`CanvasScreen` uses a **single `Modifier.graphicsLayer`** on an inner `Box`:

```kotlin
Box(modifier = Modifier.graphicsLayer {
    translationX = cam.cx
    translationY = cam.cy
    scaleX = cam.scale
    scaleY = cam.scale
    rotationZ = cam.rotation
    transformOrigin = TransformOrigin(0f, 0f)  // top-left origin
})
```

All child nodes are positioned in world coordinates inside this Box. The GPU applies the camera transform — **no recomposition** during pan/zoom/rotation.

### 6. Node Rendering

`CanvasNodeRenderer` dispatches on node type:

- **`Frame`** — `Spacer` with per-node `graphicsLayer`:
  - `translationX = t.cx`, `translationY = t.cy` — places graphicsLayer origin at node center.
  - `rotationZ = t.rotation`, `transformOrigin = TransformOrigin(0f, 0f)` — rotates around origin = visual center.
  - `drawBehind` draws rect at `topLeft = Offset(-renderW/2, -renderH/2)`, size `(renderW, renderH)`.
  - **Gotcha:** `Spacer` has 0×0 layout size. `TransformOrigin(0.5f, 0.5f)` on a 0×0 composable
    computes pivot = `(0,0)` (not center), which rotates around the top-left corner of the visual
    rect and shifts the center. Always use `TransformOrigin(0f, 0f)` with this pattern.
  - LOD tiers: `Hidden` → skip; `Stub`/`Preview` → `StubRenderer` (solid rect); `Simplified` → border-only; `Full` → rounded filled rect + border.
- **`Media`** — same `Spacer + graphicsLayer + drawBehind` pattern. LOD tiers: `Hidden` → skip; `Stub`/`Preview` → `MediaPlaceholder` (dashed border); `Simplified` → filled placeholder; `Full` → `FullMediaRenderer` using Coil 3 (`rememberAsyncImagePainter`) with `drawBehind` + `clipRect`.

Nodes use **`graphicsLayer`** for position and rotation (GPU-only, no Compose Constraints limits) and **`drawBehind`** for painting at exact world-coordinate dimensions. This avoids the ~16383dp Compose `Constraints` limit that `Modifier.size()` hits at extreme zoom levels.

### 6b. Layered Frame Rendering

> Related: [appearance.md § 6](appearance.md#6-render-pipeline-implication) | [background.md § Rendering Order](background.md#rendering-order) | [frame-membership.md](frame-membership.md)

`FrameAppearance` requires composing a frame in **layers**, not as a single `CanvasNode` pass. A frame's contentOverlays must draw above its linked contents, not just above its own surface, so the renderer needs to interleave a frame's two surfaces around its members. Conceptual order, per frame:

1. **Frame background** — `frame.appearance.background` (Solid / Texture / Procedural). Drawn behind the frame's linked contents, clipped to frame bounds.
2. **Linked frame contents** — every node bound to this frame (see [frame-membership.md](frame-membership.md)), drawn in `Transform.zIndex` order. Each child still draws its own `MediaAppearance`, including its own `MediaAppearance.overlays` list — child appearance is not mutated by the parent frame.
3. **Frame content overlays** — `frame.appearance.contentOverlays: List<OverlayStyle>`, drawn above the rendered children in list order (entry `[i]` over entry `[i-1]`), clipped to frame bounds.
4. **Frame decoration** — `frame.appearance.border`, `titleStyle`, selection handles / editor overlays (Edit mode only).
5. **Frame content effect** *(future)* — `frame.appearance.contentEffect` is a true off-screen pass that re-renders the contents through a filter (sepia, blur, grayscale of everything inside). Sits between (2) and (3) when implemented. Not MVP.

**Distinct from `contentOverlays`:** `contentOverlays` only composite new layers above the rendered children. `contentEffect` re-renders the children through a filter. Both leave child node data untouched; only the rendered frame output differs.

**How the paint loop schedules the two phases.** The `visibleNodes` loop in `CanvasScreen` is now event-driven rather than a flat `for` loop:

1. `buildFramePaintEvents` (in `feature/canvas/view/FramePaintEvents.kt`) walks `visibleNodes` once. For each layered frame F (i.e. `frame.appearance?.contentOverlays` non-empty) it emits two events: a `LayeredFrameSurface` at `F.transform.zIndex` and a `LayeredFrameOverlay` at `max(memberZ, F.transform.zIndex) + epsilon`, where `memberZ` is the highest z-index among F's [effective members](frame-membership.md) intersected with the current visible set. Every other node emits a single `NodePass` at its own z-index.
2. Events are stable-sorted by `sortKey`.
3. The loop dispatches each event: `NodePass → CanvasNodeRenderer`, `LayeredFrameSurface / LayeredFrameOverlay → FrameRendererPhased(frame, detail, phase)`.

A frame with no contentOverlays does **not** split — its border still paints together with its background in a single Spacer, preserving today's single-pass behavior for plain frames.

**Shared rendering helper.** Because `MediaAppearance.overlays` and `FrameAppearance.contentOverlays` are both `List<OverlayStyle>` with the same declaration-order compositing rule, a single helper — `DrawScope.drawOverlayStack(overlays, left, top, right, bottom, textureBitmaps)` in `OverlayRenderer.kt` — serves both scopes. The two outer fields differ in *bounds* and *pipeline position*, not in how an individual stack is drawn. Texture overlays load through `rememberOverlayTextureBitmaps`, which iterates the unique `textureRefId`s in the overlay list, runs Coil's `SingletonImageLoader.execute(...)` with `allowHardware(false)` (so the bitmaps can back `BitmapShader` for Repeat tile modes), and returns a `Map<String, ImageBitmap>` to the helper.

**Frame–content binding.** The Overlay event's sort key depends on `FrameMembershipUseCase.effectiveMembers(frame, visibleNodes)` — geometry plus per-frame overrides. The renderer uses the same membership rules as the rest of the app ([frame-membership.md](frame-membership.md)). A member that's culled out of the viewport doesn't pull the overlay's z-slot upward.

### 7. Node Creation

`CanvasNodeFactory` creates nodes positioned relative to the current viewport. Both `createFrame` and `createMedia` follow the same scaling convention:

- **`Transform.cx`, `cy`** = `viewport.centerX`, `viewport.centerY` — node is centered in the viewport.
- **`Transform.scale`** = `1 / camera.scale` at creation. Subsequent pinch-resize multiplies this; `w/h` stays put.
- **`Transform.w`, `h`** = `targetRender * camera.scale`, so `renderW = w*scale = targetRender`.
  - Frame: `targetRender = (screenW/camera.scale × 0.8) × (screenH/camera.scale × 0.8)`.
  - Media: aspect-preserving fit of `imageWidth × imageHeight` into 80% of viewport.
- **`Transform.rotation`** = `-camera.rotation` so the node appears axis-aligned on screen.
- **`VisibilityPolicy(referenceScale = camera.scale)`** is set so LOD knows the zoom at which the node is "meant to be viewed."
- No trigonometric compensation needed — center-based placement makes this trivial.

Why `scale = 1/camera.scale` (not `1`, not `camera.scale`): the `targetRender` formulas inherently contain a `1/camera.scale` factor. Putting that factor in `scale` cancels it out of `w/h`, leaving `w/h` camera-independent — they represent the canonical render size at `scale = 1`. Two frames created at different zoom levels end up with identical `w/h`. See [data-model.md § Transform](data-model.md#transform) for the rebasing primitives.

### 8. HUD / TopBar

Debug info rendered in `CanvasTopBar` (outside the canvas, not affected by camera):
- Node count: `"visible: N / total"`
- Camera: zoom level, rotation, xy position

---

## IDE Overlay

### Canvas-First Default Layout

By default, the IDE overlay is minimal — the canvas dominates the screen:

```
┌──────────────────────────────────────┐
│  TopBar: album name | undo/redo | ≡  │
├──────────────────────────────────────┤
│                                      │
│                                      │
│            (canvas ~100%)            │
│                                      │
│                                      │
│                                [+]   │  ← FAB
└──────────────────────────────────────┘
```

All docked and floating panels are hidden/collapsed by default. Users can enable them via the Panel Configuration UI (menu-accessible).

### Canvas-First UI Modes

> See also: [PRD §12.6 — Canvas-first chrome](../product/PRD.md#126-canvas-first-chrome)

Three modes layer over the canvas depending on user action:

**1. Navigate mode** (default)
- Canvas takes ~100% of screen
- Thin TopBar: album name, undo/redo buttons, menu icon
- Single FAB [+] bottom-right for adding content
- No panels visible

**2. Add content mode** (triggered by FAB [+])
- Bottom Sheet slides up from the bottom edge
- Contains: content type picker (Frame + media types: Photo, Video, Audio, Text, Sticker, Vector; future: AnimatedPhoto) + media library browser
- Canvas remains visible and interactive behind the sheet
- Sheet dismisses on drag-down or content placement

**3. Object selected mode** (triggered by tapping a canvas node)
- Contextual action bar appears at the bottom of the screen
- Actions: move, scale, delete, duplicate, edit
- Bar disappears when selection is cleared (tap on empty canvas)

### Full Panel Layout (Opt-In)

When panels are enabled via the Panel Configuration UI, `IdeOverlayScreen` composes on top of `CanvasScreen` in `MainActivity`:

```
┌──────────────────────────────────────┐
│                 Top                  │
├──────────┬────────────┬──────────────┤
│ LeftTop  │  (canvas   │   RightTop   │
│          │  viewport) │              │
│LeftBottom│            │  RightBottom │
├──────────┴────────────┴──────────────┤
│                Bottom                │
└──────────────────────────────────────┘
         + floating panels on top
```

This layout is hidden by default and available as a power-user configuration.

### Panel System

**`PanelState`** — id, title, visibility, expanded, position, offset (floating), size, active tab, z-index.

**`PanelPosition`** (enum) — `LeftTop`, `LeftBottom`, `RightTop`, `RightBottom`, `Top`, `Bottom`, `Floating`.

**Components:**

| Component | Role |
|-----------|------|
| `PanelSlot` | Filters panels for a position, renders tab bar if multiple, delegates to `DockedPanel` |
| `DockedPanel` | Collapsible header + inner tab bar + animated content area |
| `FloatingPanel` | Draggable (title bar), resizable (corner handle), auto-docks at 80dp edge threshold |

**Inner tabs:** `PanelTab.MediaLibrary`, `PanelTab.FrameList` — content rendered by `MediaLibraryPanel` / `FrameListPanel` (stubs).

### State Management

`IdeViewModel` processes `IdeAction` intents:

| Action | Effect |
|--------|--------|
| `SelectTab` | Changes active tab in a panel |
| `TogglePanelExpanded` | Expand/collapse with animated width/height |
| `TogglePanelVisibility` | Show/hide panel |
| `MovePanel` | Update floating panel offset |
| `ResizePanel` | Update floating panel dimensions |
| `DockPanel` | Move floating panel to a docked position |
| `BringToFront` | Increment z-index to top |
| `SelectSlotActivePanel` | Switch which panel is visible in a multi-panel slot |

Collapse behavior varies by position: side panels collapse width, top/bottom panels collapse height.

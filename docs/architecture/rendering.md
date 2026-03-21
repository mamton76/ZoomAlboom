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
3. Result stored in `CanvasState.visibleNodes`.

**Current:** brute-force O(n). **Planned:** spatial index (grid or R-tree) for >2k nodes.

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
  - `translationX = t.cx - renderW/2`, `translationY = t.cy - renderH/2` (top-left offset from center)
  - `rotationZ = t.rotation`, `transformOrigin = TransformOrigin(0.5f, 0.5f)` (rotation around visual center)
  - `drawBehind` paints fill + border at `Size(renderW, renderH)` in world units.
- **`Media`** — placeholder (Coil image loading deferred to Stage 2).

Nodes use **`graphicsLayer`** for position and rotation (GPU-only, no Compose Constraints limits) and **`drawBehind`** for painting at exact world-coordinate dimensions. This avoids the ~16383dp Compose `Constraints` limit that `Modifier.size()` hits at extreme zoom levels.

### 7. Node Creation

`CanvasNodeFactory` creates nodes positioned relative to the current viewport:

- **`Transform.cx`, `cy`** = `viewport.centerX`, `viewport.centerY` — frame is simply centered in the viewport.
- **`Transform.w`, `h`** = `viewport.width * 0.8`, `viewport.height * 0.8` — actual world-unit dimensions, ~80% of viewport.
- **`Transform.scale`** = 1.0 (default; user can pinch-resize later).
- **`Transform.rotation`** = `-camera.rotation` so the frame appears axis-aligned on screen.
- No trigonometric compensation needed — center-based placement makes this trivial.

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
- Contains: content type picker (photo, video, text, sticker) + media library browser
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

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

`Camera` in `CanvasViewModel`:

```kotlin
data class Camera(
    val x: Float = 0f,        // translation X (screen px)
    val y: Float = 0f,        // translation Y (screen px)
    val scale: Float = 1f,    // zoom level
    val rotation: Float = 0f, // degrees
)
```

Scale clamped to `[0.00005, 10000]`.

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
    translationX = cam.x
    translationY = cam.y
    scaleX = cam.scale
    scaleY = cam.scale
    rotationZ = cam.rotation
    transformOrigin = TransformOrigin(0f, 0f)  // top-left origin
})
```

All child nodes are positioned in world coordinates inside this Box. The GPU applies the camera transform — **no recomposition** during pan/zoom/rotation.

### 6. Node Rendering

`CanvasNodeRenderer` dispatches on node type:

- **`Frame`** — `Box` with offset/size/rotation, semi-transparent fill, border, optional label.
- **`Media`** — placeholder (Coil image loading deferred to Stage 2).

Nodes use `Modifier.offset` (world position in dp) + `Modifier.size` (width * scale, height * scale in dp) + `Modifier.rotate`.

### 7. HUD Overlay

Rendered **outside** the graphicsLayer Box (not affected by camera):
- Node count: `"visible: N / total"`
- Zoom level: `"zoom: Nx"`

---

## IDE Overlay

### Layout

`IdeOverlayScreen` composes on top of `CanvasScreen` in `MainActivity`:

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
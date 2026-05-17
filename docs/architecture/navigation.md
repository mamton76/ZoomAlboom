# Navigation

> Related: [overview](overview.md) | [rendering](rendering.md) | [modules & DI](modules.md) | [presentation-profile](presentation-profile.md) | [selection](selection.md)

## Three Levels

1. **App-level** — Jetpack Navigation between screens (`projects_home` → `canvas/{albumId}`).
2. **In-album camera** — continuous pan / pinch-zoom / rotate via [Layer 3 of the gesture stack](selection.md#5-gesture-stack). User-driven, no animation.
3. **Frame transitions** — discrete, intent-driven animated camera fit on a single node (frame or media). Dispatched by `CanvasAction.FocusNode(nodeId)`.

## Routes

Defined in `app/navigation/AppNavigation.kt`:

| Route | Screen | Description |
|-------|--------|-------------|
| `projects_home` | `AlbumListScreen` | Album list — create, open, delete albums |
| `canvas/{albumId}` | `CanvasScreen` + `IdeOverlayScreen` | Canvas editor for a specific album |

## Current State

`AppNavigation` composable sets up a `NavHost` with `PROJECTS_HOME` as the start destination. The `CANVAS` route accepts an `albumId` path parameter. Navigation is fully wired into `MainActivity`.

## Screen Composition

```
MainActivity
└── AppNavigation (NavHost)
    ├── projects_home -> AlbumListScreen
    └── canvas/{albumId} -> CanvasScaffold
            ├── CanvasTopBar (album name, HUD, back, frame list, panel config)
            ├── FAB [+] -> AddContentBottomSheet
            ├── CanvasScreen        // infinite canvas, gestures, node rendering
            ├── IdeOverlayScreen    // IDE panel overlay (opt-in, hidden by default)
            └── ContextualActionBar // stub, shown on node selection
```

## Album List (projects_home/)

- `AlbumListScreen` — displays albums, handles create/open/delete.
- `ProjectsViewModel` — observes `ProjectRepository.observeAlbums()`, manages loading state.

## Canvas Editor (canvas/)

`CanvasScaffold` is the root composable for the canvas route. It composes:

1. **`CanvasScreen`** — infinite canvas with gesture handling, node rendering, viewport culling.
2. **`IdeOverlayScreen`** — IDE-style panel overlay (docked + floating panels, hidden by default).
3. **`ContextualActionBar`** — stub shown at bottom when a node is selected.
4. **`AddContentBottomSheet`** — slides up from FAB tap; content type picker (Frame + media types: Photo, Video, Audio, Text, Sticker, Vector; future: AnimatedPhoto).
5. **`FrameListBottomSheet`** — accessible from TopBar ☰; lists frames with delete.
6. **`PanelConfigDialog`** — accessible from TopBar ⚙; toggle IDE panels on/off.

`CanvasViewModel` and `IdeViewModel` are separate to prevent cross-recomposition.

## Canvas Interaction Mode

`CanvasState.mode: CanvasInteractionMode` gates which contextual interactions are reachable:

| Mode | Tap on a node | Long-press / rect-select | Pan / pinch / rotate | Selection chrome |
|------|---------------|--------------------------|----------------------|------------------|
| `Edit` (default) | Replace selection (`SelectNode`) | Toggle / overlap picker / rect-select | Active | Visible |
| `View` | Animated focus on the node (`FocusNode`) | Swallowed (no-op) | Active | Hidden (selection always empty) |
| `Presentation` | Same as `View`. Reserved for read-only published-album surfaces (post-MVP — no dedicated UI yet) | Swallowed | Active | Hidden |

Entering any non-Edit mode clears `selectedNodeIds`, `groupSelectionTransform`, and `selectionRect`. Selection-keyed chrome (`SelectionOverlay`, handles, `ContextualActionBar`) auto-hides as a result — no per-mode branching needed in those composables.

The toggle lives in `CanvasTopBar`; it currently cycles **Edit ↔ View** only. `Presentation` is reachable programmatically via `CanvasAction.SetMode(Presentation)` and is reserved for the future Present surface.

## Animated Frame Focus

```
Tap a node in View / Presentation, OR
Tap a frame row in FrameListBottomSheet (Edit or View)
        ↓
CanvasAction.FocusNode(nodeId)
        ↓
ViewModel resolves the target camera from node.transform.toCamera(
    screenW, screenH,
    fitMode      = profile?.defaultFitMode ?: CONTAIN,
    safeAreaInset = profile?.safeAreaInset ?: 0.1,
)
        ↓
TransitionPreset + auto-duration formula → (durationMs, easing)
(see docs/architecture/future-features/transition-editor.md § Auto-Duration Formula)
        ↓
A viewModelScope coroutine ticks CanvasInterpolation.interpolate(from, to, t, easing)
into CanvasState.camera every ~16 ms. CanvasState.cameraAnimation holds a
transient snapshot of (from, to, durationMs, easing) while running.
        ↓
Animation completes (t = 1) OR any onGesture event arrives → animation cancels,
cameraAnimation cleared, camera left at its current value.
```

Key properties:

- **No persistence.** `cameraAnimation` is runtime-only — never written to disk.
- **Pan/zoom/rotate cancels mid-flight** (`onGesture()` invokes `cancelCameraAnimation()` before applying the gesture).
- **Rotation cancels the frame's own rotation** so the focused frame appears axis-aligned. See [coordinates § 8.1](coordinates.md#81-frame-focus-camera).
- **Defaults come from `AlbumPresentationProfile`** — preset, easing, fit mode, and safe-area inset. See [presentation-profile.md § 7a](presentation-profile.md#7a-motion-defaults).
- **`FocusNode` is not undoable** — camera state is not part of the scene graph mutation history.

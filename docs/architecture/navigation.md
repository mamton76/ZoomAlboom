# Navigation

> Related: [overview](overview.md) | [rendering](rendering.md) | [modules & DI](modules.md)

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
4. **`AddContentBottomSheet`** — slides up from FAB tap; content type picker (Frame, Photo, Video, …).
5. **`FrameListBottomSheet`** — accessible from TopBar ☰; lists frames with delete.
6. **`PanelConfigDialog`** — accessible from TopBar ⚙; toggle IDE panels on/off.

`CanvasViewModel` and `IdeViewModel` are separate to prevent cross-recomposition.

# Navigation

## Routes

Defined in `app/navigation/AppNavigation.kt`:

| Route | Screen | Description |
|-------|--------|-------------|
| `projects_home` | `AlbumListScreen` | Album list — create, open, delete albums |
| `canvas/{albumId}` | `CanvasScreen` + `IdeOverlayScreen` | Canvas editor for a specific album |

## Current State

`AppNavigation` composable sets up a `NavHost` with `PROJECTS_HOME` as the start destination. The `CANVAS` route accepts an `albumId` path parameter.

**Not yet integrated:** `MainActivity` currently composes `CanvasScreen` and `IdeOverlayScreen` directly in a `Box`, bypassing the NavHost. Full navigation wiring is planned.

## Screen Composition

When navigation is fully wired:

```
MainActivity
└── AppNavigation (NavHost)
    ├── projects_home -> AlbumListScreen
    └── canvas/{albumId} -> Box {
            CanvasScreen(albumId)      // background layer
            IdeOverlayScreen()         // foreground layer
        }
```

## Album List (projects_home/)

- `AlbumListScreen` — displays albums, handles create/open/delete.
- `ProjectsViewModel` — observes `ProjectRepository.observeAlbums()`, manages loading state.

## Canvas Editor (canvas/)

Two-layer composition:
1. **`CanvasScreen`** — infinite canvas with gesture handling, node rendering, viewport culling.
2. **`IdeOverlayScreen`** — IDE-style panel overlay (docked + floating panels).

Each has its own ViewModel to prevent cross-recomposition.
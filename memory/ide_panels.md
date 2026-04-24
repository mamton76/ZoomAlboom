# IDE panel system

Implemented 2026-03-12. Power-user opt-in feature; canvas-first default keeps panels hidden.

## Model

- `PanelPosition`: `LeftTop`, `LeftBottom`, `RightTop`, `RightBottom`, `Top`, `Bottom`, `Floating`
- `PanelState`: `expandedWidth` / `expandedHeight` preserve dimensions across collapse cycles; `width` / `height` are current rendered dims
- `IdeUiState.activePanelPerSlot: Map<PanelPosition, String>` — which panel tab is active per slot
- `IdeViewModel.TogglePanelExpanded` changes width (Left/Right) or height (Top/Bottom); `ResizePanel` updates expanded dims too

## Components

| Component | Role |
|-----------|------|
| `IdeOverlayScreen` | `Column[Top slot, Row[LeftCol, centre, RightCol], Bottom slot]` + floating overlay layer |
| `PanelSlot` | Shows `SlotTabBar` (PrimaryTabRow) when >1 panel shares a position; only the active panel renders |
| `DockedPanel` | Position-aware `‹›▲▼` toggle, `InnerTabBar` (`MediaLibrary` / `FrameList` only for `LeftTop` / `LeftBottom`) |
| `FloatingPanel` | Drag-to-dock snaps to `LeftTop` / `RightTop`; resize via corner handle |

## Stubs

- `ui/panels/MediaLibraryPanel.kt` — placeholder, not yet wired to `media_library` data.
- `ui/panels/FrameListPanel.kt` — partially wired (frame list is shared with `FrameListBottomSheet`).

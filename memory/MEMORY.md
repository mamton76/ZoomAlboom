# ZoomAlboom Project Memory

## Key Architecture
- Single-module Android app, Clean Architecture (core / domain / data / feature)
- MVI pattern: State/Intent/Effect interfaces in `core/mvi/MviContract.kt`
- Canvas: single `graphicsLayer` on Box, GPU-transformed, never recomposes nodes on gesture

## IDE Panel System (implemented 2026-03-12)
- PanelPosition: LeftTop, LeftBottom, RightTop, RightBottom, Top, Bottom, Floating
- PanelState: `expandedWidth`/`expandedHeight` preserve dims across collapse cycles; `width`/`height` are current rendered dims
- IdeUiState: `activePanelPerSlot: Map<PanelPosition, String>` — which panel tab is active per slot
- IdeViewModel: TogglePanelExpanded changes width (Left/Right) or height (Top/Bottom); ResizePanel updates expanded dims too
- IdeOverlayScreen: Column[Top slot | Row[LeftCol | centre | RightCol] | Bottom slot] + floating overlay layer
- PanelSlot: shows SlotTabBar (PrimaryTabRow) when >1 panel shares position; only active panel rendered
- DockedPanel: position-aware ‹›▲▼ toggle, InnerTabBar (MediaLibrary/FrameList only for LeftTop/LeftBottom)
- FloatingPanel: drag-to-dock snaps to LeftTop/RightTop; resize corner handle
- Panel stubs: `ui/panels/MediaLibraryPanel.kt`, `ui/panels/FrameListPanel.kt`

## Dependencies / Gotchas
- NO `material-icons-core` in deps — use Unicode chars (‹ › ⠿) instead of Icons.Default.*
- `TabRow` is deprecated in this BOM (2026.02.01) — use `PrimaryTabRow` with `@OptIn(ExperimentalMaterial3Api::class)`
- `PrimaryTabRow.divider` takes a `@Composable () -> Unit`, not a Color parameter
- `Modifier.offset { IntOffset }` needs `import androidx.compose.foundation.layout.offset`

## Build Commands
- `./gradlew :app:compileDebugKotlin` — fast compile check
- `./gradlew assembleDebug` — full APK
- `./gradlew testDebugUnitTest` — unit tests

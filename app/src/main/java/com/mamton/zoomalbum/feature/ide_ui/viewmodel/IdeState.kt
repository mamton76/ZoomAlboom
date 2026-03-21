package com.mamton.zoomalbum.feature.ide_ui.viewmodel

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mamton.zoomalbum.core.mvi.Intent
import com.mamton.zoomalbum.core.mvi.State

enum class PanelPosition { LeftTop, LeftBottom, RightTop, RightBottom, Top, Bottom, Floating }

enum class PanelTab(val label: String) {
    MediaLibrary("Media Library"),
    FrameList("Frame List"),
}

@Immutable
data class PanelState(
    val id: String,
    val title: String,
    val isVisible: Boolean = true,
    val isExpanded: Boolean = true,
    val position: PanelPosition = PanelPosition.LeftTop,
    val offset: Offset = Offset.Zero,
    /** Current rendered width (changes on collapse/expand and resize). */
    val width: Dp = 280.dp,
    /** Current rendered height (changes on collapse/expand and resize). */
    val height: Dp = 400.dp,
    /** Width to restore when expanding a Left/Right panel. Updated on resize. */
    val expandedWidth: Dp = 280.dp,
    /** Height to restore when expanding a Top/Bottom panel. Updated on resize. */
    val expandedHeight: Dp = 400.dp,
    val activeTab: PanelTab = PanelTab.MediaLibrary,
    val zIndex: Float = 0f,
)

@Immutable
data class PanelConfig(
    val enabledPanelIds: Set<String> = emptySet(),
    val positionOverrides: Map<String, PanelPosition> = emptyMap(),
)

@Immutable
data class IdeUiState(
    val panels: List<PanelState> = emptyList(),
    /**
     * Tracks which panel is "active" (selected tab) per slot position.
     * Defaults to the first panel in the slot when no entry is present.
     */
    val activePanelPerSlot: Map<PanelPosition, String> = emptyMap(),
    val isToolbarExpanded: Boolean = true,
    val panelConfig: PanelConfig = PanelConfig(),
) : State

sealed interface IdeAction : Intent {
    data class SelectTab(val panelId: String, val tab: PanelTab) : IdeAction
    data class TogglePanelExpanded(val panelId: String) : IdeAction
    data class TogglePanelVisibility(val panelId: String) : IdeAction
    data class MovePanel(val panelId: String, val offset: Offset) : IdeAction
    data class ResizePanel(val panelId: String, val width: Dp, val height: Dp) : IdeAction
    data class DockPanel(val panelId: String, val position: PanelPosition) : IdeAction
    data class BringToFront(val panelId: String) : IdeAction
    /** Selects which panel to display when a slot contains multiple panels. */
    data class SelectSlotActivePanel(val position: PanelPosition, val panelId: String) : IdeAction

    // Panel configuration actions
    data class TogglePanelEnabled(val panelId: String) : IdeAction
    data object ResetPanelConfig : IdeAction
}
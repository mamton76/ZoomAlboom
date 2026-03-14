package com.mamton.zoomalbum.feature.ide_ui.viewmodel

import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

private val PANEL_COLLAPSED_WIDTH = 48.dp
private val PANEL_COLLAPSED_HEIGHT = 36.dp

/**
 * Owns ONLY the IDE overlay panels, toolbar state, and theme preferences.
 * Must never reference or trigger recomposition in [CanvasViewModel].
 */
@HiltViewModel
class IdeViewModel @Inject constructor() : ViewModel() {

    private val _state = MutableStateFlow(IdeUiState())
    val state: StateFlow<IdeUiState> = _state.asStateFlow()

    fun onAction(action: IdeAction) {
        when (action) {
            is IdeAction.SelectTab -> updatePanel(action.panelId) {
                it.copy(activeTab = action.tab)
            }
            is IdeAction.TogglePanelExpanded -> updatePanel(action.panelId) { panel ->
                val expanding = !panel.isExpanded
                when (panel.position) {
                    PanelPosition.LeftTop, PanelPosition.LeftBottom,
                    PanelPosition.RightTop, PanelPosition.RightBottom -> panel.copy(
                        isExpanded = expanding,
                        width = if (expanding) panel.expandedWidth else PANEL_COLLAPSED_WIDTH,
                    )
                    PanelPosition.Top, PanelPosition.Bottom -> panel.copy(
                        isExpanded = expanding,
                        height = if (expanding) panel.expandedHeight else PANEL_COLLAPSED_HEIGHT,
                    )
                    PanelPosition.Floating -> panel.copy(isExpanded = expanding)
                }
            }
            is IdeAction.TogglePanelVisibility -> updatePanel(action.panelId) {
                it.copy(isVisible = !it.isVisible)
            }
            is IdeAction.MovePanel -> updatePanel(action.panelId) {
                it.copy(offset = action.offset)
            }
            is IdeAction.ResizePanel -> updatePanel(action.panelId) {
                it.copy(
                    width = action.width,
                    height = action.height,
                    expandedWidth = action.width,
                    expandedHeight = action.height,
                )
            }
            is IdeAction.DockPanel -> updatePanel(action.panelId) {
                it.copy(position = action.position)
            }
            is IdeAction.BringToFront -> {
                val maxZ = _state.value.panels.maxOfOrNull { it.zIndex } ?: 0f
                updatePanel(action.panelId) { it.copy(zIndex = maxZ + 1f) }
            }
            is IdeAction.SelectSlotActivePanel -> _state.update { state ->
                state.copy(
                    activePanelPerSlot = state.activePanelPerSlot + (action.position to action.panelId),
                )
            }
        }
    }

    private fun updatePanel(id: String, transform: (PanelState) -> PanelState) {
        _state.update { state ->
            state.copy(panels = state.panels.map { if (it.id == id) transform(it) else it })
        }
    }
}
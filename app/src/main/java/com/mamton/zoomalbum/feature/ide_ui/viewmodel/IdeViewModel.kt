package com.mamton.zoomalbum.feature.ide_ui.viewmodel

import androidx.lifecycle.ViewModel
import com.mamton.zoomalbum.core.mvi.State
import com.mamton.zoomalbum.domain.model.PanelDescriptor
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

/**
 * Owns ONLY the IDE overlay panels, toolbar state, and theme preferences.
 * Must never reference or trigger recomposition in [CanvasViewModel].
 */
data class IdeUiState(
    val panels: List<PanelDescriptor> = emptyList(),
    val isToolbarExpanded: Boolean = true,
) : State

@HiltViewModel
class IdeViewModel @Inject constructor() : ViewModel() {

    private val _state = MutableStateFlow(IdeUiState())
    val state: StateFlow<IdeUiState> = _state.asStateFlow()
}

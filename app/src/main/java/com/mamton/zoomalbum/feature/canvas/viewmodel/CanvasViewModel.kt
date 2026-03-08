package com.mamton.zoomalbum.feature.canvas.viewmodel

import androidx.lifecycle.ViewModel
import com.mamton.zoomalbum.core.mvi.State
import com.mamton.zoomalbum.domain.model.CanvasNode
import com.mamton.zoomalbum.domain.model.Transform
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

/**
 * Owns ONLY the camera transform and the list of visible canvas nodes.
 * Must never reference or trigger recomposition in [IdeViewModel].
 */
data class CanvasState(
    val cameraTransform: Transform = Transform(),
    val nodes: List<CanvasNode> = emptyList(),
    val selectedNodeId: String? = null,
) : State

@HiltViewModel
class CanvasViewModel @Inject constructor() : ViewModel() {

    private val _state = MutableStateFlow(CanvasState())
    val state: StateFlow<CanvasState> = _state.asStateFlow()
}

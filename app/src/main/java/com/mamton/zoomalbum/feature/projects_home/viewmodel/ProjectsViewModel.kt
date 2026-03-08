package com.mamton.zoomalbum.feature.projects_home.viewmodel

import androidx.lifecycle.ViewModel
import com.mamton.zoomalbum.core.mvi.State
import com.mamton.zoomalbum.domain.model.AlbumMeta
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

data class ProjectsState(
    val albums: List<AlbumMeta> = emptyList(),
    val isLoading: Boolean = false,
) : State

@HiltViewModel
class ProjectsViewModel @Inject constructor() : ViewModel() {

    private val _state = MutableStateFlow(ProjectsState())
    val state: StateFlow<ProjectsState> = _state.asStateFlow()
}

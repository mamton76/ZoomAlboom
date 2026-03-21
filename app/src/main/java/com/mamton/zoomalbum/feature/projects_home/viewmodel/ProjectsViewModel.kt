package com.mamton.zoomalbum.feature.projects_home.viewmodel

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mamton.zoomalbum.core.mvi.Intent
import com.mamton.zoomalbum.core.mvi.State
import com.mamton.zoomalbum.domain.model.AlbumMeta
import com.mamton.zoomalbum.domain.repository.ProjectRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@Immutable
data class ProjectsState(
    val albums: List<AlbumMeta> = emptyList(),
    val isLoading: Boolean = true,
) : State

sealed interface ProjectsAction : Intent {
    data class CreateAlbum(val name: String) : ProjectsAction
    data class DeleteAlbum(val id: Long) : ProjectsAction
}

@HiltViewModel
class ProjectsViewModel @Inject constructor(
    private val projectRepository: ProjectRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ProjectsState())
    val state: StateFlow<ProjectsState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            projectRepository.observeAlbums().collect { albums ->
                _state.update { it.copy(albums = albums, isLoading = false) }
            }
        }
    }

    fun onAction(action: ProjectsAction) {
        when (action) {
            is ProjectsAction.CreateAlbum -> viewModelScope.launch {
                projectRepository.createAlbum(action.name)
            }
            is ProjectsAction.DeleteAlbum -> viewModelScope.launch {
                projectRepository.deleteAlbum(action.id)
            }
        }
    }
}

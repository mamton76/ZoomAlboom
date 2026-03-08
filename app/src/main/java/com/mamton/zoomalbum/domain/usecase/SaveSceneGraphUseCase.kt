package com.mamton.zoomalbum.domain.usecase

import com.mamton.zoomalbum.domain.model.CanvasNode
import com.mamton.zoomalbum.domain.repository.MediaRepository
import javax.inject.Inject

class SaveSceneGraphUseCase @Inject constructor(
    private val mediaRepository: MediaRepository,
) {
    suspend operator fun invoke(albumId: Long, nodes: List<CanvasNode>) {
        mediaRepository.saveSceneGraph(albumId, nodes)
    }
}

package com.mamton.zoomalbum.domain.repository

import com.mamton.zoomalbum.domain.model.CanvasNode

interface MediaRepository {
    suspend fun loadSceneGraph(albumId: Long): List<CanvasNode>
    suspend fun saveSceneGraph(albumId: Long, nodes: List<CanvasNode>)
}

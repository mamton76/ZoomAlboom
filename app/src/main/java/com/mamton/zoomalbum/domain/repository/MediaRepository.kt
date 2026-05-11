package com.mamton.zoomalbum.domain.repository

import com.mamton.zoomalbum.domain.model.SceneGraph

interface MediaRepository {
    suspend fun loadSceneGraph(albumId: Long): SceneGraph
    suspend fun saveSceneGraph(albumId: Long, sceneGraph: SceneGraph)
}

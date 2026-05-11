package com.mamton.zoomalbum.data.repository

import com.mamton.zoomalbum.data.local.file.FileStorageHelper
import com.mamton.zoomalbum.data.local.file.SceneGraphSerializer
import com.mamton.zoomalbum.domain.model.SceneGraph
import com.mamton.zoomalbum.domain.repository.MediaRepository
import javax.inject.Inject

class MediaRepositoryImpl @Inject constructor(
    private val serializer: SceneGraphSerializer,
    private val fileStorage: FileStorageHelper,
) : MediaRepository {

    override suspend fun loadSceneGraph(albumId: Long): SceneGraph {
        val raw = fileStorage.read(albumId) ?: return SceneGraph(albumId = albumId)
        return serializer.deserialize(raw, albumId)
    }

    override suspend fun saveSceneGraph(albumId: Long, sceneGraph: SceneGraph) {
        fileStorage.write(albumId, serializer.serialize(sceneGraph))
    }
}

package com.mamton.zoomalbum.data.repository

import com.mamton.zoomalbum.data.local.file.FileStorageHelper
import com.mamton.zoomalbum.data.local.file.SceneGraphSerializer
import com.mamton.zoomalbum.domain.model.CanvasNode
import com.mamton.zoomalbum.domain.repository.MediaRepository
import javax.inject.Inject

class MediaRepositoryImpl @Inject constructor(
    private val serializer: SceneGraphSerializer,
    private val fileStorage: FileStorageHelper,
) : MediaRepository {

    override suspend fun loadSceneGraph(albumId: Long): List<CanvasNode> {
        val raw = fileStorage.read(albumId) ?: return emptyList()
        return serializer.deserialize(raw)
    }

    override suspend fun saveSceneGraph(albumId: Long, nodes: List<CanvasNode>) {
        fileStorage.write(albumId, serializer.serialize(nodes))
    }
}

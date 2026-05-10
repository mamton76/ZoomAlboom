package com.mamton.zoomalbum.data.repository

import com.mamton.zoomalbum.data.local.file.FileStorageHelper
import com.mamton.zoomalbum.data.local.file.HistorySerializer
import com.mamton.zoomalbum.domain.repository.HistoryRepository
import com.mamton.zoomalbum.domain.undo.HistorySnapshot
import javax.inject.Inject

class HistoryRepositoryImpl @Inject constructor(
    private val serializer: HistorySerializer,
    private val fileStorage: FileStorageHelper,
) : HistoryRepository {

    override suspend fun load(albumId: Long): HistorySnapshot? {
        val raw = fileStorage.readHistory(albumId) ?: return null
        return runCatching { serializer.deserialize(raw) }.getOrNull()
    }

    override suspend fun save(albumId: Long, snapshot: HistorySnapshot) {
        fileStorage.writeHistory(albumId, serializer.serialize(snapshot))
    }
}

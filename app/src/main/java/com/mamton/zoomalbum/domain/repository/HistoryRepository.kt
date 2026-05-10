package com.mamton.zoomalbum.domain.repository

import com.mamton.zoomalbum.domain.undo.HistorySnapshot

interface HistoryRepository {
    suspend fun load(albumId: Long): HistorySnapshot?
    suspend fun save(albumId: Long, snapshot: HistorySnapshot)
}

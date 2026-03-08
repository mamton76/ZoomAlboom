package com.mamton.zoomalbum.domain.repository

import com.mamton.zoomalbum.domain.model.AlbumMeta
import kotlinx.coroutines.flow.Flow

interface ProjectRepository {
    fun observeAlbums(): Flow<List<AlbumMeta>>
    suspend fun createAlbum(name: String): Long
    suspend fun deleteAlbum(id: Long)
}

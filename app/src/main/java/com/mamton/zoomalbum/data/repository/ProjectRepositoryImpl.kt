package com.mamton.zoomalbum.data.repository

import com.mamton.zoomalbum.data.local.room.AlbumDao
import com.mamton.zoomalbum.data.local.room.AlbumEntity
import com.mamton.zoomalbum.domain.model.AlbumMeta
import com.mamton.zoomalbum.domain.repository.ProjectRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class ProjectRepositoryImpl @Inject constructor(
    private val albumDao: AlbumDao,
) : ProjectRepository {

    override fun observeAlbums(): Flow<List<AlbumMeta>> =
        albumDao.observeAll().map { entities ->
            entities.map { it.toDomain() }
        }

    override suspend fun createAlbum(name: String): Long {
        val now = System.currentTimeMillis()
        return albumDao.insert(
            AlbumEntity(name = name, createdAt = now, updatedAt = now, thumbnailPath = null)
        )
    }

    override suspend fun deleteAlbum(id: Long) = albumDao.deleteById(id)

    private fun AlbumEntity.toDomain() = AlbumMeta(
        id = id,
        name = name,
        createdAt = createdAt,
        updatedAt = updatedAt,
        thumbnailPath = thumbnailPath,
    )
}

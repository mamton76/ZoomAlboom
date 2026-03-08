package com.mamton.zoomalbum.data.local.room

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AlbumDao {

    @Query("SELECT * FROM albums ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<AlbumEntity>>

    @Insert
    suspend fun insert(album: AlbumEntity): Long

    @Query("DELETE FROM albums WHERE id = :id")
    suspend fun deleteById(id: Long)
}

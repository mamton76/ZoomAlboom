package com.mamton.zoomalbum.domain.model

data class AlbumMeta(
    val id: Long = 0L,
    val name: String = "New Album",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val thumbnailPath: String? = null,
)

package com.mamton.zoomalbum.domain.model


data class AlbumData(
    val meta: AlbumMeta,
    val nodes: List<CanvasNode>
)

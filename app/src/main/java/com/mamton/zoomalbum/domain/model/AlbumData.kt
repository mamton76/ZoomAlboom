// Copyright 2026 Yandex LLC. All rights reserved.


package com.mamton.zoomalbum.domain.model


data class AlbumData(
    val meta: AlbumMeta,
    val nodes: List<CanvasNode>
)

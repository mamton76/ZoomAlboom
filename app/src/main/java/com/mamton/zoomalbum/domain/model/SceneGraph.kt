package com.mamton.zoomalbum.domain.model

import com.mamton.zoomalbum.core.math.Camera
import kotlinx.serialization.Serializable

@Serializable
data class SceneGraph(
    val albumId: Long = 0L,
    val camera: Camera = Camera(),
    val nodes: List<CanvasNode> = emptyList(),
    val profile: AlbumPresentationProfile? = null,
)

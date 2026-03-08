package com.mamton.zoomalbum.domain.model

import kotlinx.serialization.Serializable

@Serializable
sealed class CanvasNode {
    abstract val id: String
    abstract val transform: Transform

    @Serializable
    data class Media(
        override val id: String,
        override val transform: Transform,
        val uri: String,
        val mediaType: MediaType = MediaType.IMAGE,
    ) : CanvasNode()

    @Serializable
    data class Frame(
        override val id: String,
        override val transform: Transform,
        val label: String = "",
        val childIds: List<String> = emptyList(),
    ) : CanvasNode()
}

@Serializable
enum class MediaType { IMAGE, VIDEO }

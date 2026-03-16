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
        val mediaRefId: String,
        val mediaType: MediaType = MediaType.IMAGE,
        val tags: List<String> = emptyList(),
    ) : CanvasNode()

    @Serializable
    data class Frame(
        override val id: String,
        override val transform: Transform,
        val label: String = "",
        val color: String = "#888888",
        val containsNodeIds: List<String> = emptyList(),
    ) : CanvasNode()
}

@Serializable
enum class MediaType { IMAGE, VIDEO, AUDIO }

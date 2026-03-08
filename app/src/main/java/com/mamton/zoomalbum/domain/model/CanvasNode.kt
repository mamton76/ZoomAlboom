package com.mamton.zoomalbum.domain.model

import kotlinx.serialization.Serializable

@Serializable
sealed class CanvasNode {
    abstract val id: String
    abstract val transform: Transform
    abstract val zIndex: Float

    @Serializable
    data class Media(
        override val id: String,
        override val transform: Transform,
        override val zIndex: Float = 0f,
        val uri: String,
        val mediaType: MediaType = MediaType.IMAGE,
    ) : CanvasNode()

    @Serializable
    data class Frame(
        override val id: String,
        override val transform: Transform,
        override val zIndex: Float = 0f,
        val label: String = "",
        val color: Long = 0xFF_88_88_88,
        val childIds: List<String> = emptyList(),
    ) : CanvasNode()
}

@Serializable
enum class MediaType { IMAGE, VIDEO }

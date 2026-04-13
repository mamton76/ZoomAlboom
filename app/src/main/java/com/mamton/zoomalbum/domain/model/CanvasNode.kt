package com.mamton.zoomalbum.domain.model

import kotlinx.serialization.Serializable

@Serializable
sealed class CanvasNode {
    abstract val id: String
    abstract val transform: Transform
    abstract val visibilityPolicy: VisibilityPolicy?

    @Serializable
    data class Media(
        override val id: String,
        override val transform: Transform,
        val mediaRefId: String,
        val mediaType: MediaType = MediaType.IMAGE,
        val tags: List<String> = emptyList(),
        override val visibilityPolicy: VisibilityPolicy? = null,
    ) : CanvasNode()

    @Serializable
    data class Frame(
        override val id: String,
        override val transform: Transform,
        val label: String = "",
        val color: String = "#888888",
        val containsNodeIds: List<String> = emptyList(),
        override val visibilityPolicy: VisibilityPolicy? = null,
    ) : CanvasNode()
}

@Serializable
enum class MediaType { IMAGE, VIDEO, AUDIO }

/** Returns a copy of this node with the given [transform], preserving all other fields. */
fun CanvasNode.withTransform(transform: Transform): CanvasNode = when (this) {
    is CanvasNode.Frame -> copy(transform = transform)
    is CanvasNode.Media -> copy(transform = transform)
}

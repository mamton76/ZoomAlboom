package com.mamton.zoomalbum.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class Transform(
    val x: Float = 0f,
    val y: Float = 0f,
    val width: Float = 100f,
    val height: Float = 100f,
    val scale: Float = 1f,
    val rotation: Float = 0f,
)

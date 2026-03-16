package com.mamton.zoomalbum.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class Transform(
    val x: Float = 0f,
    val y: Float = 0f,
    val w: Float = 100f,
    val h: Float = 100f,
    val scale: Float = 1f,
    val rotation: Float = 0f,
    val zIndex: Float = 0f,
)

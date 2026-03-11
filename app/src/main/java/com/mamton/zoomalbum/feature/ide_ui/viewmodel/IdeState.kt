package com.mamton.zoomalbum.feature.ide_ui.viewmodel

/**
 * Models consumed exclusively by the IDE overlay layer.
 * Kept separate from canvas state to prevent cross-recomposition.
 */
data class PanelDescriptor(
    val id: String,
    val title: String,
    val isVisible: Boolean = true,
    val isDocked: Boolean = false,
    val positionX: Float = 0f,
    val positionY: Float = 0f,
)

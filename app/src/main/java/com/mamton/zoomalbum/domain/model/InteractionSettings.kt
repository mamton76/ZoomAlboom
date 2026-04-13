package com.mamton.zoomalbum.domain.model

/**
 * User-togglable settings for canvas node interaction gestures.
 */
data class InteractionSettings(
    val rotationHandleEnabled: Boolean = true,
    val twoFingerNodeRotationEnabled: Boolean = true,
)

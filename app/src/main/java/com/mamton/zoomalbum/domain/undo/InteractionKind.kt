package com.mamton.zoomalbum.domain.undo

/**
 * Gesture-side classifier emitted by gesture detectors via
 * [com.mamton.zoomalbum.feature.canvas.viewmodel.CanvasAction.BeginInteraction].
 * Maps 1:1 to a subset of [CommandKind] at commit time via [toCommandKind].
 */
enum class InteractionKind {
    MOVE,
    RESIZE,
    ROTATE,
}

fun InteractionKind.toCommandKind(): CommandKind = when (this) {
    InteractionKind.MOVE -> CommandKind.MOVE
    InteractionKind.RESIZE -> CommandKind.RESIZE
    InteractionKind.ROTATE -> CommandKind.ROTATE
}

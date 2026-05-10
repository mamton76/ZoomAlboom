package com.mamton.zoomalbum.domain.undo

import kotlinx.serialization.Serializable

/** Serialized form of [CommandHistory] for persistence. */
@Serializable
data class HistorySnapshot(
    val version: Int = 1,
    val undo: List<CanvasCommand> = emptyList(),
    val redo: List<CanvasCommand> = emptyList(),
)

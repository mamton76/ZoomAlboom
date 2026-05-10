package com.mamton.zoomalbum.data.local.file

import com.mamton.zoomalbum.domain.model.CanvasNode
import com.mamton.zoomalbum.domain.model.Transform
import com.mamton.zoomalbum.domain.undo.CanvasCommand
import com.mamton.zoomalbum.domain.undo.CommandKind
import com.mamton.zoomalbum.domain.undo.HistorySnapshot
import org.junit.Assert.assertEquals
import org.junit.Test

class HistorySerializerTest {

    private val serializer = HistorySerializer()

    private fun frame(id: String, cx: Float = 0f, cy: Float = 0f): CanvasNode.Frame =
        CanvasNode.Frame(
            id = id,
            transform = Transform(cx = cx, cy = cy, w = 100f, h = 100f),
            label = id,
        )

    private fun media(id: String, cx: Float = 0f, cy: Float = 0f): CanvasNode.Media =
        CanvasNode.Media(
            id = id,
            transform = Transform(cx = cx, cy = cy, w = 100f, h = 100f),
            mediaRefId = "media_$id",
        )

    @Test
    fun `round-trip preserves an empty snapshot`() {
        val snap = HistorySnapshot()
        val raw = serializer.serialize(snap)
        val back = serializer.deserialize(raw)
        assertEquals(snap, back)
    }

    @Test
    fun `round-trip preserves an ADD command`() {
        val snap = HistorySnapshot(
            undo = listOf(
                CanvasCommand(
                    before = null,
                    after = listOf(frame("f1", cx = 10f)),
                    kind = CommandKind.ADD,
                    timestampMs = 1000L,
                ),
            ),
        )
        val back = serializer.deserialize(serializer.serialize(snap))
        assertEquals(snap, back)
    }

    @Test
    fun `round-trip preserves a DELETE command with beforeIndices`() {
        val snap = HistorySnapshot(
            undo = listOf(
                CanvasCommand(
                    before = listOf(frame("f1"), media("m1")),
                    after = null,
                    beforeIndices = listOf(2, 5),
                    kind = CommandKind.DELETE,
                    timestampMs = 2000L,
                ),
            ),
        )
        val back = serializer.deserialize(serializer.serialize(snap))
        assertEquals(snap, back)
    }

    @Test
    fun `round-trip preserves a MOVE command with paired before-after`() {
        val before = frame("f1", cx = 0f, cy = 0f)
        val after = frame("f1", cx = 50f, cy = 50f)
        val snap = HistorySnapshot(
            undo = listOf(
                CanvasCommand(
                    before = listOf(before),
                    after = listOf(after),
                    kind = CommandKind.MOVE,
                    timestampMs = 3000L,
                ),
            ),
            redo = listOf(
                CanvasCommand(
                    before = null,
                    after = listOf(media("m1")),
                    kind = CommandKind.DUPLICATE,
                    timestampMs = 4000L,
                ),
            ),
        )
        val back = serializer.deserialize(serializer.serialize(snap))
        assertEquals(snap, back)
    }
}

package com.mamton.zoomalbum.data.local.file

import com.mamton.zoomalbum.core.math.Camera
import com.mamton.zoomalbum.domain.model.CanvasNode
import com.mamton.zoomalbum.domain.model.SceneGraph
import com.mamton.zoomalbum.domain.model.Transform
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class SceneGraphSerializerTest {

    private val serializer = SceneGraphSerializer()

    // Mirror of the serializer's Json config — used to synthesize legacy bare-list JSON.
    private val legacyJson = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    private fun frame(id: String, cx: Float = 0f, cy: Float = 0f): CanvasNode.Frame =
        CanvasNode.Frame(
            id = id,
            transform = Transform(cx = cx, cy = cy, w = 100f, h = 100f),
            label = id,
        )

    @Test
    fun `deserialize old bare-list JSON migrates to SceneGraph using albumId param`() {
        val nodes: List<CanvasNode> = listOf(frame("f1", cx = 10f), frame("f2", cx = 20f))
        val bareList = legacyJson.encodeToString(nodes)

        val sg = serializer.deserialize(bareList, albumId = 42L)

        assertEquals(42L, sg.albumId)
        assertEquals(Camera(), sg.camera)
        assertEquals(2, sg.nodes.size)
        assertEquals("f1", sg.nodes[0].id)
        assertEquals("f2", sg.nodes[1].id)
        assertNull(sg.profile)
        assertNull(sg.background)
    }

    @Test
    fun `round-trip preserves SceneGraph with nodes and camera`() {
        val original = SceneGraph(
            albumId = 7L,
            camera = Camera(cx = 100f, cy = 200f, scale = 1.5f),
            nodes = listOf(frame("f1", cx = 5f), frame("f2", cx = 15f)),
        )

        val back = serializer.deserialize(serializer.serialize(original), albumId = 999L)

        // Root-object format: albumId comes from JSON, not the param.
        assertEquals(7L, back.albumId)
        assertEquals(original.camera, back.camera)
        assertEquals(original.nodes.size, back.nodes.size)
        assertEquals(original.nodes[0].id, back.nodes[0].id)
        assertNull(back.profile)
        assertNull(back.background)
    }

    @Test
    fun `deserialize tolerates root object without profile and background fields`() {
        // Simulates an older root-object album written before profile/background existed.
        val raw = """
            {
              "albumId": 5,
              "camera": { "cx": 0.0, "cy": 0.0, "scale": 1.0, "rotation": 0.0 },
              "nodes": []
            }
        """.trimIndent()

        val sg = serializer.deserialize(raw, albumId = 999L)

        assertEquals(5L, sg.albumId)
        assertEquals(0, sg.nodes.size)
        assertNull(sg.profile)
        assertNull(sg.background)
        assertNotNull(sg.camera)
    }
}

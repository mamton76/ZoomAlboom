package com.mamton.zoomalbum.data.local.file

import com.mamton.zoomalbum.core.math.Camera
import com.mamton.zoomalbum.domain.model.BackgroundData
import com.mamton.zoomalbum.domain.model.BorderStyle
import com.mamton.zoomalbum.domain.model.CanvasNode
import com.mamton.zoomalbum.domain.model.FrameAppearance
import com.mamton.zoomalbum.domain.model.FrameMembershipOverride
import com.mamton.zoomalbum.domain.model.MediaAppearance
import com.mamton.zoomalbum.domain.model.MediaFrameDecoration
import com.mamton.zoomalbum.domain.model.MediaFrameDecorationMode
import com.mamton.zoomalbum.domain.model.MembershipOrigin
import com.mamton.zoomalbum.domain.model.MembershipState
import com.mamton.zoomalbum.domain.model.NodeBlendMode
import com.mamton.zoomalbum.domain.model.OverlaySource
import com.mamton.zoomalbum.domain.model.OverlayStyle
import com.mamton.zoomalbum.domain.model.SceneGraph
import com.mamton.zoomalbum.domain.model.ShadowStyle
import com.mamton.zoomalbum.domain.model.Transform
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
    fun `round-trip preserves Frame overrides with state and origin per entry`() {
        val frameWithOverrides = CanvasNode.Frame(
            id = "f1",
            transform = Transform(cx = 0f, cy = 0f, w = 100f, h = 100f),
            overrides = mapOf(
                "pinned" to FrameMembershipOverride(
                    state = MembershipState.Included,
                    origin = MembershipOrigin.User,
                ),
                "detached" to FrameMembershipOverride(
                    state = MembershipState.Excluded,
                    origin = MembershipOrigin.User,
                ),
                "frozen-in" to FrameMembershipOverride(
                    state = MembershipState.Included,
                    origin = MembershipOrigin.RebindSuppressed,
                ),
                "wizard-included" to FrameMembershipOverride(
                    state = MembershipState.Included,
                    origin = MembershipOrigin.Wizard,
                ),
            ),
        )
        val original = SceneGraph(albumId = 1L, nodes = listOf(frameWithOverrides))

        val back = serializer.deserialize(serializer.serialize(original), albumId = 999L)

        val restored = back.nodes.single() as CanvasNode.Frame
        assertEquals(frameWithOverrides.overrides, restored.overrides)
    }

    @Test
    fun `round-trip preserves FrameAppearance background`() {
        val frame = CanvasNode.Frame(
            id = "f1",
            transform = Transform(cx = 0f, cy = 0f, w = 100f, h = 100f),
            appearance = FrameAppearance(
                background = BackgroundData.SolidBackgroundData(color = "#FF0000", opacity = 0.8f),
            ),
        )
        val original = SceneGraph(albumId = 1L, nodes = listOf(frame))

        val back = serializer.deserialize(serializer.serialize(original), albumId = 999L)

        val restored = back.nodes.single() as CanvasNode.Frame
        assertEquals(frame.appearance, restored.appearance)
        // The convenience accessor still resolves the migrated value.
        assertEquals(frame.background, restored.background)
    }

    /**
     * Build a legacy-shape JSON object for a frame: discriminator + fields produced by
     * the *current* serializer, then `background` moved out of `appearance` to the top
     * level — mimicking what was written before the appearance system existed.
     */
    private fun legacyFrameJsonObject(
        frame: CanvasNode.Frame,
        background: BackgroundData,
    ): JsonObject {
        val realJson = legacyJson.encodeToJsonElement(CanvasNode.serializer(), frame).jsonObject
        val bgJson = legacyJson.encodeToJsonElement(BackgroundData.serializer(), background)
        return buildJsonObject {
            for ((k, v) in realJson) {
                if (k == "appearance") continue  // drop the new container
                put(k, v)
            }
            put("background", bgJson)             // …and put background at the top level
        }
    }

    @Test
    fun `legacy top-level Frame background migrates into appearance background on read`() {
        val bareFrame = CanvasNode.Frame(
            id = "f1",
            transform = Transform(cx = 0f, cy = 0f, w = 100f, h = 100f),
        )
        val legacyBg = BackgroundData.SolidBackgroundData(color = "#00FF00", opacity = 1f)

        val rootJson = buildJsonObject {
            put("albumId", JsonPrimitive(5L))
            put("camera", legacyJson.encodeToJsonElement(Camera.serializer(), Camera()))
            put("nodes", buildJsonArray { add(legacyFrameJsonObject(bareFrame, legacyBg)) })
        }
        val legacyRaw = legacyJson.encodeToString(JsonObject.serializer(), rootJson)

        val sg = serializer.deserialize(legacyRaw, albumId = 999L)

        val restored = sg.nodes.single() as CanvasNode.Frame
        assertNotNull(restored.appearance)
        val bg = restored.appearance!!.background as BackgroundData.SolidBackgroundData
        assertEquals("#00FF00", bg.color)
        assertEquals(1f, bg.opacity, 0f)
        // Re-serializing should now write the migrated shape — appearance container present.
        val reserialized = serializer.serialize(sg)
        assertTrue(
            "Re-serialized JSON should nest the background under appearance",
            reserialized.contains("\"appearance\""),
        )
    }

    @Test
    fun `legacy Frame background in bare-list format also migrates`() {
        val bareFrame = CanvasNode.Frame(
            id = "f1",
            transform = Transform(cx = 0f, cy = 0f, w = 100f, h = 100f),
        )
        val legacyBg = BackgroundData.SolidBackgroundData(color = "#0000FF")

        val bareList: JsonArray = buildJsonArray { add(legacyFrameJsonObject(bareFrame, legacyBg)) }
        val legacyRaw = legacyJson.encodeToString(JsonArray.serializer(), bareList)

        val sg = serializer.deserialize(legacyRaw, albumId = 42L)

        assertEquals(42L, sg.albumId)
        val restored = sg.nodes.single() as CanvasNode.Frame
        val bg = restored.appearance?.background as BackgroundData.SolidBackgroundData
        assertEquals("#0000FF", bg.color)
    }

    @Test
    fun `round-trip preserves MediaAppearance with overlays border shadow and decoration`() {
        val media = CanvasNode.Media(
            id = "m1",
            transform = Transform(cx = 0f, cy = 0f, w = 200f, h = 200f),
            mediaRefId = "asset-1",
            appearance = MediaAppearance(
                opacity = 0.9f,
                cornerRadius = 8f,
                border = BorderStyle(color = "#FFAA00", widthPx = 3f, opacity = 0.8f),
                shadow = ShadowStyle(
                    color = "#000000", opacity = 0.4f,
                    offsetX = 4f, offsetY = 6f, blurRadius = 12f,
                ),
                overlays = listOf(
                    OverlayStyle(
                        source = OverlaySource.SolidColor(color = "#80000000"),
                        opacity = 0.3f,
                        blendMode = NodeBlendMode.Multiply,
                    ),
                    OverlayStyle(
                        source = OverlaySource.Texture(textureRefId = "grain-1"),
                        opacity = 0.5f,
                        blendMode = NodeBlendMode.Screen,
                    ),
                ),
                frameDecoration = MediaFrameDecoration(
                    assetUri = "polaroid.png",
                    mode = MediaFrameDecorationMode.NineSlice,
                    sliceLeft = 12f, sliceTop = 12f, sliceRight = 12f, sliceBottom = 80f,
                ),
            ),
        )
        val original = SceneGraph(albumId = 11L, nodes = listOf(media))

        val back = serializer.deserialize(serializer.serialize(original), albumId = 999L)

        val restored = back.nodes.single() as CanvasNode.Media
        assertEquals(media.appearance, restored.appearance)
    }

    @Test
    fun `round-trip preserves OverlayStyle on FrameAppearance overlays`() {
        val frame = CanvasNode.Frame(
            id = "f1",
            transform = Transform(cx = 0f, cy = 0f, w = 100f, h = 100f),
            appearance = FrameAppearance(
                overlays = listOf(
                    OverlayStyle(
                        source = OverlaySource.SolidColor("#40FFFFFF"),
                        opacity = 0.25f,
                        blendMode = NodeBlendMode.SoftLight,
                    ),
                ),
            ),
        )
        val original = SceneGraph(albumId = 12L, nodes = listOf(frame))

        val back = serializer.deserialize(serializer.serialize(original), albumId = 999L)

        val restored = back.nodes.single() as CanvasNode.Frame
        assertEquals(frame.appearance, restored.appearance)
    }

    @Test
    fun `legacy contentOverlays on frame appearance migrates to overlays`() {
        // Pre-unification format: FrameAppearance JSON with `contentOverlays` key
        // instead of the unified `overlays` key. Migration lifts the value
        // verbatim. See SceneGraphSerializer.migrateAppearanceContentOverlays.
        val raw = """
            {
              "albumId": 7,
              "camera": { "cx": 0.0, "cy": 0.0, "scale": 1.0, "rotation": 0.0 },
              "nodes": [
                {
                  "type": "com.mamton.zoomalbum.domain.model.CanvasNode.Frame",
                  "id": "f1",
                  "transform": { "cx": 0.0, "cy": 0.0, "w": 100.0, "h": 100.0 },
                  "appearance": {
                    "contentOverlays": [
                      {
                        "source": { "type": "SolidColor", "color": "#40FFFFFF" },
                        "opacity": 0.25,
                        "blendMode": "SoftLight"
                      }
                    ]
                  }
                }
              ]
            }
        """.trimIndent()

        val sg = serializer.deserialize(raw, albumId = 999L)

        val restored = sg.nodes.single() as CanvasNode.Frame
        val appearance = restored.appearance!!
        assertEquals(1, appearance.overlays.size)
        assertEquals(0.25f, appearance.overlays[0].opacity)
        assertEquals(NodeBlendMode.SoftLight, appearance.overlays[0].blendMode)
    }

    @Test
    fun `legacy contentOverlays migration drops the legacy key when overlays is already present`() {
        // Both keys present (shouldn't happen in practice — only one writer ever
        // emitted either). The migration prefers the new key and discards legacy.
        val raw = """
            {
              "albumId": 7,
              "camera": { "cx": 0.0, "cy": 0.0, "scale": 1.0, "rotation": 0.0 },
              "nodes": [
                {
                  "type": "com.mamton.zoomalbum.domain.model.CanvasNode.Frame",
                  "id": "f1",
                  "transform": { "cx": 0.0, "cy": 0.0, "w": 100.0, "h": 100.0 },
                  "appearance": {
                    "overlays": [
                      {
                        "source": { "type": "SolidColor", "color": "#11111111" },
                        "opacity": 0.1,
                        "blendMode": "Normal"
                      }
                    ],
                    "contentOverlays": [
                      {
                        "source": { "type": "SolidColor", "color": "#FF000000" },
                        "opacity": 0.9,
                        "blendMode": "Multiply"
                      }
                    ]
                  }
                }
              ]
            }
        """.trimIndent()

        val sg = serializer.deserialize(raw, albumId = 999L)

        val restored = sg.nodes.single() as CanvasNode.Frame
        val appearance = restored.appearance!!
        assertEquals(1, appearance.overlays.size)
        assertEquals(0.1f, appearance.overlays[0].opacity)
        assertEquals(NodeBlendMode.Normal, appearance.overlays[0].blendMode)
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

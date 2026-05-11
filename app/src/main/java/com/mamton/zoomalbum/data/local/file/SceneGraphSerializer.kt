package com.mamton.zoomalbum.data.local.file

import com.mamton.zoomalbum.core.math.Camera
import com.mamton.zoomalbum.domain.model.CanvasNode
import com.mamton.zoomalbum.domain.model.SceneGraph
import kotlinx.serialization.json.Json
import javax.inject.Inject

class SceneGraphSerializer @Inject constructor() {

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    fun serialize(sceneGraph: SceneGraph): String = json.encodeToString(sceneGraph)

    fun deserialize(raw: String, albumId: Long): SceneGraph {
        // Migration: old format was a bare List<CanvasNode> (starts with '[').
        return if (raw.trimStart().startsWith('[')) {
            val nodes: List<CanvasNode> = json.decodeFromString(raw)
            SceneGraph(albumId = albumId, camera = Camera(), nodes = nodes)
        } else {
            json.decodeFromString(raw)
        }
    }
}

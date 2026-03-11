package com.mamton.zoomalbum.data.local.file

import com.mamton.zoomalbum.domain.model.CanvasNode
import kotlinx.serialization.json.Json
import javax.inject.Inject

class SceneGraphSerializer @Inject constructor() {

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    fun serialize(nodes: List<CanvasNode>): String = json.encodeToString(nodes)

    fun deserialize(raw: String): List<CanvasNode> = json.decodeFromString(raw)
}

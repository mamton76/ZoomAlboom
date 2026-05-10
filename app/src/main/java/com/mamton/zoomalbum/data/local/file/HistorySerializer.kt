package com.mamton.zoomalbum.data.local.file

import com.mamton.zoomalbum.domain.undo.HistorySnapshot
import kotlinx.serialization.json.Json
import javax.inject.Inject

class HistorySerializer @Inject constructor() {

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    fun serialize(snapshot: HistorySnapshot): String = json.encodeToString(snapshot)

    fun deserialize(raw: String): HistorySnapshot = json.decodeFromString(raw)
}

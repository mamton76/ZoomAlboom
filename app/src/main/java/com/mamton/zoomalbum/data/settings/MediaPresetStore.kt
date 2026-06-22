package com.mamton.zoomalbum.data.settings

import android.content.Context
import com.mamton.zoomalbum.domain.model.MediaStylePreset
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * App-level (cross-album) store of [MediaStylePreset]s. SharedPreferences-backed
 * (one JSON string), mirroring `InteractionSettingsRepository`. Not on the canvas
 * undo stack — preset-definition edits are app-global (see media-presets.md § 8).
 */
@Singleton
class MediaPresetStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    private val _presets = MutableStateFlow(load())
    val presets: StateFlow<List<MediaStylePreset>> = _presets.asStateFlow()

    val presetsById: Map<String, MediaStylePreset> get() = _presets.value.associateBy { it.id }

    fun add(preset: MediaStylePreset) = persist(_presets.value + preset)

    fun update(preset: MediaStylePreset) =
        persist(_presets.value.map { if (it.id == preset.id) preset else it })

    fun delete(id: String) = persist(_presets.value.filterNot { it.id == id })

    /** Copies an existing preset under a new id + "… copy" name; returns it (or null if not found). */
    fun duplicate(id: String): MediaStylePreset? {
        val src = _presets.value.firstOrNull { it.id == id } ?: return null
        val copy = src.copy(id = UUID.randomUUID().toString(), name = "${src.name} copy")
        persist(_presets.value + copy)
        return copy
    }

    private fun persist(list: List<MediaStylePreset>) {
        _presets.value = list
        prefs.edit().putString(KEY_PRESETS, json.encodeToString(SERIALIZER, list)).apply()
    }

    private fun load(): List<MediaStylePreset> {
        val raw = prefs.getString(KEY_PRESETS, null) ?: return emptyList()
        return runCatching { json.decodeFromString(SERIALIZER, raw) }.getOrDefault(emptyList())
    }

    companion object {
        private const val PREFS_NAME = "media_presets"
        private const val KEY_PRESETS = "presets_json"
        private val SERIALIZER = ListSerializer(MediaStylePreset.serializer())
    }
}

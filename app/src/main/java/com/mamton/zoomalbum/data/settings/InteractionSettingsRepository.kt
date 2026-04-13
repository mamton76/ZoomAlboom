package com.mamton.zoomalbum.data.settings

import android.content.Context
import com.mamton.zoomalbum.domain.model.InteractionSettings
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class InteractionSettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(load())
    val settings: Flow<InteractionSettings> = _settings.asStateFlow()

    val current: InteractionSettings get() = _settings.value

    fun setRotationHandleEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ROTATION_HANDLE, enabled).apply()
        _settings.value = _settings.value.copy(rotationHandleEnabled = enabled)
    }

    fun setTwoFingerNodeRotationEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_TWO_FINGER_ROTATION, enabled).apply()
        _settings.value = _settings.value.copy(twoFingerNodeRotationEnabled = enabled)
    }

    private fun load(): InteractionSettings = InteractionSettings(
        rotationHandleEnabled = prefs.getBoolean(KEY_ROTATION_HANDLE, true),
        twoFingerNodeRotationEnabled = prefs.getBoolean(KEY_TWO_FINGER_ROTATION, true),
    )

    companion object {
        private const val PREFS_NAME = "interaction_settings"
        private const val KEY_ROTATION_HANDLE = "rotation_handle_enabled"
        private const val KEY_TWO_FINGER_ROTATION = "two_finger_node_rotation_enabled"
    }
}

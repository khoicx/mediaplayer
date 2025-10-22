package com.khoicx.mediaplayer

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

class SettingsManager(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("MediaPlayerSettings", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_DEVICE_ENABLED = "device_enabled"
        private const val KEY_SERVER_ENABLED = "server_enabled"
        private const val KEY_SUGGESTION_MODE = "suggestion_mode"
        private const val KEY_REPEAT_MODE = "repeat_mode"
    }

    fun saveSourceSettings(isDeviceEnabled: Boolean, isServerEnabled: Boolean) {
        prefs.edit {
            putBoolean(KEY_DEVICE_ENABLED, isDeviceEnabled)
                .putBoolean(KEY_SERVER_ENABLED, isServerEnabled)
        }
    }

    fun getIsDeviceEnabled(): Boolean = prefs.getBoolean(KEY_DEVICE_ENABLED, true) // Default to true

    fun getIsServerEnabled(): Boolean = prefs.getBoolean(KEY_SERVER_ENABLED, false)

    fun saveSuggestionMode(isEnabled: Boolean) {
        prefs.edit { putBoolean(KEY_SUGGESTION_MODE, isEnabled) }
    }

    fun getIsSuggestionMode(): Boolean = prefs.getBoolean(KEY_SUGGESTION_MODE, false)

    fun saveRepeatMode(mode: RepeatMode) {
        prefs.edit { putString(KEY_REPEAT_MODE, mode.name) }
    }

    fun getRepeatMode(): RepeatMode {
        val modeName = prefs.getString(KEY_REPEAT_MODE, RepeatMode.OFF.name)
        return RepeatMode.valueOf(modeName ?: RepeatMode.OFF.name)
    }
}

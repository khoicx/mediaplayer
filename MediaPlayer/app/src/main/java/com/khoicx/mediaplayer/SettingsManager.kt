package com.khoicx.mediaplayer

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.core.content.edit
import kotlinx.serialization.json.Json

class SettingsManager(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("MediaPlayerSettings", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_DEVICE_ENABLED = "device_enabled"
        private const val KEY_SERVER_ENABLED = "server_enabled"
        private const val KEY_SUGGESTION_MODE = "suggestion_mode"
        private const val KEY_SUGGESTION_BY_LOCATION = "suggestion_by_location"
        private const val KEY_REPEAT_MODE = "repeat_mode"
        private const val KEY_PLAYLIST = "playlist"
        private const val KEY_CURRENT_SONG_INDEX = "current_song_index"
    }

    fun saveSourceSettings(isDeviceEnabled: Boolean, isServerEnabled: Boolean) {
        prefs.edit {
            putBoolean(KEY_DEVICE_ENABLED, isDeviceEnabled)
            putBoolean(KEY_SERVER_ENABLED, isServerEnabled)
        }
    }

    fun getIsDeviceEnabled(): Boolean = prefs.getBoolean(KEY_DEVICE_ENABLED, true) // Default to true

    fun getIsServerEnabled(): Boolean = prefs.getBoolean(KEY_SERVER_ENABLED, false)

    fun saveSuggestionMode(isEnabled: Boolean) {
        prefs.edit { putBoolean(KEY_SUGGESTION_MODE, isEnabled) }
    }

    fun getIsSuggestionMode(): Boolean = prefs.getBoolean(KEY_SUGGESTION_MODE, false)

    fun saveSuggestionByLocation(isEnabled: Boolean) {
        prefs.edit { putBoolean(KEY_SUGGESTION_BY_LOCATION, isEnabled) }
    }

    fun getIsSuggestionByLocation(): Boolean = prefs.getBoolean(KEY_SUGGESTION_BY_LOCATION, false)

    fun saveRepeatMode(mode: RepeatMode) {
        prefs.edit { putString(KEY_REPEAT_MODE, mode.name) }
    }

    fun getRepeatMode(): RepeatMode {
        val modeName = prefs.getString(KEY_REPEAT_MODE, RepeatMode.OFF.name)
        return RepeatMode.valueOf(modeName ?: RepeatMode.OFF.name)
    }

    fun savePlaylist(songs: List<Song>) {
        val jsonString = Json.encodeToString(songs)
        prefs.edit { putString(KEY_PLAYLIST, jsonString) }
    }

    fun getPlaylist(): List<Song> {
        val jsonString = prefs.getString(KEY_PLAYLIST, null)
        return if (jsonString != null) {
            try {
                Json.decodeFromString<List<Song>>(jsonString)
            } catch (e: Exception) {
                Log.e("SettingsManager", "Failed to decode playlist from JSON", e)
                emptyList()
            }
        } else {
            emptyList()
        }
    }

    fun saveCurrentSongIndex(index: Int) {
        prefs.edit { putInt(KEY_CURRENT_SONG_INDEX, index) }
    }

    fun getCurrentSongIndex(): Int = prefs.getInt(KEY_CURRENT_SONG_INDEX, -1)
}

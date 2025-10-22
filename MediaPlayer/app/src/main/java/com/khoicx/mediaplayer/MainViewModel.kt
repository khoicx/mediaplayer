package com.khoicx.mediaplayer

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val settingsManager = SettingsManager(application)

    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
            })
        }
        install(Logging) {
            level = LogLevel.ALL
        }
    }

    private val songRepository = SongRepository(application, client)

    private val _accessToken = MutableStateFlow<String?>(null)
    val accessToken: StateFlow<String?> = _accessToken.asStateFlow()

    private val _songs = MutableStateFlow<List<Song>>(emptyList())
    val songs: StateFlow<List<Song>> = _songs.asStateFlow()

    private val _playingSongIndex = MutableStateFlow(-1)
    val playingSongIndex: StateFlow<Int> = _playingSongIndex.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _isSuggestionMode = MutableStateFlow(settingsManager.getIsSuggestionMode())
    val isSuggestionMode: StateFlow<Boolean> = _isSuggestionMode.asStateFlow()

    private val _repeatMode = MutableStateFlow(settingsManager.getRepeatMode())
    val repeatMode: StateFlow<RepeatMode> = _repeatMode.asStateFlow()

    val isDeviceEnabled = MutableStateFlow(settingsManager.getIsDeviceEnabled())
    val isServerEnabled = MutableStateFlow(settingsManager.getIsServerEnabled())

    // Store the actual song object to keep focus across list updates
    private var currentPlayingSong: Song? = null

    fun loadSongs(fromDevice: Boolean, fromServer: Boolean, suggestionMode: Boolean) {
        settingsManager.saveSourceSettings(fromDevice, fromServer)
        settingsManager.saveSuggestionMode(suggestionMode)
        _isSuggestionMode.value = suggestionMode

        viewModelScope.launch {
            _error.value = null // Reset error on new load
            val combinedSongs = mutableListOf<Song>()

            if (suggestionMode) {
                try {
                    if (_accessToken.value == null) {
                        loginAndSetToken()
                    }
                    _accessToken.value?.let { token ->
                        combinedSongs.addAll(songRepository.getSuggestionSongs(token))
                    }
                } catch (e: Exception) {
                    Log.e("MainViewModel", "Failed to get suggestion songs or login", e)
                    _error.value = "Server error: ${e.message}"
                }
            } else {
                if (fromDevice) {
                    combinedSongs.addAll(songRepository.getDeviceSongs())
                }

                if (fromServer) {
                    try {
                        if (_accessToken.value == null) {
                            loginAndSetToken()
                        }
                        _accessToken.value?.let { token ->
                            combinedSongs.addAll(songRepository.getServerSongs(token))
                        }
                    } catch (e: Exception) {
                        Log.e("MainViewModel", "Failed to get server songs or login", e)
                        _error.value = "Server error: ${e.message}"
                    }
                }
            }

            _songs.value = combinedSongs

            // After updating the list, find the new index of the currently playing song
            _playingSongIndex.value = if (currentPlayingSong != null) {
                combinedSongs.indexOf(currentPlayingSong)
            } else {
                -1
            }
        }
    }

    fun onSongSelected(index: Int) {
        if (index >= 0 && index < _songs.value.size) {
            currentPlayingSong = _songs.value[index]
            _playingSongIndex.value = index
        } else {
            currentPlayingSong = null
            _playingSongIndex.value = -1
        }
    }

    fun shuffleSongs() {
        val shuffledList = _songs.value.shuffled()
        _songs.value = shuffledList
        _playingSongIndex.value = if (currentPlayingSong != null) {
            shuffledList.indexOf(currentPlayingSong)
        } else {
            -1
        }
    }

    fun toggleRepeatMode() {
        val newMode = when (_repeatMode.value) {
            RepeatMode.OFF -> RepeatMode.REPEAT_ALL
            RepeatMode.REPEAT_ALL -> RepeatMode.REPEAT_ONE
            RepeatMode.REPEAT_ONE -> RepeatMode.OFF
        }
        settingsManager.saveRepeatMode(newMode)
        _repeatMode.value = newMode
    }

    private suspend fun loginAndSetToken() {
        val loginResponse = client.post(MainActivity.LOGIN_URL) {
            contentType(ContentType.Application.Json)
            setBody(LoginRequest(username = BuildConfig.API_USER, password = BuildConfig.API_PASS))
        }
        val tokenResponse: TokenResponse = loginResponse.body()
        _accessToken.value = tokenResponse.token
    }

    fun onErrorShown() {
        _error.value = null
    }

    override fun onCleared() {
        super.onCleared()
        client.close()
    }
}

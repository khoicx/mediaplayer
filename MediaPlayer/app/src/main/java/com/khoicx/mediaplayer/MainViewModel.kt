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
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

/**
 * The main ViewModel for the application.
 *
 * This class is responsible for managing the app's UI state, handling user interactions,
 * and coordinating data flow between the UI, the [MusicPlayer], and the [SongRepository].
 */
class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val settingsManager = SettingsManager(application)
    private val musicPlayer = MusicPlayer(application) { playNextSong(false) }

    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
        install(Logging) { level = LogLevel.ALL }
    }

    private val songRepository = SongRepository(application, client)

    private val _accessToken = MutableStateFlow<String?>(null)

    private val _songs = MutableStateFlow<List<Song>>(emptyList())
    /**
     * The current list of songs being displayed.
     */
    val songs: StateFlow<List<Song>> = _songs.asStateFlow()

    private val _playingSongIndex = MutableStateFlow(-1)
    /**
     * The index of the currently playing song in the list.
     */
    val playingSongIndex: StateFlow<Int> = _playingSongIndex.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    /**
     * A flow that emits error messages to be displayed to the user.
     */
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _showNoSongsToast = MutableStateFlow(false)
    /**
     * A flow that signals when a "No songs found" message should be shown.
     */
    val showNoSongsToast: StateFlow<Boolean> = _showNoSongsToast.asStateFlow()

    private val _isSuggestionMode = MutableStateFlow(settingsManager.getIsSuggestionMode())
    /**
     * Whether the app is currently in "Suggestion" mode.
     */
    val isSuggestionMode: StateFlow<Boolean> = _isSuggestionMode.asStateFlow()

    private val _repeatMode = MutableStateFlow(settingsManager.getRepeatMode())
    /**
     * The current repeat mode (e.g., OFF, REPEAT_ALL, REPEAT_ONE).
     */
    val repeatMode: StateFlow<RepeatMode> = _repeatMode.asStateFlow()

    /**
     * Whether the "Device" source is currently enabled.
     */
    private val _isDeviceEnabled = MutableStateFlow(settingsManager.getIsDeviceEnabled())
    val isDeviceEnabled: StateFlow<Boolean> = _isDeviceEnabled.asStateFlow()

    /**
     * Whether the "Server" source is currently enabled.
     */
    private val _isServerEnabled = MutableStateFlow(settingsManager.getIsServerEnabled())
    val isServerEnabled: StateFlow<Boolean> = _isServerEnabled.asStateFlow()

    /**
     * The current state of the underlying music player.
     */
    val playerState: StateFlow<PlayerState> = musicPlayer.playerState
    /**
     * The current playback progress in milliseconds.
     */
    val progress: StateFlow<Int> = musicPlayer.progress

    init {
        repeatMode.onEach { mode ->
            musicPlayer.isLooping = mode == RepeatMode.REPEAT_ONE
        }.launchIn(viewModelScope)

        // Load initial state
        val savedSongs = settingsManager.getPlaylist()
        if (savedSongs.isNotEmpty()) {
            _songs.value = savedSongs
            val savedIndex = settingsManager.getCurrentSongIndex()
            if (savedIndex != -1) {
                onSongSelected(savedIndex, play = false) // Select the song but don't play automatically
            }
        }
    }

    /**
     * Loads the song list based on the current source and suggestion settings.
     *
     * @param fromDevice Whether to load songs from the device.
     * @param fromServer Whether to load songs from the server.
     * @param suggestionMode Whether to apply the suggestion filter to the loaded songs.
     */
    fun loadSongs(fromDevice: Boolean, fromServer: Boolean, suggestionMode: Boolean) {
        settingsManager.saveSourceSettings(fromDevice, fromServer)
        settingsManager.saveSuggestionMode(suggestionMode)
        _isDeviceEnabled.value = fromDevice
        _isServerEnabled.value = fromServer
        _isSuggestionMode.value = suggestionMode

        viewModelScope.launch {
            _error.value = null
            val currentPlayingSong = (playerState.value as? PlayerState.Playing)?.song ?: (playerState.value as? PlayerState.Paused)?.song

            val combinedSongs = mutableListOf<Song>()
            if (fromDevice) combinedSongs.addAll(songRepository.getDeviceSongs())
            if (fromServer) {
                try {
                    if (_accessToken.value == null) loginAndSetToken()
                    _accessToken.value?.let { token -> combinedSongs.addAll(songRepository.getServerSongs(token)) }
                } catch (e: Exception) {
                    Log.e("MainViewModel", "Failed to get server songs or login", e)
                    _error.value = "Server error: ${e.message}"
                }
            }

            val resultingSongs = applySuggestionFilter(combinedSongs)
            _songs.value = resultingSongs
            settingsManager.savePlaylist(resultingSongs)

            if (resultingSongs.isEmpty() && (fromDevice || fromServer)) _showNoSongsToast.value = true

            val newIndex = currentPlayingSong?.let { resultingSongs.indexOf(it) } ?: -1
            _playingSongIndex.value = newIndex
            settingsManager.saveCurrentSongIndex(newIndex)
        }
    }

    private fun applySuggestionFilter(songs: List<Song>): List<Song> {
        if (!_isSuggestionMode.value || songs.isEmpty()) return songs
        if (settingsManager.getIsSuggestionByLocation()) {
            Log.d("MainViewModel", "Applying suggestion filter by location")
            return songs.shuffled().take(20)
        }
        return songs.shuffled()
    }

    /**
     * Handles a user's selection of a song from the list.
     *
     * @param index The index of the selected song.
     * @param play Whether to start playback immediately.
     */
    fun onSongSelected(index: Int, play: Boolean = true) {
        if (index >= 0 && index < _songs.value.size) {
            val song = _songs.value[index]
            _playingSongIndex.value = index
            settingsManager.saveCurrentSongIndex(index)
            if (play) {
                playSong(song)
            }
        } else {
            musicPlayer.release()
            _playingSongIndex.value = -1
            settingsManager.saveCurrentSongIndex(-1)
        }
    }

    /**
     * Toggles the playback state between playing and paused.
     */
    fun togglePlayPause() {
        when (val currentState = playerState.value) {
            is PlayerState.Playing -> musicPlayer.pause()
            is PlayerState.Paused -> playSong(currentState.song)
            else -> {
                val songToPlay = _songs.value.getOrNull(playingSongIndex.value)
                if (songToPlay != null) {
                    playSong(songToPlay)
                }
            }
        }
    }

    /**
     * The single entry point for starting playback. Ensures a login token exists
     * before playing a server song.
     */
    private fun playSong(song: Song) {
        viewModelScope.launch {
            if (song.source == SongSource.SERVER && _accessToken.value == null) {
                try {
                    loginAndSetToken()
                } catch (e: Exception) {
                    Log.e("MainViewModel", "Login failed", e)
                    _error.value = "Login failed: ${e.message}"
                    return@launch // Stop playback if login fails
                }
            }
            // Now we can safely play
            musicPlayer.play(song, _accessToken.value)
        }
    }

    /**
     * Plays the next song in the list.
     *
     * @param forceNext If true, will skip to the next song even if repeat mode is OFF at the end of a playlist.
     */
    fun playNextSong(forceNext: Boolean = false) {
        if (_songs.value.isEmpty()) return
        val nextIndex = (_playingSongIndex.value + 1) % _songs.value.size
        if (!forceNext && repeatMode.value == RepeatMode.OFF && nextIndex == 0) {
            musicPlayer.release()
            _playingSongIndex.value = -1
            settingsManager.saveCurrentSongIndex(-1)
            return
        }
        onSongSelected(nextIndex)
    }

    /**
     * Plays the previous song in the list.
     */
    fun playPreviousSong() {
        if (_songs.value.isEmpty()) return
        val newIndex = if (_playingSongIndex.value - 1 < 0) _songs.value.size - 1 else _playingSongIndex.value - 1
        onSongSelected(newIndex)
    }

    /**
     * Seeks the current song to a specific progress point.
     *
     * @param progress The progress to seek to, in milliseconds.
     */
    fun seekTo(progress: Int) = musicPlayer.seekTo(progress)

    /**
     * Shuffles the current playlist.
     */
    fun shuffleSongs() {
        val currentSong = (playerState.value as? PlayerState.Playing)?.song ?: (playerState.value as? PlayerState.Paused)?.song
        val shuffledList = _songs.value.shuffled()
        _songs.value = shuffledList
        settingsManager.savePlaylist(shuffledList)
        val newIndex = currentSong?.let { shuffledList.indexOf(it) } ?: -1
        _playingSongIndex.value = newIndex
        settingsManager.saveCurrentSongIndex(newIndex)
    }

    /**
     * Cycles through the available repeat modes (OFF, REPEAT_ALL, REPEAT_ONE).
     */
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

    /**
     * To be called by the UI after an error message has been shown.
     */
    fun onErrorShown() { _error.value = null }
    /**
     * To be called by the UI after the "No songs found" message has been shown.
     */
    fun onNoSongsToastShown() { _showNoSongsToast.value = false }

    override fun onCleared() {
        super.onCleared()
        musicPlayer.release()
        client.close()
    }
}

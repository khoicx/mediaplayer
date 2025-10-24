package com.khoicx.mediaplayer

import android.content.Context
import android.media.MediaPlayer
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import android.os.Handler
import android.os.Looper

/**
 * Manages media playback using Android's [MediaPlayer].
 *
 * This class encapsulates all the logic for playing, pausing, and seeking,
 * and exposes the player's state and progress through observable [StateFlow]s.
 *
 * @param context The application context.
 * @param onCompletion A lambda function to be invoked when a track finishes playing.
 */
class MusicPlayer(private val context: Context, private val onCompletion: () -> Unit) {

    private var mediaPlayer: MediaPlayer? = null
    private val handler = Handler(Looper.getMainLooper())

    private val _playerState = MutableStateFlow<PlayerState>(PlayerState.Idle)
    /**
     * The current state of the media player (e.g., Idle, Playing, Paused).
     */
    val playerState: StateFlow<PlayerState> = _playerState.asStateFlow()

    private val _progress = MutableStateFlow(0)
    /**
     * The current playback progress in milliseconds.
     */
    val progress: StateFlow<Int> = _progress.asStateFlow()

    /**
     * Whether the current track should loop indefinitely.
     */
    var isLooping: Boolean = false
        set(value) {
            field = value
            mediaPlayer?.isLooping = value
        }

    /**
     * Plays a given song.
     *
     * If the song is already paused, it will resume. If it's a new song,
     * it will release the previous player and start playback for the new one.
     *
     * @param song The [Song] to play.
     * @param accessToken The access token required for playing songs from the server.
     */
    fun play(song: Song, accessToken: String?) {
        val currentState = _playerState.value
        if (currentState is PlayerState.Paused && currentState.song.uri == song.uri) {
            mediaPlayer?.start()
            _playerState.value = PlayerState.Playing(song, currentState.duration)
            updateSeekBar()
            return
        }

        mediaPlayer?.release()
        _playerState.value = PlayerState.Loading(song)

        val mp = try {
            if (song.source == SongSource.SERVER) {
                MediaPlayer().apply {
                    val headers = mutableMapOf<String, String>()
                    if (accessToken != null) {
                        headers["Authorization"] = "Token $accessToken"
                    }
                    setDataSource(context, song.uri, headers)
                    prepareAsync() // Asynchronous preparation
                }
            } else {
                MediaPlayer.create(context, song.uri) // Synchronous preparation
            }
        } catch (e: Exception) {
            Log.e("MusicPlayer", "Failed to create MediaPlayer", e)
            _playerState.value = PlayerState.Error("Failed to load song.")
            null
        }

        mediaPlayer = mp ?: return

        // Set common listeners for both paths
        mediaPlayer?.setOnErrorListener { _, _, _ ->
            _playerState.value = PlayerState.Error("Playback failed.")
            true
        }
        mediaPlayer?.setOnCompletionListener {
            if (!isLooping) {
                onCompletion()
            }
        }

        // Handle the appropriate setup based on how the player was created
        if (song.source == SongSource.SERVER) {
            // For server songs, setup is done when the player is prepared
            mediaPlayer?.setOnPreparedListener { preparedPlayer ->
                _playerState.value = PlayerState.Playing(song, preparedPlayer.duration)
                preparedPlayer.isLooping = isLooping
                preparedPlayer.start()
                updateSeekBar()
            }
        } else {
            // For local songs, MediaPlayer.create() returns a prepared player
            mediaPlayer?.let { preparedPlayer ->
                _playerState.value = PlayerState.Playing(song, preparedPlayer.duration)
                preparedPlayer.isLooping = isLooping
                preparedPlayer.start()
                updateSeekBar()
            }
        }
    }

    /**
     * Pauses the currently playing song.
     */
    fun pause() {
        if (playerState.value is PlayerState.Playing) {
            val playingState = playerState.value as PlayerState.Playing
            mediaPlayer?.pause()
            _playerState.value = PlayerState.Paused(playingState.song, playingState.duration)
            handler.removeCallbacks(seekBarUpdater)
        }
    }

    /**
     * Seeks the playback to a specific position.
     *
     * @param progress The position to seek to, in milliseconds.
     */
    fun seekTo(progress: Int) {
        mediaPlayer?.seekTo(progress)
    }

    /**
     * Releases the [MediaPlayer] instance and cleans up resources.
     *
     * This should be called when the player is no longer needed, such as in `onDestroy`.
     */
    fun release() {
        mediaPlayer?.release()
        mediaPlayer = null
        _playerState.value = PlayerState.Idle
        handler.removeCallbacks(seekBarUpdater)
    }

    private val seekBarUpdater = object : Runnable {
        override fun run() {
            mediaPlayer?.let {
                if (it.isPlaying) {
                    _progress.value = it.currentPosition
                    handler.postDelayed(this, 1000)
                }
            }
        }
    }

    private fun updateSeekBar() {
        handler.post(seekBarUpdater)
    }
}
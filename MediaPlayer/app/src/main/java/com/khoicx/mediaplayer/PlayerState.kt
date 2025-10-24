package com.khoicx.mediaplayer

/**
 * Represents the various states the MusicPlayer can be in.
 */
sealed class PlayerState {
    /** The player is idle and no media is loaded. */
    object Idle : PlayerState()

    /** The player is actively playing a song. */
    data class Playing(val song: Song, val duration: Int) : PlayerState()

    /** The player is paused. */
    data class Paused(val song: Song, val duration: Int) : PlayerState()

    /** An error has occurred. */
    data class Error(val message: String) : PlayerState()

    /** The player is loading media but not yet ready to play. */
    data class Loading(val song: Song) : PlayerState()
}

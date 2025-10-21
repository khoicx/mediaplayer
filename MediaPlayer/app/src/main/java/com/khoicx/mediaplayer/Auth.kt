package com.khoicx.mediaplayer

import kotlinx.serialization.Serializable

@Serializable
data class LoginRequest(val username: String, val password: String)

@Serializable
data class TokenResponse(val token: String)

@Serializable
data class MusicData(
    val files: List<Song>
)

@Serializable
data class SongListResponse(
    val status: String,
    val data: MusicData
)

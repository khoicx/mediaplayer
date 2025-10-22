package com.khoicx.mediaplayer

import android.net.Uri
import kotlinx.serialization.Serializable

@Serializable
data class Song(
    @Serializable(with = UriSerializer::class)
    val uri: Uri,
    val title: String,
    val source: SongSource
)

enum class SongSource {
    DEVICE,
    SERVER
}

package com.khoicx.mediaplayer

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Song(
    @SerialName("name") val title: String,
    val url: String
)

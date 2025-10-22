package com.khoicx.mediaplayer

import kotlinx.serialization.Serializable

@Serializable
data class SongListResponse(val data: SongListData)

@Serializable
data class SongListData(val files: List<SongFile>)

@Serializable
data class SongFile(val url: String, val name: String? = null)

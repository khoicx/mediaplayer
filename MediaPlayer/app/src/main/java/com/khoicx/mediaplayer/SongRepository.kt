package com.khoicx.mediaplayer

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import androidx.core.net.toUri
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.headers
import java.net.URLDecoder

class SongRepository(private val context: Context, private val client: HttpClient) {

    fun getDeviceSongs(): List<Song> {
        val songs = mutableListOf<Song>()
        val projection = arrayOf(MediaStore.Audio.Media._ID, MediaStore.Audio.Media.DISPLAY_NAME)
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        val sortOrder = "${MediaStore.Audio.Media.DISPLAY_NAME} ASC"

        context.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, projection, selection, null, sortOrder
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val name = cursor.getString(nameColumn)
                val contentUri: Uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
                songs.add(Song(contentUri, name.substringBeforeLast('.'), SongSource.DEVICE))
            }
        }
        return songs
    }

    suspend fun getServerSongs(accessToken: String): List<Song> {
        val songsResponse = client.get(MainActivity.MUSICS_URL) {
            headers {
                append("Authorization", "Token $accessToken")
            }
        }
        val songListResponse: SongListResponse = songsResponse.body()
        return songListResponse.data.files.map { songFile ->
            val onceDecoded = URLDecoder.decode(songFile.url, "UTF-8")
            val twiceDecoded = URLDecoder.decode(onceDecoded, "UTF-8")
            val songUri = twiceDecoded.toUri()

            val title = if (songFile.name.isNullOrBlank()) {
                songUri.lastPathSegment?.substringBeforeLast('.') ?: "Unknown Title"
            } else {
                songFile.name
            }

            Song(songUri, title, SongSource.SERVER)
        }
    }

    suspend fun getSuggestionSongs(accessToken: String): List<Song> {
        val songsResponse = client.get(MainActivity.SUGGESTIONS_URL) {
            headers {
                append("Authorization", "Token $accessToken")
            }
        }
        val songListResponse: SongListResponse = songsResponse.body()
        return songListResponse.data.files.map { songFile ->
            val onceDecoded = URLDecoder.decode(songFile.url, "UTF-8")
            val twiceDecoded = URLDecoder.decode(onceDecoded, "UTF-8")
            val songUri = twiceDecoded.toUri()

            val title = if (songFile.name.isNullOrBlank()) {
                songUri.lastPathSegment?.substringBeforeLast('.') ?: "Unknown Title"
            } else {
                songFile.name
            }

            Song(songUri, title, SongSource.SERVER)
        }
    }
}

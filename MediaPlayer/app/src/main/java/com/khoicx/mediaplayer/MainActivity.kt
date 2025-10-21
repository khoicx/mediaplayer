package com.khoicx.mediaplayer

import android.Manifest
import android.content.ContentUris
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.khoicx.mediaplayer.databinding.ActivityMainBinding
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.net.URLDecoder
import androidx.core.net.toUri

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var mediaPlayer: MediaPlayer? = null
    private val songs = mutableListOf<Uri>()
    private val songNames = mutableListOf<String>()
    private var currentSongIndex = 0
    private var isFromServer = false
    private var accessToken: String? = null

    private val client by lazy {
        HttpClient(CIO) {
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                })
            }
            install(Logging) {
                level = LogLevel.ALL
            }
        }
    }

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                initializeSongList()
            } else {
                Toast.makeText(this, R.string.error_permission_denied, Toast.LENGTH_LONG).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupRecyclerView()
        setupButtonClickListeners()
        setupSourceSelectionListener()

        if (binding.sourceSelectionGroup.checkedRadioButtonId == R.id.radio_button_device) {
            checkAndRequestPermission()
        } else {
            initializeServerSongList()
        }
    }

    private fun setupSourceSelectionListener() {
        binding.sourceSelectionGroup.setOnCheckedChangeListener { _, checkedId ->
            when (checkedId) {
                R.id.radio_button_device -> {
                    isFromServer = false
                    checkAndRequestPermission()
                }

                R.id.radio_button_server -> {
                    isFromServer = true
                    initializeServerSongList()
                }
            }
        }
    }

    private fun checkAndRequestPermission() {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

        when {
            ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED -> {
                initializeSongList()
            }

            else -> {
                requestPermissionLauncher.launch(permission)
            }
        }
    }

    private fun initializeSongList() {
        songs.clear()
        songNames.clear()

        val projection = arrayOf(MediaStore.Audio.Media._ID, MediaStore.Audio.Media.DISPLAY_NAME)
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        val sortOrder = "${MediaStore.Audio.Media.DISPLAY_NAME} ASC"

        contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, projection, selection, null, sortOrder
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val name = cursor.getString(nameColumn)
                val contentUri: Uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
                songs.add(contentUri)
                songNames.add(name)
            }
        }

        if (songs.isEmpty()) {
            Toast.makeText(this, R.string.error_no_music_found, Toast.LENGTH_LONG).show()
        }
        updateRecyclerView()
    }

    private fun initializeServerSongList() {
        lifecycleScope.launch {
            try {
                loginAndFetchSongs()
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, getString(R.string.error_failed_to_fetch_songs, e.message), Toast.LENGTH_LONG).show()
                binding.sourceSelectionGroup.check(R.id.radio_button_device)
            }
        }
    }

    private suspend fun loginAndFetchSongs() {
        if (accessToken == null) {
            val loginResponse = client.post(LOGIN_URL) {
                contentType(ContentType.Application.Json)
                setBody(LoginRequest(BuildConfig.API_USER, BuildConfig.API_PASS))
            }
            val tokenResponse: TokenResponse = loginResponse.body()
            accessToken = tokenResponse.token
        }

        val songsResponse = client.get(MUSICS_URL) {
            headers {
                append(HttpHeaders.Authorization, "Token $accessToken")
            }
        }
        val songListResponse: SongListResponse = songsResponse.body()
        processFetchedSongs(songListResponse.data.files)
    }

    private fun processFetchedSongs(fetchedSongs: List<Song>) {
        songs.clear()
        songNames.clear()
        fetchedSongs.forEach {
            val onceDecoded = URLDecoder.decode(it.url, "UTF-8")
            val twiceDecoded = URLDecoder.decode(onceDecoded, "UTF-8")
            songs.add(twiceDecoded.toUri())
            songNames.add(it.title)
        }
        updateRecyclerView()
    }

    private fun setupRecyclerView() {
        binding.recyclerViewSongs.layoutManager = LinearLayoutManager(this)
        updateRecyclerView()
    }

    private fun updateRecyclerView() {
        val adapter = SongAdapter(songNames) { position ->
            currentSongIndex = position
            startPlaying()
        }
        binding.recyclerViewSongs.adapter = adapter
    }

    private fun setupButtonClickListeners() {
        binding.buttonPlay.setOnClickListener { togglePlayPause() }
        binding.buttonNext.setOnClickListener { playNextSong() }
        binding.buttonPrevious.setOnClickListener { playPreviousSong() }
    }

    private fun togglePlayPause() {
        if (songs.isEmpty()) return

        if (mediaPlayer?.isPlaying == true) {
            mediaPlayer?.pause()
            binding.buttonPlay.setImageResource(R.drawable.ic_play)
        } else {
            if (mediaPlayer == null) {
                startPlaying()
            } else {
                mediaPlayer?.start()
            }
            binding.buttonPlay.setImageResource(R.drawable.ic_pause)
        }
    }

    private fun playNextSong() {
        if (songs.isEmpty()) return
        currentSongIndex = (currentSongIndex + 1) % songs.size
        startPlaying()
    }

    private fun playPreviousSong() {
        if (songs.isEmpty()) return
        currentSongIndex = if (currentSongIndex - 1 < 0) songs.size - 1 else currentSongIndex - 1
        startPlaying()
    }

    private fun startPlaying() {
        if (songs.isEmpty()) return

        mediaPlayer?.release()

        if (isFromServer) {
            try {
                title = getString(R.string.state_loading, songNames[currentSongIndex])
                binding.buttonPlay.isEnabled = false

                mediaPlayer = MediaPlayer().apply {
                    val headers = mutableMapOf<String, String>()
                    headers["Authorization"] = "Token $accessToken"

                    setDataSource(this@MainActivity, songs[currentSongIndex], headers)

                    setOnPreparedListener { mp ->
                        binding.buttonPlay.isEnabled = true
                        binding.buttonPlay.setImageResource(R.drawable.ic_pause)
                        title = getString(R.string.state_playing, songNames[currentSongIndex])
                        mp.start()
                    }

                    setOnErrorListener { _, what, extra ->
                        binding.buttonPlay.isEnabled = true
                        binding.buttonPlay.setImageResource(R.drawable.ic_play)
                        title = getString(R.string.state_error)
                        val errorMsg = getString(R.string.error_playback_failed, what, extra)
                        Log.e("MediaPlayerError", errorMsg)
                        Toast.makeText(this@MainActivity, errorMsg, Toast.LENGTH_LONG).show()
                        true
                    }

                    setOnCompletionListener { playNextSong() }

                    prepareAsync()
                }
            } catch (e: Exception) {
                binding.buttonPlay.isEnabled = true
                binding.buttonPlay.setImageResource(R.drawable.ic_play)
                Toast.makeText(this@MainActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        } else {
            mediaPlayer = MediaPlayer.create(this, songs[currentSongIndex])
            mediaPlayer?.start()
            binding.buttonPlay.setImageResource(R.drawable.ic_pause)
            title = getString(R.string.state_playing, songNames[currentSongIndex])
            mediaPlayer?.setOnCompletionListener { playNextSong() }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaPlayer?.release()
        mediaPlayer = null
        client.close()
    }

    companion object {
        private const val BASE_URL = "http://192.168.1.6:8000"
        private const val LOGIN_URL = "$BASE_URL/api/auth/"
        private const val MUSICS_URL = "$BASE_URL/api/musics/"
    }
}
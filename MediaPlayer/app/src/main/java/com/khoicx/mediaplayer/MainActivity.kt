package com.khoicx.mediaplayer

import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.khoicx.mediaplayer.databinding.ActivityMainBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var mediaPlayer: MediaPlayer? = null
    private val viewModel: MainViewModel by viewModels()
    private lateinit var songs: List<Song>
    private var accessToken: String? = null
    private lateinit var songAdapter: SongAdapter

    // URI of the currently playing song to prevent unnecessary restarts
    private var currentPlayingUri: Uri? = null

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                updateSongList()
            } else {
                // User denied permission, so uncheck the box and update the list accordingly
                binding.switchDevice.isChecked = false
                Toast.makeText(this, R.string.error_permission_denied, Toast.LENGTH_LONG).show()
                updateSongList()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupRecyclerView()
        setupButtonClickListeners()
        setupSourceSelectionListener()
        observeViewModel()

        // Set initial state from ViewModel
        binding.switchDevice.isChecked = viewModel.isDeviceEnabled.value
        binding.switchServer.isChecked = viewModel.isServerEnabled.value
        binding.switchSuggestion.isChecked = viewModel.isSuggestionMode.value

        updateSongList()
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            viewModel.songs.collectLatest { songList ->
                songs = songList
                if (songs.isEmpty() && (binding.switchDevice.isChecked || binding.switchServer.isChecked)) {
                    Toast.makeText(this@MainActivity, R.string.error_no_music_found, Toast.LENGTH_LONG).show()
                }
                songAdapter.submitList(songs.map { it.title })
            }
        }
        lifecycleScope.launch {
            viewModel.accessToken.collectLatest { token ->
                accessToken = token
            }
        }
        lifecycleScope.launch {
            viewModel.playingSongIndex.collectLatest { index ->
                songAdapter.updatePlayingIndex(index)
                if (index >= 0) {
                    binding.recyclerViewSongs.smoothScrollToPosition(index)
                    startPlaying()
                } else {
                    // When song is removed from list, stop playback and clean up
                    mediaPlayer?.stop()
                    mediaPlayer?.release()
                    mediaPlayer = null
                    currentPlayingUri = null
                    binding.buttonPlay.setImageResource(R.drawable.ic_play)
                    title = getString(R.string.app_name) // Reset the title
                }
            }
        }
        lifecycleScope.launch {
            viewModel.error.collectLatest { error ->
                if (error != null) {
                    Toast.makeText(this@MainActivity, error, Toast.LENGTH_LONG).show()
                    viewModel.onErrorShown()
                }
            }
        }
        lifecycleScope.launch {
            viewModel.repeatMode.collectLatest { repeatMode ->
                mediaPlayer?.isLooping = repeatMode == RepeatMode.REPEAT_ONE
                when (repeatMode) {
                    RepeatMode.OFF -> {
                        binding.buttonRepeat.setImageResource(R.drawable.ic_repeat_off)
                        binding.buttonRepeat.imageTintList = ContextCompat.getColorStateList(this@MainActivity, android.R.color.white)
                    }
                    RepeatMode.REPEAT_ALL -> {
                        binding.buttonRepeat.setImageResource(R.drawable.ic_repeat_all)
                        binding.buttonRepeat.imageTintList = ContextCompat.getColorStateList(this@MainActivity, R.color.accent_color)
                    }
                    RepeatMode.REPEAT_ONE -> {
                        binding.buttonRepeat.setImageResource(R.drawable.ic_repeat_one)
                        binding.buttonRepeat.imageTintList = ContextCompat.getColorStateList(this@MainActivity, R.color.accent_color)
                    }
                }
            }
        }
    }

    private fun setupSourceSelectionListener() {
        binding.switchDevice.setOnCheckedChangeListener { _, _ -> updateSongList() }
        binding.switchServer.setOnCheckedChangeListener { _, _ -> updateSongList() }
        binding.switchSuggestion.setOnCheckedChangeListener { _, isChecked ->
            binding.switchDevice.isEnabled = !isChecked
            binding.switchServer.isEnabled = !isChecked
            binding.buttonShuffle.isEnabled = !isChecked
            updateSongList()
        }
        binding.buttonShuffle.setOnClickListener { viewModel.shuffleSongs() }
        binding.buttonRepeat.setOnClickListener { viewModel.toggleRepeatMode() }
    }

    private fun updateSongList() {
        val wantsDevice = binding.switchDevice.isChecked
        val wantsServer = binding.switchServer.isChecked
        val wantsSuggestion = binding.switchSuggestion.isChecked

        if (wantsDevice) {
            val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                Manifest.permission.READ_MEDIA_AUDIO
            } else {
                Manifest.permission.READ_EXTERNAL_STORAGE
            }

            if (ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED) {
                viewModel.loadSongs(wantsDevice, wantsServer, wantsSuggestion)
            } else {
                requestPermissionLauncher.launch(permission)
            }
        } else {
            // No device songs requested, no permission needed.
            viewModel.loadSongs(false, wantsServer, wantsSuggestion)
        }
    }

    private fun setupRecyclerView() {
        songAdapter = SongAdapter { position ->
            viewModel.onSongSelected(position)
        }
        binding.recyclerViewSongs.layoutManager = LinearLayoutManager(this)
        binding.recyclerViewSongs.adapter = songAdapter
    }


    private fun setupButtonClickListeners() {
        binding.buttonPlay.setOnClickListener { togglePlayPause() }
        binding.buttonNext.setOnClickListener { playNextSong(true) } // Force next song on button click
        binding.buttonPrevious.setOnClickListener { playPreviousSong() }
    }

    private fun togglePlayPause() {
        if (!::songs.isInitialized || songs.isEmpty()) return

        if (mediaPlayer?.isPlaying == true) {
            mediaPlayer?.pause()
            binding.buttonPlay.setImageResource(R.drawable.ic_play)
        } else {
            if (mediaPlayer == null && viewModel.playingSongIndex.value >= 0) {
                startPlaying()
            } else {
                mediaPlayer?.start()
                binding.buttonPlay.setImageResource(R.drawable.ic_pause)
            }
        }
    }

    private fun playNextSong(forceNext: Boolean = false) {
        if (!::songs.isInitialized || songs.isEmpty()) return

        val nextIndex = (viewModel.playingSongIndex.value + 1) % songs.size

        if (!forceNext && viewModel.repeatMode.value == RepeatMode.OFF && nextIndex == 0) {
            // Stop playback if we've reached the end of the list and repeat is off
            mediaPlayer?.stop()
            mediaPlayer?.release()
            mediaPlayer = null
            currentPlayingUri = null
            binding.buttonPlay.setImageResource(R.drawable.ic_play)
            title = getString(R.string.app_name)
            viewModel.onSongSelected(-1)
            return
        }

        viewModel.onSongSelected(nextIndex)
    }

    private fun playPreviousSong() {
        if (!::songs.isInitialized || songs.isEmpty()) return
        val previousIndex = if (viewModel.playingSongIndex.value - 1 < 0) songs.size - 1 else viewModel.playingSongIndex.value - 1
        viewModel.onSongSelected(previousIndex)
    }

    private fun startPlaying() {
        val currentIndex = viewModel.playingSongIndex.value
        if (!::songs.isInitialized || songs.isEmpty() || currentIndex < 0) return

        val song = songs[currentIndex]

        // If a media player already exists for this song's URI, do nothing.
        // This prevents the track from restarting and preserves its state (playing/paused).
        if (song.uri == currentPlayingUri && mediaPlayer != null) {
            return
        }

        mediaPlayer?.release()
        currentPlayingUri = song.uri // Track the new song's URI

        if (song.source == SongSource.SERVER) {
            try {
                title = getString(R.string.state_loading, song.title)
                binding.buttonPlay.isEnabled = false

                mediaPlayer = MediaPlayer().apply {
                    isLooping = viewModel.repeatMode.value == RepeatMode.REPEAT_ONE
                    val headers = mutableMapOf<String, String>()
                    if (accessToken != null) {
                        headers["Authorization"] = "Token $accessToken"
                    }

                    setDataSource(this@MainActivity, song.uri, headers)

                    setOnPreparedListener { mp ->
                        binding.buttonPlay.isEnabled = true
                        binding.buttonPlay.setImageResource(R.drawable.ic_pause)
                        title = getString(R.string.state_playing, song.title)
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
            mediaPlayer = MediaPlayer.create(this, song.uri)
            mediaPlayer?.isLooping = viewModel.repeatMode.value == RepeatMode.REPEAT_ONE
            mediaPlayer?.start()
            binding.buttonPlay.setImageResource(R.drawable.ic_pause)
            title = getString(R.string.state_playing, song.title)
            mediaPlayer?.setOnCompletionListener { playNextSong() }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaPlayer?.release()
        mediaPlayer = null
        currentPlayingUri = null
    }

    companion object {
        const val BASE_URL = "http://192.168.1.100:8000"
        const val LOGIN_URL = "$BASE_URL/api/auth/"
        const val MUSICS_URL = "$BASE_URL/api/musics/"
        const val SUGGESTIONS_URL = "$BASE_URL/api/musics/suggestions/"
    }
}

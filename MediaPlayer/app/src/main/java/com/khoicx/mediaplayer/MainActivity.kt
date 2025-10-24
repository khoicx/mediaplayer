package com.khoicx.mediaplayer

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.SeekBar
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
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * The main screen of the application, responsible for displaying the song list and playback controls.
 *
 * This activity observes the [MainViewModel] for state updates and delegates all user actions
 * to the ViewModel.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()
    private lateinit var songAdapter: SongAdapter

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                updateSongList()
            } else {
                binding.switchDevice.isChecked = false
                Toast.makeText(this, R.string.error_permission_denied, Toast.LENGTH_LONG).show()
                updateSongList()
            }
        }

    private val suggestionConfigLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (viewModel.isSuggestionMode.value) {
            updateSongList()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Set initial UI state from the ViewModel BEFORE attaching any listeners
        binding.switchDevice.isChecked = viewModel.isDeviceEnabled.value
        binding.switchServer.isChecked = viewModel.isServerEnabled.value
        binding.switchSuggestion.isChecked = viewModel.isSuggestionMode.value

        // Now that the UI is in the correct state, set up the rest
        setupRecyclerView()
        setupButtonClickListeners()
        setupSourceSelectionListener() // This attaches the OnCheckedChangeListeners
        setupSeekBarListener()
        observeViewModel()

        // Do NOT trigger an initial load here. The ViewModel handles restoring state.
    }

    /**
     * Observes all the StateFlows from the [MainViewModel] and updates the UI accordingly.
     */
    private fun observeViewModel() {
        lifecycleScope.launch {
            viewModel.songs.collectLatest { songs ->
                songAdapter.submitList(songs.map { it.title })
            }
        }
        lifecycleScope.launch {
            viewModel.playingSongIndex.collectLatest { index ->
                songAdapter.updatePlayingIndex(index)
                if (index >= 0) binding.recyclerViewSongs.smoothScrollToPosition(index)
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
            viewModel.showNoSongsToast.collectLatest { show ->
                if (show) {
                    Toast.makeText(this@MainActivity, R.string.error_no_music_found, Toast.LENGTH_LONG).show()
                    viewModel.onNoSongsToastShown()
                }
            }
        }
        lifecycleScope.launch {
            viewModel.repeatMode.collectLatest { repeatMode ->
                updateRepeatButton(repeatMode)
            }
        }
        lifecycleScope.launch {
            viewModel.playerState.collectLatest { state ->
                updatePlayerState(state)
            }
        }
        lifecycleScope.launch {
            viewModel.progress.collectLatest { progress ->
                binding.seekBar.progress = progress
                binding.textCurrentTime.text = formatDuration(progress)
            }
        }
    }

    /**
     * Updates the player UI based on the current [PlayerState].
     */
    private fun updatePlayerState(state: PlayerState) {
        when (state) {
            is PlayerState.Playing -> {
                binding.buttonPlay.setImageResource(R.drawable.ic_pause)
                binding.buttonPlay.isEnabled = true
                title = getString(R.string.state_playing, state.song.title)
                binding.seekBar.max = state.duration
                binding.textTotalDuration.text = formatDuration(state.duration)
            }
            is PlayerState.Paused -> {
                binding.buttonPlay.setImageResource(R.drawable.ic_play)
                binding.buttonPlay.isEnabled = true
            }
            is PlayerState.Loading -> {
                binding.buttonPlay.isEnabled = false
                title = getString(R.string.state_loading, state.song.title)
            }
            is PlayerState.Error -> {
                binding.buttonPlay.isEnabled = true
                binding.buttonPlay.setImageResource(R.drawable.ic_play)
                title = getString(R.string.state_error)
                Toast.makeText(this, state.message, Toast.LENGTH_LONG).show()
            }
            is PlayerState.Idle -> {
                binding.buttonPlay.setImageResource(R.drawable.ic_play)
                binding.buttonPlay.isEnabled = true
                title = getString(R.string.app_name)
                binding.seekBar.progress = 0
                binding.textCurrentTime.text = getString(R.string.default_time)
                binding.textTotalDuration.text = getString(R.string.default_time)
            }
        }
    }

    /**
     * Updates the repeat button icon and tint based on the current [RepeatMode].
     */
    private fun updateRepeatButton(repeatMode: RepeatMode) {
        when (repeatMode) {
            RepeatMode.OFF -> {
                binding.buttonRepeat.setImageResource(R.drawable.ic_repeat_off)
                binding.buttonRepeat.imageTintList = ContextCompat.getColorStateList(this, android.R.color.white)
            }
            RepeatMode.REPEAT_ALL -> {
                binding.buttonRepeat.setImageResource(R.drawable.ic_repeat_all)
                binding.buttonRepeat.imageTintList = ContextCompat.getColorStateList(this, R.color.accent_color)
            }
            RepeatMode.REPEAT_ONE -> {
                binding.buttonRepeat.setImageResource(R.drawable.ic_repeat_one)
                binding.buttonRepeat.imageTintList = ContextCompat.getColorStateList(this, R.color.accent_color)
            }
        }
    }

    /**
     * Sets up the listeners for the source selection switches and buttons.
     */
    private fun setupSourceSelectionListener() {
        binding.switchDevice.setOnCheckedChangeListener { _, _ -> updateSongList() }
        binding.switchServer.setOnCheckedChangeListener { _, _ -> updateSongList() }
        binding.textSuggestionLabel.setOnClickListener {
            suggestionConfigLauncher.launch(Intent(this, SuggestionConfigActivity::class.java))
        }
        binding.switchSuggestion.setOnCheckedChangeListener { _, _ -> updateSongList() }
        binding.buttonShuffle.setOnClickListener { viewModel.shuffleSongs() }
        binding.buttonRepeat.setOnClickListener { viewModel.toggleRepeatMode() }
    }

    /**
     * Triggers the ViewModel to load the song list based on the current UI settings.
     */
    private fun updateSongList() {
        val wantsDevice = binding.switchDevice.isChecked
        val wantsServer = binding.switchServer.isChecked
        val wantsSuggestion = binding.switchSuggestion.isChecked

        if (wantsDevice) {
            val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE
            if (ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED) {
                viewModel.loadSongs(wantsDevice, wantsServer, wantsSuggestion)
            } else {
                requestPermissionLauncher.launch(permission)
            }
        } else {
            viewModel.loadSongs(false, wantsServer, wantsSuggestion)
        }
    }

    /**
     * Sets up the RecyclerView and its adapter.
     */
    private fun setupRecyclerView() {
        songAdapter = SongAdapter { position ->
            if (position == viewModel.playingSongIndex.value) {
                viewModel.togglePlayPause()
            } else {
                viewModel.onSongSelected(position)
            }
        }
        binding.recyclerViewSongs.layoutManager = LinearLayoutManager(this)
        binding.recyclerViewSongs.adapter = songAdapter
    }

    /**
     * Sets up the listener for the seek bar.
     */
    private fun setupSeekBarListener() {
        binding.seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) viewModel.seekTo(progress)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    /**
     * Sets up the listeners for the main playback control buttons.
     */
    private fun setupButtonClickListeners() {
        binding.buttonPlay.setOnClickListener { viewModel.togglePlayPause() }
        binding.buttonNext.setOnClickListener { viewModel.playNextSong(true) }
        binding.buttonPrevious.setOnClickListener { viewModel.playPreviousSong() }
    }

    /**
     * Formats a duration in milliseconds to a "minutes:seconds" string.
     */
    private fun formatDuration(duration: Int): String {
        val minutes = TimeUnit.MILLISECONDS.toMinutes(duration.toLong())
        val seconds = TimeUnit.MILLISECONDS.toSeconds(duration.toLong()) - TimeUnit.MINUTES.toSeconds(minutes)
        return String.format(Locale.US, "%d:%02d", minutes, seconds)
    }

    override fun onDestroy() {
        super.onDestroy()
    }

    companion object {
        const val BASE_URL = "http://192.168.1.100:8000"
        const val LOGIN_URL = "$BASE_URL/api/auth/"
        const val MUSICS_URL = "$BASE_URL/api/musics/"
    }
}

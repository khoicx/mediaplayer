package com.khoicx.mediaplayer

import android.Manifest
import android.content.ContentUris
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.khoicx.mediaplayer.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding

    private var mediaPlayer: MediaPlayer? = null
    private var songs = mutableListOf<Uri>()
    private var songNames = mutableListOf<String>()
    private var currentSongIndex = 0

    // Trình xử lý yêu cầu quyền
    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                initializeSongList()
                setupRecyclerView()
            } else {
                Toast.makeText(this, "Quyền truy cập bộ nhớ bị từ chối!", Toast.LENGTH_LONG).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, 0)
            binding.buttonContainer.setPadding(0, 0, 0, systemBars.bottom)
            insets
        }

        checkAndRequestPermission()
        setupButtonClickListeners()
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
                setupRecyclerView()
            }
            else -> {
                requestPermissionLauncher.launch(permission)
            }
        }
    }

    private fun initializeSongList() {
        songs.clear()
        songNames.clear()

        // Các cột cần truy vấn từ MediaStore
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.DISPLAY_NAME
        )

        // Chỉ lấy các file nhạc từ thư mục Music
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"

        // Sắp xếp theo tên hiển thị
        val sortOrder = "${MediaStore.Audio.Media.DISPLAY_NAME} ASC"

        // Bắt đầu truy vấn
        contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            null,
            sortOrder
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val name = cursor.getString(nameColumn)
                // Tạo Uri cho từng file nhạc
                val contentUri: Uri = ContentUris.withAppendedId(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    id
                )
                // Thêm vào danh sách
                songs.add(contentUri)
                songNames.add(name)
            }
        }

        if (songs.isEmpty()) {
            Toast.makeText(this, "Không tìm thấy file nhạc nào trong thư mục Music", Toast.LENGTH_LONG).show()
        }
    }

    private fun setupRecyclerView() {
        if (songNames.isNotEmpty()) {
            val adapter = SongAdapter(songNames) { position ->
                currentSongIndex = position
                startPlaying()
            }
            binding.recyclerViewSongs.layoutManager = LinearLayoutManager(this)
            binding.recyclerViewSongs.adapter = adapter
        }
    }

    private fun setupButtonClickListeners() {
        binding.buttonPlay.setOnClickListener {
            togglePlayPause()
        }
        binding.buttonNext.setOnClickListener {
            playNextSong()
        }
        binding.buttonPrevious.setOnClickListener {
            playPreviousSong()
        }
    }

    private fun togglePlayPause() {
        if (songs.isEmpty()) return

        if (mediaPlayer?.isPlaying == true) {
            mediaPlayer?.pause()
            "@string/play_play".also { binding.buttonPlay.text = it }
        } else {
            if (mediaPlayer == null) {
                startPlaying()
            } else {
                mediaPlayer?.start()
            }
            "@string/play_pause".also { binding.buttonPlay.text = it }
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
        mediaPlayer = MediaPlayer.create(this, songs[currentSongIndex])
        mediaPlayer?.start()
        "@string/play_pause".also { binding.buttonPlay.text = it }

        title = "Playing: ${songNames[currentSongIndex]}"

        mediaPlayer?.setOnCompletionListener {
            playNextSong()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaPlayer?.release()
        mediaPlayer = null
    }
}

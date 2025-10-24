package com.khoicx.mediaplayer

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.khoicx.mediaplayer.databinding.ActivitySuggestionConfigBinding

class SuggestionConfigActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySuggestionConfigBinding
    private lateinit var settingsManager: SettingsManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySuggestionConfigBinding.inflate(layoutInflater)
        setContentView(binding.root)

        settingsManager = SettingsManager(this)

        // Enable the back button in the action bar
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        // Load the saved setting and set the switch state
        binding.switchLocationSuggestion.isChecked = settingsManager.getIsSuggestionByLocation()

        // Save the setting when the switch is toggled
        binding.switchLocationSuggestion.setOnCheckedChangeListener { _, isChecked ->
            settingsManager.saveSuggestionByLocation(isChecked)
        }
    }

    // Handle the back button click
    override fun onSupportNavigateUp(): Boolean {
        finish() // Close this activity and return to the previous one
        return true
    }
}

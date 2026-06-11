package com.nordic.mediahub

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import com.google.android.material.textfield.TextInputEditText

class SettingsFragment : Fragment() {
    private lateinit var configManager: ConfigManager

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_settings, container, false)
        configManager = ConfigManager(requireContext())

        val navidromeUrl = view.findViewById<TextInputEditText>(R.id.navidrome_url)
        val navidromeUsername = view.findViewById<TextInputEditText>(R.id.navidrome_username)
        val navidromePassword = view.findViewById<TextInputEditText>(R.id.navidrome_password)
        val audiobookshelfUrl = view.findViewById<TextInputEditText>(R.id.audiobookshelf_url)
        val audiobookshelfToken = view.findViewById<TextInputEditText>(R.id.audiobookshelf_token)
        val videoServiceSpinner = view.findViewById<Spinner>(R.id.video_service_spinner)
        val videoUrl = view.findViewById<TextInputEditText>(R.id.video_url)
        val videoUsername = view.findViewById<TextInputEditText>(R.id.video_username)
        val videoPassword = view.findViewById<TextInputEditText>(R.id.video_password)
        val btnSave = view.findViewById<Button>(R.id.btn_save)

        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, listOf("Emby", "Plex", "WebDav"))
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        videoServiceSpinner.adapter = adapter

        val config = configManager.loadConfig()
        navidromeUrl.setText(config.navidromeUrl)
        navidromeUsername.setText(config.navidromeUsername)
        navidromePassword.setText(config.navidromePassword)
        audiobookshelfUrl.setText(config.audiobookshelfUrl)
        audiobookshelfToken.setText(config.audiobookshelfToken)
        videoServiceSpinner.setSelection(config.videoServiceType.ordinal)
        videoUrl.setText(config.videoServiceUrl)
        videoUsername.setText(config.videoServiceUsername)
        videoPassword.setText(config.videoServicePassword)

        btnSave.setOnClickListener {
            val newConfig = ServerConfig(
                navidromeUrl = navidromeUrl.text.toString(),
                navidromeUsername = navidromeUsername.text.toString(),
                navidromePassword = navidromePassword.text.toString(),
                audiobookshelfUrl = audiobookshelfUrl.text.toString(),
                audiobookshelfToken = audiobookshelfToken.text.toString(),
                videoServiceType = VideoServiceType.values()[videoServiceSpinner.selectedItemPosition],
                videoServiceUrl = videoUrl.text.toString(),
                videoServiceUsername = videoUsername.text.toString(),
                videoServicePassword = videoPassword.text.toString()
            )
            configManager.saveConfig(newConfig)
            Toast.makeText(requireContext(), "配置已保存", Toast.LENGTH_SHORT).show()
        }

        return view
    }
}

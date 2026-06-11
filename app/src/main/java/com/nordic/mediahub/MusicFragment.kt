package com.nordic.mediahub

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class MusicFragment : Fragment() {
    private lateinit var musicList: RecyclerView
    private lateinit var miniPlayer: View
    private lateinit var trackTitle: TextView
    private lateinit var trackArtist: TextView
    private lateinit var btnPlay: ImageButton

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_music, container, false)

        musicList = view.findViewById(R.id.music_list)
        miniPlayer = view.findViewById(R.id.mini_player)
        trackTitle = view.findViewById(R.id.track_title)
        trackArtist = view.findViewById(R.id.track_artist)
        btnPlay = view.findViewById(R.id.btn_play)

        view.findViewById<ImageButton>(R.id.btn_settings).setOnClickListener {
            showMusicSettings()
        }

        setupRecyclerView()
        setupMiniPlayer()

        return view
    }

    private fun showMusicSettings() {
        val dialog = android.app.Dialog(requireContext())
        dialog.setContentView(R.layout.dialog_music_settings)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val configManager = ConfigManager(requireContext())
        val config = configManager.loadConfig()

        val urlField = dialog.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.navidrome_url)
        val usernameField = dialog.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.navidrome_username)
        val passwordField = dialog.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.navidrome_password)
        val btnSave = dialog.findViewById<Button>(R.id.btn_save)

        urlField.setText(config.navidromeUrl)
        usernameField.setText(config.navidromeUsername)
        passwordField.setText(config.navidromePassword)

        btnSave.setOnClickListener {
            val newConfig = config.copy(
                navidromeUrl = urlField.text.toString(),
                navidromeUsername = usernameField.text.toString(),
                navidromePassword = passwordField.text.toString()
            )
            configManager.saveConfig(newConfig)
            Toast.makeText(requireContext(), "已保存", Toast.LENGTH_SHORT).show()
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun setupRecyclerView() {
        val tracks = listOf(
            Track("1", "Lonely Night", "张子豪", "04:08"),
            Track("2", "杨千嬅", "杨千嬅", "05:41"),
            Track("3", "我仍爱你", "张国荣", "04:32"),
            Track("4", "给爱丽丝", "贝多芬", "03:18"),
            Track("5", "夜曲", "周杰伦", "04:01")
        )

        musicList.layoutManager = LinearLayoutManager(requireContext())
        musicList.adapter = MusicAdapter(tracks)
    }

    private fun setupMiniPlayer() {
        trackTitle.text = "爱情之光"
        trackArtist.text = "张子豪"

        btnPlay.setOnClickListener {
            // 播放/暂停逻辑
        }
    }
}

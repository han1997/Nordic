package com.nordic.mediahub

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ImageButton
import android.widget.Spinner
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView

class VideoFragment : Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_video, container, false)

        val videoList = view.findViewById<RecyclerView>(R.id.video_list)

        view.findViewById<ImageButton>(R.id.btn_settings).setOnClickListener {
            showVideoSettings()
        }

        val videos = listOf(
            Video("1", "肖申克的救赎", "剧情 / 犯罪", "2:22:00"),
            Video("2", "阿甘正传", "剧情 / 爱情", "2:22:00"),
            Video("3", "星际穿越", "科幻 / 冒险", "2:49:00"),
            Video("4", "盗梦空间", "科幻 / 悬疑", "2:28:00"),
            Video("5", "泰坦尼克号", "爱情 / 灾难", "3:14:00"),
            Video("6", "教父", "剧情 / 犯罪", "2:55:00")
        )

        videoList.layoutManager = GridLayoutManager(requireContext(), 2)
        videoList.adapter = VideoAdapter(videos)

        return view
    }

    private fun showVideoSettings() {
        val dialog = android.app.Dialog(requireContext())
        dialog.setContentView(R.layout.dialog_video_settings)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val configManager = ConfigManager(requireContext())
        val config = configManager.loadConfig()

        val spinner = dialog.findViewById<Spinner>(R.id.video_service_spinner)
        val urlField = dialog.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.video_url)
        val usernameField = dialog.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.video_username)
        val passwordField = dialog.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.video_password)
        val btnSave = dialog.findViewById<Button>(R.id.btn_save)

        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, listOf("Emby", "Plex", "WebDav"))
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = adapter

        spinner.setSelection(config.videoServiceType.ordinal)
        urlField.setText(config.videoServiceUrl)
        usernameField.setText(config.videoServiceUsername)
        passwordField.setText(config.videoServicePassword)

        btnSave.setOnClickListener {
            val newConfig = config.copy(
                videoServiceType = VideoServiceType.values()[spinner.selectedItemPosition],
                videoServiceUrl = urlField.text.toString(),
                videoServiceUsername = usernameField.text.toString(),
                videoServicePassword = passwordField.text.toString()
            )
            configManager.saveConfig(newConfig)
            Toast.makeText(requireContext(), "已保存", Toast.LENGTH_SHORT).show()
            dialog.dismiss()
        }

        dialog.show()
    }
}

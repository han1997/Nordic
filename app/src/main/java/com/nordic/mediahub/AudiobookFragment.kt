package com.nordic.mediahub

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class AudiobookFragment : Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_audiobook, container, false)

        val audiobookList = view.findViewById<RecyclerView>(R.id.audiobook_list)

        view.findViewById<ImageButton>(R.id.btn_settings).setOnClickListener {
            showAudiobookSettings()
        }

        val books = listOf(
            Audiobook("1", "三体", "刘慈欣", "已听 45%"),
            Audiobook("2", "活着", "余华", "已听 78%"),
            Audiobook("3", "解忧杂货店", "东野圭吾", "已听 12%"),
            Audiobook("4", "白夜行", "东野圭吾", "已听 90%")
        )

        audiobookList.layoutManager = LinearLayoutManager(requireContext())
        audiobookList.adapter = AudiobookAdapter(books)

        return view
    }

    private fun showAudiobookSettings() {
        val dialog = android.app.Dialog(requireContext())
        dialog.setContentView(R.layout.dialog_audiobook_settings)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val configManager = ConfigManager(requireContext())
        val config = configManager.loadConfig()

        val urlField = dialog.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.audiobookshelf_url)
        val tokenField = dialog.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.audiobookshelf_token)
        val btnSave = dialog.findViewById<Button>(R.id.btn_save)

        urlField.setText(config.audiobookshelfUrl)
        tokenField.setText(config.audiobookshelfToken)

        btnSave.setOnClickListener {
            val newConfig = config.copy(
                audiobookshelfUrl = urlField.text.toString(),
                audiobookshelfToken = tokenField.text.toString()
            )
            configManager.saveConfig(newConfig)
            Toast.makeText(requireContext(), "已保存", Toast.LENGTH_SHORT).show()
            dialog.dismiss()
        }

        dialog.show()
    }
}

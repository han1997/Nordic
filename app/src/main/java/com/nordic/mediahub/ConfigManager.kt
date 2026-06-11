package com.nordic.mediahub

import android.content.Context
import android.content.SharedPreferences

class ConfigManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("server_config", Context.MODE_PRIVATE)

    fun saveConfig(config: ServerConfig) {
        prefs.edit().apply {
            putString("navidrome_url", config.navidromeUrl)
            putString("navidrome_username", config.navidromeUsername)
            putString("navidrome_password", config.navidromePassword)
            putString("audiobookshelf_url", config.audiobookshelfUrl)
            putString("audiobookshelf_token", config.audiobookshelfToken)
            putString("video_service_type", config.videoServiceType.name)
            putString("video_service_url", config.videoServiceUrl)
            putString("video_service_username", config.videoServiceUsername)
            putString("video_service_password", config.videoServicePassword)
            apply()
        }
    }

    fun loadConfig(): ServerConfig {
        return ServerConfig(
            navidromeUrl = prefs.getString("navidrome_url", "") ?: "",
            navidromeUsername = prefs.getString("navidrome_username", "") ?: "",
            navidromePassword = prefs.getString("navidrome_password", "") ?: "",
            audiobookshelfUrl = prefs.getString("audiobookshelf_url", "") ?: "",
            audiobookshelfToken = prefs.getString("audiobookshelf_token", "") ?: "",
            videoServiceType = VideoServiceType.valueOf(prefs.getString("video_service_type", "EMBY") ?: "EMBY"),
            videoServiceUrl = prefs.getString("video_service_url", "") ?: "",
            videoServiceUsername = prefs.getString("video_service_username", "") ?: "",
            videoServicePassword = prefs.getString("video_service_password", "") ?: ""
        )
    }
}

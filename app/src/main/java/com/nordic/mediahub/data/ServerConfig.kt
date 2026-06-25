package com.nordic.mediahub.data

import androidx.compose.runtime.Stable

@Stable
data class NavidromeConfig(
    val serverUrl: String = "",
    val username: String = "",
    val password: String = ""
)

fun NavidromeConfig.isReadyForMusicSync(): Boolean {
    return serverUrl.isNotBlank() && username.isNotBlank() && password.isNotBlank()
}

@Stable
data class AudiobookShelfConfig(
    val serverUrl: String = "",
    val username: String = "",
    val password: String = ""
)

@Stable
data class VideoServerConfig(
    val type: VideoServerType = VideoServerType.EMBY,
    val serverUrl: String = "",
    val username: String = "",
    val password: String = "",
    val apiKey: String = ""
)

enum class VideoServerType {
    EMBY, PLEX, WEBDAV
}

fun VideoServerConfig.isReadyForVideoSync(): Boolean {
    if (type != VideoServerType.EMBY) return false

    val hasApiKey = apiKey.isNotBlank()
    val hasPasswordLogin = username.isNotBlank() && password.isNotBlank()
    return serverUrl.isNotBlank() && (hasApiKey || hasPasswordLogin)
}

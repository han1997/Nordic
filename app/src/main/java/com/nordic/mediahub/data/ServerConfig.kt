package com.nordic.mediahub.data

data class NavidromeConfig(
    val serverUrl: String = "",
    val username: String = "",
    val password: String = ""
)

fun NavidromeConfig.isReadyForMusicSync(): Boolean {
    return serverUrl.isNotBlank() && username.isNotBlank()
}

data class AudiobookShelfConfig(
    val serverUrl: String = "",
    val username: String = "",
    val password: String = ""
)

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

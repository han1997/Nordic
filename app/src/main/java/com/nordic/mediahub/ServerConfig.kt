package com.nordic.mediahub

data class ServerConfig(
    val navidromeUrl: String = "",
    val navidromeUsername: String = "",
    val navidromePassword: String = "",
    val audiobookshelfUrl: String = "",
    val audiobookshelfToken: String = "",
    val videoServiceType: VideoServiceType = VideoServiceType.EMBY,
    val videoServiceUrl: String = "",
    val videoServiceUsername: String = "",
    val videoServicePassword: String = ""
)

enum class VideoServiceType {
    EMBY, PLEX, WEBDAV
}

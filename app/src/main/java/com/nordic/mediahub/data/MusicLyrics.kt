package com.nordic.mediahub.data

data class MusicLyrics(
    val lines: List<MusicLyricsLine> = emptyList(),
    val synced: Boolean = false
)

data class MusicLyricsLine(
    val startMillis: Int? = null,
    val text: String
)

package com.nordic.mediahub.data

import androidx.compose.runtime.Stable

@Stable
data class MusicLyrics(
    val lines: List<MusicLyricsLine> = emptyList(),
    val synced: Boolean = false
)

@Stable
data class MusicLyricsLine(
    val startMillis: Int? = null,
    val text: String
)

package com.nordic.mediahub.data

import androidx.compose.runtime.Stable

@Stable
data class MusicLyrics(
    val lines: List<MusicLyricsLine> = emptyList(),
    val synced: Boolean = false,
    val syncedLines: List<MusicLyricsLine> = if (synced) lines else emptyList(),
    val plainLines: List<MusicLyricsLine> = if (!synced) lines else emptyList()
) {
    val hasSynced: Boolean
        get() = syncedLines.isNotEmpty()

    val hasPlain: Boolean
        get() = plainLines.isNotEmpty()
}

@Stable
data class MusicLyricsLine(
    val startMillis: Int? = null,
    val text: String
)

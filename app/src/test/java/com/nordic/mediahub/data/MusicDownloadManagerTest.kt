package com.nordic.mediahub.data

import com.nordic.mediahub.api.NavidromeSong
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MusicDownloadManagerStateTest {
    @Test
    fun downloadStateEntry_defaultsToNotDownloadedWithZeroProgress() {
        val entry = DownloadStateEntry()
        assertEquals(DownloadState.NOT_DOWNLOADED, entry.state)
        assertEquals(0f, entry.progress, 0.001f)
        assertEquals(null, entry.song)
    }

    @Test
    fun downloadStateEntry_copyPreservesSongOnDownloaded() {
        val song = NavidromeSong(id = "song-1", title = "Test Song", artist = "Artist")
        val entry = DownloadStateEntry(state = DownloadState.DOWNLOADED, progress = 1f, song = song)
        assertEquals(DownloadState.DOWNLOADED, entry.state)
        assertEquals(1f, entry.progress, 0.001f)
        assertEquals("song-1", entry.song?.id)
        assertEquals("Test Song", entry.song?.title)
    }

    @Test
    fun downloadStateEnum_hasExpectedValues() {
        assertEquals(3, DownloadState.entries.size)
        assertEquals(DownloadState.NOT_DOWNLOADED, DownloadState.valueOf("NOT_DOWNLOADED"))
        assertEquals(DownloadState.DOWNLOADING, DownloadState.valueOf("DOWNLOADING"))
        assertEquals(DownloadState.DOWNLOADED, DownloadState.valueOf("DOWNLOADED"))
    }

    @Test
    fun extensionFromContentType_mapsCommonAudioTypes() {
        assertEquals("mp3", extensionFromContentType("audio/mpeg"))
        assertEquals("flac", extensionFromContentType("audio/flac"))
        assertEquals("ogg", extensionFromContentType("audio/ogg"))
        assertEquals("wav", extensionFromContentType("audio/wav"))
        assertEquals("aac", extensionFromContentType("audio/aac"))
        assertEquals("m4a", extensionFromContentType("audio/m4a"))
        assertEquals("opus", extensionFromContentType("audio/opus"))
        assertEquals("wma", extensionFromContentType("audio/wma"))
        assertEquals("mp3", extensionFromContentType("application/octet-stream"))
    }

    @Test
    fun extensionFromContentType_isCaseInsensitive() {
        assertEquals("flac", extensionFromContentType("AUDIO/FLAC"))
        assertEquals("ogg", extensionFromContentType("Audio/Ogg"))
    }
}

internal fun extensionFromContentType(contentType: String): String {
    return when {
        contentType.contains("ogg", ignoreCase = true) -> "ogg"
        contentType.contains("flac", ignoreCase = true) -> "flac"
        contentType.contains("wav", ignoreCase = true) -> "wav"
        contentType.contains("aac", ignoreCase = true) -> "aac"
        contentType.contains("m4a", ignoreCase = true) -> "m4a"
        contentType.contains("opus", ignoreCase = true) -> "opus"
        contentType.contains("wma", ignoreCase = true) -> "wma"
        else -> "mp3"
    }
}

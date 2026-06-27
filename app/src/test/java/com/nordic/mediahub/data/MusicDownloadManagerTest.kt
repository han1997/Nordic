package com.nordic.mediahub.data

import com.nordic.mediahub.api.NavidromeSong
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

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

    @Test
    fun musicDownloadMetadataFileName_usesStableSidecarName() {
        assertEquals("song-1.metadata.json", musicDownloadMetadataFileName("song-1"))
    }

    @Test
    fun isDownloadedMusicFile_excludesTempAndMetadataSidecarFiles() {
        assertTrue(isDownloadedMusicFile("song-1.mp3"))
        assertTrue(isDownloadedMusicFile("song-1.flac"))
        assertFalse(isDownloadedMusicFile("song-1.mp3.tmp"))
        assertFalse(isDownloadedMusicFile("song-1.metadata.json"))
    }

    @Test
    fun downloadedSongMetadata_roundTripsNavidromeSong() {
        val dir = Files.createTempDirectory("music-download-metadata").toFile()
        val file = dir.resolve(musicDownloadMetadataFileName("song-1"))
        val song = NavidromeSong(
            id = "song-1",
            title = "Downloaded Song",
            artist = "Artist",
            album = "Album",
            duration = 245,
            coverArt = "cover-1",
            streamUrl = "https://example.test/stream",
            created = "2026-06-27T00:00:00Z",
            starred = "2026-06-27T00:01:00Z"
        )

        saveDownloadedSongMetadata(file, song)

        val restored = loadDownloadedSongMetadata(file)
        assertEquals(song, restored)
    }

    @Test
    fun loadDownloadedSongMetadata_returnsNullForMissingOrMalformedFile() {
        val dir = Files.createTempDirectory("music-download-invalid-metadata").toFile()
        val missing = dir.resolve("missing.metadata.json")
        val malformed = dir.resolve("bad.metadata.json").apply { writeText("{not-json", Charsets.UTF_8) }

        assertEquals(null, loadDownloadedSongMetadata(missing))
        assertEquals(null, loadDownloadedSongMetadata(malformed))
    }
}

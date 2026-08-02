package com.nordic.mediahub.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
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
    fun extensionFromContentType_mapsM4bAndMp4() {
        assertEquals("m4b", extensionFromContentType("audio/m4b"))
        assertEquals("m4a", extensionFromContentType("audio/mp4"))
        assertEquals("m4b", extensionFromContentType("AUDIO/X-M4B"))
        assertEquals("m4a", extensionFromContentType("audio/x-mp4"))
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
            created = "2026-06-27T00:00:00Z"
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

class MusicDownloadManagerDownloadTest {
    private lateinit var server: MockWebServer
    private lateinit var dir: File
    private lateinit var scope: CoroutineScope
    private lateinit var client: OkHttpClient
    private lateinit var manager: MusicDownloadManager

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
        dir = Files.createTempDirectory("music-dl-test").toFile()
        scope = CoroutineScope(Dispatchers.Unconfined + SupervisorJob())
        client = OkHttpClient.Builder().build()
        manager = MusicDownloadManager(
            scope = scope,
            client = client,
            downloadDir = dir
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
        scope.cancel()
        client.dispatcher.executorService.shutdown()
        client.connectionPool.evictAll()
        dir.deleteRecursively()
    }

    private fun config() = NavidromeConfig(
        serverUrl = server.url("/").toString(),
        username = "user",
        password = "pass"
    )

    private fun song() = NavidromeSong(id = "song-1", title = "Test Song", artist = "Artist")

    @Test
    fun performDownload_serverError_closesResponseAndLeavesNoOrphan() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(500))
        manager.performDownload(song(), config())
        val temps = dir.listFiles { f -> f.name.endsWith(".tmp") }.orEmpty()
        assertEquals(0, temps.size)
        assertEquals(DownloadState.NOT_DOWNLOADED, manager.downloadStates.value["song-1"]?.state)
    }

    @Test
    fun performDownload_success_renamesToFinalAndMarksDownloaded() = runBlocking {
        val audioBytes = ByteArray(1024) { 0x42 }
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "audio/mpeg")
                .setBody(Buffer().write(audioBytes))
        )
        manager.performDownload(song(), config())
        val finalFile = File(dir, "song-1.mp3")
        assertTrue(finalFile.exists())
        assertFalse(File(dir, "song-1.mp3.tmp").exists())
        assertTrue(File(dir, "song-1.metadata.json").exists())
        assertEquals(DownloadState.DOWNLOADED, manager.downloadStates.value["song-1"]?.state)
    }

    @Test
    fun performDownload_m4bContentType_usesM4bExtension() = runBlocking {
        val audioBytes = ByteArray(64) { 0x42 }
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "audio/m4b")
                .setBody(Buffer().write(audioBytes))
        )
        manager.performDownload(song(), config())
        assertTrue(File(dir, "song-1.m4b").exists())
        assertEquals(DownloadState.DOWNLOADED, manager.downloadStates.value["song-1"]?.state)
    }

    @Test
    fun performDownload_mp4ContentType_usesM4aExtension() = runBlocking {
        val audioBytes = ByteArray(64) { 0x42 }
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "audio/mp4")
                .setBody(Buffer().write(audioBytes))
        )
        manager.performDownload(song(), config())
        assertTrue(File(dir, "song-1.m4a").exists())
    }

    @Test
    fun beginDownloading_rejectsSecondConcurrentRequestForSameSong() {
        val s = song()
        assertTrue(manager.beginDownloading(s))
        assertFalse(manager.beginDownloading(s))
        assertEquals(DownloadState.DOWNLOADING, manager.downloadStates.value["song-1"]?.state)
    }

    @Test
    fun beginDownloading_acceptsDownloadForDifferentSongs() {
        val a = NavidromeSong(id = "song-a", title = "A")
        val b = NavidromeSong(id = "song-b", title = "B")
        assertTrue(manager.beginDownloading(a))
        assertTrue(manager.beginDownloading(b))
        assertEquals(DownloadState.DOWNLOADING, manager.downloadStates.value["song-a"]?.state)
        assertEquals(DownloadState.DOWNLOADING, manager.downloadStates.value["song-b"]?.state)
    }
}

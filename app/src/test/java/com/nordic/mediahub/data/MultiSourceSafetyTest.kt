package com.nordic.mediahub.data

import android.content.SharedPreferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.io.IOException
import java.nio.file.Files

class MultiSourceSafetyTest {
    private fun failingCommit(base: SharedPreferences): SharedPreferences = object : SharedPreferences by base {
        override fun edit(): SharedPreferences.Editor {
            val editor = base.edit()
            return object : SharedPreferences.Editor by editor {
                override fun putString(key: String, value: String?): SharedPreferences.Editor = apply { editor.putString(key, value) }
                override fun putBoolean(key: String, value: Boolean): SharedPreferences.Editor = apply { editor.putBoolean(key, value) }
                override fun remove(key: String): SharedPreferences.Editor = apply { editor.remove(key) }
                // Android can update memory/listeners even when the disk commit reports failure.
                override fun commit(): Boolean { editor.commit(); return false }
            }
        }
    }
    @Test fun failedCredentialCommitDoesNotRemoveLegacyOrMarkMigrationComplete() {
        val base = FakeSharedPreferences()
        var deleted = false
        assertThrows(IOException::class.java) {
            runEncryptedConfigMigration(failingCommit(base), mapOf(EncryptedConfigKeys.NAVIDROME_PASS to "secret")) { deleted = true }
        }
        assertFalse(deleted)
        assertFalse(base.getBoolean(ENCRYPTED_PREFS_MIGRATED_KEY, false))
    }
    @Test fun failedSourceMigrationRestoresLegacyFieldsAndRemovesGhostProfile() {
        val base = FakeSharedPreferences()
        base.edit().putString(EncryptedConfigKeys.VIDEO_URL, "https://example.com")
            .putString(EncryptedConfigKeys.VIDEO_API_KEY, "secret").commit()
        assertThrows(IOException::class.java) { migrateMediaSources(failingCommit(base)) }
        assertNull(base.getString(MEDIA_SOURCES_KEY, null))
        assertEquals("secret", base.getString(EncryptedConfigKeys.VIDEO_API_KEY, null))
    }
    @Test fun successfulSourceMigrationRemovesDuplicateLegacyCredentialSlots() {
        val prefs = FakeSharedPreferences()
        prefs.edit().putString(EncryptedConfigKeys.NAVIDROME_URL, "https://example.com")
            .putString(EncryptedConfigKeys.NAVIDROME_PASS, "secret").commit()
        val state = migrateMediaSources(prefs)
        assertEquals("secret", state.sources.single().password)
        assertNull(prefs.getString(EncryptedConfigKeys.NAVIDROME_PASS, null))
        assertEquals(state, migrateMediaSources(prefs))
    }
    @Test fun eagerCacheMigrationRemovesAccountKeysAndSignedParameters() = runBlocking {
        val store = fakeDataStore()
        val source = MediaSource(kind = MediaSourceKind.EMBY, serverUrl = "https://example.com", apiKey = "secret-key")
        val legacy = source.videoConfig().copy(sourceId = "")
        val cache = EmbyVideoCacheRepository(dataStoreProvider = { store })
        cache.save(legacy, EmbyVideoCache(videos = listOf(VideoItem("1", "lib", "Movie", "Movie",
            streamUrl = "https://example.com/Videos/1/stream?api_key=secret-key"))))
        migrateLegacySourceCaches(store, MediaSourceState(sources = listOf(source), activeVideoId = source.id))
        val values = store.data.first().asMap().values.joinToString()
        assertFalse(values.contains("secret-key"))
        assertEquals(source.id, cache.load(source.videoConfig())!!.videos.single().sourceId)
        assertNull(store.data.first()[stringPreferencesKey("emby_video_cache")])
    }
    @Test fun unknownLegacyCacheIsDiscardedWithoutTouchingHistory() = runBlocking {
        val store = fakeDataStore()
        store.edit {
            it[stringPreferencesKey("emby_video_cache")] = "{\"configKey\":\"old-secret\"}"
            it[stringPreferencesKey("navidrome_play_history")] = "[{\"songId\":\"keep\"}]"
        }
        migrateLegacySourceCaches(store, MediaSourceState())
        assertNull(store.data.first()[stringPreferencesKey("emby_video_cache")])
        assertNotNull(store.data.first()[stringPreferencesKey("navidrome_play_history")])
    }
    @Test fun manualLegacyDownloadAssignmentDoesNotOverwriteOrEscapeRoot() {
        val root = Files.createTempDirectory("nordic-source-assignment-").toFile()
        try {
            File(root, "song.mp3").writeBytes(byteArrayOf(1, 2, 3))
            saveDownloadedSongMetadata(File(root, "song.metadata.json"), NavidromeSong("song", "Song", streamUrl = "https://example.com/stream?u=user&t=secret&s=salt"))
            assertEquals(1, assignLegacyMusicDownloads(root, "source-a"))
            assertTrue(File(root, "source-a/song.mp3").exists())
            assertFalse(File(root, "source-a/song.metadata.json").readText().contains("secret"))
            File(root, "song.mp3").writeBytes(byteArrayOf(9))
            assertEquals(0, assignLegacyMusicDownloads(root, "source-a"))
            assertEquals(1, File(root, "song.mp3").length())
            assertEquals(3, File(root, "source-a/song.mp3").length())
            assertThrows(IllegalArgumentException::class.java) { assignLegacyMusicDownloads(root, "../outside") }
        } finally {
            check(requireNotNull(root.parentFile).canonicalFile == File(requireNotNull(System.getProperty("java.io.tmpdir"))).canonicalFile)
            root.deleteRecursively()
        }
    }
}
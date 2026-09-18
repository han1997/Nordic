package com.nordic.mediahub.data

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class BackupRepositoryTest {
    private lateinit var scope: CoroutineScope
    private lateinit var configPrefs: FakeSharedPreferences
    private lateinit var settingsPrefs: FakeSharedPreferences
    private lateinit var repository: BackupRepository

    @Before
    fun setUp() {
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        configPrefs = FakeSharedPreferences()
        settingsPrefs = FakeSharedPreferences()
        repository = BackupRepository(
            configStore = EncryptedConfigStore(null, { configPrefs }, { emptyMap() }, {}),
            dataStore = fakeDataStore(),
            prefsProvider = { settingsPrefs },
            appVersion = { "0.1.21" }
        )
    }

    @After
    fun tearDown() {
        scope.cancel()
    }

    private fun newStore() = EncryptedConfigStore(null, { configPrefs }, { emptyMap() }, {})

    private fun propfindBody(vararg files: String): String {
        val rows = files.joinToString("") { file ->
            "<d:response><d:href>/dav/nordic-backup/$file</d:href><d:propstat><d:prop>" +
                "<d:displayname>$file</d:displayname><d:resourcetype/><d:getcontentlength>999</d:getcontentlength>" +
                "</d:prop><d:status>HTTP/1.1 200 OK</d:status></d:propstat></d:response>"
        }
        return "<?xml version=\"1.0\"?><d:multistatus xmlns:d=\"DAV:\">" +
            "<d:response><d:href>/dav/nordic-backup/</d:href><d:propstat><d:prop>" +
            "<d:displayname>nordic-backup</d:displayname><d:resourcetype><d:collection/></d:resourcetype>" +
            "</d:prop><d:status>HTTP/1.1 200 OK</d:status></d:propstat></d:response>" +
            rows + "</d:multistatus>"
    }

    private suspend fun seedStore(): String {
        val store = newStore()
        store.updateSources {
            saveMediaSource(it, MediaSource(name = "nas", kind = MediaSourceKind.WEBDAV,
                serverUrl = "https://dav.example/", username = "u", password = "p")).state
        }
        store.updateSources {
            saveMediaSource(it, MediaSource(name = "books", kind = MediaSourceKind.AUDIOBOOKSHELF,
                serverUrl = "https://abs.example/", username = "a", password = "b")).state
        }
        store.updatePreferences { it.copy(theme = ThemeMode.DARK) }
        val absId = store.sources.first().sources.first { it.kind == MediaSourceKind.AUDIOBOOKSHELF }.id
        store.saveLastAudiobookItem(absId, "item-7")
        return absId
    }

    private fun target(server: MockWebServer) = BackupWebDavConfig(serverUrl = server.url("/dav/").toString(),
        username = "user", password = "secret", directory = "nordic-backup", allowInsecureHttp = true)

    @Test fun settingsRoundTripNormalizesDirectory() = runBlocking {
        repository.saveSettings(BackupWebDavConfig(serverUrl = "https://dav.example/dav/",
            username = "u", password = "p", directory = "app/backup/", allowInsecureHttp = false))
        val settings = repository.currentSettings()
        assertEquals("https://dav.example/dav/", settings.serverUrl)
        assertEquals("app/backup", settings.directory)
        assertEquals("p", settingsPrefs.getString(BackupSettingsKeys.PASS, null))
    }

    @Test fun testConnectionCreatesMissingNestedBackupDirectory() = runBlocking {
        val server = MockWebServer(); server.start()
        try {
            server.enqueue(MockResponse().setResponseCode(201)) // MKCOL /app/
            server.enqueue(MockResponse().setResponseCode(201)) // MKCOL /app/backup/
            server.enqueue(MockResponse().setResponseCode(207).setBody(propfindBody())) // PROPFIND
            repository.testConnection(BackupWebDavConfig(
                serverUrl = server.url("/dav/").toString(), username = "user", password = "secret",
                directory = "app/backup", allowInsecureHttp = true
            ))
            val parent = server.takeRequest()
            assertEquals("MKCOL", parent.method)
            assertEquals("/dav/app/", parent.path)
            val target = server.takeRequest()
            assertEquals("MKCOL", target.method)
            assertEquals("/dav/app/backup/", target.path)
            val probe = server.takeRequest()
            assertEquals("PROPFIND", probe.method)
            assertEquals("/dav/app/backup/", probe.path)
        } finally { server.shutdown() }
    }

    @Test fun createBackupUploadsEncryptedArchiveAndPrunesHistory() = runBlocking {
        val server = MockWebServer(); server.start()
        try {
            repository.saveSettings(target(server))

            server.enqueue(MockResponse().setResponseCode(201)) // MKCOL
            server.enqueue(MockResponse().setResponseCode(201)) // PUT
            server.enqueue(MockResponse().setResponseCode(207).setBody(propfindBody("placeholder"))) // prune listing
            val absId = seedStore()
            val name = backupFileName(repository.createBackup("密码123456".toCharArray()).createdAtMillis)

            assertEquals("MKCOL", server.takeRequest().method)
            val put = server.takeRequest()
            assertEquals("PUT", put.method)
            assertEquals("/dav/nordic-backup/$name", put.path)
            assertEquals("PROPFIND", server.takeRequest().method)

            val uploaded = put.body.readByteArray()
            val header = BackupArchiveCodec.decodeHeader(uploaded)
            assertEquals("0.1.21", header.appVersion)
            assertEquals(BACKUP_SCHEMA_VERSION, header.schemaVersion)
            val (_, sealed) = BackupArchiveCodec.decode(uploaded)
            val payload = BackupPayloadCodec.decode(BackupCrypto.decrypt("密码123456".toCharArray(), sealed).toString(Charsets.UTF_8))
            assertEquals(2, payload.sources.sources.size)
            assertEquals(ThemeMode.DARK, payload.preferences.theme)
            assertEquals(mapOf(absId to "item-7"), payload.lastBooks)
        } finally { server.shutdown() }
    }

    @Test fun prepareRestoreRejectsWrongPasswordWithoutLocalWrites() = runBlocking {
        val server = MockWebServer(); server.start()
        try {
            seedStore()
            repository.saveSettings(target(server))
            val payload = BackupPayload(
                createdAtMillis = 1726963200000L,
                sources = MediaSourceState(
                    sources = listOf(MediaSource(name = "other", kind = MediaSourceKind.WEBDAV,
                        serverUrl = "https://other.example/", username = "o", password = "p")),
                    activeVideoId = null
                ),
                preferences = AppPreferences(theme = ThemeMode.DARK),
                lastBooks = mapOf("book-src" to "item-9")
            )
            val sealed = BackupCrypto.encrypt("密码123456".toCharArray(), BackupPayloadCodec.encode(payload).toByteArray(Charsets.UTF_8))
            val archive = BackupArchiveCodec.encode(
                BackupArchiveHeader(1726963200000L, "0.1.21", BACKUP_SCHEMA_VERSION), sealed)
            val name = backupFileName(1726963200000L)
            server.enqueue(MockResponse().setResponseCode(200).setBody(okio.Buffer().write(archive))) // GET
            server.enqueue(MockResponse().setResponseCode(200).setBody(okio.Buffer().write(archive))) // GET

            val restored = repository.prepareRestore(name, "密码123456".toCharArray())
            assertEquals(1, restored.sources.sources.size)
            assertEquals("item-9", restored.lastBooks["book-src"])

            val wrongPassword = assertThrows(BackupCryptoException::class.java) {
                runBlocking { repository.prepareRestore(name, "错误密码99".toCharArray()) }
            }
            assertTrue(wrongPassword.message!!.contains("本地数据未修改"))

            repository.applyRestore(restored)
            val store = newStore()
            assertEquals(1, store.sources.first().sources.size)
            assertEquals("other", store.sources.first().sources.single().name)
            assertEquals(ThemeMode.DARK, store.preferences.first().theme)
            assertEquals("item-9", store.lastAudiobookItem("book-src").first())
        } finally { server.shutdown() }
    }

    @Test fun applyRestoreClearsLastBookPositionsAbsentFromBackup() = runBlocking {
        newStore().saveLastAudiobookItem("orphan-src", "orphan-item")
        repository.applyRestore(BackupPayload(
            preferences = AppPreferences(),
            lastBooks = mapOf("book-src" to "item-7")
        ))
        val store = newStore()
        assertEquals("item-7", store.lastAudiobookItem("book-src").first())
        assertNull("orphan-item 不应残留", store.lastAudiobookItem("orphan-src").first())
    }

    @Test fun applyRestoreReplacesScopedDataStoreKeysWithBackupEntries() = runBlocking {
        val dataStore = fakeDataStore()
        val repo = BackupRepository(
            configStore = EncryptedConfigStore(null, { configPrefs }, { emptyMap() }, {}),
            dataStore = dataStore,
            prefsProvider = { settingsPrefs },
            appVersion = { "0.1.21" }
        )
        dataStore.edit {
            it[stringPreferencesKey("navidrome_play_history_src")] = "[{\"songId\":\"a\",\"timestamp\":1}]"
            it[stringPreferencesKey("webdav_progress_legacy")] = "old"
        }
        repo.applyRestore(BackupPayload(
            preferences = AppPreferences(),
            localData = mapOf(
                "navidrome_play_history_src" to "[{\"songId\":\"b\",\"timestamp\":2}]",
                "audiobook_bookmarks_src" to "[]"
            )
        ))
        val prefs = dataStore.data.first()
        assertEquals("[{\"songId\":\"b\",\"timestamp\":2}]", prefs[stringPreferencesKey("navidrome_play_history_src")])
        assertEquals("[]", prefs[stringPreferencesKey("audiobook_bookmarks_src")])
        assertNull(prefs[stringPreferencesKey("webdav_progress_legacy")])
    }

    @Test fun listBackupsReadsHeadersAndOrdersNewestFirst() = runBlocking {
        val server = MockWebServer(); server.start()
        try {
            repository.saveSettings(target(server))
            val old = backupFileName(1000000000000L)
            val new = backupFileName(2000000000000L)
            val archive = BackupArchiveCodec.encode(BackupArchiveHeader(2000000000000L, "0.1.21"), ByteArray(64))
            server.enqueue(MockResponse().setResponseCode(201)) // MKCOL
            server.enqueue(MockResponse().setResponseCode(207).setBody(propfindBody(old, new)))
            server.enqueue(MockResponse().setResponseCode(200).setBody(okio.Buffer().write(archive))) // header GET ×2
            server.enqueue(MockResponse().setResponseCode(200).setBody(okio.Buffer().write(archive)))
            val backups = repository.listBackups()
            assertTrue(backups.contains(BackupArchiveInfo(new, 2000000000000L, "0.1.21", 999L)))
            assertTrue(backups.indexOfFirst { it.fileName == new } < backups.indexOfFirst { it.fileName == old })
        } finally { server.shutdown() }
    }

    @Test fun restoreRejectsForeignFileNamesBeforeAnyNetworkCall() = runBlocking {
        val server = MockWebServer(); server.start()
        try {
            repository.saveSettings(target(server))
            assertThrows(IllegalArgumentException::class.java) {
                runBlocking { repository.prepareRestore("../evil.nbk", "密码123456".toCharArray()) }
            }
            assertEquals(0, server.requestCount)
        } finally { server.shutdown() }
    }
}

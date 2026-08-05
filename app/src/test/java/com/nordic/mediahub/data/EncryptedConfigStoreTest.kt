package com.nordic.mediahub.data

import android.content.SharedPreferences
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EncryptedConfigStoreTest {
    @Test
    fun runEncryptedConfigMigration_copiesAllLegacyKeysAndRemovesCredentialKeys() {
        val prefs = FakeSharedPreferences()
        val snapshot = mapOf(
            EncryptedConfigKeys.NAVIDROME_URL to "https://nav",
            EncryptedConfigKeys.NAVIDROME_USER to "nav-user",
            EncryptedConfigKeys.NAVIDROME_PASS to "nav-pass",
            EncryptedConfigKeys.AUDIOBOOK_URL to "https://abs",
            EncryptedConfigKeys.AUDIOBOOK_USER to "abs-user",
            EncryptedConfigKeys.AUDIOBOOK_PASS to "abs-pass",
            EncryptedConfigKeys.AUDIOBOOK_LAST_ITEM_ID to "last-item",
            EncryptedConfigKeys.VIDEO_TYPE to "EMBY",
            EncryptedConfigKeys.VIDEO_URL to "https://vid",
            EncryptedConfigKeys.VIDEO_USER to "vid-user",
            EncryptedConfigKeys.VIDEO_PASS to "vid-pass",
            EncryptedConfigKeys.VIDEO_API_KEY to "vid-key"
        )
        var removeCalls = 0

        runEncryptedConfigMigration(prefs, snapshot) { removeCalls++ }

        assertTrue(prefs.getBoolean(ENCRYPTED_PREFS_MIGRATED_KEY, false))
        snapshot.forEach { (key, value) -> assertEquals(value, prefs.getString(key, null)) }
        assertEquals(1, removeCalls)
    }

    @Test
    fun runEncryptedConfigMigration_isIdempotentWhenMigratedFlagAlreadySet() {
        val prefs = FakeSharedPreferences()
        prefs.edit().putBoolean(ENCRYPTED_PREFS_MIGRATED_KEY, true).commit()
        val snapshot = mapOf(EncryptedConfigKeys.NAVIDROME_URL to "https://nav")
        var removeCalls = 0

        runEncryptedConfigMigration(prefs, snapshot) { removeCalls++ }

        assertEquals(0, removeCalls)
        assertNull(prefs.getString(EncryptedConfigKeys.NAVIDROME_URL, null))
    }

    @Test
    fun runEncryptedConfigMigration_skipsNullLegacyValues() {
        val prefs = FakeSharedPreferences()
        val snapshot = mapOf(
            EncryptedConfigKeys.NAVIDROME_URL to "https://nav",
            EncryptedConfigKeys.NAVIDROME_USER to null
        )

        runEncryptedConfigMigration(prefs, snapshot) {}

        assertEquals("https://nav", prefs.getString(EncryptedConfigKeys.NAVIDROME_URL, null))
        assertNull(prefs.getString(EncryptedConfigKeys.NAVIDROME_USER, null))
    }

    @Test
    fun runEncryptedConfigMigration_removesCredentialKeysOnlyOnFirstRun() {
        val prefs = FakeSharedPreferences()
        val snapshot = mapOf(EncryptedConfigKeys.NAVIDROME_URL to "https://nav")
        var removeCalls = 0

        runEncryptedConfigMigration(prefs, snapshot) { removeCalls++ }
        runEncryptedConfigMigration(prefs, snapshot) { removeCalls++ }

        assertEquals(1, removeCalls)
    }

    @Test
    fun encryptedConfigKeys_allExcludesNonCredentialDataStoreKeys() {
        // Play history, audiobook bookmarks, and the per-domain browse/detail caches
        // share the same "settings" DataStore file but are NOT credentials. The
        // credential-key removal must never touch them, so they must stay out of the
        // ALL list.
        val nonCredentialKeys = setOf(
            "navidrome_play_history",
            "audiobook_bookmarks",
            "navidrome_music_cache",
            "audiobook_library_cache",
            "emby_video_cache"
        )
        EncryptedConfigKeys.ALL.forEach { key ->
            assertFalse(nonCredentialKeys.contains(key))
        }
    }

    @Test
    fun navidromeConfig_emitsInitialValueAndUpdatesOnSave() = runBlocking {
        val prefs = FakeSharedPreferences()
        prefs.writeNavidrome("https://nav", "nav-user", "nav-pass")
        val store = EncryptedConfigStore(
            context = null,
            prefsProvider = { prefs },
            legacyDataStoreSnapshot = { emptyMap() },
            removeLegacyCredentialKeys = {}
        )
        val initial = store.navidromeConfig.first()
        assertEquals(NavidromeConfig("https://nav", "nav-user", "nav-pass"), initial)

        val updated = async(start = CoroutineStart.UNDISPATCHED) {
            store.navidromeConfig.first { it != initial }
        }
        store.saveNavidromeConfig(NavidromeConfig("https://new", "u2", "p2"))

        assertEquals(NavidromeConfig("https://new", "u2", "p2"), updated.await())
    }

    @Test
    fun navidromeConfig_dropsDuplicateEmissionsViaDistinctUntilChanged() = runBlocking {
        val prefs = FakeSharedPreferences()
        prefs.writeNavidrome("https://nav", "nav-user", "nav-pass")
        val store = EncryptedConfigStore(
            context = null,
            prefsProvider = { prefs },
            legacyDataStoreSnapshot = { emptyMap() },
            removeLegacyCredentialKeys = {}
        )
        val initial = store.navidromeConfig.first()

        val updated = async(start = CoroutineStart.UNDISPATCHED) {
            store.navidromeConfig.first { it != initial }
        }
        store.saveNavidromeConfig(NavidromeConfig("https://nav", "nav-user", "nav-pass"))

        val result = withTimeoutOrNull(200) { updated.await() }
        assertNull(result)
        updated.cancel()
    }

    @Test
    fun videoConfig_emitsUpdatedConfigOnSave() = runBlocking {
        val prefs = FakeSharedPreferences()
        prefs.writeVideo(
            type = "EMBY",
            url = "https://vid",
            user = "vid-user",
            pass = "vid-pass",
            apiKey = "vid-key"
        )
        val store = EncryptedConfigStore(
            context = null,
            prefsProvider = { prefs },
            legacyDataStoreSnapshot = { emptyMap() },
            removeLegacyCredentialKeys = {}
        )
        val initialVideo = store.videoConfig.first()
        assertEquals(VideoServerType.EMBY, initialVideo.type)
        assertEquals("vid-key", initialVideo.apiKey)

        val updated = async(start = CoroutineStart.UNDISPATCHED) {
            store.videoConfig.first { it != initialVideo }
        }
        store.saveVideoConfig(
            VideoServerConfig(
                type = VideoServerType.EMBY,
                serverUrl = "https://new-vid",
                username = "u2",
                password = "p2",
                apiKey = "key2"
            )
        )

        val newVideo = updated.await()
        assertEquals("https://new-vid", newVideo.serverUrl)
        assertEquals("key2", newVideo.apiKey)
    }

    @Test
    fun lastAudiobookItemId_emitsNullWhenBlankAndValueWhenPresent() = runBlocking {
        val prefs = FakeSharedPreferences()
        prefs.edit().putString(EncryptedConfigKeys.AUDIOBOOK_LAST_ITEM_ID, "  ").commit()
        val store = EncryptedConfigStore(
            context = null,
            prefsProvider = { prefs },
            legacyDataStoreSnapshot = { emptyMap() },
            removeLegacyCredentialKeys = {}
        )
        val initial = store.lastAudiobookItemId.first()
        assertEquals(null, initial)

        val updated = async(start = CoroutineStart.UNDISPATCHED) {
            store.lastAudiobookItemId.first { it != null && it != initial }
        }
        store.saveLastAudiobookItemId("item-42")

        assertEquals("item-42", updated.await())
    }

    @Test
    fun videoConfig_mapsMissingTypeToEmbyFallback() = runBlocking {
        val prefs = FakeSharedPreferences()
        prefs.writeVideo(type = null, url = "https://vid", user = "", pass = "", apiKey = "")
        val store = EncryptedConfigStore(
            context = null,
            prefsProvider = { prefs },
            legacyDataStoreSnapshot = { emptyMap() },
            removeLegacyCredentialKeys = {}
        )
        val initial = store.videoConfig.first()
        assertEquals(VideoServerType.EMBY, initial.type)
        assertEquals("https://vid", initial.serverUrl)
        assertFalse(initial.isReadyForVideoSync())
    }

    private fun FakeSharedPreferences.writeNavidrome(url: String, user: String, pass: String) {
        edit()
            .putString(EncryptedConfigKeys.NAVIDROME_URL, url)
            .putString(EncryptedConfigKeys.NAVIDROME_USER, user)
            .putString(EncryptedConfigKeys.NAVIDROME_PASS, pass)
            .commit()
    }

    private fun FakeSharedPreferences.writeVideo(
        type: String?,
        url: String,
        user: String,
        pass: String,
        apiKey: String
    ) {
        val editor = edit()
            .putString(EncryptedConfigKeys.VIDEO_URL, url)
            .putString(EncryptedConfigKeys.VIDEO_USER, user)
            .putString(EncryptedConfigKeys.VIDEO_PASS, pass)
            .putString(EncryptedConfigKeys.VIDEO_API_KEY, apiKey)
        if (type != null) editor.putString(EncryptedConfigKeys.VIDEO_TYPE, type)
        editor.commit()
    }
}

private class FakeSharedPreferences : SharedPreferences {
    private val data = mutableMapOf<String, Any?>()
    private val listeners = mutableListOf<SharedPreferences.OnSharedPreferenceChangeListener>()

    override fun getAll(): Map<String, *> = synchronized(data) { data.toMap() }
    override fun getString(key: String, defValue: String?): String? =
        synchronized(data) { data[key] as? String } ?: defValue
    override fun getStringSet(key: String, defValue: Set<String?>?): Set<String?>? =
        synchronized(data) { data[key] as? Set<String?> } ?: defValue
    override fun getInt(key: String, defValue: Int): Int =
        synchronized(data) { (data[key] as? Int) ?: defValue }
    override fun getLong(key: String, defValue: Long): Long =
        synchronized(data) { (data[key] as? Long) ?: defValue }
    override fun getFloat(key: String, defValue: Float): Float =
        synchronized(data) { (data[key] as? Float) ?: defValue }
    override fun getBoolean(key: String, defValue: Boolean): Boolean =
        synchronized(data) { (data[key] as? Boolean) ?: defValue }
    override fun contains(key: String): Boolean = synchronized(data) { data.containsKey(key) }
    override fun edit(): SharedPreferences.Editor = FakeEditor()
    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        synchronized(listeners) { listeners += listener }
    }
    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        synchronized(listeners) { listeners -= listener }
    }

    private fun notifyListeners(key: String) {
        val snapshot = synchronized(listeners) { listeners.toList() }
        snapshot.forEach { it.onSharedPreferenceChanged(this, key) }
    }

    private inner class FakeEditor : SharedPreferences.Editor {
        private val pending = mutableMapOf<String, Any?>()
        private val removals = mutableListOf<String>()
        private var clearAll = false

        override fun putString(key: String, value: String?): SharedPreferences.Editor {
            pending[key] = value
            return this
        }

        override fun putStringSet(key: String, values: Set<String?>?): SharedPreferences.Editor {
            pending[key] = values
            return this
        }

        override fun putInt(key: String, value: Int): SharedPreferences.Editor {
            pending[key] = value
            return this
        }

        override fun putLong(key: String, value: Long): SharedPreferences.Editor {
            pending[key] = value
            return this
        }

        override fun putFloat(key: String, value: Float): SharedPreferences.Editor {
            pending[key] = value
            return this
        }

        override fun putBoolean(key: String, value: Boolean): SharedPreferences.Editor {
            pending[key] = value
            return this
        }

        override fun remove(key: String): SharedPreferences.Editor {
            removals += key
            return this
        }

        override fun clear(): SharedPreferences.Editor {
            clearAll = true
            return this
        }

        override fun commit(): Boolean {
            val changedKeys = mutableListOf<String>()
            synchronized(data) {
                if (clearAll) {
                    changedKeys += data.keys
                    data.clear()
                    clearAll = false
                }
                removals.forEach { data.remove(it); changedKeys += it }
                removals.clear()
                pending.forEach { (k, v) -> data[k] = v; changedKeys += k }
                pending.clear()
            }
            changedKeys.forEach { notifyListeners(it) }
            return true
        }

        override fun apply() {
            commit()
        }
    }
}

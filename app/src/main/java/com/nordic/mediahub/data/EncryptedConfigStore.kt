package com.nordic.mediahub.data

import android.content.Context
import android.content.SharedPreferences
import androidx.datastore.preferences.core.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

internal const val ENCRYPTED_PREFS_FILE_NAME = "secret_prefs"
internal const val ENCRYPTED_PREFS_MIGRATED_KEY = "migrated"
internal const val ENCRYPTED_PREFS_DATASTORE_FILE = "datastore/settings.preferences_pb"

internal object EncryptedConfigKeys {
    const val NAVIDROME_URL = "navidrome_url"
    const val NAVIDROME_USER = "navidrome_user"
    const val NAVIDROME_PASS = "navidrome_pass"

    const val AUDIOBOOK_URL = "audiobook_url"
    const val AUDIOBOOK_USER = "audiobook_user"
    const val AUDIOBOOK_PASS = "audiobook_pass"
    const val AUDIOBOOK_LAST_ITEM_ID = "audiobook_last_item_id"

        const val VIDEO_TYPE = "video_type"
        const val VIDEO_URL = "video_url"
        const val VIDEO_USER = "video_user"
        const val VIDEO_PASS = "video_pass"
        const val VIDEO_API_KEY = "video_api_key"
        const val VIDEO_PLAYBACK_SPEED = "video_playback_speed"
        const val VIDEO_PIP_ENABLED = "video_pip_enabled"
        const val VIDEO_AUTO_SKIP_INTRO = "video_auto_skip_intro"
        const val VIDEO_QUALITY_MODE = "video_quality_mode"

    val ALL = listOf(
        NAVIDROME_URL, NAVIDROME_USER, NAVIDROME_PASS,
        AUDIOBOOK_URL, AUDIOBOOK_USER, AUDIOBOOK_PASS, AUDIOBOOK_LAST_ITEM_ID,
        VIDEO_TYPE, VIDEO_URL, VIDEO_USER, VIDEO_PASS, VIDEO_API_KEY
    )
}

internal fun createEncryptedSharedPreferences(context: Context): SharedPreferences {
    val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()
    return EncryptedSharedPreferences.create(
        context,
        ENCRYPTED_PREFS_FILE_NAME,
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )
}

internal fun readLegacyDataStoreSnapshot(context: Context): Map<String, String?> {
    return runBlocking {
        val preferences = context.dataStore.data.first()
        EncryptedConfigKeys.ALL.associateWith { key ->
            preferences[stringPreferencesKeyLegacy(key)]
        }
    }
}

internal fun deleteLegacyDataStoreFile(context: Context) {
    runCatching {
        context.filesDir.resolve(ENCRYPTED_PREFS_DATASTORE_FILE).delete()
    }
}

internal fun removeLegacyCredentialKeysFromDataStore(context: Context) {
    runBlocking {
        context.dataStore.edit { prefs ->
            EncryptedConfigKeys.ALL.forEach { key ->
                prefs.remove(stringPreferencesKeyLegacy(key))
            }
        }
    }
}

internal fun runEncryptedConfigMigration(
    prefs: SharedPreferences,
    legacySnapshot: Map<String, String?>,
    removeLegacyCredentialKeys: () -> Unit
) {
    if (prefs.getBoolean(ENCRYPTED_PREFS_MIGRATED_KEY, false)) return

    val editor = prefs.edit()
    legacySnapshot.forEach { (key, value) ->
        if (value != null) editor.putString(key, value)
    }
    editor.putBoolean(ENCRYPTED_PREFS_MIGRATED_KEY, true)
    editor.commit()
    removeLegacyCredentialKeys()
}

private fun stringPreferencesKeyLegacy(name: String): androidx.datastore.preferences.core.Preferences.Key<String> =
    androidx.datastore.preferences.core.stringPreferencesKey(name)

private fun SharedPreferences.safeGetString(key: String): String =
    getString(key, null).orEmpty()

class EncryptedConfigStore(
    private val context: Context?,
    private val prefsProvider: () -> SharedPreferences = { createEncryptedSharedPreferences(context!!) },
    private val legacyDataStoreSnapshot: () -> Map<String, String?> = { readLegacyDataStoreSnapshot(context!!) },
    private val removeLegacyCredentialKeys: () -> Unit = { removeLegacyCredentialKeysFromDataStore(context!!) }
) {
    private val prefs: SharedPreferences by lazy { prefsProvider() }
    private val migrationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        migrationScope.launch { runMigrationIfNeeded() }
    }

    val navidromeConfig: Flow<NavidromeConfig> =
        configFlow(
            watchedKeys = setOf(
                EncryptedConfigKeys.NAVIDROME_URL,
                EncryptedConfigKeys.NAVIDROME_USER,
                EncryptedConfigKeys.NAVIDROME_PASS
            )
        ) { p ->
            NavidromeConfig(
                serverUrl = p.safeGetString(EncryptedConfigKeys.NAVIDROME_URL),
                username = p.safeGetString(EncryptedConfigKeys.NAVIDROME_USER),
                password = p.safeGetString(EncryptedConfigKeys.NAVIDROME_PASS)
            )
        }

    val audiobookConfig: Flow<AudiobookShelfConfig> =
        configFlow(
            watchedKeys = setOf(
                EncryptedConfigKeys.AUDIOBOOK_URL,
                EncryptedConfigKeys.AUDIOBOOK_USER,
                EncryptedConfigKeys.AUDIOBOOK_PASS
            )
        ) { p ->
            AudiobookShelfConfig(
                serverUrl = p.safeGetString(EncryptedConfigKeys.AUDIOBOOK_URL),
                username = p.safeGetString(EncryptedConfigKeys.AUDIOBOOK_USER),
                password = p.safeGetString(EncryptedConfigKeys.AUDIOBOOK_PASS)
            )
        }

    val lastAudiobookItemId: Flow<String?> =
        configFlow(
            watchedKeys = setOf(EncryptedConfigKeys.AUDIOBOOK_LAST_ITEM_ID)
        ) { p ->
            p.getString(EncryptedConfigKeys.AUDIOBOOK_LAST_ITEM_ID, null)
                ?.takeIf { it.isNotBlank() }
        }

    /** Persisted video playback speed; null when unset (defaults to 1x). */
    val videoPlaybackSpeed: Flow<Float?> =
        configFlow(
            watchedKeys = setOf(EncryptedConfigKeys.VIDEO_PLAYBACK_SPEED)
        ) { p ->
            p.getString(EncryptedConfigKeys.VIDEO_PLAYBACK_SPEED, null)
                ?.toFloatOrNull()
                ?.takeIf { it.isFinite() && it > 0f }
        }

    /** Picture-in-picture preference; enabled by default when unset. */
    val videoPipEnabled: Flow<Boolean> =
        configFlow(
            watchedKeys = setOf(EncryptedConfigKeys.VIDEO_PIP_ENABLED)
        ) { p ->
            p.getString(EncryptedConfigKeys.VIDEO_PIP_ENABLED, null)
                ?.toBooleanStrictOrNull() ?: true
        }

    /** Auto intro-skip preference; enabled by default when unset. */
    val videoAutoSkipIntro: Flow<Boolean> =
        configFlow(
            watchedKeys = setOf(EncryptedConfigKeys.VIDEO_AUTO_SKIP_INTRO)
        ) { p ->
            p.getString(EncryptedConfigKeys.VIDEO_AUTO_SKIP_INTRO, null)
                ?.toBooleanStrictOrNull() ?: true
        }

    /** Video quality mode; AUTO when unset or unrecognized. */
    val videoQualityMode: Flow<VideoQualityMode> =
        configFlow(
            watchedKeys = setOf(EncryptedConfigKeys.VIDEO_QUALITY_MODE)
        ) { p ->
            VideoQualityMode.fromName(p.getString(EncryptedConfigKeys.VIDEO_QUALITY_MODE, null))
        }

    val videoConfig: Flow<VideoServerConfig> =
        configFlow(
            watchedKeys = setOf(
                EncryptedConfigKeys.VIDEO_TYPE,
                EncryptedConfigKeys.VIDEO_URL,
                EncryptedConfigKeys.VIDEO_USER,
                EncryptedConfigKeys.VIDEO_PASS,
                EncryptedConfigKeys.VIDEO_API_KEY
            )
        ) { p ->
            VideoServerConfig(
                type = p.getString(EncryptedConfigKeys.VIDEO_TYPE, null).toVideoServerType(),
                serverUrl = p.safeGetString(EncryptedConfigKeys.VIDEO_URL),
                username = p.safeGetString(EncryptedConfigKeys.VIDEO_USER),
                password = p.safeGetString(EncryptedConfigKeys.VIDEO_PASS),
                apiKey = p.safeGetString(EncryptedConfigKeys.VIDEO_API_KEY)
            )
        }

    suspend fun saveNavidromeConfig(config: NavidromeConfig) {
        withContext(Dispatchers.IO) {
            prefs.edit().apply {
                putString(EncryptedConfigKeys.NAVIDROME_URL, config.serverUrl)
                putString(EncryptedConfigKeys.NAVIDROME_USER, config.username)
                putString(EncryptedConfigKeys.NAVIDROME_PASS, config.password)
            }.commit()
        }
    }

    suspend fun saveAudiobookConfig(config: AudiobookShelfConfig) {
        withContext(Dispatchers.IO) {
            prefs.edit().apply {
                putString(EncryptedConfigKeys.AUDIOBOOK_URL, config.serverUrl)
                putString(EncryptedConfigKeys.AUDIOBOOK_USER, config.username)
                putString(EncryptedConfigKeys.AUDIOBOOK_PASS, config.password)
            }.commit()
        }
    }

    suspend fun saveLastAudiobookItemId(itemId: String) {
        withContext(Dispatchers.IO) {
            prefs.edit()
                .putString(EncryptedConfigKeys.AUDIOBOOK_LAST_ITEM_ID, itemId)
                .commit()
        }
    }

    suspend fun saveVideoPlaybackSpeed(speed: Float) {
        withContext(Dispatchers.IO) {
            prefs.edit()
                .putString(EncryptedConfigKeys.VIDEO_PLAYBACK_SPEED, speed.toString())
                .commit()
        }
    }

    suspend fun saveVideoPipEnabled(enabled: Boolean) {
        withContext(Dispatchers.IO) {
            prefs.edit()
                .putString(EncryptedConfigKeys.VIDEO_PIP_ENABLED, enabled.toString())
                .commit()
        }
    }

    suspend fun saveVideoAutoSkipIntro(enabled: Boolean) {
        withContext(Dispatchers.IO) {
            prefs.edit()
                .putString(EncryptedConfigKeys.VIDEO_AUTO_SKIP_INTRO, enabled.toString())
                .commit()
        }
    }

    suspend fun saveVideoQualityMode(mode: VideoQualityMode) {
        withContext(Dispatchers.IO) {
            prefs.edit()
                .putString(EncryptedConfigKeys.VIDEO_QUALITY_MODE, mode.name)
                .commit()
        }
    }

    suspend fun saveVideoConfig(config: VideoServerConfig) {
        withContext(Dispatchers.IO) {
            prefs.edit().apply {
                putString(EncryptedConfigKeys.VIDEO_TYPE, config.type.name)
                putString(EncryptedConfigKeys.VIDEO_URL, config.serverUrl)
                putString(EncryptedConfigKeys.VIDEO_USER, config.username)
                putString(EncryptedConfigKeys.VIDEO_PASS, config.password)
                putString(EncryptedConfigKeys.VIDEO_API_KEY, config.apiKey)
            }.commit()
        }
    }

    internal fun runMigrationIfNeeded() {
        runEncryptedConfigMigration(
            prefs = prefs,
            legacySnapshot = legacyDataStoreSnapshot(),
            removeLegacyCredentialKeys = removeLegacyCredentialKeys
        )
    }

    private fun <T> configFlow(
        watchedKeys: Set<String>,
        read: (SharedPreferences) -> T
    ): Flow<T> = callbackFlow {
        val emitCurrent = { trySend(read(prefs)) }
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, changedKey ->
            if (changedKey == null || changedKey in watchedKeys) emitCurrent()
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        try {
            // Listen before reading: a save between the first read and listener
            // registration would otherwise be lost until another write occurs.
            emitCurrent()
            awaitClose()
        } finally {
            prefs.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }.distinctUntilChanged()
}

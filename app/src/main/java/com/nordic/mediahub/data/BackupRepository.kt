package com.nordic.mediahub.data

import android.content.Context
import android.content.SharedPreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.IOException

/** Encrypted-preferences keys for the independent backup WebDAV target; not part of legacy migration. */
internal object BackupSettingsKeys {
    const val URL = "backup_webdav_url"
    const val USER = "backup_webdav_user"
    const val PASS = "backup_webdav_pass"
    const val DIRECTORY = "backup_webdav_dir"
    const val INSECURE = "backup_webdav_insecure"
}

/**
 * Owns manual backup and restore against a user-configured WebDAV target.
 *
 * Restore is failure-safe: the archive is downloaded, decrypted and fully validated before any
 * local write; the write phase replaces the covered logical data (connections, preferences,
 * source-scoped history/bookmark/progress) and never merges.
 */
internal class BackupRepository(
    context: Context? = null,
    private val configStore: EncryptedConfigStore = EncryptedConfigStore(requireNotNull(context).applicationContext),
    private val dataStore: androidx.datastore.core.DataStore<Preferences> = requireNotNull(context).applicationContext.dataStore,
    private val prefsProvider: () -> SharedPreferences = { createEncryptedSharedPreferences(requireNotNull(context).applicationContext) },
    private val appVersion: () -> String = { readAppVersion(requireNotNull(context).applicationContext) },
    private val clientFactory: (BackupWebDavConfig) -> BackupWebDavClient = { BackupWebDavClient(it) }
) {
    // --- Backup target settings -------------------------------------------------

    suspend fun currentSettings(): BackupWebDavConfig = withContext(Dispatchers.IO) {
        val prefs = prefsProvider()
        BackupWebDavConfig(
            serverUrl = prefs.getString(BackupSettingsKeys.URL, null).orEmpty(),
            username = prefs.getString(BackupSettingsKeys.USER, null).orEmpty(),
            password = prefs.getString(BackupSettingsKeys.PASS, null).orEmpty(),
            directory = prefs.getString(BackupSettingsKeys.DIRECTORY, null)?.takeIf { it.isNotBlank() }
                ?: DEFAULT_DIRECTORY,
            allowInsecureHttp = prefs.getBoolean(BackupSettingsKeys.INSECURE, false)
        )
    }

    suspend fun saveSettings(config: BackupWebDavConfig) = withContext(Dispatchers.IO) {
        val directory = normalizeWebDavDirectory(config.directory.ifBlank { DEFAULT_DIRECTORY })
        val prefs = prefsProvider()
        if (!prefs.edit()
                .putString(BackupSettingsKeys.URL, config.serverUrl.trim())
                .putString(BackupSettingsKeys.USER, config.username.trim())
                .putString(BackupSettingsKeys.PASS, config.password)
                .putString(BackupSettingsKeys.DIRECTORY, directory.trim('/'))
                .putBoolean(BackupSettingsKeys.INSECURE, config.allowInsecureHttp)
                .commit()
        ) throw IOException("保存备份设置失败，请重试")
    }

    suspend fun testConnection(config: BackupWebDavConfig) {
        validateTarget(config)
        val client = clientFactory(config)
        client.ensureDirectory()
        client.testConnection()
    }

    // --- Backup -----------------------------------------------------------------

    /** Collects, encrypts and uploads a new archive, then prunes remote history. */
    suspend fun createBackup(password: CharArray): BackupArchiveHeader {
        val settings = currentSettings()
        validateTarget(settings)
        val payload = collectPayload()
        val header = BackupArchiveHeader(
            createdAtMillis = payload.createdAtMillis,
            appVersion = appVersion(),
            schemaVersion = BACKUP_SCHEMA_VERSION
        )
        val sealed = BackupCrypto.encrypt(password, BackupPayloadCodec.encode(payload).toByteArray(Charsets.UTF_8))
        val bytes = BackupArchiveCodec.encode(header, sealed)
        val client = clientFactory(settings)
        client.ensureDirectory()
        client.upload(backupFileName(payload.createdAtMillis), bytes)
        pruneRemote(client)
        return header
    }

    /** Lists remote archives newest-first; app version comes from the unencrypted header. */
    suspend fun listBackups(): List<BackupArchiveInfo> {
        val settings = currentSettings()
        validateTarget(settings)
        val client = clientFactory(settings)
        client.ensureDirectory()
        return client.list()
            .mapNotNull { entry ->
                val createdAt = parseBackupFileName(entry.name) ?: return@mapNotNull null
                val header = runCatching { BackupArchiveCodec.decodeHeader(client.downloadHeader(entry.name)) }.getOrNull()
                BackupArchiveInfo(entry.name, createdAt, header?.appVersion.orEmpty(), entry.size ?: 0L)
            }
            .sortedByDescending { it.createdAtMillis }
    }

    private suspend fun pruneRemote(client: BackupWebDavClient) {
        val archives = client.list()
            .mapNotNull { entry -> parseBackupFileName(entry.name)?.let { it to entry.name } }
            .sortedByDescending { it.first }
            .drop(BACKUP_KEEP_COUNT)
        archives.forEach { (_, name) -> runCatching { client.delete(name) } }
    }

    // --- Restore ----------------------------------------------------------------

    /** Downloads, decrypts and validates the archive; performs no local writes. */
    suspend fun prepareRestore(fileName: String, password: CharArray): BackupPayload {
        require(parseBackupFileName(fileName) != null) { "备份文件名不合法" }
        val settings = currentSettings()
        validateTarget(settings)
        val bytes = clientFactory(settings).download(fileName)
        val (header, sealed) = BackupArchiveCodec.decode(bytes)
        if (header.schemaVersion != BACKUP_SCHEMA_VERSION) {
            throw BackupFormatException("备份格式版本不受支持，本地数据未修改")
        }
        val json = BackupCrypto.decrypt(password, sealed).toString(Charsets.UTF_8)
        return BackupPayloadCodec.decode(json)
    }

    /** Applies a validated payload: full overwrite of the covered logical data, no merge. */
    suspend fun applyRestore(payload: BackupPayload) {
        if (payload.schemaVersion != BACKUP_SCHEMA_VERSION) {
            throw BackupFormatException("备份格式版本不受支持，本地数据未修改")
        }
        val validated = BackupPayloadCodec.decode(BackupPayloadCodec.encode(payload))
        configStore.updateSources { validated.sources }
        configStore.updatePreferences { validated.preferences }
        dataStore.edit { prefs ->
            prefs.asMap().keys.map { it.name }.filter { isBackupDataKey(it) }
                .forEach { name -> prefs.remove(stringPreferencesKey(name)) }
            validated.localData.forEach { (name, value) -> prefs[stringPreferencesKey(name)] = value }
        }
        validated.lastBooks.forEach { (sourceId, itemId) ->
            configStore.saveLastAudiobookItem(sourceId, itemId)
        }
        configStore.clearLastAudiobookItemsExcept(validated.lastBooks.keys)
    }

    // --- Payload collection -----------------------------------------------------

    private suspend fun collectPayload(): BackupPayload {
        val sources = configStore.sources.first()
        val preferences = configStore.preferences.first()
        val snapshot = dataStore.data.first()
        val localData = mutableMapOf<String, String>()
        snapshot.asMap().forEach { (key, value) ->
            if (isBackupDataKey(key.name) && value is String) localData[key.name] = value
        }
        val lastBooks = sources.sources.filter { it.kind == MediaSourceKind.AUDIOBOOKSHELF }
            .mapNotNull { source ->
                configStore.lastAudiobookItem(source.id).first()?.let { source.id to it }
            }
            .toMap()
        return BackupPayload(
            createdAtMillis = System.currentTimeMillis(),
            sources = sources,
            preferences = preferences,
            localData = localData,
            lastBooks = lastBooks
        )
    }

    private fun validateTarget(config: BackupWebDavConfig) {
        require(config.isReady) { "请先配置备份 WebDAV 服务器" }
    }

    private companion object {
        const val DEFAULT_DIRECTORY = "nordic-backup"

        fun readAppVersion(context: Context): String = try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
        } catch (_: Exception) {
            ""
        }
    }
}

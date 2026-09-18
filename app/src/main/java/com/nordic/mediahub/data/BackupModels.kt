package com.nordic.mediahub.data

import com.google.gson.Gson
import com.google.gson.JsonParser
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/** Independent WebDAV target used only by manual backup/restore; never reused for media browsing. */
data class BackupWebDavConfig(
    val serverUrl: String = "",
    val username: String = "",
    val password: String = "",
    val directory: String = "nordic-backup",
    val allowInsecureHttp: Boolean = false
) {
    val isReady: Boolean get() = serverUrl.isNotBlank()
}

/** Plaintext archive header; the payload itself is always password-encrypted. */
data class BackupArchiveHeader(
    val createdAtMillis: Long = 0,
    val appVersion: String = "",
    val schemaVersion: Int = BACKUP_SCHEMA_VERSION
)

data class BackupArchiveInfo(
    val fileName: String,
    val createdAtMillis: Long,
    val appVersion: String,
    val sizeBytes: Long
)

/**
 * Logical app data exported by a backup: media source connections (with credentials, encrypted at
 * rest inside the archive), app preferences, and source-scoped local history/bookmark/progress JSON
 * entries. Downloads, image caches and media catalogs are intentionally excluded.
 */
data class BackupPayload(
    val schemaVersion: Int = BACKUP_SCHEMA_VERSION,
    val createdAtMillis: Long = 0,
    val sources: MediaSourceState = MediaSourceState(),
    val preferences: AppPreferences = AppPreferences(),
    val localData: Map<String, String> = emptyMap(),
    val lastBooks: Map<String, String> = emptyMap()
)

class BackupFormatException(message: String, cause: Throwable? = null) : IOException(message, cause)

class BackupCryptoException(message: String, cause: Throwable? = null) : IOException(message, cause)

const val BACKUP_SCHEMA_VERSION = 1
internal const val BACKUP_KEEP_COUNT = 5
internal const val BACKUP_PASSWORD_MIN_LENGTH = 6

/** DataStore key prefixes carrying source-scoped logical data included in backups. */
internal val BACKUP_DATA_PREFIXES = listOf(
    "navidrome_play_history",
    "audiobook_bookmarks",
    "webdav_browse",
    "webdav_progress"
)

private val BACKUP_DATA_KEY_PATTERN = Regex("[A-Za-z0-9_-]{1,200}")

internal fun isBackupDataKey(name: String): Boolean =
    BACKUP_DATA_PREFIXES.any { name == it || name.startsWith("${it}_") } &&
        BACKUP_DATA_KEY_PATTERN.matches(name)

private val BACKUP_FILE_NAME_PATTERN = Regex("^nordic-backup-(\\d{8}T\\d{6})Z\\.nbk$")
private val BACKUP_FILE_NAME_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss")

internal fun backupFileName(createdAtMillis: Long): String =
    "nordic-backup-" + BACKUP_FILE_NAME_FORMAT.format(
        java.time.LocalDateTime.ofInstant(Instant.ofEpochMilli(createdAtMillis), ZoneOffset.UTC)) + "Z.nbk"

internal fun parseBackupFileName(name: String): Long? {
    val match = BACKUP_FILE_NAME_PATTERN.find(name) ?: return null
    return runCatching {
        java.time.LocalDateTime.parse(match.groupValues[1], BACKUP_FILE_NAME_FORMAT)
            .toInstant(ZoneOffset.UTC).toEpochMilli()
    }.getOrNull()
}

internal object BackupPayloadCodec {
    private val gson = Gson()

    fun encode(payload: BackupPayload): String = gson.toJson(payload)

    /** Decodes and validates a payload before anything is written locally. */
    fun decode(json: String): BackupPayload = try {
        val payload = gson.fromJson(json, BackupPayload::class.java)
        require(payload.schemaVersion == BACKUP_SCHEMA_VERSION) { "备份格式版本不受支持" }
        MediaSourceCodec.decode(MediaSourceCodec.encode(payload.sources))
        val preferences = AppPreferencesCodec.decode(AppPreferencesCodec.encode(payload.preferences))
        payload.localData.keys.forEach { key ->
            require(isBackupDataKey(key)) { "备份包含不受支持的数据键" }
        }
        payload.lastBooks.forEach { (sourceId, itemId) ->
            require(sourceId.matches(Regex("[A-Za-z0-9_-]{1,100}")) && itemId.isNotBlank()) { "备份包含无效的阅读位置" }
        }
        payload.copy(preferences = preferences)
    } catch (error: Exception) {
        throw BackupFormatException("备份内容无法识别或已不兼容，本地数据未修改", error)
    }
}

internal object BackupArchiveCodec {
    private val gson = Gson()
    private val MAGIC = byteArrayOf('N'.code.toByte(), 'D'.code.toByte(), 'B'.code.toByte(), 'K'.code.toByte())
    private const val FORMAT_VERSION = 1
    private const val HEADER_BYTES = 4 + 1 + 4
    private const val SEALED_MIN_BYTES = 16 + 12 + 16

    fun encode(header: BackupArchiveHeader, sealed: ByteArray): ByteArray {
        val headerJson = gson.toJson(header).toByteArray(Charsets.UTF_8)
        require(headerJson.size <= 4096) { "备份元数据过长" }
        val output = ByteArrayOutputStream(HEADER_BYTES + headerJson.size + sealed.size)
        output.write(MAGIC)
        output.write(FORMAT_VERSION)
        output.write(headerJson.size shr 24)
        output.write(headerJson.size shr 16)
        output.write(headerJson.size shr 8)
        output.write(headerJson.size)
        output.write(headerJson)
        output.write(sealed)
        return output.toByteArray()
    }

    /** Full archive decode; requires the complete sealed body to be present. */
    fun decode(bytes: ByteArray): Pair<BackupArchiveHeader, ByteArray> {
        val (header, offset) = readHeader(bytes)
        val sealed = bytes.copyOfRange(offset, bytes.size)
        require(sealed.size >= SEALED_MIN_BYTES) { "备份文件不完整" }
        return header to sealed
    }

    /** Header-only decode; tolerates a truncated sealed body (used by remote listing). */
    fun decodeHeader(bytes: ByteArray): BackupArchiveHeader = readHeader(bytes).first

    private fun readHeader(bytes: ByteArray): Pair<BackupArchiveHeader, Int> {
        val fail: (Throwable?) -> BackupFormatException = { cause ->
            BackupFormatException("备份文件无法识别，本地数据未修改", cause)
        }
        try {
            require(bytes.size > HEADER_BYTES) { "文件过小" }
            require(bytes.copyOfRange(0, 4).contentEquals(MAGIC)) { "文件头不匹配" }
            require(bytes[4].toInt() == FORMAT_VERSION) { "备份格式版本不受支持" }
            val headerLength = ((bytes[5].toInt() and 0xFF) shl 24) or ((bytes[6].toInt() and 0xFF) shl 16) or
                ((bytes[7].toInt() and 0xFF) shl 8) or (bytes[8].toInt() and 0xFF)
            require(headerLength in 1..4096) { "备份元数据长度非法" }
            require(bytes.size >= HEADER_BYTES + headerLength) { "文件不完整" }
            val headerJson = String(bytes, HEADER_BYTES, headerLength, Charsets.UTF_8)
            val header = gson.fromJson(JsonParser.parseString(headerJson), BackupArchiveHeader::class.java)
            require(header.schemaVersion == BACKUP_SCHEMA_VERSION) { "备份格式版本不受支持" }
            return header to HEADER_BYTES + headerLength
        } catch (error: BackupFormatException) {
            throw error
        } catch (error: IllegalArgumentException) {
            throw fail(error)
        } catch (error: Exception) {
            throw fail(error)
        }
    }
}

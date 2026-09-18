package com.nordic.mediahub.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupArchiveTest {
    private val header = BackupArchiveHeader(createdAtMillis = 1726963200000L, appVersion = "0.1.21")

    @Test fun archiveRoundTripsHeaderAndSealedBody() {
        val sealed = ByteArray(64) { it.toByte() }
        val bytes = BackupArchiveCodec.encode(header, sealed)
        val (decoded, decodedSealed) = BackupArchiveCodec.decode(bytes)
        assertEquals(header, decoded)
        assertTrue(decodedSealed.contentEquals(sealed))
    }

    @Test fun headerDecodeToleratesTruncatedSealedBody() {
        val bytes = BackupArchiveCodec.encode(header, ByteArray(64) { it.toByte() })
        val partial = bytes.copyOf(bytes.size - 32)
        assertEquals(header, BackupArchiveCodec.decodeHeader(partial))
    }

    @Test fun foreignMagicAndUnknownFormatAreRejected() {
        val bytes = BackupArchiveCodec.encode(header, ByteArray(64))
        assertThrows(BackupFormatException::class.java) {
            BackupArchiveCodec.decode(bytes.copyOf().also { it[0] = 'X'.code.toByte() })
        }
        assertThrows(BackupFormatException::class.java) {
            BackupArchiveCodec.decode(bytes.copyOf().also { it[4] = 9 })
        }
        assertThrows(BackupFormatException::class.java) { BackupArchiveCodec.decode(bytes.copyOf(8)) }
    }

    @Test fun payloadRoundTripsAndValidates() {
        val payload = BackupPayload(
            createdAtMillis = 1L,
            sources = MediaSourceState(
                sources = listOf(MediaSource(name = "nas", kind = MediaSourceKind.WEBDAV,
                    serverUrl = "https://dav.example/", username = "u", password = "p")),
                activeVideoId = null
            ),
            preferences = AppPreferences(theme = ThemeMode.DARK),
            localData = mapOf("navidrome_play_history_src1" to "[]"),
            lastBooks = mapOf("src2" to "item-9")
        )
        val decoded = BackupPayloadCodec.decode(BackupPayloadCodec.encode(payload))
        assertEquals(payload, decoded)
    }

    @Test fun payloadDecodeRejectsUnknownDataKeysAndSchema() {
        val payload = BackupPayload(localData = mapOf("unknown_prefix_x" to "value"))
        assertThrows(BackupFormatException::class.java) {
            BackupPayloadCodec.decode(BackupPayloadCodec.encode(payload))
        }
        assertThrows(BackupFormatException::class.java) {
            BackupPayloadCodec.decode(BackupPayloadCodec.encode(payload).replace("\"schemaVersion\":1", "\"schemaVersion\":99"))
        }
    }

    @Test fun backupDataKeyValidationCoversKnownPrefixesOnly() {
        assertTrue(isBackupDataKey("navidrome_play_history"))
        assertTrue(isBackupDataKey("audiobook_bookmarks_src-1"))
        assertTrue(isBackupDataKey("webdav_browse_abc"))
        assertTrue(isBackupDataKey("webdav_progress_xyz"))
        assertFalse(isBackupDataKey("navidrome_music_cache_src1"))
        assertFalse(isBackupDataKey("other_key"))
        assertFalse(isBackupDataKey("navidrome_play_history../evil"))
    }

    @Test fun backupFileNameFormatsAndParsesUtcTimestamps() {
        val millis = 1726963200000L
        val name = backupFileName(millis)
        assertTrue(name.startsWith("nordic-backup-") && name.endsWith(".nbk"))
        assertEquals(millis, parseBackupFileName(name))
        assertNull(parseBackupFileName("nordic-backup-notatime.nbk"))
        assertNull(parseBackupFileName("other-file.nbk"))
    }
}

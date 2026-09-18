package com.nordic.mediahub.data

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupCryptoTest {
    private val password = "正确密码abc".toCharArray()

    @Test fun encryptDecryptRoundTrips() {
        val plaintext = "备份负载 JSON ünïcode ✓".toByteArray(Charsets.UTF_8)
        val sealed = BackupCrypto.encrypt(password, plaintext)
        assertArrayEquals(plaintext, BackupCrypto.decrypt(password, sealed))
    }

    @Test fun wrongPasswordFailsWithoutThrowingPlaintextErrors() {
        val sealed = BackupCrypto.encrypt(password, "payload".toByteArray())
        val error = assertThrows(BackupCryptoException::class.java) {
            BackupCrypto.decrypt("错误密码xyz".toCharArray(), sealed)
        }
        assertTrue(error.message!!.contains("本地数据未修改"))
    }

    @Test fun tamperedCiphertextFailsIntegrity() {
        val sealed = BackupCrypto.encrypt(password, "payload".toByteArray())
        sealed[sealed.size - 1] = (sealed.last().toInt() xor 0x01).toByte()
        assertThrows(BackupCryptoException::class.java) { BackupCrypto.decrypt(password, sealed) }
    }

    @Test fun truncatedSealedDataIsRejected() {
        val sealed = BackupCrypto.encrypt(password, "payload".toByteArray())
        assertThrows(IllegalArgumentException::class.java) {
            BackupCrypto.decrypt(password, sealed.copyOf(sealed.size - 8))
        }
    }

    @Test fun encryptionIsRandomizedPerCall() {
        val plaintext = "same".toByteArray()
        assertFalse(BackupCrypto.encrypt(password, plaintext).contentEquals(BackupCrypto.encrypt(password, plaintext)))
    }

    @Test fun shortPasswordIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            BackupCrypto.encrypt("12345".toCharArray(), "payload".toByteArray())
        }
    }
}

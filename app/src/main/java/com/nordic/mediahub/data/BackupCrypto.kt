package com.nordic.mediahub.data

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

internal const val BACKUP_KEY_ITERATIONS = 150_000

/**
 * Password-based sealing for backup archives: PBKDF2-HMAC-SHA256 key derivation plus
 * AES-GCM authenticated encryption. The 16-byte salt and 12-byte IV are stored in front
 * of the ciphertext; the GCM tag doubles as the archive integrity check.
 */
internal object BackupCrypto {
    private const val SALT_BYTES = 16
    private const val IV_BYTES = 12
    private const val KEY_BITS = 256
    private const val TAG_BITS = 128

    fun encrypt(password: CharArray, plaintext: ByteArray): ByteArray {
        require(password.size >= BACKUP_PASSWORD_MIN_LENGTH) { "备份密码至少 $BACKUP_PASSWORD_MIN_LENGTH 位" }
        val random = SecureRandom()
        val salt = ByteArray(SALT_BYTES).also { random.nextBytes(it) }
        val iv = ByteArray(IV_BYTES).also { random.nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, deriveKey(password, salt), GCMParameterSpec(TAG_BITS, iv))
        return salt + iv + cipher.doFinal(plaintext)
    }

    fun decrypt(password: CharArray, sealed: ByteArray): ByteArray {
        val minimum = SALT_BYTES + IV_BYTES + TAG_BITS / 8
        require(sealed.size > minimum) { "加密数据不完整" }
        val salt = sealed.copyOfRange(0, SALT_BYTES)
        val iv = sealed.copyOfRange(SALT_BYTES, SALT_BYTES + IV_BYTES)
        val body = sealed.copyOfRange(SALT_BYTES + IV_BYTES, sealed.size)
        try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, deriveKey(password, salt), GCMParameterSpec(TAG_BITS, iv))
            return cipher.doFinal(body)
        } catch (error: Exception) {
            throw BackupCryptoException("备份密码错误或归档已损坏，本地数据未修改", error)
        }
    }

    private fun deriveKey(password: CharArray, salt: ByteArray): SecretKeySpec {
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec = PBEKeySpec(password, salt, BACKUP_KEY_ITERATIONS, KEY_BITS)
        return SecretKeySpec(factory.generateSecret(spec).encoded, "AES")
    }
}

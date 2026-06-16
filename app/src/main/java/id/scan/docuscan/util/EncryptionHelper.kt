package id.scan.docuscan.util

import android.util.Base64
import java.nio.charset.StandardCharsets
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import java.security.MessageDigest

object EncryptionHelper {
    private const val ALGORITHM = "AES/CBC/PKCS5Padding"

    // Generates a proper 128-bit key from a user-provided text password
    private fun generateKey(password: String): SecretKeySpec {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = password.toByteArray(StandardCharsets.UTF_8)
        val hash = digest.digest(bytes)
        // Extract 16 bytes for AES-128
        val keyBytes = hash.copyOfRange(0, 16)
        return SecretKeySpec(keyBytes, "AES")
    }

    // Generates an IV from the key
    private fun generateIv(password: String): IvParameterSpec {
        val digest = MessageDigest.getInstance("MD5")
        val bytes = password.toByteArray(StandardCharsets.UTF_8)
        val ivBytes = digest.digest(bytes)
        return IvParameterSpec(ivBytes)
    }

    fun encrypt(plainText: String, secretKey: String): String {
        if (plainText.isEmpty()) return ""
        val key = if (secretKey.isEmpty()) "DocuScanDefaultKeySecret" else secretKey
        return try {
            val cipher = Cipher.getInstance(ALGORITHM)
            cipher.init(Cipher.ENCRYPT_MODE, generateKey(key), generateIv(key))
            val encryptedBytes = cipher.doFinal(plainText.toByteArray(StandardCharsets.UTF_8))
            Base64.encodeToString(encryptedBytes, Base64.DEFAULT).trim()
        } catch (e: Exception) {
            "ENCRYPTION_ERROR: ${e.message}"
        }
    }

    fun decrypt(cipherText: String, secretKey: String): String {
        if (cipherText.isEmpty()) return ""
        val key = if (secretKey.isEmpty()) "DocuScanDefaultKeySecret" else secretKey
        return try {
            val cipher = Cipher.getInstance(ALGORITHM)
            cipher.init(Cipher.DECRYPT_MODE, generateKey(key), generateIv(key))
            val decryptedBytes = cipher.doFinal(Base64.decode(cipherText, Base64.DEFAULT))
            String(decryptedBytes, StandardCharsets.UTF_8)
        } catch (e: Exception) {
            "DECRYPTION_ERROR: Kunci dekripsi tidak cocok atau data rusak."
        }
    }
}

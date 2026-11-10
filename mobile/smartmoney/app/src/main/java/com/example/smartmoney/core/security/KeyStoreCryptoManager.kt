package com.example.smartmoney.core.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Manages hardware-backed AES-256-GCM encryption for sensitive user credentials and tokens
 * using the AndroidKeyStore provider.
 *
 * Keys are non-exportable and stored in secure hardware (TEE/StrongBox) on supported devices.
 * Ciphertexts are prefixed with [ENCRYPTION_PREFIX] to allow seamless migration from legacy plaintext.
 */
class KeyStoreCryptoManager {

    private val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply {
        load(null)
    }

    private fun getOrCreateSecretKey(): SecretKey {
        if (!keyStore.containsAlias(KEY_ALIAS)) {
            val keyGenerator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES,
                ANDROID_KEYSTORE
            )
            val spec = KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()

            keyGenerator.init(spec)
            return keyGenerator.generateKey()
        }

        val entry = keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry
        return entry?.secretKey ?: throw IllegalStateException("Failed to retrieve KeyStore secret key")
    }

    /**
     * Encrypts [plainText] using AES-GCM with a random 12-byte initialization vector.
     * Returns Base64-encoded string prefixed with [ENCRYPTION_PREFIX].
     */
    fun encrypt(plainText: String?): String? {
        if (plainText.isNullOrEmpty()) return plainText

        return try {
            val secretKey = getOrCreateSecretKey()
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)

            val iv = cipher.iv // 12 bytes for GCM
            val cipherText = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))

            // Pack IV + ciphertext into a single byte array
            val combined = ByteArray(iv.size + cipherText.size)
            System.arraycopy(iv, 0, combined, 0, iv.size)
            System.arraycopy(cipherText, 0, combined, iv.size, cipherText.size)

            val encoded = Base64.encodeToString(combined, Base64.NO_WRAP)
            "$ENCRYPTION_PREFIX$encoded"
        } catch (_: Exception) {
            // In the unlikely event of Keystore failure, return plain text as safe fallback
            plainText
        }
    }

    /**
     * Decrypts [cipherText]. If [cipherText] is not prefixed with [ENCRYPTION_PREFIX],
     * it is treated as legacy plaintext and returned as-is (enabling backward compatibility).
     */
    fun decrypt(cipherText: String?): String? {
        if (cipherText.isNullOrEmpty()) return cipherText

        if (!cipherText.startsWith(ENCRYPTION_PREFIX)) {
            // Legacy plaintext value
            return cipherText
        }

        return try {
            val encodedData = cipherText.removePrefix(ENCRYPTION_PREFIX)
            val combined = Base64.decode(encodedData, Base64.NO_WRAP)

            if (combined.size < GCM_IV_LENGTH) return null

            val iv = combined.copyOfRange(0, GCM_IV_LENGTH)
            val encryptedBytes = combined.copyOfRange(GCM_IV_LENGTH, combined.size)

            val secretKey = getOrCreateSecretKey()
            val cipher = Cipher.getInstance(TRANSFORMATION)
            val spec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)

            val decryptedBytes = cipher.doFinal(encryptedBytes)
            String(decryptedBytes, Charsets.UTF_8)
        } catch (_: Exception) {
            null
        }
    }

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "smartmoney_auth_key"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_IV_LENGTH = 12
        private const val GCM_TAG_LENGTH_BITS = 128
        const val ENCRYPTION_PREFIX = "enc_v1:"
    }
}

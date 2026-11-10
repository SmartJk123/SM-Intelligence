package com.example.smartmoney.core.security

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Manages secure, salted hashing of the application PIN and persists security preferences.
 *
 * Implements constant-time hash comparison via [MessageDigest.isEqual] to protect against timing attacks.
 * The raw PIN is never stored on disk or transmitted.
 */
class SecurityStorageHelper(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val secureRandom = SecureRandom()

    /**
     * Checks if a user has configured an App Lock PIN.
     */
    fun hasPin(): Boolean {
        val hash = prefs.getString(KEY_PIN_HASH, null)
        val salt = prefs.getString(KEY_PIN_SALT, null)
        return !hash.isNullOrBlank() && !salt.isNullOrBlank()
    }

    /**
     * Saves a new PIN by generating a cryptographically random salt and computing a salted SHA-256 hash.
     */
    fun savePin(pin: String): Boolean {
        if (pin.length < 4) return false

        val saltBytes = ByteArray(16)
        secureRandom.nextBytes(saltBytes)

        val hashBytes = computeSaltedHash(pin, saltBytes)

        val saltBase64 = Base64.encodeToString(saltBytes, Base64.NO_WRAP)
        val hashBase64 = Base64.encodeToString(hashBytes, Base64.NO_WRAP)

        prefs.edit()
            .putString(KEY_PIN_SALT, saltBase64)
            .putString(KEY_PIN_HASH, hashBase64)
            .apply()

        return true
    }

    /**
     * Verifies whether [inputPin] matches the stored salted hash in constant time.
     */
    fun verifyPin(inputPin: String): Boolean {
        val storedHashBase64 = prefs.getString(KEY_PIN_HASH, null) ?: return false
        val storedSaltBase64 = prefs.getString(KEY_PIN_SALT, null) ?: return false

        return try {
            val saltBytes = Base64.decode(storedSaltBase64, Base64.NO_WRAP)
            val storedHashBytes = Base64.decode(storedHashBase64, Base64.NO_WRAP)

            val computedHashBytes = computeSaltedHash(inputPin, saltBytes)

            MessageDigest.isEqual(storedHashBytes, computedHashBytes)
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Removes the stored PIN and disables biometric unlock.
     */
    fun clearPin() {
        prefs.edit()
            .remove(KEY_PIN_HASH)
            .remove(KEY_PIN_SALT)
            .putBoolean(KEY_BIOMETRIC_ENABLED, false)
            .apply()
    }

    /**
     * Whether biometric unlock is enabled by the user.
     */
    var isBiometricEnabled: Boolean
        get() = prefs.getBoolean(KEY_BIOMETRIC_ENABLED, false)
        set(value) {
            prefs.edit().putBoolean(KEY_BIOMETRIC_ENABLED, value).apply()
        }

    /**
     * Inactivity timeout in minutes before locking.
     * 0 = Immediately, 1 = 1 Minute (default), 5 = 5 Minutes.
     */
    var lockTimeoutMinutes: Int
        get() = prefs.getInt(KEY_LOCK_TIMEOUT_MINUTES, 1)
        set(value) {
            prefs.edit().putInt(KEY_LOCK_TIMEOUT_MINUTES, value).apply()
        }

    private fun computeSaltedHash(pin: String, salt: ByteArray): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(salt)
        var result = digest.digest(pin.toByteArray(Charsets.UTF_8))

        // Multiple rounds to increase brute-force work factor
        for (i in 1..4096) {
            digest.reset()
            digest.update(result)
            digest.update(salt)
            result = digest.digest()
        }
        return result
    }

    companion object {
        private const val PREFS_NAME = "smartmoney_security_prefs"
        private const val KEY_PIN_HASH = "sec_pin_hash"
        private const val KEY_PIN_SALT = "sec_pin_salt"
        private const val KEY_BIOMETRIC_ENABLED = "sec_biometric_enabled"
        private const val KEY_LOCK_TIMEOUT_MINUTES = "sec_lock_timeout_minutes"
    }
}

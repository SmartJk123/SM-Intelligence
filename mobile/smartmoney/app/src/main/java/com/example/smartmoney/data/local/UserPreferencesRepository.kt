package com.example.smartmoney.data.local

import android.content.Context
import android.content.SharedPreferences
import com.example.smartmoney.core.coroutine.DefaultDispatcherProvider
import com.example.smartmoney.core.coroutine.DispatcherProvider
import com.example.smartmoney.core.security.KeyStoreCryptoManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * Manages user preferences and authentication session credentials.
 * Sensitive data (tokens, identifiers, profile details) are encrypted at rest
 * using hardware-backed AES-256-GCM via [KeyStoreCryptoManager].
 */
class UserPreferencesRepository(
    context: Context,
    private val dispatchers: DispatcherProvider = DefaultDispatcherProvider()
) {

    private val prefs: SharedPreferences = context.getSharedPreferences("smartmoney_prefs", Context.MODE_PRIVATE)
    private val cryptoManager = KeyStoreCryptoManager()

    private val _hasSeenOnboarding = MutableStateFlow(prefs.getBoolean(KEY_HAS_SEEN_ONBOARDING, false))
    val hasSeenOnboarding: StateFlow<Boolean> = _hasSeenOnboarding.asStateFlow()

    suspend fun setHasSeenOnboarding(hasSeen: Boolean) = withContext(dispatchers.io) {
        prefs.edit().putBoolean(KEY_HAS_SEEN_ONBOARDING, hasSeen).apply()
        _hasSeenOnboarding.value = hasSeen
    }

    fun getAuthToken(): String? {
        val stored = prefs.getString(KEY_AUTH_TOKEN, null) ?: return null
        return cryptoManager.decrypt(stored)
    }

    fun getRefreshToken(): String? {
        val stored = prefs.getString(KEY_REFRESH_TOKEN, null) ?: return null
        return cryptoManager.decrypt(stored)
    }

    fun getUserId(): String? {
        val stored = prefs.getString(KEY_USER_ID, null) ?: return null
        return cryptoManager.decrypt(stored)
    }

    fun getUserName(): String? {
        val stored = prefs.getString(KEY_USER_NAME, null) ?: return null
        return cryptoManager.decrypt(stored)
    }

    fun getUserEmail(): String? {
        val stored = prefs.getString(KEY_USER_EMAIL, null) ?: return null
        return cryptoManager.decrypt(stored)
    }

    fun getUserPhoneNumber(): String? {
        val stored = prefs.getString(KEY_USER_PHONE, null) ?: return null
        return cryptoManager.decrypt(stored)
    }

    suspend fun saveAuthSession(
        token: String?,
        userId: String?,
        name: String?,
        email: String?,
        phoneNumber: String? = null,
        refreshToken: String? = null
    ) = withContext(dispatchers.io) {
        prefs.edit()
            .putString(KEY_AUTH_TOKEN, cryptoManager.encrypt(token))
            .putString(KEY_REFRESH_TOKEN, cryptoManager.encrypt(refreshToken))
            .putString(KEY_USER_ID, cryptoManager.encrypt(userId))
            .putString(KEY_USER_NAME, cryptoManager.encrypt(name))
            .putString(KEY_USER_EMAIL, cryptoManager.encrypt(email))
            .putString(KEY_USER_PHONE, cryptoManager.encrypt(phoneNumber))
            .apply()
    }

    suspend fun clearAuthSession() = withContext(dispatchers.io) {
        prefs.edit()
            .remove(KEY_AUTH_TOKEN)
            .remove(KEY_REFRESH_TOKEN)
            .remove(KEY_USER_ID)
            .remove(KEY_USER_NAME)
            .remove(KEY_USER_EMAIL)
            .remove(KEY_USER_PHONE)
            .apply()
    }

    companion object {
        private const val KEY_HAS_SEEN_ONBOARDING = "has_seen_onboarding"
        private const val KEY_AUTH_TOKEN = "auth_token"
        private const val KEY_REFRESH_TOKEN = "refresh_token"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_USER_NAME = "user_name"
        private const val KEY_USER_EMAIL = "user_email"
        private const val KEY_USER_PHONE = "user_phone"
    }
}

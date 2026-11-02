package com.example.smartmoney.data.local

import android.content.Context
import android.content.SharedPreferences
import com.example.smartmoney.core.coroutine.DefaultDispatcherProvider
import com.example.smartmoney.core.coroutine.DispatcherProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

class UserPreferencesRepository(
    context: Context,
    private val dispatchers: DispatcherProvider = DefaultDispatcherProvider()
) {

    private val prefs: SharedPreferences = context.getSharedPreferences("smartmoney_prefs", Context.MODE_PRIVATE)

    private val _hasSeenOnboarding = MutableStateFlow(prefs.getBoolean(KEY_HAS_SEEN_ONBOARDING, false))
    val hasSeenOnboarding: StateFlow<Boolean> = _hasSeenOnboarding.asStateFlow()

    suspend fun setHasSeenOnboarding(hasSeen: Boolean) = withContext(dispatchers.io) {
        prefs.edit().putBoolean(KEY_HAS_SEEN_ONBOARDING, hasSeen).apply()
        _hasSeenOnboarding.value = hasSeen
    }

    fun getAuthToken(): String? = prefs.getString(KEY_AUTH_TOKEN, null)

    fun getUserId(): String? = prefs.getString(KEY_USER_ID, null)

    fun getUserName(): String? = prefs.getString(KEY_USER_NAME, null)

    fun getUserEmail(): String? = prefs.getString(KEY_USER_EMAIL, null)

    fun getUserPhoneNumber(): String? = prefs.getString(KEY_USER_PHONE, null)

    suspend fun saveAuthSession(
        token: String?,
        userId: String?,
        name: String?,
        email: String?,
        phoneNumber: String? = null
    ) = withContext(dispatchers.io) {
        prefs.edit()
            .putString(KEY_AUTH_TOKEN, token)
            .putString(KEY_USER_ID, userId)
            .putString(KEY_USER_NAME, name)
            .putString(KEY_USER_EMAIL, email)
            .putString(KEY_USER_PHONE, phoneNumber)
            .apply()
    }

    suspend fun clearAuthSession() = withContext(dispatchers.io) {
        prefs.edit()
            .remove(KEY_AUTH_TOKEN)
            .remove(KEY_USER_ID)
            .remove(KEY_USER_NAME)
            .remove(KEY_USER_EMAIL)
            .remove(KEY_USER_PHONE)
            .apply()
    }

    companion object {
        private const val KEY_HAS_SEEN_ONBOARDING = "has_seen_onboarding"
        private const val KEY_AUTH_TOKEN = "auth_token"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_USER_NAME = "user_name"
        private const val KEY_USER_EMAIL = "user_email"
        private const val KEY_USER_PHONE = "user_phone"
    }
}

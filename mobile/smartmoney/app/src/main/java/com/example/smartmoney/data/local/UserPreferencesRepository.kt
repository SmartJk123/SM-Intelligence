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

    companion object {
        private const val KEY_HAS_SEEN_ONBOARDING = "has_seen_onboarding"
    }
}

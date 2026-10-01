package com.example.smartmoney

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.smartmoney.core.coroutine.DefaultDispatcherProvider
import com.example.smartmoney.core.coroutine.DispatcherProvider
import com.example.smartmoney.data.local.UserPreferencesRepository
import com.example.smartmoney.domain.repository.AuthRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(
    private val userPreferencesRepository: UserPreferencesRepository,
    private val authRepository: AuthRepository,
    private val dispatchers: DispatcherProvider = DefaultDispatcherProvider()
) : ViewModel() {

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    // Determine start destination: "onboarding", "signup", "login", or "dashboard"
    val startDestination: StateFlow<String?> = combine(
        userPreferencesRepository.hasSeenOnboarding,
        authRepository.observeAuthState()
    ) { hasSeenOnboarding, isLoggedIn ->
        val destination = when {
            isLoggedIn -> "dashboard"
            !hasSeenOnboarding -> "onboarding"
            else -> "login"
        }
        
        // Artificial small delay to ensure smooth splash screen transition
        delay(300)
        _isLoading.value = false
        
        destination
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = null
    )

    fun completeOnboarding() {
        viewModelScope.launch(dispatchers.io) {
            userPreferencesRepository.setHasSeenOnboarding(true)
        }
    }

    class Factory(
        private val userPreferencesRepository: UserPreferencesRepository,
        private val authRepository: AuthRepository,
        private val dispatchers: DispatcherProvider = DefaultDispatcherProvider()
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return MainViewModel(userPreferencesRepository, authRepository, dispatchers) as T
        }
    }
}

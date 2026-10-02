package com.example.smartmoney.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.smartmoney.core.coroutine.DefaultDispatcherProvider
import com.example.smartmoney.core.coroutine.DispatcherProvider
import com.example.smartmoney.domain.repository.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * ViewModel managing authentication state and actions, routing all operations
 * through the Spring Boot backend repository with robust error handling.
 */
class AuthViewModel(
    private val authRepository: AuthRepository,
    private val dispatchers: DispatcherProvider = DefaultDispatcherProvider()
) : ViewModel() {

    private val _authState = MutableStateFlow<AuthState>(AuthState.Idle)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    // Backward-compatible alias for existing consumers referencing uiState
    val uiState: StateFlow<AuthState> get() = authState

    val isLoggedIn: StateFlow<Boolean> = authRepository.observeAuthState()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = authRepository.isUserLoggedIn()
        )

    fun currentUserId(): String? = authRepository.currentUserId()

    fun currentUserName(): String? = authRepository.currentUserName()

    fun currentUserEmail(): String? = authRepository.currentUserEmail()

    fun currentUserPhoneNumber(): String? = authRepository.currentUserPhoneNumber()

    /**
     * Registers a new user with the Spring Boot backend.
     * Collects the Result from the repository and emits Success or Error state.
     */
    fun signUp(
        name: String,
        email: String,
        password: String,
        phoneNumber: String? = null
    ) {
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            val result = authRepository.signUp(
                name = name,
                email = email,
                password = password,
                phoneNumber = phoneNumber
            )
            result.fold(
                onSuccess = {
                    _authState.value = AuthState.Success("Account created successfully!")
                },
                onFailure = { error ->
                    val errorMessage = error.localizedMessage ?: error.message ?: "Sign up failed. Please try again."
                    _authState.value = AuthState.Error(errorMessage)
                }
            )
        }
    }

    /**
     * Authenticates an existing user with the Spring Boot backend.
     * Collects the Result from the repository and emits Success or Error state.
     */
    fun signIn(
        email: String,
        password: String
    ) {
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            val result = authRepository.signIn(
                email = email,
                password = password
            )
            result.fold(
                onSuccess = {
                    _authState.value = AuthState.Success("Sign in successful!")
                },
                onFailure = { error ->
                    val errorMessage = error.localizedMessage ?: error.message ?: "Invalid email or password."
                    _authState.value = AuthState.Error(errorMessage)
                }
            )
        }
    }

    /**
     * Clears user session and resets UI state.
     */
    fun signOut() {
        viewModelScope.launch {
            authRepository.signOut()
            _authState.value = AuthState.Idle
        }
    }

    /**
     * Resets authentication state back to Idle (e.g. when navigating or clearing form).
     */
    fun clearState() {
        _authState.value = AuthState.Idle
    }

    class Factory(
        private val authRepository: AuthRepository,
        private val dispatchers: DispatcherProvider = DefaultDispatcherProvider()
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return AuthViewModel(authRepository, dispatchers) as T
        }
    }
}

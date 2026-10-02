package com.example.smartmoney.ui.auth

/**
 * Represents the authentication UI state for the Sign Up and Login flows.
 */
sealed interface AuthState {
    data object Idle : AuthState
    data object Loading : AuthState
    data class Success(val message: String) : AuthState
    data class Error(val message: String) : AuthState
}

/**
 * Backward-compatible alias for existing references to [AuthUiState].
 */
typealias AuthUiState = AuthState

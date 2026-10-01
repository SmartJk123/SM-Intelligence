package com.example.smartmoney.domain.repository

import kotlinx.coroutines.flow.Flow

/**
 * Domain repository contract for authentication operations.
 * Decoupled from any specific cloud provider; implemented via Spring Boot Retrofit endpoints.
 */
interface AuthRepository {
    suspend fun signUp(name: String, email: String, password: String, phoneNumber: String? = null): Result<Unit>
    suspend fun signIn(email: String, password: String): Result<Unit>
    suspend fun signOut(): Result<Unit>
    fun currentUserId(): String?
    fun currentUserName(): String?
    fun currentUserEmail(): String?
    fun currentUserPhoneNumber(): String?
    fun isUserLoggedIn(): Boolean
    fun observeAuthState(): Flow<Boolean>
}

package com.example.smartmoney.domain.repository

import com.example.smartmoney.domain.model.User
import kotlinx.coroutines.flow.Flow

interface UserRepository {
    fun getUserProfile(userId: String): Flow<User?>
    suspend fun syncUserProfile(userId: String): Result<User>
    suspend fun updateUserProfile(user: User): Result<Unit>
}

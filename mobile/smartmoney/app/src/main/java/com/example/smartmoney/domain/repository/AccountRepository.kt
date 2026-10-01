package com.example.smartmoney.domain.repository

import com.example.smartmoney.domain.model.Account
import kotlinx.coroutines.flow.Flow

interface AccountRepository {
    fun getAccountsFlow(userId: String): Flow<List<Account>>
    suspend fun syncAccounts(userId: String): Result<Unit>
    suspend fun createAccount(account: Account): Result<Account>
    suspend fun deleteAccount(id: String): Result<Unit>
}

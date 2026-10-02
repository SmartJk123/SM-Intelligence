package com.example.smartmoney.domain.repository

import com.example.smartmoney.domain.model.AccountConnection
import kotlinx.coroutines.flow.Flow

interface AccountConnectionRepository {
    fun getConnectionsFlow(accountId: String): Flow<List<AccountConnection>>
    suspend fun syncConnections(accountId: String): Result<Unit>
}

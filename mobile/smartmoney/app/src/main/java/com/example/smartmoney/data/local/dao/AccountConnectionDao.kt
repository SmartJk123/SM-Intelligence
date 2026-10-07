package com.example.smartmoney.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.example.smartmoney.data.local.entity.AccountConnectionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AccountConnectionDao {

    @Query("SELECT * FROM account_connections WHERE accountId = :accountId")
    fun getConnectionsForAccount(accountId: String): Flow<List<AccountConnectionEntity>>

    @Transaction
    @Upsert
    suspend fun upsertConnections(connections: List<AccountConnectionEntity>)
}

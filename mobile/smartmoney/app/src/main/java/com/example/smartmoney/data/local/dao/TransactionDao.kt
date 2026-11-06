package com.example.smartmoney.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.example.smartmoney.data.local.entity.TransactionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {

    @Query("SELECT * FROM transactions WHERE userId = :userId ORDER BY timestamp DESC")
    fun getTransactionsForUser(userId: String): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE userId = :userId AND accountId = :accountId ORDER BY timestamp DESC")
    fun getTransactionsForAccount(userId: String, accountId: String): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions ORDER BY timestamp DESC")
    fun getAllTransactions(): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE accountId = :accountId ORDER BY timestamp DESC")
    fun getTransactionsForAccount(accountId: String): Flow<List<TransactionEntity>>

    @Transaction
    @Upsert
    suspend fun upsertTransactions(transactions: List<TransactionEntity>)

    @Upsert
    suspend fun upsertTransaction(transaction: TransactionEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertTransaction(transaction: TransactionEntity): Long

    @Query("SELECT * FROM transactions WHERE providerTransactionId = :providerTxId OR id = :id LIMIT 1")
    suspend fun findTransactionByProviderIdOrId(providerTxId: String, id: String): TransactionEntity?

    @Query("SELECT * FROM transactions WHERE providerTransactionId = :providerTxId LIMIT 1")
    suspend fun findTransactionByProviderId(providerTxId: String): TransactionEntity?

    @Query("DELETE FROM transactions WHERE userId = :userId AND (accountId = :accountId OR accountId IN (SELECT accountId FROM accounts WHERE id = :accountId AND userId = :userId))")
    suspend fun deleteTransactionsForAccount(userId: String, accountId: String)

    @Query("DELETE FROM transactions WHERE userId = :userId")
    suspend fun clearTransactionsForUser(userId: String)

    @Query("DELETE FROM transactions")
    suspend fun clearAll()
}

package com.example.smartmoney.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.example.smartmoney.data.local.entity.AccountEntity
import kotlinx.coroutines.flow.Flow
import java.math.BigDecimal

@Dao
interface AccountDao {

    @Query("SELECT * FROM accounts WHERE userId = :userId ORDER BY createdAt DESC")
    fun getAccountsForUser(userId: String): Flow<List<AccountEntity>>

    @Query("SELECT * FROM accounts WHERE id = :id")
    suspend fun getAccountById(id: String): AccountEntity?

    @Query("SELECT * FROM accounts WHERE institution = 'KCB' OR accountName LIKE '%KCB%' LIMIT 1")
    suspend fun getKcbAccount(): AccountEntity?

    @Query("UPDATE accounts SET availableBalance = :newBalance, ledgerBalance = :newBalance WHERE institution = 'KCB' OR accountName LIKE '%KCB%'")
    suspend fun updateKcbBalance(newBalance: BigDecimal)

    @Query("UPDATE accounts SET availableBalance = availableBalance + :delta, ledgerBalance = ledgerBalance + :delta WHERE institution = 'KCB' OR accountName LIKE '%KCB%'")
    suspend fun adjustKcbBalance(delta: BigDecimal)

    @Transaction
    @Upsert
    suspend fun upsertAccounts(accounts: List<AccountEntity>)

    @Upsert
    suspend fun upsertAccount(account: AccountEntity)

    @Query("DELETE FROM accounts WHERE userId = :userId")
    suspend fun clearAccountsForUser(userId: String)

    @Query("DELETE FROM accounts WHERE id = :id OR accountId = :id")
    suspend fun deleteAccountById(id: String)
}

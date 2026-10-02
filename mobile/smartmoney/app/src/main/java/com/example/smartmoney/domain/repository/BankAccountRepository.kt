package com.example.smartmoney.domain.repository

import com.example.smartmoney.domain.model.BankAccount
import kotlinx.coroutines.flow.Flow
import java.math.BigDecimal

/**
 * Domain repository interface for managing linked bank accounts.
 */
interface BankAccountRepository {

    /**
     * Adds a new bank account to the remote backend.
     *
     * @param bankName Name of the bank (e.g., "NCBA", "Equity").
     * @param accountNumber The account or card number.
     * @param cardType Card type classification ("Debit", "Credit").
     * @return [Result.success] if insertion succeeded, or [Result.failure] with error details.
     */
    suspend fun addBankAccount(
        bankName: String,
        accountNumber: String,
        cardType: String
    ): Result<Unit>

    /**
     * Removes a linked bank account from the remote backend and database.
     *
     * @param id The account ID (UUID) or account number.
     * @return [Result.success] on deletion, or [Result.failure] on error.
     */
    suspend fun removeBankAccount(id: String): Result<Unit>

    /**
     * Observes the list of linked bank accounts.
     *
     * @return A cold/hot [Flow] emitting the latest list of [BankAccount] models.
     */
    fun getBankAccounts(): Flow<List<BankAccount>>

    /**
     * Adjusts the balance of the KCB account in the local database.
     *
     * @param delta The positive (credit) or negative (debit) amount to apply.
     */
    suspend fun adjustKcbBalance(delta: BigDecimal)
}

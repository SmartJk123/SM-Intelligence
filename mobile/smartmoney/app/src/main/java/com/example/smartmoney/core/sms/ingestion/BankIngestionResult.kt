package com.example.smartmoney.core.sms.ingestion

import com.example.smartmoney.data.local.entity.TransactionEntity

/**
 * Result representing the outcome of an offline Bank transaction ingestion attempt (KCB or NCBA).
 */
sealed interface BankIngestionResult {

    /**
     * The transaction was validated, mapped to a bank account, and successfully persisted into Room.
     */
    data class Success(
        val transaction: TransactionEntity,
        val message: String = "Bank transaction successfully recorded in local storage."
    ) : BankIngestionResult

    /**
     * The transaction was rejected because a record with this reference or deterministic ID already exists.
     */
    data class Duplicate(
        val reference: String,
        val existingTransactionId: String,
        val message: String = "Transaction $reference has already been recorded."
    ) : BankIngestionResult

    /**
     * The movement failed basic invariant validation (e.g. blank reference, non-positive amount).
     */
    data class InvalidMovement(
        val reason: String
    ) : BankIngestionResult

    /**
     * No eligible account could be identified or auto-provisioned for the transaction.
     */
    data class MissingAccount(
        val reason: String
    ) : BankIngestionResult

    /**
     * Multiple eligible bank accounts were found and could not be disambiguated by account numbers or suffixes.
     */
    data class AmbiguousAccount(
        val reason: String,
        val candidateAccountIds: List<String> = emptyList()
    ) : BankIngestionResult

    /**
     * An unexpected Room or SQLite database failure occurred.
     */
    data class DatabaseError(
        val cause: Throwable,
        val message: String = cause.message ?: "Database persistence operation failed."
    ) : BankIngestionResult
}

package com.example.smartmoney.core.sms.ingestion

import com.example.smartmoney.data.local.entity.TransactionEntity

/**
 * Result representing the outcome of an offline M-PESA transaction ingestion attempt.
 */
sealed interface MpesaIngestionResult {

    /**
     * The transaction was validated, mapped, and successfully persisted into the local Room database.
     *
     * @property transaction The newly persisted [TransactionEntity].
     * @property message Descriptive confirmation message.
     */
    data class Success(
        val transaction: TransactionEntity,
        val message: String = "Transaction successfully imported into local storage."
    ) : MpesaIngestionResult

    /**
     * The transaction was rejected because a transaction with the same receipt reference
     * (or deterministic M-PESA ID) already exists in the database.
     *
     * @property reference The M-PESA receipt reference code (e.g. "QH789XYZ12").
     * @property existingTransactionId The primary key ID of the pre-existing record.
     * @property message Explanatory message.
     */
    data class Duplicate(
        val reference: String,
        val existingTransactionId: String,
        val message: String = "Transaction $reference has already been imported."
    ) : MpesaIngestionResult

    /**
     * The parsed movement failed validation checks (e.g., blank reference, zero or negative amount).
     *
     * @property reason Specific reason explaining why the movement is invalid.
     */
    data class InvalidMovement(
        val reason: String
    ) : MpesaIngestionResult

    /**
     * An eligible M-PESA account could not be found for the target user.
     *
     * Ingestion will not create duplicate accounts or arbitrarily bind the transaction to
     * an unrelated banking account.
     *
     * @property reason Explanatory reason detailing what was searched.
     */
    data class MissingAccount(
        val reason: String
    ) : MpesaIngestionResult

    /**
     * Multiple eligible M-PESA accounts were found for the target user and no [targetAccountId]
     * was explicitly provided to disambiguate which account should receive the transaction.
     *
     * @property reason Explanatory reason listing the ambiguous candidate accounts.
     * @property candidateAccountIds List of account IDs identified as potential matches.
     */
    data class AmbiguousAccount(
        val reason: String,
        val candidateAccountIds: List<String> = emptyList()
    ) : MpesaIngestionResult

    /**
     * An unexpected database or I/O failure occurred while checking or persisting the transaction.
     *
     * @property cause The underlying exception thrown by Room / SQLite.
     * @property message A human-readable error description.
     */
    data class DatabaseError(
        val cause: Throwable,
        val message: String = cause.message ?: "Database persistence operation failed."
    ) : MpesaIngestionResult
}

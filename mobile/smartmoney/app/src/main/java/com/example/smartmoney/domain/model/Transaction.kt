package com.example.smartmoney.domain.model

import androidx.compose.runtime.Immutable
import java.math.BigDecimal

/**
 * Identified origin of a financial transaction.
 */
enum class TransactionSource {
    REMOTE,           // Confirmed by remote server (transactions-service / bank integration)
    LOCAL_SMS,        // Ingested locally from M-PESA SMS notification
    LOCAL_BANK_SMS,   // Ingested locally from Bank SMS notification
    LOCAL_INVOICE,    // Ingested locally from scanned invoice OCR
    LOCAL_MANUAL      // Manually entered by user
}

/**
 * Synchronization and verification status against the authoritative remote ledger.
 */
enum class TransactionSyncStatus {
    SYNCED,       // Verified with backend server (source of truth)
    LOCAL_ONLY    // Ingested locally, not yet synced/reconciled with remote
}

/**
 * Pure domain model representing a financial/energy transaction.
 * Monetary amount uses BigDecimal to avoid rounding and precision issues.
 */
@Immutable
data class Transaction(
    val id: String,
    val accountId: String,
    val amount: BigDecimal,
    val type: String, // "CREDIT", "DEBIT", "TRANSFER", etc.
    val timestamp: String,
    val description: String? = null,
    val providerTransactionId: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
    val status: String = "COMPLETED",
    val source: String = TransactionSource.REMOTE.name,
    val syncStatus: String = TransactionSyncStatus.SYNCED.name
) {
    val transactionType: String
        get() = type
}

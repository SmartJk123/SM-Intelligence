package com.example.smartmoney.domain.model

import androidx.compose.runtime.Immutable
import java.math.BigDecimal

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
    val source: String = "BANK"
) {
    val transactionType: String
        get() = type
}

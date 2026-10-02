package com.example.smartmoney.domain.model

import androidx.compose.runtime.Immutable
import java.math.BigDecimal

/**
 * Pure domain model representing a linked bank account.
 *
 * @property id Unique identifier for the bank account record.
 * @property bankName Name of the banking institution (e.g., "NCBA", "Equity", "KCB", "Stanbic").
 * @property accountNumber Full account or card number.
 * @property cardType Type of card associated with the account (e.g., "Debit", "Credit").
 * @property balance Current available balance of the account or card.
 */
@Immutable
data class BankAccount(
    val id: String,
    val bankName: String,
    val accountNumber: String,
    val cardType: String,
    val balance: BigDecimal = BigDecimal.ZERO
) {
    /**
     * Formats the account number as a masked string (e.g., "**** 1234").
     */
    val maskedAccountNumber: String
        get() {
            val trimmed = accountNumber.trim()
            if (trimmed.startsWith("*")) return trimmed
            return if (trimmed.length > 4) {
                "**** " + trimmed.takeLast(4)
            } else {
                "**** $trimmed"
            }
        }
}

package com.example.smartmoney.domain.model

import androidx.compose.runtime.Immutable
import java.math.BigDecimal

/**
 * Pure domain model representing an energy or financial account.
 * Monetary balances use BigDecimal to prevent IEEE-754 floating point precision loss.
 */
@Immutable
data class Account(
    val id: String,
    val userId: String,
    val accountId: String,
    val accountName: String,
    val institution: String,
    val accountType: String,
    val maskedIdentifier: String? = null,
    val currency: String = "KES",
    val ledgerBalance: BigDecimal = BigDecimal.ZERO,
    val availableBalance: BigDecimal = BigDecimal.ZERO,
    val creditOutstanding: BigDecimal = BigDecimal.ZERO,
    val creditLimit: BigDecimal? = null,
    val availableCredit: BigDecimal? = null,
    val accountStatus: String = "active",
    val connectionStatus: String = "pending",
    val lastUpdated: String? = null,
    val dataSource: String = "MANUAL",
    val createdAt: String? = null,
    val updatedAt: String? = null
)

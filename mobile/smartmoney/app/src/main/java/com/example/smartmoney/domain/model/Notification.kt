package com.example.smartmoney.domain.model

import androidx.compose.runtime.Immutable
import java.math.BigDecimal

/**
 * Pure domain model representing an in-app transaction or system notification.
 */
@Immutable
data class Notification(
    val id: String,
    val title: String,
    val message: String,
    val amount: BigDecimal? = null,
    val type: String, // "CREDIT", "DEBIT", "SYSTEM"
    val timestamp: String,
    val reference: String? = null,
    val isRead: Boolean = false
)

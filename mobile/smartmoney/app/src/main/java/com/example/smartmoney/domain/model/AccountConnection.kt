package com.example.smartmoney.domain.model

import androidx.compose.runtime.Immutable

/**
 * Pure domain model representing the connection state with an external provider/institution.
 */
@Immutable
data class AccountConnection(
    val id: String,
    val accountId: String,
    val provider: String,
    val connectionStatus: String = "connected",
    val externalIdentifier: String? = null,
    val lastSyncAt: String? = null,
    val errorMessage: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null
)

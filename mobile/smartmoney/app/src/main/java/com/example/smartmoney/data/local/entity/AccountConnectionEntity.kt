package com.example.smartmoney.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.smartmoney.domain.model.AccountConnection

/**
 * Local Room cache entity for public.account_connections table.
 */
@Entity(
    tableName = "account_connections",
    indices = [
        Index(value = ["accountId"])
    ]
)
data class AccountConnectionEntity(
    @PrimaryKey
    val id: String,
    val accountId: String,
    val provider: String,
    val connectionStatus: String = "connected",
    val externalIdentifier: String? = null,
    val lastSyncAt: String? = null,
    val errorMessage: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null
) {
    fun toDomain(): AccountConnection = AccountConnection(
        id = id,
        accountId = accountId,
        provider = provider,
        connectionStatus = connectionStatus,
        externalIdentifier = externalIdentifier,
        lastSyncAt = lastSyncAt,
        errorMessage = errorMessage,
        createdAt = createdAt,
        updatedAt = updatedAt
    )

    companion object {
        fun fromDomain(conn: AccountConnection): AccountConnectionEntity = AccountConnectionEntity(
            id = conn.id,
            accountId = conn.accountId,
            provider = conn.provider,
            connectionStatus = conn.connectionStatus,
            externalIdentifier = conn.externalIdentifier,
            lastSyncAt = conn.lastSyncAt,
            errorMessage = conn.errorMessage,
            createdAt = conn.createdAt,
            updatedAt = conn.updatedAt
        )
    }
}

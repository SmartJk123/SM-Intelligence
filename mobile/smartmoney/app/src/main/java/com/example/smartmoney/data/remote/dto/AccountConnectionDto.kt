package com.example.smartmoney.data.remote.dto

import com.example.smartmoney.domain.model.AccountConnection
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AccountConnectionDto(
    @SerialName("id") val id: String,
    @SerialName("account_id") val accountId: String,
    @SerialName("provider") val provider: String,
    @SerialName("connection_status") val connectionStatus: String = "connected",
    @SerialName("external_identifier") val externalIdentifier: String? = null,
    @SerialName("last_sync_at") val lastSyncAt: String? = null,
    @SerialName("error_message") val errorMessage: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null
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
        fun fromDomain(conn: AccountConnection): AccountConnectionDto = AccountConnectionDto(
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

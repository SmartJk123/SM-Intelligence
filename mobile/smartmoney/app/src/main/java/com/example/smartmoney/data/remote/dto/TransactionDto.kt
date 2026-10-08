package com.example.smartmoney.data.remote.dto

import com.example.smartmoney.domain.model.Transaction
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.math.BigDecimal

@Serializable
data class TransactionDto(
    @SerialName("id") val id: String,
    @SerialName("account_id") val accountId: String,
    @Serializable(with = BigDecimalSerializer::class)
    @SerialName("amount") val amount: BigDecimal,
    @SerialName("transaction_type") val transactionType: String,
    @SerialName("timestamp") val timestamp: String,
    @SerialName("description") val description: String? = null,
    @SerialName("provider_transaction_id") val providerTransactionId: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null
) {
    fun toDomain(): Transaction = Transaction(
        id = id,
        accountId = accountId,
        amount = amount,
        type = transactionType,
        timestamp = timestamp,
        description = description,
        providerTransactionId = providerTransactionId,
        createdAt = createdAt,
        updatedAt = updatedAt
    )

    companion object {
        fun fromDomain(tx: Transaction): TransactionDto = TransactionDto(
            id = tx.id,
            accountId = tx.accountId,
            amount = tx.amount,
            transactionType = tx.transactionType,
            timestamp = tx.timestamp,
            description = tx.description,
            providerTransactionId = tx.providerTransactionId,
            createdAt = tx.createdAt,
            updatedAt = tx.updatedAt
        )
    }
}
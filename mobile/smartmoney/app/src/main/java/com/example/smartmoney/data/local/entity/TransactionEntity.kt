package com.example.smartmoney.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.smartmoney.domain.model.Transaction
import java.math.BigDecimal

/**
 * Local Room cache entity for public.transactions table.
 */
@Entity(
    tableName = "transactions",
    indices = [
        Index(value = ["userId"]),
        Index(value = ["accountId"]),
        Index(value = ["timestamp"]),
        Index(value = ["userId", "timestamp"]),
        Index(value = ["userId", "accountId", "timestamp"])
    ]
)
data class TransactionEntity(
    @PrimaryKey
    val id: String,
    val userId: String,
    val accountId: String,
    val amount: BigDecimal,
    val transactionType: String,
    val timestamp: String,
    val description: String? = null,
    val providerTransactionId: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null
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
        fun fromDomain(tx: Transaction, userId: String = ""): TransactionEntity = TransactionEntity(
            id = tx.id,
            userId = userId,
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

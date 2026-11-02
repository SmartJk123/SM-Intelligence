package com.example.smartmoney.data.remote.dto

import com.example.smartmoney.domain.model.Transaction
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.math.BigDecimal

@Serializable
data class CreateTransactionBackendRequest(
    @SerialName("accountId")
    val accountId: String,

    @Serializable(with = BigDecimalSerializer::class)
    @SerialName("amount")
    val amount: BigDecimal,

    @SerialName("currency")
    val currency: String = "KES",

    @SerialName("transactionType")
    val transactionType: String,

    @SerialName("counterparty")
    val counterparty: String? = null,

    @SerialName("paymentMethod")
    val paymentMethod: String? = null,

    @SerialName("providerReference")
    val providerReference: String? = null,

    @SerialName("description")
    val description: String? = null,

    @SerialName("categoryId")
    val categoryId: String? = null,

    @SerialName("transactionDate")
    val transactionDate: String? = null
)

@Serializable
data class TransactionBackendResponse(
    @SerialName("id")
    val id: String,

    @SerialName("accountId")
    val accountId: String,

    @SerialName("relatedTransactionId")
    val relatedTransactionId: String? = null,

    @Serializable(with = BigDecimalSerializer::class)
    @SerialName("amount")
    val amount: BigDecimal,

    @SerialName("currency")
    val currency: String = "KES",

    @SerialName("transactionType")
    val transactionType: String,

    @SerialName("counterparty")
    val counterparty: String? = null,

    @SerialName("paymentMethod")
    val paymentMethod: String? = null,

    @SerialName("categoryId")
    val categoryId: String? = null,

    @SerialName("status")
    val status: String? = null,

    @SerialName("providerReference")
    val providerReference: String? = null,

    @SerialName("description")
    val description: String? = null,

    @SerialName("transactionDate")
    val transactionDate: String? = null,

    @SerialName("postingDate")
    val postingDate: String? = null,

    @SerialName("createdAt")
    val createdAt: String? = null,

    @SerialName("updatedAt")
    val updatedAt: String? = null
) {
    fun toDomain(): Transaction = Transaction(
        id = id,
        accountId = accountId,
        amount = amount,
        type = transactionType,
        timestamp = transactionDate ?: createdAt ?: java.time.Instant.now().toString(),
        description = description,
        providerTransactionId = providerReference,
        createdAt = createdAt,
        updatedAt = updatedAt
    )

    companion object {
        fun fromDomain(domain: Transaction): CreateTransactionBackendRequest = CreateTransactionBackendRequest(
            accountId = domain.accountId,
            amount = domain.amount,
            currency = "KES",
            transactionType = domain.type.uppercase(),
            counterparty = null,
            paymentMethod = null,
            providerReference = domain.providerTransactionId,
            description = domain.description,
            categoryId = null,
            transactionDate = domain.timestamp.takeIf { it.isNotBlank() }
        )
    }
}

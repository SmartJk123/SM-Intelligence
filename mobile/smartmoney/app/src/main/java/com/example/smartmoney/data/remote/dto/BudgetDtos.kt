package com.example.smartmoney.data.remote.dto

import com.example.smartmoney.domain.model.Budget
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.math.BigDecimal
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

@Serializable
data class CreateBudgetBackendRequest(
    @SerialName("userId")
    val userId: String,

    @SerialName("category")
    val category: String,

    @Serializable(with = BigDecimalSerializer::class)
    @SerialName("monthlyLimit")
    val monthlyLimit: BigDecimal
)

@Serializable
data class BudgetBackendResponse(
    @SerialName("id")
    val id: String,

    @SerialName("userId")
    val userId: String,

    @SerialName("category")
    val category: String,

    @SerialName("currency")
    val currency: String = "KES",

    @Serializable(with = BigDecimalSerializer::class)
    @SerialName("monthlyLimit")
    val monthlyLimit: BigDecimal,

    @Serializable(with = BigDecimalSerializer::class)
    @SerialName("alertThresholdPercentage")
    val alertThresholdPercentage: BigDecimal? = null,

    @SerialName("status")
    val status: String? = null,

    @SerialName("createdAt")
    val createdAt: String? = null
) {
    fun toDomain(): Budget {
        val now = LocalDate.now()
        val startOfMonth = now.with(TemporalAdjusters.firstDayOfMonth())
        val endOfMonth = now.with(TemporalAdjusters.lastDayOfMonth())
        val minor = monthlyLimit.multiply(BigDecimal(100)).toLong()
        val threshold = alertThresholdPercentage?.toInt() ?: 80

        return Budget(
            id = id,
            category = category,
            allocatedMinor = minor,
            start = startOfMonth,
            end = endOfMonth,
            threshold = threshold
        )
    }

    companion object {
        fun fromDomain(domain: Budget, userId: String): CreateBudgetBackendRequest {
            val limit = BigDecimal(domain.allocatedMinor).divide(BigDecimal(100))
            return CreateBudgetBackendRequest(
                userId = userId,
                category = domain.category,
                monthlyLimit = limit
            )
        }
    }
}

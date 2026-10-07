package com.example.smartmoney.data.remote.dto

import com.example.smartmoney.domain.model.Account
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.math.BigDecimal

@Serializable
data class AccountDto(
    @SerialName("id") val id: String,
    @SerialName("user_id") val userId: String,
    @SerialName("account_id") val accountId: String,
    @SerialName("account_name") val accountName: String,
    @SerialName("institution") val institution: String,
    @SerialName("account_type") val accountType: String,
    @SerialName("masked_identifier") val maskedIdentifier: String? = null,
    @SerialName("currency") val currency: String = "KES",
    @Serializable(with = BigDecimalSerializer::class)
    @SerialName("ledger_balance") val ledgerBalance: BigDecimal = BigDecimal.ZERO,
    @Serializable(with = BigDecimalSerializer::class)
    @SerialName("available_balance") val availableBalance: BigDecimal = BigDecimal.ZERO,
    @Serializable(with = BigDecimalSerializer::class)
    @SerialName("credit_outstanding") val creditOutstanding: BigDecimal = BigDecimal.ZERO,
    @Serializable(with = BigDecimalSerializer::class)
    @SerialName("credit_limit") val creditLimit: BigDecimal? = null,
    @Serializable(with = BigDecimalSerializer::class)
    @SerialName("available_credit") val availableCredit: BigDecimal? = null,
    @SerialName("account_status") val accountStatus: String = "active",
    @SerialName("connection_status") val connectionStatus: String = "pending",
    @SerialName("last_updated") val lastUpdated: String? = null,
    @SerialName("data_source") val dataSource: String = "MANUAL",
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null
) {
    fun toDomain(): Account = Account(
        id = id,
        userId = userId,
        accountId = accountId,
        accountName = accountName,
        institution = institution,
        accountType = accountType,
        maskedIdentifier = maskedIdentifier,
        currency = currency,
        ledgerBalance = ledgerBalance,
        availableBalance = availableBalance,
        creditOutstanding = creditOutstanding,
        creditLimit = creditLimit,
        availableCredit = availableCredit,
        accountStatus = accountStatus,
        connectionStatus = connectionStatus,
        lastUpdated = lastUpdated,
        dataSource = dataSource,
        createdAt = createdAt,
        updatedAt = updatedAt
    )

    companion object {
        fun fromDomain(account: Account): AccountDto = AccountDto(
            id = account.id,
            userId = account.userId,
            accountId = account.accountId,
            accountName = account.accountName,
            institution = account.institution,
            accountType = account.accountType,
            maskedIdentifier = account.maskedIdentifier,
            currency = account.currency,
            ledgerBalance = account.ledgerBalance,
            availableBalance = account.availableBalance,
            creditOutstanding = account.creditOutstanding,
            creditLimit = account.creditLimit,
            availableCredit = account.availableCredit,
            accountStatus = account.accountStatus,
            connectionStatus = account.connectionStatus,
            lastUpdated = account.lastUpdated,
            dataSource = account.dataSource,
            createdAt = account.createdAt,
            updatedAt = account.updatedAt
        )
    }
}

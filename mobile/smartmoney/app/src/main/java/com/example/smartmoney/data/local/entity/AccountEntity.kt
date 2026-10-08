package com.example.smartmoney.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.smartmoney.domain.model.Account
import java.math.BigDecimal

/**
 * Local Room cache entity for public.accounts table.
 */
@Entity(
    tableName = "accounts",
    indices = [
        Index(value = ["userId"]),
        Index(value = ["accountId"]),
        Index(value = ["accountStatus"]),
        Index(value = ["userId", "createdAt"])
    ]
)
data class AccountEntity(
    @PrimaryKey
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
        fun fromDomain(account: Account): AccountEntity = AccountEntity(
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

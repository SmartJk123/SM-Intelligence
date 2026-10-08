package com.example.smartmoney.data.remote.dto

import com.example.smartmoney.domain.model.BankAccount
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * Data Transfer Object corresponding to the `public.bank_accounts` Supabase table.
 *
 * Columns:
 * - id: UUID (primary key)
 * - user_id: UUID (references auth.users)
 * - bank_name: TEXT
 * - account_number: TEXT
 * - card_type: TEXT
 */
@Serializable
data class BankAccountDto(
    @SerialName("id") val id: String = UUID.randomUUID().toString(),
    @SerialName("user_id") val userId: String? = null,
    @SerialName("bank_name") val bankName: String,
    @SerialName("account_number") val accountNumber: String,
    @SerialName("card_type") val cardType: String
) {
    /**
     * Maps this DTO to its pure domain model representation.
     */
    fun toDomain(): BankAccount = BankAccount(
        id = id,
        bankName = bankName,
        accountNumber = accountNumber,
        cardType = cardType
    )

    companion object {
        /**
         * Creates a BankAccountDto from a domain model and optional user ID.
         */
        fun fromDomain(
            account: BankAccount,
            userId: String? = null
        ): BankAccountDto = BankAccountDto(
            id = account.id.ifBlank { UUID.randomUUID().toString() },
            userId = userId,
            bankName = account.bankName,
            accountNumber = account.accountNumber,
            cardType = account.cardType
        )
    }
}

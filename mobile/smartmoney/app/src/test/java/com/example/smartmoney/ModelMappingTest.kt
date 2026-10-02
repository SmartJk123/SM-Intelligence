package com.example.smartmoney

import com.example.smartmoney.data.local.entity.AccountConnectionEntity
import com.example.smartmoney.data.local.entity.AccountEntity
import com.example.smartmoney.data.local.entity.TransactionEntity
import com.example.smartmoney.data.local.entity.UserEntity
import com.example.smartmoney.data.remote.dto.AccountConnectionDto
import com.example.smartmoney.data.remote.dto.AccountDto
import com.example.smartmoney.data.remote.dto.TransactionDto
import com.example.smartmoney.data.remote.dto.UserDto
import com.example.smartmoney.domain.model.Account
import com.example.smartmoney.domain.model.AccountConnection
import com.example.smartmoney.domain.model.Transaction
import com.example.smartmoney.domain.model.User
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal

class ModelMappingTest {

    @Test
    fun user_dto_and_entity_mapping_preserves_data_without_password() {
        val userDto = UserDto(
            id = "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
            name = "John Mwangi",
            emailAddress = "john@example.com",
            phoneNumber = "+254712345678",
            createdAt = "2026-09-09T00:00:00Z",
            updatedAt = "2026-09-09T00:00:00Z"
        )

        val domainUser = userDto.toDomain()
        assertEquals(userDto.id, domainUser.id)
        assertEquals(userDto.name, domainUser.name)
        assertEquals(userDto.emailAddress, domainUser.emailAddress)

        val entity = UserEntity.fromDomain(domainUser)
        assertEquals(domainUser.id, entity.id)
        assertEquals(domainUser.emailAddress, entity.emailAddress)

        val roundTripDomain = entity.toDomain()
        assertEquals(domainUser, roundTripDomain)
    }

    @Test
    fun account_mapping_preserves_big_decimal_precision() {
        val domainAccount = Account(
            id = "acc-uuid-1111",
            userId = "usr-uuid-2222",
            accountId = "KPLC-001",
            accountName = "Primary Meter",
            institution = "Kenya Power",
            accountType = "PREPAID",
            maskedIdentifier = "***7890",
            currency = "KES",
            ledgerBalance = BigDecimal("15420.5050"),
            availableBalance = BigDecimal("15420.5050"),
            creditOutstanding = BigDecimal("0.0000")
        )

        val dto = AccountDto.fromDomain(domainAccount)
        assertEquals(domainAccount.availableBalance, dto.availableBalance)
        assertEquals(domainAccount.ledgerBalance, dto.ledgerBalance)

        val entity = AccountEntity.fromDomain(domainAccount)
        assertEquals(domainAccount.availableBalance, entity.availableBalance)

        val reconstructed = entity.toDomain()
        assertEquals(domainAccount.availableBalance, reconstructed.availableBalance)
        assertEquals(domainAccount.institution, reconstructed.institution)
    }

    @Test
    fun transaction_mapping_preserves_monetary_amount_and_types() {
        val domainTx = Transaction(
            id = "tx-uuid-9999",
            accountId = "acc-uuid-1111",
            amount = BigDecimal("2500.7500"),
            type = "CREDIT",
            timestamp = "2026-09-09T10:00:00Z",
            description = "Token purchase",
            providerTransactionId = "MPESA-QK888"
        )

        val dto = TransactionDto.fromDomain(domainTx)
        assertEquals(domainTx.amount, dto.amount)
        assertEquals(domainTx.providerTransactionId, dto.providerTransactionId)

        val entity = TransactionEntity.fromDomain(domainTx)
        assertEquals(domainTx.amount, entity.amount)

        val roundTrip = entity.toDomain()
        assertEquals(domainTx, roundTrip)
    }

    @Test
    fun account_connection_mapping_round_trip() {
        val conn = AccountConnection(
            id = "conn-123",
            accountId = "acc-1111",
            provider = "KPLC_API",
            connectionStatus = "connected",
            externalIdentifier = "MTR-888"
        )

        val dto = AccountConnectionDto.fromDomain(conn)
        assertEquals(conn.provider, dto.provider)

        val entity = AccountConnectionEntity.fromDomain(conn)
        assertEquals(conn.connectionStatus, entity.connectionStatus)

        assertEquals(conn, entity.toDomain())
    }
}

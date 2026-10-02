package com.example.smartmoney

import com.example.smartmoney.data.remote.dto.AccountDto
import com.example.smartmoney.data.remote.dto.TransactionDto
import com.example.smartmoney.data.remote.dto.UserDto
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal

class SerializationTest {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    @Test
    fun transactionDto_deserializes_from_numeric_and_string_amounts() {
        val rawJsonNumeric = """
            {
                "id": "tx-12345",
                "account_id": "acc-9999",
                "amount": 2500.5000,
                "transaction_type": "CREDIT",
                "timestamp": "2026-09-09T10:00:00Z",
                "description": "Token purchase",
                "provider_transaction_id": "MPESA-001"
            }
        """.trimIndent()

        val parsedNumeric = json.decodeFromString<TransactionDto>(rawJsonNumeric)
        assertEquals(BigDecimal("2500.5000"), parsedNumeric.amount)
        assertEquals("CREDIT", parsedNumeric.transactionType)

        val rawJsonString = """
            {
                "id": "tx-12345",
                "account_id": "acc-9999",
                "amount": "2500.5000",
                "transaction_type": "CREDIT",
                "timestamp": "2026-09-09T10:00:00Z"
            }
        """.trimIndent()

        val parsedString = json.decodeFromString<TransactionDto>(rawJsonString)
        assertEquals(BigDecimal("2500.5000"), parsedString.amount)
    }

    @Test
    fun accountDto_serializes_and_deserializes_cleanly() {
        val accountDto = AccountDto(
            id = "acc-uuid-1",
            userId = "user-uuid-1",
            accountId = "KPLC-MTR-1",
            accountName = "Home Prepaid Meter",
            institution = "Kenya Power",
            accountType = "PREPAID",
            currency = "KES",
            ledgerBalance = BigDecimal("3450.7500"),
            availableBalance = BigDecimal("3450.7500")
        )

        val encoded = json.encodeToString(accountDto)
        val decoded = json.decodeFromString<AccountDto>(encoded)

        assertEquals(accountDto.id, decoded.id)
        assertEquals(accountDto.availableBalance, decoded.availableBalance)
        assertEquals(accountDto.ledgerBalance, decoded.ledgerBalance)
        assertEquals("Home Prepaid Meter", decoded.accountName)
    }

    @Test
    fun userDto_round_trip() {
        val userDto = UserDto(
            id = "usr-123",
            name = "Alice Wanjiku",
            emailAddress = "alice@example.com",
            phoneNumber = "+254700112233"
        )

        val encoded = json.encodeToString(userDto)
        val decoded = json.decodeFromString<UserDto>(encoded)

        assertEquals(userDto.name, decoded.name)
        assertEquals(userDto.emailAddress, decoded.emailAddress)
    }
}

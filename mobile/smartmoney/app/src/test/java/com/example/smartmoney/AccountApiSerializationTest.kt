package com.example.smartmoney

import com.example.smartmoney.data.remote.RetrofitClient
import com.example.smartmoney.data.remote.api.AccountResponse
import com.example.smartmoney.data.remote.api.CreateAccountRequest
import com.example.smartmoney.data.remote.api.UpdateBalanceRequest
import com.example.smartmoney.data.remote.api.UpdateStatusRequest
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class AccountApiSerializationTest {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    @Test
    fun createAccountRequest_serializes_with_exact_backend_property_names() {
        val request = CreateAccountRequest(
            userId = "550e8400-e29b-41d4-a716-446655440000",
            providerAccountId = "0112345678",
            accountName = "Equity Debit Account",
            institution = "Equity",
            accountType = "DEPOSIT",
            maskedIdentifier = "**** 5678",
            currency = "KES",
            initialBalance = BigDecimal("15000.50"),
            creditLimit = BigDecimal.ZERO,
            dataSource = "MANUAL"
        )

        val jsonString = json.encodeToString(request)
        val jsonObject = json.decodeFromString<JsonObject>(jsonString)

        assertEquals("550e8400-e29b-41d4-a716-446655440000", jsonObject["userId"]?.jsonPrimitive?.content)
        assertEquals("0112345678", jsonObject["providerAccountId"]?.jsonPrimitive?.content)
        assertEquals("Equity Debit Account", jsonObject["accountName"]?.jsonPrimitive?.content)
        assertEquals("Equity", jsonObject["institution"]?.jsonPrimitive?.content)
        assertEquals("DEPOSIT", jsonObject["accountType"]?.jsonPrimitive?.content)
        assertEquals("**** 5678", jsonObject["maskedIdentifier"]?.jsonPrimitive?.content)
        assertEquals("KES", jsonObject["currency"]?.jsonPrimitive?.content)
        assertEquals("15000.50", jsonObject["initialBalance"]?.jsonPrimitive?.content)
        assertEquals("0", jsonObject["creditLimit"]?.jsonPrimitive?.content)
        assertEquals("MANUAL", jsonObject["dataSource"]?.jsonPrimitive?.content)
    }

    @Test
    fun accountResponse_deserializes_from_accounts_service_payload() {
        val rawJson = """
            {
                "id": "e8b2a3c4-1111-2222-3333-444455556666",
                "userId": "550e8400-e29b-41d4-a716-446655440000",
                "providerAccountId": "0112345678",
                "accountName": "Equity Debit Account",
                "institution": "Equity",
                "accountType": "DEPOSIT",
                "maskedIdentifier": "**** 5678",
                "currency": "KES",
                "ledgerBalance": "25000.0000",
                "availableBalance": "24500.5000",
                "creditOutstanding": "0.0000",
                "creditLimit": "0.0000",
                "accountStatus": "ACTIVE",
                "connectionStatus": "CONNECTED",
                "lastUpdated": "2026-09-21T08:00:00Z",
                "dataSource": "MANUAL",
                "createdAt": "2026-09-21T08:00:00Z",
                "updatedAt": "2026-09-21T08:00:00Z"
            }
        """.trimIndent()

        val response = json.decodeFromString<AccountResponse>(rawJson)
        assertEquals("e8b2a3c4-1111-2222-3333-444455556666", response.id)
        assertEquals("550e8400-e29b-41d4-a716-446655440000", response.userId)
        assertEquals("0112345678", response.providerAccountId)
        assertEquals("Equity Debit Account", response.accountName)
        assertEquals("Equity", response.institution)
        assertEquals("DEPOSIT", response.accountType)
        assertEquals("**** 5678", response.maskedIdentifier)
        assertEquals("KES", response.currency)
        assertEquals(BigDecimal("25000.0000"), response.ledgerBalance)
        assertEquals(BigDecimal("24500.5000"), response.availableBalance)
        assertEquals("ACTIVE", response.accountStatus)

        val domain = response.toDomain()
        assertEquals(response.id, domain.id)
        assertEquals(response.providerAccountId, domain.accountId)
        assertEquals(response.institution, domain.institution)
        assertEquals(response.availableBalance, domain.availableBalance)
    }

    @Test
    fun updateBalanceRequest_serializes_properly() {
        val request = UpdateBalanceRequest(
            availableBalance = BigDecimal("50000.00"),
            ledgerBalance = BigDecimal("50000.00"),
            creditOutstanding = BigDecimal.ZERO
        )

        val jsonString = json.encodeToString(request)
        val jsonObject = json.decodeFromString<JsonObject>(jsonString)

        assertEquals("50000.00", jsonObject["availableBalance"]?.jsonPrimitive?.content)
    }

    @Test
    fun updateStatusRequest_serializes_properly() {
        val request = UpdateStatusRequest(
            accountStatus = "CLOSED",
            connectionStatus = "DISCONNECTED"
        )

        val jsonString = json.encodeToString(request)
        val jsonObject = json.decodeFromString<JsonObject>(jsonString)

        assertEquals("CLOSED", jsonObject["accountStatus"]?.jsonPrimitive?.content)
        assertEquals("DISCONNECTED", jsonObject["connectionStatus"]?.jsonPrimitive?.content)
    }

    @Test
    fun retrofitClient_initializes_accountApi() {
        assertNotNull(RetrofitClient.accountApi)
        assertNotNull(RetrofitClient.authApi)
        assertEquals(8081, RetrofitClient.IDENTITY_PORT)
        assertEquals(8082, RetrofitClient.ACCOUNTS_PORT)
    }
}

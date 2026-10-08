package com.example.smartmoney.data.remote.datasource

import com.example.smartmoney.data.remote.RetrofitClient
import com.example.smartmoney.data.remote.api.AccountApi
import com.example.smartmoney.data.remote.api.AccountResponse
import com.example.smartmoney.data.remote.api.CreateAccountRequest
import com.example.smartmoney.data.remote.api.UpdateBalanceRequest
import com.example.smartmoney.data.remote.api.UpdateStatusRequest
import com.example.smartmoney.data.remote.dto.AccountDto
import com.example.smartmoney.domain.model.Account
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import retrofit2.Response
import java.math.BigDecimal

/**
 * Remote data source routing account management operations to the Spring Boot accounts-service via Retrofit.
 * Completely eliminates any client-side Supabase Postgrest calls for accounts.
 */
class AccountRemoteDataSource(
    private val accountApi: AccountApi = RetrofitClient.accountApi
) {

    /**
     * Fetches all accounts belonging to the specified [userId] from GET /api/accounts.
     */
    suspend fun fetchAccounts(userId: String, status: String? = null): List<Account> {
        val response = accountApi.getAccounts(userId = userId, status = status)
        if (response.isSuccessful) {
            val list = response.body().orEmpty()
            return list.map { it.toDomain() }
        } else {
            val errorMsg = parseErrorMessage(response.errorBody()?.string()?.trim(), response.code(), response.message())
            throw Exception(errorMsg)
        }
    }

    /**
     * Creates a new financial or energy account via POST /api/accounts.
     */
    suspend fun createAccount(request: CreateAccountRequest): Account {
        val response = accountApi.createAccount(request)
        if (response.isSuccessful) {
            val body = response.body() ?: throw Exception("Empty response body from accounts-service")
            return body.toDomain()
        } else {
            val errorMsg = parseErrorMessage(response.errorBody()?.string()?.trim(), response.code(), response.message())
            throw Exception(errorMsg)
        }
    }

    /**
     * Overload for compatibility with [AccountDto] calls.
     */
    suspend fun createAccount(account: AccountDto): AccountDto {
        val request = CreateAccountRequest(
            userId = account.userId,
            providerAccountId = account.accountId,
            accountName = account.accountName,
            institution = account.institution,
            accountType = if (account.accountType.equals("CREDIT", ignoreCase = true)) "CREDIT" else "DEPOSIT",
            maskedIdentifier = account.maskedIdentifier ?: "**** ${account.accountId.takeLast(4)}",
            currency = account.currency,
            initialBalance = account.availableBalance,
            creditLimit = account.creditLimit ?: BigDecimal.ZERO,
            dataSource = account.dataSource
        )
        val createdDomain = createAccount(request)
        return AccountDto.fromDomain(createdDomain)
    }

    /**
     * Retrieves an account by its unique UUID via GET /api/accounts/{id}.
     */
    suspend fun getAccountById(id: String, userId: String? = null): Account? {
        val response = accountApi.getAccountById(id = id, userId = userId)
        return if (response.isSuccessful) {
            response.body()?.toDomain()
        } else if (response.code() == 404) {
            null
        } else {
            val errorMsg = parseErrorMessage(response.errorBody()?.string()?.trim(), response.code(), response.message())
            throw Exception(errorMsg)
        }
    }

    /**
     * Updates account balances via PATCH /api/accounts/{id}/balance.
     */
    suspend fun updateBalance(
        id: String,
        availableBalance: BigDecimal,
        ledgerBalance: BigDecimal? = null,
        creditOutstanding: BigDecimal? = null
    ): Account {
        val request = UpdateBalanceRequest(
            availableBalance = availableBalance,
            ledgerBalance = ledgerBalance,
            creditOutstanding = creditOutstanding
        )
        val response = accountApi.updateBalance(id = id, request = request)
        if (response.isSuccessful) {
            val body = response.body() ?: throw Exception("Empty response from accounts-service")
            return body.toDomain()
        } else {
            val errorMsg = parseErrorMessage(response.errorBody()?.string()?.trim(), response.code(), response.message())
            throw Exception(errorMsg)
        }
    }

    /**
     * Updates account or connection status via PATCH /api/accounts/{id}/status.
     */
    suspend fun updateStatus(
        id: String,
        accountStatus: String? = null,
        connectionStatus: String? = null
    ): Account {
        val request = UpdateStatusRequest(
            accountStatus = accountStatus,
            connectionStatus = connectionStatus
        )
        val response = accountApi.updateStatus(id = id, request = request)
        if (response.isSuccessful) {
            val body = response.body() ?: throw Exception("Empty response from accounts-service")
            return body.toDomain()
        } else {
            val errorMsg = parseErrorMessage(response.errorBody()?.string()?.trim(), response.code(), response.message())
            throw Exception(errorMsg)
        }
    }

    /**
     * Closes an account via DELETE /api/accounts/{id}?permanent=false.
     */
    suspend fun closeAccount(id: String): Account {
        val response = accountApi.closeAccount(id = id)
        if (response.isSuccessful) {
            val body = response.body() ?: throw Exception("Empty response from accounts-service")
            return body.toDomain()
        } else {
            val errorMsg = parseErrorMessage(response.errorBody()?.string()?.trim(), response.code(), response.message())
            throw Exception(errorMsg)
        }
    }

    /**
     * Permanently deletes an account from the database via DELETE /api/accounts/{id}.
     */
    suspend fun deleteAccount(id: String, permanent: Boolean = true): Account {
        val response = accountApi.deleteAccount(id = id, permanent = permanent)
        if (response.isSuccessful) {
            val body = response.body() ?: throw Exception("Empty response from accounts-service")
            return body.toDomain()
        } else {
            val errorMsg = parseErrorMessage(response.errorBody()?.string()?.trim(), response.code(), response.message())
            throw Exception(errorMsg)
        }
    }

    /**
     * Extracts readable error messages from JSON error responses returned by Spring Boot.
     */
    private fun parseErrorMessage(
        errorBody: String?,
        statusCode: Int,
        statusMessage: String
    ): String {
        if (errorBody.isNullOrBlank()) {
            return "HTTP $statusCode: $statusMessage"
        }
        try {
            if (errorBody.startsWith("{") && errorBody.endsWith("}")) {
                val jsonElement = Json.parseToJsonElement(errorBody)
                if (jsonElement is JsonObject) {
                    val message = (jsonElement["message"] as? JsonPrimitive)?.content
                    if (!message.isNullOrBlank()) return message

                    val details = jsonElement["details"] as? JsonObject
                    if (details != null && details.isNotEmpty()) {
                        val detailMsgs = details.values
                            .mapNotNull { (it as? JsonPrimitive)?.content }
                            .filter { it.isNotBlank() }
                        if (detailMsgs.isNotEmpty()) return detailMsgs.joinToString(", ")
                    }

                    val error = (jsonElement["error"] as? JsonPrimitive)?.content
                    if (!error.isNullOrBlank()) return error
                }
            }
        } catch (_: Exception) {
        }
        return errorBody
    }
}

package com.example.smartmoney.data.remote.api

import com.example.smartmoney.data.remote.dto.BigDecimalSerializer
import com.example.smartmoney.domain.model.Account
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import java.math.BigDecimal

/**
 * Request payload for creating an account on the accounts-service.
 */
@Serializable
data class CreateAccountRequest(
    @SerialName("userId")
    val userId: String,

    @SerialName("providerAccountId")
    val providerAccountId: String,

    @SerialName("accountName")
    val accountName: String,

    @SerialName("institution")
    val institution: String,

    @SerialName("accountType")
    val accountType: String, // "DEPOSIT" or "CREDIT"

    @SerialName("maskedIdentifier")
    val maskedIdentifier: String,

    @SerialName("currency")
    val currency: String = "KES",

    @Serializable(with = BigDecimalSerializer::class)
    @SerialName("initialBalance")
    val initialBalance: BigDecimal = BigDecimal.ZERO,

    @Serializable(with = BigDecimalSerializer::class)
    @SerialName("creditLimit")
    val creditLimit: BigDecimal = BigDecimal.ZERO,

    @SerialName("dataSource")
    val dataSource: String = "MANUAL"
)

/**
 * Response payload returned from accounts-service.
 */
@Serializable
data class AccountResponse(
    @SerialName("id")
    val id: String,

    @SerialName("userId")
    val userId: String,

    @SerialName("providerAccountId")
    val providerAccountId: String,

    @SerialName("accountName")
    val accountName: String,

    @SerialName("institution")
    val institution: String,

    @SerialName("accountType")
    val accountType: String,

    @SerialName("maskedIdentifier")
    val maskedIdentifier: String? = null,

    @SerialName("currency")
    val currency: String = "KES",

    @Serializable(with = BigDecimalSerializer::class)
    @SerialName("ledgerBalance")
    val ledgerBalance: BigDecimal = BigDecimal.ZERO,

    @Serializable(with = BigDecimalSerializer::class)
    @SerialName("availableBalance")
    val availableBalance: BigDecimal = BigDecimal.ZERO,

    @Serializable(with = BigDecimalSerializer::class)
    @SerialName("creditOutstanding")
    val creditOutstanding: BigDecimal = BigDecimal.ZERO,

    @Serializable(with = BigDecimalSerializer::class)
    @SerialName("creditLimit")
    val creditLimit: BigDecimal? = null,

    @SerialName("accountStatus")
    val accountStatus: String = "ACTIVE",

    @SerialName("connectionStatus")
    val connectionStatus: String = "CONNECTED",

    @SerialName("lastUpdated")
    val lastUpdated: String? = null,

    @SerialName("dataSource")
    val dataSource: String = "MANUAL",

    @SerialName("createdAt")
    val createdAt: String? = null,

    @SerialName("updatedAt")
    val updatedAt: String? = null
) {
    fun toDomain(): Account = Account(
        id = id,
        userId = userId,
        accountId = providerAccountId,
        accountName = accountName,
        institution = institution,
        accountType = accountType,
        maskedIdentifier = maskedIdentifier,
        currency = currency,
        ledgerBalance = ledgerBalance,
        availableBalance = availableBalance,
        creditOutstanding = creditOutstanding,
        creditLimit = creditLimit,
        availableCredit = creditLimit?.let { limit -> (limit - creditOutstanding).coerceAtLeast(BigDecimal.ZERO) },
        accountStatus = accountStatus,
        connectionStatus = connectionStatus,
        lastUpdated = lastUpdated,
        dataSource = dataSource,
        createdAt = createdAt,
        updatedAt = updatedAt
    )
}

/**
 * Request payload for updating account balance.
 */
@Serializable
data class UpdateBalanceRequest(
    @Serializable(with = BigDecimalSerializer::class)
    @SerialName("availableBalance")
    val availableBalance: BigDecimal,

    @Serializable(with = BigDecimalSerializer::class)
    @SerialName("ledgerBalance")
    val ledgerBalance: BigDecimal? = null,

    @Serializable(with = BigDecimalSerializer::class)
    @SerialName("creditOutstanding")
    val creditOutstanding: BigDecimal? = null
)

/**
 * Request payload for updating account status or connection status.
 */
@Serializable
data class UpdateStatusRequest(
    @SerialName("accountStatus")
    val accountStatus: String? = null,

    @SerialName("connectionStatus")
    val connectionStatus: String? = null
)

/**
 * Retrofit interface for accounts-service endpoints.
 */
interface AccountApi {

    @POST("api/accounts")
    suspend fun createAccount(
        @Body request: CreateAccountRequest
    ): Response<AccountResponse>

    @GET("api/accounts")
    suspend fun getAccounts(
        @Query("userId") userId: String,
        @Query("status") status: String? = null
    ): Response<List<AccountResponse>>

    @GET("api/accounts/{id}")
    suspend fun getAccountById(
        @Path("id") id: String,
        @Query("userId") userId: String? = null
    ): Response<AccountResponse>

    @PATCH("api/accounts/{id}/balance")
    suspend fun updateBalance(
        @Path("id") id: String,
        @Body request: UpdateBalanceRequest
    ): Response<AccountResponse>

    @PATCH("api/accounts/{id}/status")
    suspend fun updateStatus(
        @Path("id") id: String,
        @Body request: UpdateStatusRequest
    ): Response<AccountResponse>

    @DELETE("api/accounts/{id}")
    suspend fun deleteAccount(
        @Path("id") id: String,
        @Query("permanent") permanent: Boolean? = true
    ): Response<AccountResponse>

    @DELETE("api/accounts/{id}")
    suspend fun closeAccount(
        @Path("id") id: String,
        @Query("permanent") permanent: Boolean? = false
    ): Response<AccountResponse>
}

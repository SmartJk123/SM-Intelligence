package com.example.smartmoney.data.remote.api

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonPrimitive
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.POST
import retrofit2.http.Query

/**
 * Custom serializer that cleanly parses both numeric and string values into a String.
 */
object FlexibleStringSerializer : KSerializer<String> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("FlexibleString", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: String) {
        encoder.encodeString(value)
    }

    override fun deserialize(decoder: Decoder): String {
        return if (decoder is JsonDecoder) {
            val element = decoder.decodeJsonElement()
            if (element is JsonPrimitive) {
                element.content
            } else {
                element.toString()
            }
        } else {
            decoder.decodeString()
        }
    }
}

/**
 * Request payload for linking a bank account via bank-integration-service:
 * POST /api/v1/admin/account-links
 *
 * Parameters match backend LinkRequest:
 * (String bankId, String accountNumber, String userId, String accountName, String accountId)
 */
@Serializable
data class LinkBankRequest(
    @SerialName("bankId")
    val bankId: String = "kcb",

    @SerialName("accountNumber")
    val accountNumber: String,

    @SerialName("userId")
    val userId: String,

    @SerialName("accountName")
    val accountName: String? = null,

    @SerialName("accountId")
    val accountId: String? = null,

    @SerialName("cardType")
    val cardType: String? = null
)

/**
 * Response payload returned when linking a bank account via bank-integration-service.
 * Matches backend LinkView:
 * (Long id, String bankId, String accountNumber, String userId, String accountId, String accountName,
 *  Instant createdAt, int pendingDeliveries, String lastError)
 */
@Serializable
data class BankLinkResponse(
    @SerialName("id")
    val id: Long? = null,

    @SerialName("bankId")
    val bankId: String = "kcb",

    @SerialName("accountNumber")
    val accountNumber: String = "",

    @SerialName("userId")
    val userId: String = "",

    @SerialName("accountId")
    val accountId: String? = null,

    @SerialName("accountName")
    val accountName: String? = null,

    @SerialName("createdAt")
    val createdAt: String? = null,

    @SerialName("pendingDeliveries")
    val pendingDeliveries: Int = 0,

    @SerialName("lastError")
    val lastError: String? = null,

    // Legacy compatibility fields
    @SerialName("success")
    val success: Boolean = true,

    @SerialName("institution")
    val institution: String = "KCB",

    @SerialName("cardType")
    val cardType: String = "Credit",

    @SerialName("maskedIdentifier")
    val maskedIdentifier: String = "",

    @SerialName("connectionStatus")
    val connectionStatus: String = "CONNECTED",

    @SerialName("dataSource")
    val dataSource: String = "BANK_API",

    @SerialName("message")
    val message: String? = null
)

typealias AccountLinkResponse = BankLinkResponse

/**
 * Supported bank information returned from bank-integration-service.
 */
@Serializable
data class SupportedBankResponse(
    @SerialName("bankId")
    val bankId: String = "kcb",

    @SerialName("name")
    val name: String = "KCB",

    @SerialName("status")
    val status: String = "ACTIVE",

    @SerialName("authType")
    val authType: String = "OAUTH2",

    @SerialName("supportsInstantAlerts")
    val supportsInstantAlerts: Boolean = true,

    @SerialName("apiStatus")
    val apiStatus: String? = null,

    @SerialName("webhookStatus")
    val webhookStatus: String? = null
)

/**
 * Transaction response model representing movements recorded or fetched from bank-integration-service.
 */
@Serializable
data class BankTransactionResponse(
    @SerialName("id") val id: Long? = null,
    @SerialName("reference") val reference: String = "",
    @SerialName("bankId") val bankId: String = "kcb",
    @Serializable(with = FlexibleStringSerializer::class)
    @SerialName("amount") val amount: String = "0.00",
    @SerialName("currency") val currency: String = "KES",
    @SerialName("direction") val direction: String = "Credit",
    @SerialName("narration") val narration: String? = null,
    @SerialName("accountNumber") val accountNumber: String? = null,
    @SerialName("accountName") val accountName: String? = null,
    @SerialName("bookingDate") val bookingDate: String? = null,
    @SerialName("createdAt") val createdAt: String? = null,
    @SerialName("simulated") val simulated: Boolean = false
)

/**
 * Status and health metrics for bank connections.
 */
@Serializable
data class BankConnectionStatusResponse(
    @SerialName("bankId") val bankId: String = "kcb",
    @SerialName("connectionStatus") val connectionStatus: String = "CONNECTED",
    @SerialName("lastWebhookAt") val lastWebhookAt: String? = null,
    @SerialName("tokenStatus") val tokenStatus: String? = null,
    @SerialName("linkedAccountCount") val linkedAccountCount: Int = 0
)

/**
 * Integration health details matching IntegrationHealth record on backend.
 */
@Serializable
data class IntegrationHealthResponse(
    @SerialName("bankId") val bankId: String = "kcb",
    @SerialName("environment") val environment: String? = null,
    @SerialName("apiStatus") val apiStatus: String? = null,
    @SerialName("webhookStatus") val webhookStatus: String? = null,
    @SerialName("tokenStatus") val tokenStatus: String? = null,
    @SerialName("tokenExpiresInMinutes") val tokenExpiresInMinutes: Int? = null,
    @SerialName("lastTokenRefresh") val lastTokenRefresh: String? = null,
    @SerialName("lastWebhookReceived") val lastWebhookReceived: String? = null,
    @SerialName("lastSuccessfulRequest") val lastSuccessfulRequest: String? = null,
    @SerialName("latencyMs") val latencyMs: Int? = null,
    @SerialName("errorsLast24h") val errorsLast24h: Long = 0L,
    @SerialName("notificationsTotal") val notificationsTotal: Long = 0L,
    @SerialName("notificationsToday") val notificationsToday: Long = 0L,
    @SerialName("checkedAt") val checkedAt: String? = null
)

/**
 * Request payload for triggering simulated / demo movements via bank-integration-service:
 * POST /api/v1/admin/demo/transactions
 */
@Serializable
data class SimulateTransactionRequest(
    @SerialName("bankId") val bankId: String = "kcb",
    @SerialName("direction") val direction: String = "Credit",
    @SerialName("amount") val amount: String = "1000.00",
    @SerialName("narration") val narration: String = "Simulated KCB Inflow",
    @SerialName("accountNumber") val accountNumber: String? = null
)

/**
 * Retrofit interface for communicating with bank-integration-service (:8090).
 */
interface BankIntegrationApi {

    /**
     * Links a customer's bank account via POST /api/v1/admin/account-links
     */
    @POST("api/v1/admin/account-links")
    suspend fun linkKcbAccount(
        @Body request: LinkBankRequest
    ): Response<BankLinkResponse>

    @POST("api/v1/admin/account-links")
    suspend fun linkBank(
        @Body request: LinkBankRequest
    ): Response<BankLinkResponse>

    @POST("api/v1/admin/account-links")
    suspend fun linkAccount(
        @Body request: LinkBankRequest
    ): Response<BankLinkResponse>

    /**
     * Retrieves account links, optionally filtered by user ID:
     * GET /api/v1/admin/account-links
     */
    @GET("api/v1/admin/account-links")
    suspend fun getAccountLinks(
        @Query("userId") userId: String? = null
    ): Response<List<BankLinkResponse>>

    @GET("api/v1/admin/bank-integrations")
    suspend fun getSupportedBanks(): Response<List<SupportedBankResponse>>

    /**
     * Retrieves transactions/movements from demo controller:
     * GET /api/v1/admin/demo/transactions
     */
    @GET("api/v1/admin/demo/transactions")
    suspend fun getKcbTransactions(
        @Query("userId") userId: String? = null,
        @Query("since") since: String? = null
    ): Response<List<BankTransactionResponse>>

    @GET("api/v1/admin/bank-integrations/{bankId}")
    suspend fun getBankHealth(
        @Path("bankId") bankId: String
    ): Response<IntegrationHealthResponse>

    @GET("api/v1/admin/bank-integrations/{bankId}")
    suspend fun getKcbConnectionStatus(
        @Path("bankId") bankId: String = "kcb",
        @Query("userId") userId: String? = null
    ): Response<BankConnectionStatusResponse>

    /**
     * Unlinks an account link by its primary key ID:
     * DELETE /api/v1/admin/account-links/{id}
     */
    @DELETE("api/v1/admin/account-links/{id}")
    suspend fun unlinkKcbAccount(
        @Path("id") id: String
    ): Response<Unit>

    @DELETE("api/v1/admin/account-links/{id}")
    suspend fun unlinkAccount(
        @Path("id") id: String
    ): Response<Unit>

    /**
     * Simulates a transaction movement via DemoTransactionController:
     * POST /api/v1/admin/demo/transactions
     */
    @POST("api/v1/admin/demo/transactions")
    suspend fun simulateKcbTransaction(
        @Body request: SimulateTransactionRequest? = null
    ): Response<BankTransactionResponse>

    @POST("api/v1/admin/demo/transactions")
    suspend fun recordDemoTransaction(
        @Body request: SimulateTransactionRequest? = null
    ): Response<BankTransactionResponse>
}

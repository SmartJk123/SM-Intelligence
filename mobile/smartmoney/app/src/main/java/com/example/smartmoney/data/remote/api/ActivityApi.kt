package com.example.smartmoney.data.remote.api

import com.example.smartmoney.data.remote.dto.BigDecimalSerializer
import kotlinx.serialization.Serializable
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query
import java.math.BigDecimal

/**
 * One movement on one of the signed-in user's accounts, as transactions-service
 * returns it. The same movements the web dashboard and the admin portal show.
 */
@Serializable
data class ActivityItem(
    val id: String,
    val accountId: String,
    val bank: String? = null,
    val accountName: String? = null,
    val maskedIdentifier: String? = null,
    /** CREDIT is money in, DEBIT is money out. */
    val direction: String,
    @Serializable(with = BigDecimalSerializer::class)
    val amount: BigDecimal,
    val currency: String? = null,
    val counterparty: String? = null,
    val description: String? = null,
    val status: String? = null,
    /** When the bank booked it, ISO-8601 in UTC. */
    val transactionDate: String? = null,
    /** When it reached SmartMoney, ISO-8601 in UTC. */
    val receivedAt: String? = null
)

/**
 * The signed-in user's money in and money out across all their accounts, newest first.
 * Needs the identity-service token (added by RetrofitClient's auth interceptor).
 */
interface ActivityApi {

    /**
     * GET /api/transactions/activity?limit=200
     * GET /api/transactions/activity?since=2026-10-08T09:00:00Z  (only what arrived since)
     */
    @GET("api/transactions/activity")
    suspend fun getActivity(
        @Query("limit") limit: Int = 200,
        @Query("since") since: String? = null
    ): Response<List<ActivityItem>>
}

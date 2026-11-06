package com.example.smartmoney.data.remote.api

import com.example.smartmoney.data.remote.dto.CreateTransactionBackendRequest
import com.example.smartmoney.data.remote.dto.TransactionBackendResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.Path
import retrofit2.http.POST
import retrofit2.http.Query

/**
 * Retrofit API contract for communicating with the transactions-service via the API Gateway.
 * Base endpoint: /api/transactions
 */
interface TransactionApi {

    @POST("api/transactions")
    suspend fun recordTransaction(
        @Body request: CreateTransactionBackendRequest
    ): Response<TransactionBackendResponse>

    @GET("api/transactions")
    suspend fun getTransactions(
        @Query("accountId") accountId: String,
        @Query("status") status: String? = null
    ): Response<List<TransactionBackendResponse>>

    @GET("api/transactions/{id}")
    suspend fun getTransactionById(
        @Path("id") id: String
    ): Response<TransactionBackendResponse>

    @PATCH("api/transactions/{id}/category")
    suspend fun updateCategory(
        @Path("id") id: String,
        @Body payload: Map<String, String>
    ): Response<TransactionBackendResponse>

    @POST("api/transactions/{id}/reverse")
    suspend fun reverseTransaction(
        @Path("id") id: String,
        @Body payload: Map<String, String>? = null
    ): Response<TransactionBackendResponse>
}

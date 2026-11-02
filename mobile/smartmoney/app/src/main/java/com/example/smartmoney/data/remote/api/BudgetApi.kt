package com.example.smartmoney.data.remote.api

import com.example.smartmoney.data.remote.dto.BudgetBackendResponse
import com.example.smartmoney.data.remote.dto.CreateBudgetBackendRequest
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.POST
import retrofit2.http.Query

/**
 * Retrofit API contract for communicating with the budgets-service via the API Gateway.
 * Base endpoint: /api/budgets
 */
interface BudgetApi {

    @POST("api/budgets")
    suspend fun createBudget(
        @Body request: CreateBudgetBackendRequest
    ): Response<BudgetBackendResponse>

    @GET("api/budgets")
    suspend fun getBudgets(
        @Query("userId") userId: String
    ): Response<List<BudgetBackendResponse>>

    @DELETE("api/budgets/{id}")
    suspend fun deleteBudget(
        @Path("id") id: String
    ): Response<Unit>
}

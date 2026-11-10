package com.example.smartmoney.data.remote.api

import com.example.smartmoney.data.remote.dto.RahaChatRequest
import com.example.smartmoney.data.remote.dto.RahaChatResponse
import com.example.smartmoney.data.remote.dto.RahaPageBriefingRequest
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface RahaApi {

    @POST("api/assistant/chat")
    suspend fun chat(
        @Body request: RahaChatRequest
    ): Response<RahaChatResponse>

    @POST("api/assistant/briefing")
    suspend fun getPageBriefing(
        @Body request: RahaPageBriefingRequest
    ): Response<RahaChatResponse>

    @GET("api/assistant/nudge")
    suspend fun getNudge(
        @Query("screenRoute") screenRoute: String
    ): Response<Map<String, String>>

    @DELETE("api/assistant/conversations/{id}")
    suspend fun clearConversation(
        @Path("id") conversationId: String
    ): Response<Unit>
}

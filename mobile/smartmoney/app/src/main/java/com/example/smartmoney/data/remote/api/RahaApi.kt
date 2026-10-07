package com.example.smartmoney.data.remote.api

import com.example.smartmoney.data.remote.dto.RahaChatRequest
import com.example.smartmoney.data.remote.dto.RahaChatResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.POST
import retrofit2.http.Path

interface RahaApi {

    @POST("api/assistant/chat")
    suspend fun chat(
        @Body request: RahaChatRequest
    ): Response<RahaChatResponse>

    @DELETE("api/assistant/conversations/{id}")
    suspend fun clearConversation(
        @Path("id") conversationId: String
    ): Response<Unit>
}

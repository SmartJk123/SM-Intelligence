package com.example.smartmoney.domain.repository

import com.example.smartmoney.domain.model.RahaMessage

interface RahaRepository {
    suspend fun sendMessage(
        message: String,
        conversationId: String?,
        screenRoute: String? = null,
        screenTitle: String? = null,
        pageData: Map<String, String> = emptyMap()
    ): Result<RahaMessage>

    suspend fun getPageBriefing(
        screenRoute: String,
        screenTitle: String,
        conversationId: String? = null,
        pageData: Map<String, String> = emptyMap()
    ): Result<RahaMessage>

    suspend fun getPromptNudge(
        screenRoute: String
    ): String

    suspend fun clearConversation(
        conversationId: String
    ): Result<Unit>
}

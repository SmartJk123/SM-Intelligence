package com.example.smartmoney.domain.repository

import com.example.smartmoney.domain.model.RahaMessage

interface RahaRepository {
    suspend fun sendMessage(
        message: String,
        conversationId: String?
    ): Result<RahaMessage>

    suspend fun clearConversation(
        conversationId: String
    ): Result<Unit>
}

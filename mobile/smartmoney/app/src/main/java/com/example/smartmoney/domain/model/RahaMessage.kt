package com.example.smartmoney.domain.model

import java.util.UUID

enum class RahaSender {
    USER,
    RAHA
}

data class RahaMessage(
    val id: String = UUID.randomUUID().toString(),
    val sender: RahaSender,
    val text: String,
    val timestamp: Long = System.currentTimeMillis(),
    val action: RahaAction? = null,
    val quickReplies: List<String> = emptyList(),
    val conversationId: String? = null
)

package com.example.smartmoney.ui.raha

import com.example.smartmoney.domain.model.RahaMessage

data class RahaUiState(
    val isOpen: Boolean = false,
    val isTyping: Boolean = false,
    val messages: List<RahaMessage> = emptyList(),
    val quickReplies: List<String> = listOf(
        "What is my total balance?",
        "Check my Budgets",
        "Recent Transactions",
        "Upload an Invoice"
    ),
    val hasUnreadAlert: Boolean = false,
    val alertMessage: String? = null,
    val conversationId: String? = null
)

package com.example.smartmoney.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class RahaInvestmentDto(
    @SerialName("name") val name: String,
    @SerialName("productType") val productType: String,
    @SerialName("institution") val institution: String,
    @SerialName("amount") val amount: Double,
    @SerialName("returnRate") val returnRate: Double,
    @SerialName("maturityDate") val maturityDate: String? = null
)

@Serializable
data class RahaChatRequest(
    @SerialName("message") val message: String,
    @SerialName("conversationId") val conversationId: String? = null,
    @SerialName("clientInvestments") val clientInvestments: List<RahaInvestmentDto> = emptyList()
)

@Serializable
data class RahaActionDto(
    @SerialName("type") val type: String, // NAVIGATE, SHOW_SUMMARY, RECOMMEND_BUDGET
    @SerialName("targetRoute") val targetRoute: String, // e.g., "accounts", "budgets", "transactions", "invoice", "investments"
    @SerialName("title") val title: String? = null,
    @SerialName("description") val description: String? = null,
    @SerialName("params") val params: Map<String, String> = emptyMap()
)

@Serializable
data class RahaChatResponse(
    @SerialName("conversationId") val conversationId: String,
    @SerialName("replyMessage") val replyMessage: String,
    @SerialName("action") val action: RahaActionDto? = null,
    @SerialName("quickReplies") val quickReplies: List<String> = emptyList()
)

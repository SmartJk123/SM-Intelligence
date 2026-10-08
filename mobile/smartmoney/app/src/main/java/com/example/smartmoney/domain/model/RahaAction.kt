package com.example.smartmoney.domain.model

data class RahaAction(
    val type: String,
    val targetRoute: String,
    val title: String? = null,
    val description: String? = null,
    val params: Map<String, String> = emptyMap()
)

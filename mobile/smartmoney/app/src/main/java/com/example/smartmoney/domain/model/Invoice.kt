package com.example.smartmoney.domain.model

import java.math.BigDecimal

data class Invoice(
    val id: String,
    val vendor: String,
    val invoiceNumber: String?,
    val amount: BigDecimal,
    val currency: String,
    val invoiceDate: String,
    val dueDate: String?,
    val filename: String,
    val status: String = "PENDING",
    val source: String = "INVOICE"
)

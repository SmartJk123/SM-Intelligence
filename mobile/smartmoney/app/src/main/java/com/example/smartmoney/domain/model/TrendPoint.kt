package com.example.smartmoney.domain.model

import androidx.compose.runtime.Immutable
import java.math.BigDecimal

/**
 * Domain data model for daily/weekly cash flow trend points.
 */
@Immutable
data class TrendPoint(
    val dayLabel: String,
    val cashIn: BigDecimal,
    val cashOut: BigDecimal
)

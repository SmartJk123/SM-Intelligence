package com.example.smartmoney.core.util

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat

/**
 * High-performance, zero-allocation currency formatting utility.
 * Utilizes [ThreadLocal] cached [DecimalFormat] instances to prevent
 * object allocations on every frame during LazyColumn scrolling or recomposition.
 */
object CurrencyUtils {

    private val kesFormat = ThreadLocal.withInitial {
        DecimalFormat("KES #,##0.00").apply {
            roundingMode = RoundingMode.HALF_EVEN
        }
    }

    private val plainAmountFormat = ThreadLocal.withInitial {
        DecimalFormat("#,##0.00").apply {
            roundingMode = RoundingMode.HALF_EVEN
        }
    }

    /**
     * Formats monetary amount in major units with KES prefix:
     * e.g. BigDecimal("35000.00") -> "KES 35,000.00"
     */
    fun formatKes(amount: BigDecimal): String {
        val formatter = kesFormat.get() ?: DecimalFormat("KES #,##0.00")
        return formatter.format(amount)
    }

    /**
     * Formats minor monetary units (cents) into formatted KES major currency string:
     * e.g. 3500000L -> "KES 35,000.00"
     */
    fun formatMinor(cents: Long): String {
        val major = BigDecimal(cents).divide(BigDecimal(100))
        val formatter = kesFormat.get() ?: DecimalFormat("KES #,##0.00")
        return formatter.format(major)
    }

    /**
     * Formats monetary amount without currency prefix:
     * e.g. BigDecimal("35000.00") -> "35,000.00"
     */
    fun formatPlain(amount: BigDecimal): String {
        val formatter = plainAmountFormat.get() ?: DecimalFormat("#,##0.00")
        return formatter.format(amount)
    }
}

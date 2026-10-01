package com.example.smartmoney.domain.util

import com.example.smartmoney.domain.model.Transaction
import com.example.smartmoney.domain.model.TrendPoint
import java.math.BigDecimal
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Result data holder for precomputed transaction aggregates and 7-day trend metrics.
 */
data class TransactionTrendCalculation(
    val totalInflow: BigDecimal = BigDecimal.ZERO,
    val totalOutflow: BigDecimal = BigDecimal.ZERO,
    val trendPoints: List<TrendPoint> = TransactionTrendCalculator.DefaultTrend,
    val hasTransactions: Boolean = false
)

/**
 * Pure, high-performance financial calculation utility for transaction aggregations
 * and 7-day weekly cash flow distributions.
 *
 * Designed to execute off the Main/UI thread on [kotlinx.coroutines.Dispatchers.Default]
 * without Compose runtime dependencies or UI frame overhead.
 */
object TransactionTrendCalculator {

    val DefaultTrend: List<TrendPoint> = listOf(
        TrendPoint("Mon", BigDecimal("4200.00"), BigDecimal("2100.00")),
        TrendPoint("Tue", BigDecimal("7500.00"), BigDecimal("1200.00")),
        TrendPoint("Wed", BigDecimal("3800.00"), BigDecimal("3000.00")),
        TrendPoint("Thu", BigDecimal("9200.00"), BigDecimal("5000.00")),
        TrendPoint("Fri", BigDecimal("6400.00"), BigDecimal("8000.00")),
        TrendPoint("Sat", BigDecimal("11500.00"), BigDecimal("4500.00")),
        TrendPoint("Sun", BigDecimal("2400.00"), BigDecimal("1500.00"))
    )

    private val DayOrder: List<Pair<DayOfWeek, String>> = listOf(
        DayOfWeek.MONDAY to "Mon",
        DayOfWeek.TUESDAY to "Tue",
        DayOfWeek.WEDNESDAY to "Wed",
        DayOfWeek.THURSDAY to "Thu",
        DayOfWeek.FRIDAY to "Fri",
        DayOfWeek.SATURDAY to "Sat",
        DayOfWeek.SUNDAY to "Sun"
    )

    /**
     * Performs a single-pass O(N) aggregation over [transactions] computing:
     * 1. Total cash inflow (sum of CREDIT transactions)
     * 2. Total cash outflow (sum of DEBIT transactions)
     * 3. Day-of-week 7-day bucketed cash-in / cash-out trend points
     *
     * Financial precision is strictly preserved using [BigDecimal].
     */
    fun calculateTrendSummary(
        transactions: List<Transaction>,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): TransactionTrendCalculation {
        if (transactions.isEmpty()) {
            return TransactionTrendCalculation(
                totalInflow = BigDecimal.ZERO,
                totalOutflow = BigDecimal.ZERO,
                trendPoints = DefaultTrend,
                hasTransactions = false
            )
        }

        var totalInflow = BigDecimal.ZERO
        var totalOutflow = BigDecimal.ZERO

        val dayBuckets = DayOrder.associate { it.first to Pair(BigDecimal.ZERO, BigDecimal.ZERO) }.toMutableMap()
        val today = LocalDate.now(zoneId)

        for (tx in transactions) {
            val isCredit = tx.type.equals("CREDIT", ignoreCase = true)
            val isDebit = tx.type.equals("DEBIT", ignoreCase = true)
            if (!isCredit && !isDebit) continue

            val amount = tx.amount
            val date = parseTransactionDate(tx.timestamp, zoneId) ?: today
            val day = date.dayOfWeek
            val current = dayBuckets[day] ?: Pair(BigDecimal.ZERO, BigDecimal.ZERO)

            if (isCredit) {
                totalInflow = totalInflow.add(amount)
                dayBuckets[day] = Pair(current.first.add(amount), current.second)
            } else {
                totalOutflow = totalOutflow.add(amount)
                dayBuckets[day] = Pair(current.first, current.second.add(amount))
            }
        }

        val trendPoints = DayOrder.map { (dayOfWeek, label) ->
            val pair = dayBuckets[dayOfWeek] ?: Pair(BigDecimal.ZERO, BigDecimal.ZERO)
            TrendPoint(
                dayLabel = label,
                cashIn = pair.first,
                cashOut = pair.second
            )
        }

        return TransactionTrendCalculation(
            totalInflow = totalInflow,
            totalOutflow = totalOutflow,
            trendPoints = trendPoints,
            hasTransactions = true
        )
    }

    /**
     * Parses ISO 8601 or date strings safely without throwing exceptions or allocating
     * excessive temporary objects.
     */
    fun parseTransactionDate(timestamp: String?, zoneId: ZoneId = ZoneId.systemDefault()): LocalDate? {
        if (timestamp.isNullOrBlank()) return null
        return try {
            if (timestamp.length >= 10 && timestamp[4] == '-' && timestamp[7] == '-') {
                LocalDate.parse(timestamp.substring(0, 10))
            } else {
                Instant.parse(timestamp).atZone(zoneId).toLocalDate()
            }
        } catch (_: Exception) {
            try {
                Instant.parse(timestamp).atZone(zoneId).toLocalDate()
            } catch (_: Exception) {
                null
            }
        }
    }
}

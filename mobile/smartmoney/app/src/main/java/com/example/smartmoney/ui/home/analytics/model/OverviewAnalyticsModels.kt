package com.example.smartmoney.ui.home.analytics.model

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth

/**
 * Timeframe options for the Cash Flow chart toggle.
 */
enum class CashFlowTimeframe(val label: String) {
    DAY("Day"),
    WEEK("Week"),
    MONTH("Month")
}

/**
 * Single data point along a Cash Flow timeline (Day, Week, or Month).
 */
@Immutable
data class CashFlowPoint(
    val label: String,            // e.g. "12 AM", "Mon", "W1"
    val subLabel: String? = null, // e.g. "00:00 - 04:00", "15 Sep", "1 - 7 Sep"
    val moneyIn: BigDecimal,
    val moneyOut: BigDecimal,
    val net: BigDecimal = moneyIn.subtract(moneyOut)
)

/**
 * Summary totals (In, Out, Net) for a specific cash flow timeframe.
 */
@Immutable
data class CashFlowSummary(
    val totalMoneyIn: BigDecimal = BigDecimal.ZERO,
    val totalMoneyOut: BigDecimal = BigDecimal.ZERO,
    val netCashFlow: BigDecimal = BigDecimal.ZERO
)

/**
 * Grouped weekly cash flow record for a specific week in the selected month.
 */
@Immutable
data class WeeklyCashFlow(
    val weekNumber: Int,
    val weekLabel: String,         // e.g. "Week 1", "Week 2"
    val dateRangeLabel: String,    // e.g. "1 - 7 Sep"
    val moneyIn: BigDecimal,       // Inflow amount
    val moneyOut: BigDecimal,      // Outflow amount
    val net: BigDecimal            // moneyIn - moneyOut
)

/**
 * Spending breakdown for a specific category within the selected month.
 */
@Immutable
data class SpendingCategory(
    val category: String,
    val amount: BigDecimal,
    val percentage: Double,        // 0.0 .. 100.0, e.g. 41.4
    val color: Color,
    val transactionCount: Int = 0
)

/**
 * Daily spending record for the calendar heatmap.
 */
@Immutable
data class DailySpending(
    val date: LocalDate,
    val dayOfMonth: Int,
    val amount: BigDecimal,
    val transactionCount: Int,
    val categoryBreakdown: List<Pair<String, BigDecimal>> = emptyList(),
    val intensityLevel: Int = 0    // 0 = KES 0, 1 = KES 1–999, 2 = KES 1,000–2,499, 3 = KES 2,500+
)

/**
 * Single day pacing point comparing actual cumulative spending to the uniform monthly budget pace.
 */
@Immutable
data class BudgetPacingPoint(
    val date: LocalDate,
    val dayOfMonth: Int,
    val actualCumulative: BigDecimal?,  // Null for future days in the month
    val plannedCumulative: BigDecimal
)

/**
 * Pacing status relative to planned uniform pace.
 */
enum class PacingStatus(val displayName: String) {
    AHEAD_OF_BUDGET("Ahead of budget"),
    ON_PACE("On pace"),
    BEHIND_BUDGET("Behind budget"),
    NO_BUDGET("No budget")
}

/**
 * Comprehensive immutable analytics dataset for the Overview screen for a specific [month].
 */
@Immutable
data class OverviewAnalyticsData(
    val month: YearMonth = YearMonth.now(),
    val totalMoneyIn: BigDecimal = BigDecimal.ZERO,
    val totalMoneyOut: BigDecimal = BigDecimal.ZERO,
    val netCashFlow: BigDecimal = BigDecimal.ZERO,
    val weeklyCashFlows: List<WeeklyCashFlow> = emptyList(),
    val dayCashFlowPoints: List<CashFlowPoint> = emptyList(),
    val daySummary: CashFlowSummary = CashFlowSummary(),
    val weekCashFlowPoints: List<CashFlowPoint> = emptyList(),
    val weekSummary: CashFlowSummary = CashFlowSummary(),
    val categories: List<SpendingCategory> = emptyList(),
    val dailySpendings: Map<Int, DailySpending> = emptyMap(),
    val highestSpendingDay: DailySpending? = null,
    val monthlyBudgetLimit: BigDecimal = BigDecimal.ZERO,
    val budgetSpent: BigDecimal = BigDecimal.ZERO,
    val budgetRemaining: BigDecimal = BigDecimal.ZERO,
    val pacingStatus: PacingStatus = PacingStatus.NO_BUDGET,
    val pacingPoints: List<BudgetPacingPoint> = emptyList(),
    val hasTransactions: Boolean = false,
    val hasBudget: Boolean = false
) {
    companion object {
        val EMPTY = OverviewAnalyticsData()
    }
}

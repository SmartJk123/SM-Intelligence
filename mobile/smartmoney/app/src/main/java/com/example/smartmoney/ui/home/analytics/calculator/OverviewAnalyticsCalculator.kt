package com.example.smartmoney.ui.home.analytics.calculator

import androidx.compose.ui.graphics.Color
import com.example.smartmoney.domain.model.Budget
import com.example.smartmoney.domain.model.Transaction
import com.example.smartmoney.ui.home.analytics.model.BudgetPacingPoint
import com.example.smartmoney.ui.home.analytics.model.CashFlowPoint
import com.example.smartmoney.ui.home.analytics.model.CashFlowSummary
import com.example.smartmoney.ui.home.analytics.model.DailySpending
import com.example.smartmoney.ui.home.analytics.model.OverviewAnalyticsData
import com.example.smartmoney.ui.home.analytics.model.PacingStatus
import com.example.smartmoney.ui.home.analytics.model.SpendingCategory
import com.example.smartmoney.ui.home.analytics.model.WeeklyCashFlow
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

/**
 * Pure, high-performance financial calculation engine for the Overview screen analytics.
 * Executes on background threads off the Main/UI thread with strict [BigDecimal] precision.
 */
object OverviewAnalyticsCalculator {

    private val CategoryColors: Map<String, Color> = mapOf(
        "Rent" to Color(0xFF657166),       // Dark Slate Green
        "Groceries" to Color(0xFF22C55E),  // Mint Green
        "Transport" to Color(0xFF38BDF8),  // Powder Blue
        "Utilities" to Color(0xFFF59E0B),  // Amber
        "Dining" to Color(0xFFFB923C),     // Warm Orange
        "Shopping" to Color(0xFFEC4899),   // Rose Pink
        "Healthcare" to Color(0xFFEF4444), // Coral Red
        "Education" to Color(0xFF8B5CF6),  // Purple
        "Other" to Color(0xFF94A3B8)       // Slate Gray
    )

    fun calculate(
        transactions: List<Transaction>,
        budgets: List<Budget>,
        selectedMonth: YearMonth,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): OverviewAnalyticsData {
        val daysInMonth = selectedMonth.lengthOfMonth()
        val monthNameShort = selectedMonth.month.getDisplayName(TextStyle.SHORT, Locale.US)
        val today = LocalDate.now(zoneId)
        val isCurrentMonth = selectedMonth == YearMonth.from(today)
        val isPastMonth = selectedMonth < YearMonth.from(today)

        // 1. Filter transactions belonging to the selected month
        val monthTransactions = transactions.filter { tx ->
            val txDate = parseDate(tx.timestamp, zoneId)
            txDate != null && YearMonth.from(txDate) == selectedMonth
        }

        val hasTx = monthTransactions.isNotEmpty()

        // 2. Weekly Cash Flow Aggregation (Weeks 1 to 5)
        var totalMoneyIn = BigDecimal.ZERO
        var totalMoneyOut = BigDecimal.ZERO

        val weekRanges = listOf(
            1 to (1..7),
            2 to (8..14),
            3 to (15..21),
            4 to (22..28),
            5 to (29..daysInMonth)
        )

        val weeklyList = mutableListOf<WeeklyCashFlow>()

        for ((weekNum, dayRange) in weekRanges) {
            val weekTxs = monthTransactions.filter { tx ->
                val day = parseDate(tx.timestamp, zoneId)?.dayOfMonth ?: 0
                day in dayRange
            }

            var wIn = BigDecimal.ZERO
            var wOut = BigDecimal.ZERO

            for (tx in weekTxs) {
                val isCredit = tx.type.equals("CREDIT", ignoreCase = true)
                val isDebit = tx.type.equals("DEBIT", ignoreCase = true) || tx.type.equals("EXPENSE", ignoreCase = true)
                val amt = tx.amount.abs()

                if (isCredit) {
                    wIn = wIn.add(amt)
                } else if (isDebit) {
                    wOut = wOut.add(amt)
                }
            }

            totalMoneyIn = totalMoneyIn.add(wIn)
            totalMoneyOut = totalMoneyOut.add(wOut)

            val startDay = dayRange.first
            val endDay = dayRange.last
            val rangeLabel = "$startDay - $endDay $monthNameShort"

            weeklyList.add(
                WeeklyCashFlow(
                    weekNumber = weekNum,
                    weekLabel = "Week $weekNum",
                    dateRangeLabel = rangeLabel,
                    moneyIn = wIn,
                    moneyOut = wOut,
                    net = wIn.subtract(wOut)
                )
            )
        }

        val netCashFlow = totalMoneyIn.subtract(totalMoneyOut)

        // 2b. Day Cash Flow Points (Intraday 24h timeline)
        val targetDay = if (isCurrentMonth) {
            today
        } else {
            monthTransactions.mapNotNull { parseDate(it.timestamp, zoneId) }.maxOrNull() ?: selectedMonth.atDay(1)
        }

        val dayTxs = transactions.filter { tx -> parseDate(tx.timestamp, zoneId) == targetDay }
        val daySlots = listOf(
            Triple("12 AM", "00:00 - 04:00", 0..3),
            Triple("4 AM", "04:00 - 08:00", 4..7),
            Triple("8 AM", "08:00 - 12:00", 8..11),
            Triple("12 PM", "12:00 - 16:00", 12..15),
            Triple("4 PM", "16:00 - 20:00", 16..19),
            Triple("8 PM", "20:00 - 24:00", 20..23)
        )
        val dayCashFlowPoints = mutableListOf<CashFlowPoint>()
        var dayTotalIn = BigDecimal.ZERO
        var dayTotalOut = BigDecimal.ZERO

        for ((slotLabel, slotSub, hourRange) in daySlots) {
            val slotTxs = dayTxs.filter { tx ->
                val h = parseHour(tx.timestamp)
                h in hourRange
            }
            var sIn = BigDecimal.ZERO
            var sOut = BigDecimal.ZERO
            for (tx in slotTxs) {
                val isCredit = tx.type.equals("CREDIT", ignoreCase = true)
                val isDebit = tx.type.equals("DEBIT", ignoreCase = true) || tx.type.equals("EXPENSE", ignoreCase = true)
                val amt = tx.amount.abs()
                if (isCredit) sIn = sIn.add(amt)
                else if (isDebit) sOut = sOut.add(amt)
            }
            dayTotalIn = dayTotalIn.add(sIn)
            dayTotalOut = dayTotalOut.add(sOut)
            dayCashFlowPoints.add(
                CashFlowPoint(
                    label = slotLabel,
                    subLabel = slotSub,
                    moneyIn = sIn,
                    moneyOut = sOut,
                    net = sIn.subtract(sOut)
                )
            )
        }
        val daySummary = CashFlowSummary(
            totalMoneyIn = dayTotalIn,
            totalMoneyOut = dayTotalOut,
            netCashFlow = dayTotalIn.subtract(dayTotalOut)
        )

        // 2c. Week Cash Flow Points (7-Day Mon–Sun timeline)
        val targetWeekMonday = if (isCurrentMonth) {
            today.with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))
        } else {
            selectedMonth.atDay(1).with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))
        }

        val weekCashFlowPoints = mutableListOf<CashFlowPoint>()
        var weekTotalIn = BigDecimal.ZERO
        var weekTotalOut = BigDecimal.ZERO

        for (i in 0..6) {
            val d = targetWeekMonday.plusDays(i.toLong())
            val dName = d.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.US)
            val dSub = "${d.dayOfMonth} ${d.month.getDisplayName(TextStyle.SHORT, Locale.US)}"
            val dTxs = transactions.filter { tx -> parseDate(tx.timestamp, zoneId) == d }

            var dIn = BigDecimal.ZERO
            var dOut = BigDecimal.ZERO
            for (tx in dTxs) {
                val isCredit = tx.type.equals("CREDIT", ignoreCase = true)
                val isDebit = tx.type.equals("DEBIT", ignoreCase = true) || tx.type.equals("EXPENSE", ignoreCase = true)
                val amt = tx.amount.abs()
                if (isCredit) dIn = dIn.add(amt)
                else if (isDebit) dOut = dOut.add(amt)
            }
            weekTotalIn = weekTotalIn.add(dIn)
            weekTotalOut = weekTotalOut.add(dOut)
            weekCashFlowPoints.add(
                CashFlowPoint(
                    label = dName,
                    subLabel = dSub,
                    moneyIn = dIn,
                    moneyOut = dOut,
                    net = dIn.subtract(dOut)
                )
            )
        }
        val weekSummary = CashFlowSummary(
            totalMoneyIn = weekTotalIn,
            totalMoneyOut = weekTotalOut,
            netCashFlow = weekTotalIn.subtract(weekTotalOut)
        )

        // 3. Category Breakdown (Share of total spending)
        val debitTxs = monthTransactions.filter { tx ->
            tx.type.equals("DEBIT", ignoreCase = true) || tx.type.equals("EXPENSE", ignoreCase = true)
        }

        val categoryAmountMap = mutableMapOf<String, BigDecimal>()
        val categoryCountMap = mutableMapOf<String, Int>()

        for (tx in debitTxs) {
            val cat = resolveCategory(tx.description)
            val amt = tx.amount.abs()
            categoryAmountMap.merge(cat, amt, BigDecimal::add)
            categoryCountMap.merge(cat, 1) { a, b -> a + b }
        }

        val totalSpending = categoryAmountMap.values.fold(BigDecimal.ZERO, BigDecimal::add)

        val categoriesList = categoryAmountMap.entries
            .sortedByDescending { it.value }
            .map { (catName, amt) ->
                val pct = if (totalSpending > BigDecimal.ZERO) {
                    amt.multiply(BigDecimal(100))
                        .divide(totalSpending, 1, RoundingMode.HALF_UP)
                        .toDouble()
                } else 0.0

                SpendingCategory(
                    category = catName,
                    amount = amt,
                    percentage = pct,
                    color = CategoryColors[catName] ?: CategoryColors["Other"]!!,
                    transactionCount = categoryCountMap[catName] ?: 0
                )
            }

        // 4. Daily Spending Calendar Heatmap (Days 1 to daysInMonth)
        val dailyMap = mutableMapOf<Int, DailySpending>()
        var highestDay: DailySpending? = null

        for (day in 1..daysInMonth) {
            val date = selectedMonth.atDay(day)
            val dayTxs = debitTxs.filter { tx ->
                parseDate(tx.timestamp, zoneId)?.dayOfMonth == day
            }

            var dayAmt = BigDecimal.ZERO
            val dayCatMap = mutableMapOf<String, BigDecimal>()

            for (tx in dayTxs) {
                val amt = tx.amount.abs()
                dayAmt = dayAmt.add(amt)
                val cat = resolveCategory(tx.description)
                dayCatMap.merge(cat, amt, BigDecimal::add)
            }

            val intensity = when {
                dayAmt == BigDecimal.ZERO -> 0
                dayAmt < BigDecimal(1000) -> 1
                dayAmt < BigDecimal(2500) -> 2
                else -> 3
            }

            val daySpending = DailySpending(
                date = date,
                dayOfMonth = day,
                amount = dayAmt,
                transactionCount = dayTxs.size,
                categoryBreakdown = dayCatMap.entries.map { it.key to it.value }.sortedByDescending { it.second },
                intensityLevel = intensity
            )

            dailyMap[day] = daySpending

            if (dayAmt > BigDecimal.ZERO) {
                if (highestDay == null || dayAmt > highestDay.amount) {
                    highestDay = daySpending
                }
            }
        }

        // 5. Budget Pacing Line Graph
        val totalBudgetLimit = budgets.fold(BigDecimal.ZERO) { acc, b ->
            acc.add(BigDecimal.valueOf(b.allocatedMajor))
        }

        val hasBudget = totalBudgetLimit > BigDecimal.ZERO

        val pacingPoints = mutableListOf<BudgetPacingPoint>()
        var runningActual = BigDecimal.ZERO

        val maxEvaluationDay = when {
            isPastMonth -> daysInMonth
            isCurrentMonth -> today.dayOfMonth.coerceAtMost(daysInMonth)
            else -> 0 // Future month
        }

        val dailyPace = if (daysInMonth > 0 && hasBudget) {
            totalBudgetLimit.divide(BigDecimal(daysInMonth), 4, RoundingMode.HALF_UP)
        } else BigDecimal.ZERO

        for (day in 1..daysInMonth) {
            val date = selectedMonth.atDay(day)
            val daySpend = dailyMap[day]?.amount ?: BigDecimal.ZERO

            val planned = dailyPace.multiply(BigDecimal(day)).setScale(2, RoundingMode.HALF_UP)

            val actual: BigDecimal? = if (day <= maxEvaluationDay) {
                runningActual = runningActual.add(daySpend)
                runningActual
            } else null

            pacingPoints.add(
                BudgetPacingPoint(
                    date = date,
                    dayOfMonth = day,
                    actualCumulative = actual,
                    plannedCumulative = planned
                )
            )
        }

        val currentActual = if (maxEvaluationDay > 0) runningActual else BigDecimal.ZERO
        val currentPlanned = dailyPace.multiply(BigDecimal(maxEvaluationDay.coerceAtLeast(1)))

        val pacingStatus = when {
            !hasBudget -> PacingStatus.NO_BUDGET
            maxEvaluationDay == 0 -> PacingStatus.ON_PACE
            currentActual > totalBudgetLimit -> PacingStatus.BEHIND_BUDGET
            currentActual <= currentPlanned.multiply(BigDecimal("0.95")) -> PacingStatus.AHEAD_OF_BUDGET
            currentActual <= currentPlanned.multiply(BigDecimal("1.05")) -> PacingStatus.ON_PACE
            else -> PacingStatus.BEHIND_BUDGET
        }

        val budgetRemaining = if (hasBudget) {
            totalBudgetLimit.subtract(currentActual)
        } else BigDecimal.ZERO

        return OverviewAnalyticsData(
            month = selectedMonth,
            totalMoneyIn = totalMoneyIn,
            totalMoneyOut = totalMoneyOut,
            netCashFlow = netCashFlow,
            weeklyCashFlows = weeklyList,
            dayCashFlowPoints = dayCashFlowPoints,
            daySummary = daySummary,
            weekCashFlowPoints = weekCashFlowPoints,
            weekSummary = weekSummary,
            categories = categoriesList,
            dailySpendings = dailyMap,
            highestSpendingDay = highestDay,
            monthlyBudgetLimit = totalBudgetLimit,
            budgetSpent = currentActual,
            budgetRemaining = budgetRemaining,
            pacingStatus = pacingStatus,
            pacingPoints = pacingPoints,
            hasTransactions = hasTx,
            hasBudget = hasBudget
        )
    }

    private fun resolveCategory(description: String?): String {
        val lower = description?.lowercase()?.trim() ?: return "Other"
        return when {
            lower.contains("rent") || lower.contains("hous") || lower.contains("mortgage") -> "Rent"
            lower.contains("grocer") || lower.contains("supermarket") || lower.contains("food") ||
                    lower.contains("naivas") || lower.contains("carrefour") || lower.contains("quickmart") -> "Groceries"
            lower.contains("trans") || lower.contains("fuel") || lower.contains("uber") ||
                    lower.contains("bolt") || lower.contains("matatu") || lower.contains("total") ||
                    lower.contains("shell") || lower.contains("rubis") -> "Transport"
            lower.contains("util") || lower.contains("power") || lower.contains("kplc") ||
                    lower.contains("electric") || lower.contains("water") || lower.contains("internet") ||
                    lower.contains("zuku") || lower.contains("safaricom") || lower.contains("airtime") ||
                    lower.contains("wifi") || lower.contains("bill") -> "Utilities"
            lower.contains("din") || lower.contains("restaur") || lower.contains("cafe") ||
                    lower.contains("java") || lower.contains("pizza") || lower.contains("kfc") ||
                    lower.contains("artcaffe") || lower.contains("coffee") -> "Dining"
            lower.contains("shop") || lower.contains("cloth") || lower.contains("mall") ||
                    lower.contains("jumia") || lower.contains("amazon") || lower.contains("shoe") -> "Shopping"
            lower.contains("health") || lower.contains("hosp") || lower.contains("pharm") ||
                    lower.contains("med") || lower.contains("doctor") || lower.contains("clinic") -> "Healthcare"
            lower.contains("educ") || lower.contains("school") || lower.contains("fee") ||
                    lower.contains("course") || lower.contains("uni") -> "Education"
            else -> "Other"
        }
    }

    private fun parseDate(timestamp: String?, zoneId: ZoneId): LocalDate? {
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

    private fun parseHour(timestamp: String?): Int {
        if (timestamp.isNullOrBlank()) return 12
        return try {
            val tIdx = timestamp.indexOf('T')
            val sIdx = timestamp.indexOf(' ')
            val timeStart = when {
                tIdx >= 0 -> tIdx + 1
                sIdx >= 0 -> sIdx + 1
                else -> return 12
            }
            if (timestamp.length >= timeStart + 2) {
                timestamp.substring(timeStart, timeStart + 2).toIntOrNull() ?: 12
            } else 12
        } catch (_: Exception) {
            12
        }
    }
}

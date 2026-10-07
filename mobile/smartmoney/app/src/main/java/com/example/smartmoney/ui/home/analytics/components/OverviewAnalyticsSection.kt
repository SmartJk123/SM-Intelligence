package com.example.smartmoney.ui.home.analytics.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.smartmoney.ui.home.analytics.model.OverviewAnalyticsData
import java.time.YearMonth

/**
 * Cohesive financial analytics section displaying:
 * 1. Month Navigation Header
 * 2. Weekly Cash Flow Columns Card
 * 3. Spending by Category Donut Card
 * 4. Daily Spending Calendar Heatmap Card
 * 5. Budget Pacing Cumulative Line Graph Card
 */
@Composable
fun OverviewAnalyticsSection(
    analyticsData: OverviewAnalyticsData,
    selectedMonth: YearMonth,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onOpenBudgetsClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        // Month navigation header
        AnalyticsMonthHeader(
            selectedMonth = selectedMonth,
            onPreviousMonth = onPreviousMonth,
            onNextMonth = onNextMonth
        )

        // 1. Cash flow card (Day, Week, Month toggle)
        CashFlowColumnsCard(
            weeklyCashFlows = analyticsData.weeklyCashFlows,
            totalMoneyIn = analyticsData.totalMoneyIn,
            totalMoneyOut = analyticsData.totalMoneyOut,
            netCashFlow = analyticsData.netCashFlow,
            hasTransactions = analyticsData.hasTransactions,
            dayCashFlowPoints = analyticsData.dayCashFlowPoints,
            daySummary = analyticsData.daySummary,
            weekCashFlowPoints = analyticsData.weekCashFlowPoints,
            weekSummary = analyticsData.weekSummary
        )

        // 2. Spending by category donut
        SpendingDonutCard(
            categories = analyticsData.categories,
            totalSpending = analyticsData.totalMoneyOut,
            hasTransactions = analyticsData.hasTransactions
        )

        // 3. Daily spending heatmap
        DailySpendingHeatmapCard(
            month = selectedMonth,
            dailySpendings = analyticsData.dailySpendings,
            highestSpendingDay = analyticsData.highestSpendingDay,
            hasTransactions = analyticsData.hasTransactions
        )

        // 4. Budget pacing graph
        BudgetPacingGraphCard(
            pacingPoints = analyticsData.pacingPoints,
            monthlyBudgetLimit = analyticsData.monthlyBudgetLimit,
            budgetSpent = analyticsData.budgetSpent,
            budgetRemaining = analyticsData.budgetRemaining,
            pacingStatus = analyticsData.pacingStatus,
            hasBudget = analyticsData.hasBudget,
            onOpenBudgetsClick = onOpenBudgetsClick
        )
    }
}

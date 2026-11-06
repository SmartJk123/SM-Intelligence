package com.example.smartmoney.ui.analytics.model

import java.math.BigDecimal
import java.time.LocalDate

enum class WealthScopeFilter {
    PURE_LIQUID,
    LIQUID_PLUS_MMF,
    TOTAL_NET_WORTH
}

enum class TimeHorizon(val displayName: String, val months: Int) {
    THREE_MONTHS("3M", 3),
    SIX_MONTHS("6M", 6),
    ONE_YEAR("1Y", 12),
    THREE_YEARS("3Y", 36),
    FIVE_YEARS("5Y", 60)
}

enum class BudgetCutPreset(val displayName: String, val cutRatio: Float) {
    CURRENT_PACE("Current Pace", 0.0f),
    BUDGETS_MET("Target Budgets Met", 0.08f),
    MODERATE_CUT("Save 10%", 0.10f),
    AGGRESSIVE_CUT("Save 20%", 0.20f)
}

data class TrajectoryPoint(
    val date: LocalDate,
    val baselineAmount: BigDecimal,
    val optimizedAmount: BigDecimal,
    val label: String
)

data class MaturityMilestone(
    val investmentName: String,
    val maturityDate: LocalDate,
    val payoutAmount: BigDecimal,
    val principalAmount: BigDecimal,
    val gainAmount: BigDecimal,
    val daysRemaining: Long
)

data class WealthComposition(
    val pureLiquidCash: BigDecimal,
    val mmfHoldings: BigDecimal,
    val fixedTermHoldings: BigDecimal,
    val totalNetWorth: BigDecimal,
    val liquidRatio: Float
)

data class FinancialDiagnostics(
    val monthlyBurnRate: BigDecimal,
    val emergencyRunwayMonths: Double,
    val savingsRatePercent: Float,
    val projectedExtraSavings1Y: BigDecimal,
    val runwayExtensionMonths: Double
)

data class AnalyticsUiState(
    val scopeFilter: WealthScopeFilter = WealthScopeFilter.PURE_LIQUID,
    val timeHorizon: TimeHorizon = TimeHorizon.ONE_YEAR,
    val budgetCutPreset: BudgetCutPreset = BudgetCutPreset.CURRENT_PACE,
    val rolloverMaturities: Boolean = false,
    
    val composition: WealthComposition? = null,
    val trajectory: List<TrajectoryPoint> = emptyList(),
    val milestones: List<MaturityMilestone> = emptyList(),
    val diagnostics: FinancialDiagnostics? = null,
    
    val isLoading: Boolean = true
)

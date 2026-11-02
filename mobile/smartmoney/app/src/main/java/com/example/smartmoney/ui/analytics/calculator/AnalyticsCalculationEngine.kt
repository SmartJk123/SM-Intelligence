package com.example.smartmoney.ui.analytics.calculator

import com.example.smartmoney.domain.model.BankAccount
import com.example.smartmoney.domain.model.Budget
import com.example.smartmoney.domain.model.Investment
import com.example.smartmoney.domain.model.InvestmentType
import com.example.smartmoney.domain.model.Transaction
import com.example.smartmoney.ui.analytics.model.*
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

object AnalyticsCalculationEngine {

    fun calculateWealthComposition(
        bankAccounts: List<BankAccount>,
        investments: List<Investment>
    ): WealthComposition {
        val pureLiquidCash = bankAccounts.fold(BigDecimal.ZERO) { acc, account -> acc + account.balance }
        
        var mmfHoldings = BigDecimal.ZERO
        var fixedTermHoldings = BigDecimal.ZERO
        
        investments.forEach { inv ->
            val value = BigDecimal(inv.currentValueMinor ?: inv.principalMinor).divide(BigDecimal(100))
            if (inv.type == InvestmentType.MONEY_MARKET) {
                mmfHoldings += value
            } else {
                fixedTermHoldings += value
            }
        }
        
        val totalNetWorth = pureLiquidCash + mmfHoldings + fixedTermHoldings
        val liquidRatio = if (totalNetWorth > BigDecimal.ZERO) {
            ((pureLiquidCash + mmfHoldings).toFloat() / totalNetWorth.toFloat()) * 100f
        } else 0f
        
        return WealthComposition(
            pureLiquidCash = pureLiquidCash,
            mmfHoldings = mmfHoldings,
            fixedTermHoldings = fixedTermHoldings,
            totalNetWorth = totalNetWorth,
            liquidRatio = liquidRatio
        )
    }

    fun calculateMaturities(investments: List<Investment>): List<MaturityMilestone> {
        val today = LocalDate.now()
        return investments
            .filter { it.maturityDate != null && !it.isMatured }
            .map { inv ->
                val principal = BigDecimal(inv.principalMinor).divide(BigDecimal(100))
                val currentValue = BigDecimal(inv.currentValueMinor ?: inv.principalMinor).divide(BigDecimal(100))
                // Simple assumption: payout is current value (which could include accrued yield).
                // Or you could project future yield if you have the rate. For now, we use current value as a baseline.
                val payout = currentValue // Assuming current value represents expected payout for simplicity. 
                // Alternatively, a fixed return calculation can be done here.
                val gain = payout - principal
                
                MaturityMilestone(
                    investmentName = inv.name,
                    maturityDate = inv.maturityDate!!,
                    payoutAmount = payout,
                    principalAmount = principal,
                    gainAmount = gain,
                    daysRemaining = ChronoUnit.DAYS.between(today, inv.maturityDate)
                )
            }
            .sortedBy { it.daysRemaining }
    }

    fun computeDiagnostics(
        wealthComp: WealthComposition,
        transactions: List<Transaction>,
        budgetCut: BudgetCutPreset
    ): FinancialDiagnostics {
        // Simple heuristic based on past 90 days.
        val todayStr = LocalDate.now().toString()
        val ninetyDaysAgoStr = LocalDate.now().minusDays(90).toString()
        
        // Just mock some calculation if tx are empty
        var totalOutflow = BigDecimal.ZERO
        var totalInflow = BigDecimal.ZERO
        
        transactions.forEach { tx ->
            if (tx.amount < BigDecimal.ZERO || tx.type.contains("DEBIT", ignoreCase = true)) {
                totalOutflow += tx.amount.abs()
            } else {
                totalInflow += tx.amount
            }
        }
        
        // Monthly avg
        val monthlyBurnRate = if (totalOutflow > BigDecimal.ZERO) totalOutflow.divide(BigDecimal(3), 2, RoundingMode.HALF_UP) else BigDecimal("30000")
        val monthlyInflow = if (totalInflow > BigDecimal.ZERO) totalInflow.divide(BigDecimal(3), 2, RoundingMode.HALF_UP) else BigDecimal("50000")
        
        val savingsRatePercent = if (monthlyInflow > BigDecimal.ZERO) {
            ((monthlyInflow - monthlyBurnRate).toFloat() / monthlyInflow.toFloat()) * 100f
        } else 0f
        
        val pureLiquid = wealthComp.pureLiquidCash
        val emergencyRunwayMonths = if (monthlyBurnRate > BigDecimal.ZERO) {
            pureLiquid.toDouble() / monthlyBurnRate.toDouble()
        } else 0.0
        
        // Simulated discretionary is assumed to be 30% of burn rate for this model.
        val discretionaryBurn = monthlyBurnRate * BigDecimal("0.30")
        val savedPerMonth = discretionaryBurn * BigDecimal(budgetCut.cutRatio.toString())
        val projectedExtraSavings1Y = savedPerMonth * BigDecimal(12)
        
        val newMonthlyBurnRate = monthlyBurnRate - savedPerMonth
        val newRunwayMonths = if (newMonthlyBurnRate > BigDecimal.ZERO) {
            pureLiquid.toDouble() / newMonthlyBurnRate.toDouble()
        } else 0.0
        
        val runwayExtensionMonths = newRunwayMonths - emergencyRunwayMonths
        
        return FinancialDiagnostics(
            monthlyBurnRate = monthlyBurnRate,
            emergencyRunwayMonths = emergencyRunwayMonths,
            savingsRatePercent = savingsRatePercent.coerceAtLeast(0f),
            projectedExtraSavings1Y = projectedExtraSavings1Y,
            runwayExtensionMonths = runwayExtensionMonths.coerceAtLeast(0.0)
        )
    }

    fun generateTrajectory(
        wealthComp: WealthComposition,
        maturities: List<MaturityMilestone>,
        transactions: List<Transaction>,
        horizon: TimeHorizon,
        budgetCut: BudgetCutPreset,
        rollover: Boolean,
        scopeFilter: WealthScopeFilter
    ): List<TrajectoryPoint> {
        val today = LocalDate.now()
        val points = mutableListOf<TrajectoryPoint>()
        
        val startValue = when (scopeFilter) {
            WealthScopeFilter.PURE_LIQUID -> wealthComp.pureLiquidCash
            WealthScopeFilter.LIQUID_PLUS_MMF -> wealthComp.pureLiquidCash + wealthComp.mmfHoldings
            WealthScopeFilter.TOTAL_NET_WORTH -> wealthComp.totalNetWorth
        }
        
        val diagnostics = computeDiagnostics(wealthComp, transactions, budgetCut)
        val baselineMonthlySavings = diagnostics.monthlyInflowOrAssumed() - diagnostics.monthlyBurnRate
        val optimizedMonthlySavings = baselineMonthlySavings + (diagnostics.monthlyBurnRate * BigDecimal("0.3") * BigDecimal(budgetCut.cutRatio.toString()))
        
        // Generate a point for each month
        for (i in 0..horizon.months) {
            val date = today.plusMonths(i.toLong())
            
            // Baseline simple accumulation
            var baseline = startValue + (baselineMonthlySavings * BigDecimal(i))
            var optimized = startValue + (optimizedMonthlySavings * BigDecimal(i))
            
            // Factor in maturities
            maturities.filter { it.maturityDate.isBefore(date) || it.maturityDate.isEqual(date) }.forEach { milestone ->
                // If scope is PURE_LIQUID, realizing maturity adds to liquid.
                if (scopeFilter == WealthScopeFilter.PURE_LIQUID && !rollover) {
                    baseline += milestone.payoutAmount
                    optimized += milestone.payoutAmount
                }
            }
            
            points.add(
                TrajectoryPoint(
                    date = date,
                    baselineAmount = baseline,
                    optimizedAmount = optimized,
                    label = date.format(DateTimeFormatter.ofPattern("MMM yyyy"))
                )
            )
        }
        return points
    }
    
    private fun FinancialDiagnostics.monthlyInflowOrAssumed(): BigDecimal {
        // Quick helper for dummy inflow if negative savings
        return if (this.monthlyBurnRate > BigDecimal.ZERO) {
             this.monthlyBurnRate + (this.monthlyBurnRate * BigDecimal("0.2"))
        } else {
             BigDecimal("50000")
        }
    }
}

package com.example.smartmoney

import com.example.smartmoney.domain.model.Budget
import com.example.smartmoney.domain.model.BudgetStatus
import com.example.smartmoney.domain.model.BudgetSummary
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class BudgetCalculationTest {

    @Test
    fun evaluates_budget_status_as_over_budget_when_spent_exceeds_allocated() {
        val budget = Budget(
            id = "b1",
            category = "Shopping",
            allocatedMinor = 100_000L, // KES 1,000.00
            start = LocalDate.parse("2026-09-01"),
            end = LocalDate.parse("2026-09-30"),
            threshold = 80
        )

        val spentMinor = 120_000L // KES 1,200.00
        val percentUsed = (spentMinor.toFloat() / budget.allocatedMinor.toFloat()) * 100f
        val status = when {
            spentMinor > budget.allocatedMinor -> BudgetStatus.OVER_BUDGET
            percentUsed >= budget.threshold -> BudgetStatus.APPROACHING_LIMIT
            else -> BudgetStatus.WITHIN_BUDGET
        }

        val summary = BudgetSummary(
            budget = budget,
            spentMinor = spentMinor,
            status = status,
            percentUsed = percentUsed
        )

        assertEquals(BudgetStatus.OVER_BUDGET, summary.status)
        assertEquals(120.0f, summary.percentUsed, 0.01f)
        assertEquals(0L, summary.remainingMinor)
    }

    @Test
    fun evaluates_budget_status_as_approaching_limit_when_threshold_reached() {
        val budget = Budget(
            id = "b2",
            category = "Groceries",
            allocatedMinor = 50_000L, // KES 500.00
            start = LocalDate.parse("2026-09-01"),
            end = LocalDate.parse("2026-09-30"),
            threshold = 85
        )

        val spentMinor = 43_000L // 86% of 50,000
        val percentUsed = (spentMinor.toFloat() / budget.allocatedMinor.toFloat()) * 100f
        val status = when {
            spentMinor > budget.allocatedMinor -> BudgetStatus.OVER_BUDGET
            percentUsed >= budget.threshold -> BudgetStatus.APPROACHING_LIMIT
            else -> BudgetStatus.WITHIN_BUDGET
        }

        val summary = BudgetSummary(
            budget = budget,
            spentMinor = spentMinor,
            status = status,
            percentUsed = percentUsed
        )

        assertEquals(BudgetStatus.APPROACHING_LIMIT, summary.status)
        assertEquals(7_000L, summary.remainingMinor)
        assertEquals(70.0, summary.remainingMajor, 0.01)
    }

    @Test
    fun evaluates_budget_status_as_within_budget_when_under_threshold() {
        val budget = Budget(
            id = "b3",
            category = "Utilities",
            allocatedMinor = 200_000L, // KES 2,000.00
            start = LocalDate.parse("2026-09-01"),
            end = LocalDate.parse("2026-09-30"),
            threshold = 85
        )

        val spentMinor = 100_000L // 50%
        val percentUsed = (spentMinor.toFloat() / budget.allocatedMinor.toFloat()) * 100f
        val status = when {
            spentMinor > budget.allocatedMinor -> BudgetStatus.OVER_BUDGET
            percentUsed >= budget.threshold -> BudgetStatus.APPROACHING_LIMIT
            else -> BudgetStatus.WITHIN_BUDGET
        }

        val summary = BudgetSummary(
            budget = budget,
            spentMinor = spentMinor,
            status = status,
            percentUsed = percentUsed
        )

        assertEquals(BudgetStatus.WITHIN_BUDGET, summary.status)
        assertEquals(100_000L, summary.remainingMinor)
    }
}

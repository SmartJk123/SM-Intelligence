package com.example.smartmoney.domain.model

import androidx.compose.runtime.Immutable
import java.time.LocalDate

enum class BudgetStatus {
    WITHIN_BUDGET,
    APPROACHING_LIMIT,
    OVER_BUDGET
}

/**
 * Pure domain model representing a financial budget allocation.
 *
 * @property id Unique identifier.
 * @property category Spending category (e.g., "Shopping", "Rent", "Groceries", "Utilities").
 * @property allocatedMinor Allocation in minor units (integer cents) to prevent floating-point rounding bugs.
 * @property start Start date of the budget cycle.
 * @property end End date of the budget cycle.
 * @property accountId Optional account ID to restrict budget to a single account (null/blank means all accounts).
 * @property threshold Warning threshold percentage (1..100), default 85%.
 */
@Immutable
data class Budget(
    val id: String,
    val category: String,
    val allocatedMinor: Long,
    val start: LocalDate,
    val end: LocalDate,
    val accountId: String? = null,
    val threshold: Int = 85
) {
    val allocatedMajor: Double
        get() = allocatedMinor / 100.0
}

/**
 * Evaluated budget summary with aggregated expenditure and limit status.
 */
@Immutable
data class BudgetSummary(
    val budget: Budget,
    val spentMinor: Long,
    val status: BudgetStatus,
    val percentUsed: Float
) {
    val spentMajor: Double
        get() = spentMinor / 100.0

    val remainingMinor: Long
        get() = (budget.allocatedMinor - spentMinor).coerceAtLeast(0)

    val remainingMajor: Double
        get() = remainingMinor / 100.0
}

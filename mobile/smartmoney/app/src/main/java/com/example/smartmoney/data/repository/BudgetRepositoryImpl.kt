package com.example.smartmoney.data.repository

import com.example.smartmoney.domain.model.Budget
import com.example.smartmoney.domain.repository.BudgetRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import java.util.UUID

/**
 * Concrete implementation of [BudgetRepository] with in-memory stateful backing store.
 * Initialized with representative Kenyan household budget categories.
 */
class BudgetRepositoryImpl : BudgetRepository {

    private val now = LocalDate.now()
    private val startOfMonth = now.with(TemporalAdjusters.firstDayOfMonth())
    private val endOfMonth = now.with(TemporalAdjusters.lastDayOfMonth())

    private val _budgetsFlow = MutableStateFlow<List<Budget>>(
        listOf(
            Budget(
                id = "budget-1",
                category = "Groceries",
                allocatedMinor = 3500000L, // KES 35,000.00
                start = startOfMonth,
                end = endOfMonth,
                threshold = 85
            ),
            Budget(
                id = "budget-2",
                category = "Utilities & Power",
                allocatedMinor = 1200000L, // KES 12,000.00
                start = startOfMonth,
                end = endOfMonth,
                threshold = 80
            ),
            Budget(
                id = "budget-3",
                category = "Shopping",
                allocatedMinor = 1500000L, // KES 15,000.00
                start = startOfMonth,
                end = endOfMonth,
                threshold = 85
            ),
            Budget(
                id = "budget-4",
                category = "Dining & Leisure",
                allocatedMinor = 800000L, // KES 8,000.00
                start = startOfMonth,
                end = endOfMonth,
                threshold = 75
            )
        )
    )

    override fun getBudgets(): Flow<List<Budget>> = _budgetsFlow.asStateFlow()

    override suspend fun saveBudget(budget: Budget): Result<Unit> {
        val currentList = _budgetsFlow.value.toMutableList()
        val index = currentList.indexOfFirst { it.id == budget.id }
        if (index >= 0) {
            currentList[index] = budget
        } else {
            val newBudget = if (budget.id.isBlank()) {
                budget.copy(id = UUID.randomUUID().toString())
            } else budget
            currentList.add(0, newBudget)
        }
        _budgetsFlow.value = currentList
        return Result.success(Unit)
    }

    override suspend fun deleteBudget(id: String): Result<Unit> {
        val updated = _budgetsFlow.value.filter { it.id != id }
        _budgetsFlow.value = updated
        return Result.success(Unit)
    }
}

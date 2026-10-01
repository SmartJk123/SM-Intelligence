package com.example.smartmoney.domain.repository

import com.example.smartmoney.domain.model.Budget
import kotlinx.coroutines.flow.Flow

interface BudgetRepository {
    fun getBudgets(): Flow<List<Budget>>
    suspend fun saveBudget(budget: Budget): Result<Unit>
    suspend fun deleteBudget(id: String): Result<Unit>
}

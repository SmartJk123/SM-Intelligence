package com.example.smartmoney.domain.repository

import com.example.smartmoney.domain.model.Budget
import kotlinx.coroutines.flow.Flow

interface BudgetRepository {
    fun getBudgets(): Flow<List<Budget>>
    fun getBudgets(userId: String?): Flow<List<Budget>>
    suspend fun syncBudgets(userId: String? = null): Result<Unit>
    suspend fun saveBudget(budget: Budget): Result<Unit>
    suspend fun deleteBudget(id: String): Result<Unit>
}

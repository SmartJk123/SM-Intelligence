package com.example.smartmoney.domain.repository

import com.example.smartmoney.domain.model.Investment
import kotlinx.coroutines.flow.Flow

interface InvestmentRepository {
    fun getInvestments(): Flow<List<Investment>>
    fun getInvestments(userId: String?): Flow<List<Investment>>
    suspend fun loadInvestmentsForUser(userId: String?)
    suspend fun saveInvestment(investment: Investment): Result<Unit>
    suspend fun saveInvestment(investment: Investment, userId: String?): Result<Unit>
    suspend fun deleteInvestment(id: String): Result<Unit>
    suspend fun deleteInvestment(id: String, userId: String?): Result<Unit>
}

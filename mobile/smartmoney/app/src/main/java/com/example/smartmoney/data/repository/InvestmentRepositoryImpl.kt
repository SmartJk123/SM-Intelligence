package com.example.smartmoney.data.repository

import com.example.smartmoney.domain.model.Investment
import com.example.smartmoney.domain.model.InvestmentType
import com.example.smartmoney.domain.repository.InvestmentRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDate
import java.util.UUID

/**
 * Concrete implementation of [InvestmentRepository] with in-memory stateful backing store.
 * Initialized with representative Kenyan investments (MMF, T-Bills, Fixed Deposit).
 */
class InvestmentRepositoryImpl : InvestmentRepository {

    private val now = LocalDate.now()

    private val _investmentsFlow = MutableStateFlow<List<Investment>>(
        listOf(
            Investment(
                id = "inv-1",
                name = "Sanlam Money Market Fund",
                type = InvestmentType.MONEY_MARKET,
                principalMinor = 25000000L, // KES 250,000.00
                currentValueMinor = 26850000L, // KES 268,500.00
                valuationDate = now,
                maturityDate = null
            ),
            Investment(
                id = "inv-2",
                name = "CBK Treasury Bill (91-Day)",
                type = InvestmentType.TREASURY_BILL,
                principalMinor = 10000000L, // KES 100,000.00
                currentValueMinor = 10320000L, // KES 103,200.00
                valuationDate = now.minusDays(5),
                maturityDate = now.plusDays(42) // Maturing within 90 days
            ),
            Investment(
                id = "inv-3",
                name = "Stanbic 6-Month Fixed Deposit",
                type = InvestmentType.FIXED_DEPOSIT,
                principalMinor = 15000000L, // KES 150,000.00
                currentValueMinor = 15600000L, // KES 156,000.00
                valuationDate = now.minusDays(12),
                maturityDate = now.plusDays(115)
            )
        )
    )

    override fun getInvestments(): Flow<List<Investment>> = _investmentsFlow.asStateFlow()

    override suspend fun saveInvestment(investment: Investment): Result<Unit> {
        val currentList = _investmentsFlow.value.toMutableList()
        val index = currentList.indexOfFirst { it.id == investment.id }
        if (index >= 0) {
            currentList[index] = investment
        } else {
            val newInv = if (investment.id.isBlank()) {
                investment.copy(id = UUID.randomUUID().toString())
            } else investment
            currentList.add(0, newInv)
        }
        _investmentsFlow.value = currentList
        return Result.success(Unit)
    }

    override suspend fun deleteInvestment(id: String): Result<Unit> {
        val updated = _investmentsFlow.value.filter { it.id != id }
        _investmentsFlow.value = updated
        return Result.success(Unit)
    }
}

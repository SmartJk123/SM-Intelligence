package com.example.smartmoney.ui.transactions

import com.example.smartmoney.core.coroutine.TestDispatcherProvider
import com.example.smartmoney.domain.model.Transaction
import com.example.smartmoney.domain.repository.TransactionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class TransactionViewModelCoroutineTest {

    private class FakeTransactionRepository : TransactionRepository {
        val flow = MutableStateFlow<List<Transaction>>(
            listOf(
                Transaction("1", "acc", BigDecimal("100.00"), "CREDIT", "2026-09-01T12:00:00Z", "Inflow 1"),
                Transaction("2", "acc", BigDecimal("50.00"), "DEBIT", "2026-09-02T12:00:00Z", "Outflow 1"),
                Transaction("3", "acc", BigDecimal("200.00"), "CREDIT", "2026-09-03T12:00:00Z", "Inflow 2")
            )
        )

        override fun getTransactionsFlow(accountId: String?): Flow<List<Transaction>> = flow
        override suspend fun syncTransactions(accountId: String?): Result<Unit> = Result.success(Unit)
        override suspend fun recordTransaction(transaction: Transaction): Result<Transaction> = Result.success(transaction)
    }

    @Test
    fun testFilteredTransactions_filtersOnDefaultDispatcher() = runTest {
        val testDispatchers = TestDispatcherProvider()
        val repo = FakeTransactionRepository()
        val viewModel = TransactionViewModel(
            repository = repo,
            dispatchers = testDispatchers,
            externalScope = backgroundScope
        )

        backgroundScope.launch(testDispatchers.unconfined) {
            viewModel.uiState.collect {
                println("DEBUG_UISTATE: $it")
            }
        }
        val emissions = mutableListOf<List<Transaction>>()
        backgroundScope.launch(testDispatchers.unconfined) {
            viewModel.filteredTransactions.collect {
                emissions.add(it)
            }
        }
        testScheduler.advanceUntilIdle()
        println("DEBUG_EMISSIONS count=${emissions.size}, last_size=${emissions.lastOrNull()?.size}")
        assertEquals(3, emissions.last().size)

        // Switch filter to CREDIT
        viewModel.setFilter("CREDIT")
        testScheduler.advanceUntilIdle()
        val creditTx = viewModel.filteredTransactions.value
        assertEquals(2, creditTx.size)
        assertTrue(creditTx.all { it.type == "CREDIT" })

        // Switch filter to DEBIT
        viewModel.setFilter("DEBIT")
        testScheduler.advanceUntilIdle()
        val debitTx = viewModel.filteredTransactions.value
        assertEquals(1, debitTx.size)
        assertEquals("DEBIT", debitTx.first().type)

        // Switch back to ALL
        viewModel.setFilter("ALL")
        testScheduler.advanceUntilIdle()
        assertEquals(3, viewModel.filteredTransactions.value.size)
    }
}

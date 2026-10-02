package com.example.smartmoney.ui.transactions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.smartmoney.core.coroutine.DefaultDispatcherProvider
import com.example.smartmoney.core.coroutine.DispatcherProvider
import com.example.smartmoney.domain.model.Transaction
import com.example.smartmoney.domain.model.TrendPoint
import com.example.smartmoney.domain.repository.TransactionRepository
import com.example.smartmoney.domain.util.TransactionTrendCalculation
import com.example.smartmoney.domain.util.TransactionTrendCalculator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.math.BigDecimal

class TransactionViewModel(
    private val repository: TransactionRepository? = null,
    private val userId: String? = null,
    private val dispatchers: DispatcherProvider = DefaultDispatcherProvider(),
    externalScope: CoroutineScope? = null
) : ViewModel() {

    private val scope = externalScope ?: viewModelScope

    companion object {
        /**
         * 10 realistic energy and utility transactions populated for testing and prototype preview.
         */
        val SAMPLE_TRANSACTIONS = emptyList<Transaction>()
    }

    private val _errorState = MutableStateFlow<String?>(null)
    private val _selectedFilter = MutableStateFlow("ALL")
    val selectedFilter: StateFlow<String> = _selectedFilter.asStateFlow()

    private val _pendingInvoices = MutableStateFlow<List<Transaction>>(emptyList())

    // Reactive StateFlow with WhileSubscribed(5_000) to stop background database observation when inactive
    val uiState: StateFlow<TransactionUiState> = if (repository != null) {
        combine(
            repository.getTransactionsFlow(userId = userId),
            _errorState
        ) { cachedTransactions, errorMessage ->
            println("DEBUG_COMBINE_BLOCK: cachedTransactions=${cachedTransactions.size}, error=$errorMessage")
            withContext(dispatchers.default) {
                if (errorMessage != null && cachedTransactions.isEmpty()) {
                    TransactionUiState.Error(errorMessage)
                } else if (cachedTransactions.isNotEmpty()) {
                    TransactionUiState.Success(cachedTransactions)
                } else {
                    TransactionUiState.Success(SAMPLE_TRANSACTIONS)
                }
            }
        }
            .catch { e ->
                println("DEBUG_EXCEPTION: $e")
                e.printStackTrace()
                emit(TransactionUiState.Success(SAMPLE_TRANSACTIONS))
            }
            .stateIn(
                scope = scope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = TransactionUiState.Success(SAMPLE_TRANSACTIONS)
            )
    } else {
        MutableStateFlow(TransactionUiState.Success(SAMPLE_TRANSACTIONS)).asStateFlow()
    }

    /**
     * Filtered transactions computed off the Main Thread on [dispatchers.default]
     * to ensure solid 60 FPS performance without in-composition list processing.
     */
    val filteredTransactions: StateFlow<List<Transaction>> = combine(
        uiState,
        _selectedFilter,
        _pendingInvoices
    ) { state, filter, pending ->
        withContext(dispatchers.default) {
            val transactions = when (state) {
                is TransactionUiState.Success -> pending + state.transactions
                else -> pending
            }
            when (filter) {
                "CREDIT" -> transactions.filter { it.type.equals("CREDIT", ignoreCase = true) }
                "DEBIT" -> transactions.filter { it.type.equals("DEBIT", ignoreCase = true) }
                else -> transactions
            }
        }
    }.stateIn(
        scope = scope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SAMPLE_TRANSACTIONS
    )

    /**
     * Unfiltered live transactions stream for the Overview dashboard metrics and trend calculations.
     */
    val allTransactions: StateFlow<List<Transaction>> = combine(
        uiState,
        _pendingInvoices
    ) { state, pending ->
        withContext(dispatchers.default) {
            when (state) {
                is TransactionUiState.Success -> pending + state.transactions
                else -> pending
            }
        }
    }.stateIn(
        scope = scope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SAMPLE_TRANSACTIONS
    )

    /**
     * Precomputed single-pass aggregation and 7-day trend metrics evaluated off the Main Thread
     * on [dispatchers.default] for high-performance 60/120 FPS rendering without in-composition processing.
     */
    private val trendCalculation: StateFlow<TransactionTrendCalculation> = allTransactions
        .map { list ->
            TransactionTrendCalculator.calculateTrendSummary(list)
        }
        .flowOn(dispatchers.default)
        .stateIn(
            scope = scope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = TransactionTrendCalculation()
        )

    val totalInflow: StateFlow<BigDecimal> = trendCalculation
        .map { it.totalInflow }
        .stateIn(
            scope = scope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = BigDecimal.ZERO
        )

    val totalOutflow: StateFlow<BigDecimal> = trendCalculation
        .map { it.totalOutflow }
        .stateIn(
            scope = scope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = BigDecimal.ZERO
        )

    val weeklyTrend: StateFlow<List<TrendPoint>> = trendCalculation
        .map { it.trendPoints }
        .stateIn(
            scope = scope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = TransactionTrendCalculator.DefaultTrend
        )

    val hasTransactions: StateFlow<Boolean> = trendCalculation
        .map { it.hasTransactions }
        .stateIn(
            scope = scope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = false
        )


    fun setFilter(filter: String) {
        _selectedFilter.value = filter
    }

    fun addPendingTransaction(transaction: Transaction) {
        _pendingInvoices.value = _pendingInvoices.value + transaction
        // In a real app, we'd also call InvoiceApi.uploadInvoice here
    }

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    fun refreshTransactions() {
        if (repository != null) {
            scope.launch(dispatchers.io) {
                _isSyncing.value = true
                val result = repository.syncTransactions(userId = userId)
                withContext(dispatchers.main) {
                    _isSyncing.value = false
                    result.onFailure { error ->
                        _errorState.value = error.localizedMessage ?: "Failed to sync transactions"
                    }
                    result.onSuccess {
                        _errorState.value = null
                    }
                }
            }
        }
    }

    fun simulateKcbInflow(
        amount: String = "1000.00",
        onSuccess: (() -> Unit)? = null,
        onError: ((String) -> Unit)? = null
    ) {
        simulateKcbTransaction(amount = amount, direction = "Credit", onSuccess = onSuccess, onError = onError)
    }

    fun simulateKcbOutflow(
        amount: String = "500.00",
        onSuccess: (() -> Unit)? = null,
        onError: ((String) -> Unit)? = null
    ) {
        simulateKcbTransaction(amount = amount, direction = "Debit", onSuccess = onSuccess, onError = onError)
    }

    fun simulateKcbTransaction(
        amount: String = "1000.00",
        direction: String = "Credit",
        onSuccess: (() -> Unit)? = null,
        onError: ((String) -> Unit)? = null
    ) {
        scope.launch(dispatchers.io) {
            try {
                val isDebit = direction.equals("Debit", ignoreCase = true)
                val narration = if (isDebit) "Simulated KCB Outflow" else "Simulated KCB Inflow"
                val req = com.example.smartmoney.data.remote.api.SimulateTransactionRequest(
                    amount = amount,
                    direction = direction,
                    narration = narration
                )
                val response = com.example.smartmoney.data.remote.RetrofitClient.bankIntegrationApi.simulateKcbTransaction(req)
                if (response.isSuccessful) {
                    repository?.syncTransactions(userId = userId)
                    withContext(dispatchers.main) {
                        onSuccess?.invoke()
                    }
                } else {
                    withContext(dispatchers.main) {
                        onError?.invoke("Simulation returned HTTP ${response.code()}")
                    }
                }
            } catch (e: Exception) {
                withContext(dispatchers.main) {
                    onError?.invoke(e.localizedMessage ?: "Network error during simulation")
                }
            }
        }
    }

    class Factory(
        private val repository: TransactionRepository? = null,
        private val userId: String? = null,
        private val dispatchers: DispatcherProvider = DefaultDispatcherProvider()
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return TransactionViewModel(repository, userId, dispatchers) as T
        }
    }
}

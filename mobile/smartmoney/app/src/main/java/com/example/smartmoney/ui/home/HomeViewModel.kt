package com.example.smartmoney.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.smartmoney.core.coroutine.DefaultDispatcherProvider
import com.example.smartmoney.core.coroutine.DispatcherProvider
import com.example.smartmoney.data.remote.RetrofitClient
import com.example.smartmoney.data.remote.api.SimulateTransactionRequest
import com.example.smartmoney.domain.model.Account
import com.example.smartmoney.domain.model.BankAccount
import com.example.smartmoney.domain.repository.AccountRepository
import com.example.smartmoney.domain.repository.BankAccountRepository
import com.example.smartmoney.domain.repository.TransactionRepository
import com.example.smartmoney.domain.util.TransactionTrendCalculation
import com.example.smartmoney.domain.util.TransactionTrendCalculator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.math.BigDecimal

/**
 * Screen-level ViewModel for the Overview / Home screen.
 *
 * Responsibilities:
 * - Aggregates domain models from [AccountRepository], [BankAccountRepository], and [TransactionRepository].
 * - Executes all heavy trend and currency calculations off the Main thread on [Dispatchers.Default].
 * - Owns background network synchronization on [Dispatchers.IO], decoupling network lifecycle from Compose UI.
 * - Manages KCB transaction simulation operations.
 */
class HomeViewModel(
    private val accountRepository: AccountRepository,
    private val bankAccountRepository: BankAccountRepository,
    private val transactionRepository: TransactionRepository,
    private val userId: String,
    private val dispatchers: DispatcherProvider = DefaultDispatcherProvider()
) : ViewModel() {

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _isSimulatingInflow = MutableStateFlow(false)
    val isSimulatingInflow: StateFlow<Boolean> = _isSimulatingInflow.asStateFlow()

    private val _isSimulatingOutflow = MutableStateFlow(false)
    val isSimulatingOutflow: StateFlow<Boolean> = _isSimulatingOutflow.asStateFlow()

    private val _hasDismissedWelcomeSheet = MutableStateFlow(false)

    private data class HomeAggregatedData(
        val trendCalc: TransactionTrendCalculation,
        val activeBalance: BigDecimal,
        val effectiveBanks: List<BankAccount>,
        val isOnboardingActive: Boolean,
        val shouldShowWelcomeSheet: Boolean
    )

    private val aggregatedDataFlow = combine(
        accountRepository.getAccountsFlow(userId),
        bankAccountRepository.getBankAccounts(),
        transactionRepository.getTransactionsFlow(userId = userId),
        _hasDismissedWelcomeSheet
    ) { accounts, bankAccounts, transactions, dismissedWelcome ->
        val trendCalc = TransactionTrendCalculator.calculateTrendSummary(transactions)
        val calculatedBalance = accounts.fold(BigDecimal.ZERO) { acc, a -> acc.add(a.availableBalance) }
        val finalBalance = if (calculatedBalance > BigDecimal.ZERO || accounts.isNotEmpty()) {
            calculatedBalance
        } else {
            bankAccounts.fold(BigDecimal.ZERO) { acc, b -> acc.add(b.balance) }
        }
        val isOnboarding = accounts.isEmpty() && bankAccounts.isEmpty()
        val showSheet = isOnboarding && !dismissedWelcome
        HomeAggregatedData(
            trendCalc = trendCalc,
            activeBalance = finalBalance,
            effectiveBanks = bankAccounts,
            isOnboardingActive = isOnboarding,
            shouldShowWelcomeSheet = showSheet
        )
    }

    val uiState: StateFlow<HomeUiState> = combine(
        aggregatedDataFlow,
        _isSyncing,
        _isSimulatingInflow,
        _isSimulatingOutflow
    ) { data, syncing, simInflow, simOutflow ->
        HomeUiState(
            isLoading = false,
            isSyncing = syncing,
            isSimulatingInflow = simInflow,
            isSimulatingOutflow = simOutflow,
            totalBalance = data.activeBalance,
            totalCashIn = data.trendCalc.totalInflow,
            totalCashOut = data.trendCalc.totalOutflow,
            trend = data.trendCalc.trendPoints,
            bankAccounts = data.effectiveBanks,
            hasTransactions = data.trendCalc.hasTransactions,
            isOnboardingActive = data.isOnboardingActive,
            shouldShowWelcomeSheet = data.shouldShowWelcomeSheet
        )
    }
    .flowOn(dispatchers.default)
    .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HomeUiState.DEFAULT
    )

    init {
        refresh()
    }

    /**
     * Synchronizes transactions and accounts from remote backend services off the Main thread.
     */
    fun refresh() {
        viewModelScope.launch(dispatchers.io) {
            _isSyncing.value = true
            try {
                transactionRepository.syncTransactions(userId = userId)
                if (userId.isNotBlank()) {
                    accountRepository.syncAccounts(userId)
                }
            } catch (_: Exception) {
                // Gracefully tolerate network unavailability
            } finally {
                _isSyncing.value = false
            }
        }
    }

    /**
     * Dismisses the onboarding welcome bottom sheet off the Main thread.
     */
    fun dismissWelcomeSheet() {
        viewModelScope.launch(dispatchers.default) {
            _hasDismissedWelcomeSheet.value = true
        }
    }

    /**
     * Executes bank account linking asynchronously on [Dispatchers.IO], ensuring
     * zero network or database blocking operations occur on the Main thread.
     */
    fun linkBankAccount(
        bankName: String,
        accountNumber: String,
        cardType: String,
        onSuccess: (() -> Unit)? = null,
        onError: ((String) -> Unit)? = null
    ) {
        viewModelScope.launch(dispatchers.io) {
            val result = bankAccountRepository.addBankAccount(
                bankName = bankName,
                accountNumber = accountNumber,
                cardType = cardType
            )
            withContext(dispatchers.main) {
                result.onSuccess {
                    _hasDismissedWelcomeSheet.value = true
                    onSuccess?.invoke()
                }.onFailure { error ->
                    onError?.invoke(error.localizedMessage ?: "Failed to link bank account")
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

    private fun simulateKcbTransaction(
        amount: String,
        direction: String,
        onSuccess: (() -> Unit)?,
        onError: ((String) -> Unit)?
    ) {
        val isCredit = direction.equals("Credit", ignoreCase = true)
        if (isCredit) {
            _isSimulatingInflow.value = true
        } else {
            _isSimulatingOutflow.value = true
        }

        viewModelScope.launch(dispatchers.io) {
            try {
                val narration = if (isCredit) "Simulated KCB Inflow" else "Simulated KCB Outflow"
                val req = SimulateTransactionRequest(
                    amount = amount,
                    direction = direction,
                    narration = narration
                )
                val response = RetrofitClient.bankIntegrationApi.simulateKcbTransaction(req)
                if (response.isSuccessful) {
                    transactionRepository.syncTransactions(userId = userId)
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
            } finally {
                if (isCredit) {
                    _isSimulatingInflow.value = false
                } else {
                    _isSimulatingOutflow.value = false
                }
            }
        }
    }

    class Factory(
        private val accountRepository: AccountRepository,
        private val bankAccountRepository: BankAccountRepository,
        private val transactionRepository: TransactionRepository,
        private val userId: String,
        private val dispatchers: DispatcherProvider = DefaultDispatcherProvider()
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return HomeViewModel(
                accountRepository = accountRepository,
                bankAccountRepository = bankAccountRepository,
                transactionRepository = transactionRepository,
                userId = userId,
                dispatchers = dispatchers
            ) as T
        }
    }
}

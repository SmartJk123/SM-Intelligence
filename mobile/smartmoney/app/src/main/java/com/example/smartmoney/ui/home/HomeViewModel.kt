package com.example.smartmoney.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.smartmoney.core.coroutine.DefaultDispatcherProvider
import com.example.smartmoney.core.coroutine.DispatcherProvider
import com.example.smartmoney.domain.model.Account
import com.example.smartmoney.domain.model.BankAccount
import com.example.smartmoney.domain.model.Budget
import com.example.smartmoney.domain.model.Transaction
import com.example.smartmoney.domain.repository.AccountRepository
import com.example.smartmoney.domain.repository.BankAccountRepository
import com.example.smartmoney.domain.repository.BudgetRepository
import com.example.smartmoney.domain.repository.TransactionRepository
import com.example.smartmoney.domain.util.TransactionTrendCalculation
import com.example.smartmoney.domain.util.TransactionTrendCalculator
import com.example.smartmoney.ui.home.analytics.calculator.OverviewAnalyticsCalculator
import com.example.smartmoney.ui.home.analytics.model.OverviewAnalyticsData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.math.BigDecimal
import java.time.YearMonth

/**
 * Screen-level ViewModel for the Overview / Home screen.
 *
 * Responsibilities:
 * - Aggregates domain models from [AccountRepository], [BankAccountRepository], [TransactionRepository], and [BudgetRepository].
 * - Executes all heavy trend, analytics, and currency calculations off the Main thread on [Dispatchers.Default].
 * - Owns background network synchronization on [Dispatchers.IO], decoupling network lifecycle from Compose UI.
 */
class HomeViewModel(
    private val accountRepository: AccountRepository,
    private val bankAccountRepository: BankAccountRepository,
    private val transactionRepository: TransactionRepository,
    private val budgetRepository: BudgetRepository? = null,
    private val userId: String,
    private val dispatchers: DispatcherProvider = DefaultDispatcherProvider()
) : ViewModel() {

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _hasDismissedWelcomeSheet = MutableStateFlow(false)

    // Interactive month selection state (defaults to current month)
    private val _selectedMonth = MutableStateFlow(YearMonth.now())
    val selectedMonth: StateFlow<YearMonth> = _selectedMonth.asStateFlow()

    // Trackers for live money movement detection
    private var lastKnownBalance: BigDecimal? = null
    private var lastKnownTxIds = mutableSetOf<String>()
    private var isInitialDataLoaded = false

    private val _glowEvent = MutableStateFlow<TopCardGlowEvent?>(null)
    val glowEvent: StateFlow<TopCardGlowEvent?> = _glowEvent.asStateFlow()

    private data class RawFinancialSnapshot(
        val accounts: List<Account>,
        val bankAccounts: List<BankAccount>,
        val transactions: List<Transaction>,
        val budgets: List<Budget>
    )

    private val budgetsFlow = budgetRepository?.getBudgets() ?: flowOf(emptyList())

    // Stage 1: Combine data streams into raw snapshot
    private val rawDataFlow = combine(
        accountRepository.getAccountsFlow(userId),
        bankAccountRepository.getBankAccounts(),
        transactionRepository.getTransactionsFlow(userId = userId),
        budgetsFlow
    ) { accounts, bankAccounts, transactions, budgets ->
        RawFinancialSnapshot(accounts, bankAccounts, transactions, budgets)
    }

    // Stage 2: Evaluate balance, trend, and Overview analytics for the selected month
    val uiState: StateFlow<HomeUiState> = combine(
        rawDataFlow,
        _selectedMonth,
        _hasDismissedWelcomeSheet,
        _isSyncing,
        _glowEvent
    ) { snapshot, currentMonth, dismissedWelcome, syncing, glow ->
        val accounts = snapshot.accounts
        val bankAccounts = snapshot.bankAccounts
        val transactions = snapshot.transactions
        val budgets = snapshot.budgets

        val trendCalc = TransactionTrendCalculator.calculateTrendSummary(transactions)
        val calculatedBalance = accounts.fold(BigDecimal.ZERO) { acc, a -> acc.add(a.availableBalance) }
        val finalBalance = if (calculatedBalance > BigDecimal.ZERO || accounts.isNotEmpty()) {
            calculatedBalance
        } else {
            bankAccounts.fold(BigDecimal.ZERO) { acc, b -> acc.add(b.balance) }
        }
        val isOnboarding = accounts.isEmpty() && bankAccounts.isEmpty()
        val showSheet = isOnboarding && !dismissedWelcome

        // Detect live money movement after initial data has stabilized
        if (!isInitialDataLoaded) {
            if (accounts.isNotEmpty() || bankAccounts.isNotEmpty() || transactions.isNotEmpty()) {
                lastKnownBalance = finalBalance
                lastKnownTxIds.clear()
                lastKnownTxIds.addAll(transactions.map { it.id })
                isInitialDataLoaded = true
            }
        } else {
            val previousBalance = lastKnownBalance
            val currentTxIds = transactions.map { it.id }.toSet()
            val newTxList = transactions.filter { it.id !in lastKnownTxIds }

            if (previousBalance != null && finalBalance != previousBalance) {
                if (finalBalance > previousBalance) {
                    _glowEvent.value = TopCardGlowEvent(type = TopCardGlowType.INFLOW_GREEN)
                } else if (finalBalance < previousBalance) {
                    _glowEvent.value = TopCardGlowEvent(type = TopCardGlowType.OUTFLOW_RED)
                }
                lastKnownBalance = finalBalance
            } else if (newTxList.isNotEmpty()) {
                val hasCredit = newTxList.any { it.type.equals("CREDIT", true) || it.amount > BigDecimal.ZERO }
                val hasDebit = newTxList.any { it.type.equals("DEBIT", true) || it.amount < BigDecimal.ZERO }
                if (hasCredit) {
                    _glowEvent.value = TopCardGlowEvent(type = TopCardGlowType.INFLOW_GREEN)
                } else if (hasDebit) {
                    _glowEvent.value = TopCardGlowEvent(type = TopCardGlowType.OUTFLOW_RED)
                }
            }
            lastKnownTxIds.addAll(currentTxIds)
        }

        // Calculate comprehensive 4-dimension financial analytics for the selected month
        val analyticsData = OverviewAnalyticsCalculator.calculate(
            transactions = transactions,
            budgets = budgets,
            selectedMonth = currentMonth
        )

        HomeUiState(
            isLoading = false,
            isSyncing = syncing,
            totalBalance = finalBalance,
            totalCashIn = trendCalc.totalInflow,
            totalCashOut = trendCalc.totalOutflow,
            trend = trendCalc.trendPoints,
            bankAccounts = bankAccounts,
            hasTransactions = trendCalc.hasTransactions,
            isOnboardingActive = isOnboarding,
            shouldShowWelcomeSheet = showSheet,
            unreadNotificationCount = 0,
            glowEvent = glow,
            selectedMonth = currentMonth,
            analytics = analyticsData
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

    /**
     * Programmatically triggers the top card glow animation (useful for testing or simulated events).
     */
    fun triggerGlow(type: TopCardGlowType) {
        _glowEvent.value = TopCardGlowEvent(type = type)
    }

    /**
     * Clears the active glow event once consumed by the UI animation lifecycle.
     */
    fun clearGlow() {
        _glowEvent.value = null
    }

    /** Shows the month before the one on screen. */
    fun previousMonth() {
        _selectedMonth.value = _selectedMonth.value.minusMonths(1)
    }

    /** Shows the month after the one on screen, never beyond the current month. */
    fun nextMonth() {
        val next = _selectedMonth.value.plusMonths(1)
        if (!next.isAfter(YearMonth.now())) {
            _selectedMonth.value = next
        }
    }

    class Factory(
        private val accountRepository: AccountRepository,
        private val bankAccountRepository: BankAccountRepository,
        private val transactionRepository: TransactionRepository,
        private val budgetRepository: BudgetRepository? = null,
        private val userId: String,
        private val dispatchers: DispatcherProvider = DefaultDispatcherProvider()
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return HomeViewModel(
                accountRepository = accountRepository,
                bankAccountRepository = bankAccountRepository,
                transactionRepository = transactionRepository,
                budgetRepository = budgetRepository,
                userId = userId,
                dispatchers = dispatchers
            ) as T
        }
    }
}

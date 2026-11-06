cat << 'INNER_EOF' > /home/frank/AndroidStudioProjects/smartmoney/app/src/main/java/com/example/smartmoney/ui/analytics/AnalyticsViewModel.kt
package com.example.smartmoney.ui.analytics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.smartmoney.domain.repository.BankAccountRepository
import com.example.smartmoney.domain.repository.BudgetRepository
import com.example.smartmoney.domain.repository.InvestmentRepository
import com.example.smartmoney.domain.repository.TransactionRepository
import com.example.smartmoney.ui.analytics.calculator.AnalyticsCalculationEngine
import com.example.smartmoney.ui.analytics.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class AnalyticsViewModel(
    private val bankAccountRepository: BankAccountRepository,
    private val investmentRepository: InvestmentRepository,
    private val transactionRepository: TransactionRepository,
    private val budgetRepository: BudgetRepository
) : ViewModel() {

    private val _scopeFilter = MutableStateFlow(WealthScopeFilter.PURE_LIQUID)
    private val _timeHorizon = MutableStateFlow(TimeHorizon.ONE_YEAR)
    private val _budgetCutPreset = MutableStateFlow(BudgetCutPreset.CURRENT_PACE)
    private val _rolloverMaturities = MutableStateFlow(false)

    private val dataFlow1 = combine(
        bankAccountRepository.getBankAccounts(),
        investmentRepository.getInvestments(),
        transactionRepository.getTransactionsFlow()
    ) { bankAccounts, investments, transactions ->
        Triple(bankAccounts, investments, transactions)
    }
    
    private val dataFlow2 = combine(
        budgetRepository.getBudgets(),
        _scopeFilter,
        _timeHorizon
    ) { budgets, scopeFilter, timeHorizon ->
        Triple(budgets, scopeFilter, timeHorizon)
    }
    
    private val dataFlow3 = combine(
        _budgetCutPreset,
        _rolloverMaturities
    ) { budgetCutPreset, rolloverMaturities ->
        Pair(budgetCutPreset, rolloverMaturities)
    }

    val uiState: StateFlow<AnalyticsUiState> = combine(
        dataFlow1,
        dataFlow2,
        dataFlow3
    ) { (bankAccounts, investments, transactions), (budgets, scopeFilter, horizon), (budgetCut, rollover) ->
        
        val wealthComp = AnalyticsCalculationEngine.calculateWealthComposition(bankAccounts, investments)
        val maturities = AnalyticsCalculationEngine.calculateMaturities(investments)
        val diagnostics = AnalyticsCalculationEngine.computeDiagnostics(wealthComp, transactions, budgetCut)
        
        val trajectory = AnalyticsCalculationEngine.generateTrajectory(
            wealthComp = wealthComp,
            maturities = maturities,
            transactions = transactions,
            horizon = horizon,
            budgetCut = budgetCut,
            rollover = rollover,
            scopeFilter = scopeFilter
        )
        
        AnalyticsUiState(
            scopeFilter = scopeFilter,
            timeHorizon = horizon,
            budgetCutPreset = budgetCut,
            rolloverMaturities = rollover,
            composition = wealthComp,
            trajectory = trajectory,
            milestones = maturities,
            diagnostics = diagnostics,
            isLoading = false
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = AnalyticsUiState(isLoading = true)
    )

    fun setWealthScope(scope: WealthScopeFilter) {
        _scopeFilter.value = scope
    }

    fun setTimeHorizon(horizon: TimeHorizon) {
        _timeHorizon.value = horizon
    }

    fun setBudgetCutPreset(preset: BudgetCutPreset) {
        _budgetCutPreset.value = preset
    }

    fun toggleRolloverMaturities(enabled: Boolean) {
        _rolloverMaturities.value = enabled
    }
}

class AnalyticsViewModelFactory(
    private val bankAccountRepository: BankAccountRepository,
    private val investmentRepository: InvestmentRepository,
    private val transactionRepository: TransactionRepository,
    private val budgetRepository: BudgetRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(AnalyticsViewModel::class.java)) {
            return AnalyticsViewModel(
                bankAccountRepository,
                investmentRepository,
                transactionRepository,
                budgetRepository
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
INNER_EOF
cp /home/frank/AndroidStudioProjects/smartmoney/app/src/main/java/com/example/smartmoney/ui/analytics/AnalyticsViewModel.kt /home/frank/SmartMoney_intelligence/SM-Intelligence/mobile/smartmoney/app/src/main/java/com/example/smartmoney/ui/analytics/AnalyticsViewModel.kt

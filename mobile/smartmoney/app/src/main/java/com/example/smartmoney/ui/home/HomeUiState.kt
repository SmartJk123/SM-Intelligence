package com.example.smartmoney.ui.home

import androidx.compose.runtime.Immutable
import com.example.smartmoney.domain.model.BankAccount
import com.example.smartmoney.domain.model.TrendPoint
import com.example.smartmoney.domain.util.TransactionTrendCalculator
import java.math.BigDecimal

/**
 * Immutable UI State representing the complete presentation dataset for the Overview / Home screen.
 * All computations, fallbacks, and aggregations are evaluated upstream off the Main/UI thread.
 */
@Immutable
data class HomeUiState(
    val isLoading: Boolean = false,
    val isSyncing: Boolean = false,
    val isSimulatingInflow: Boolean = false,
    val isSimulatingOutflow: Boolean = false,
    val totalBalance: BigDecimal = BigDecimal.ZERO,
    val totalCashIn: BigDecimal = BigDecimal.ZERO,
    val totalCashOut: BigDecimal = BigDecimal.ZERO,
    val trend: List<TrendPoint> = emptyList(),
    val bankAccounts: List<BankAccount> = emptyList(),
    val hasTransactions: Boolean = false,
    val isOnboardingActive: Boolean = false,
    val shouldShowWelcomeSheet: Boolean = false,
    val unreadNotificationCount: Int = 0,
    val errorMessage: String? = null
) {
    companion object {
        val DEFAULT = HomeUiState()
    }
}

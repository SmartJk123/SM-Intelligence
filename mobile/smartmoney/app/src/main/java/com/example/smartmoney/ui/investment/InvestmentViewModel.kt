package com.example.smartmoney.ui.investment

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.smartmoney.core.coroutine.DefaultDispatcherProvider
import com.example.smartmoney.core.coroutine.DispatcherProvider
import com.example.smartmoney.domain.model.Investment
import com.example.smartmoney.domain.model.InvestmentType
import com.example.smartmoney.domain.repository.InvestmentRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

data class InvestmentUiState(
    val isLoading: Boolean = false,
    val investments: List<Investment> = emptyList(),
    val totalPrincipalMinor: Long = 0,
    val portfolioValuationMinor: Long? = null, // Null if any holding valuation is unknown/unrecorded
    val totalHoldingsCount: Int = 0,
    val maturingSoonCount: Int = 0,
    val errorMessage: String? = null,
    val successMessage: String? = null
)

private data class InvestmentMessageState(
    val error: String? = null,
    val success: String? = null
)

class InvestmentViewModel(
    private val investmentRepository: InvestmentRepository,
    private val dispatchers: DispatcherProvider = DefaultDispatcherProvider()
) : ViewModel() {

    private val _messageState = MutableStateFlow(InvestmentMessageState())

    val uiState: StateFlow<InvestmentUiState> = combine(
        investmentRepository.getInvestments(),
        _messageState
    ) { list, messages ->
        withContext(dispatchers.default) {
            val portfolioValuation: Long? = if (list.any { it.currentValueMinor == null }) {
                null
            } else {
                list.sumOf { it.currentValueMinor ?: 0L }
            }

            InvestmentUiState(
                isLoading = false,
                investments = list,
                totalPrincipalMinor = list.sumOf { it.principalMinor },
                portfolioValuationMinor = portfolioValuation,
                totalHoldingsCount = list.size,
                maturingSoonCount = list.count { it.isMaturingSoon },
                errorMessage = messages.error,
                successMessage = messages.success
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = InvestmentUiState(isLoading = true)
    )

    fun saveInvestment(
        id: String = "",
        name: String,
        type: InvestmentType,
        principalMinor: Long,
        currentValueMinor: Long?,
        valuationDate: LocalDate,
        maturityDate: LocalDate?
    ) {
        viewModelScope.launch(dispatchers.main) {
            val investment = Investment(
                id = id,
                name = name,
                type = type,
                principalMinor = principalMinor,
                currentValueMinor = currentValueMinor,
                valuationDate = valuationDate,
                maturityDate = maturityDate
            )
            investmentRepository.saveInvestment(investment)
        }
    }

    fun deleteInvestment(id: String) {
        viewModelScope.launch(dispatchers.main) {
            investmentRepository.deleteInvestment(id)
        }
    }

    fun clearMessages() {
        _messageState.value = InvestmentMessageState()
    }

    class Factory(
        private val investmentRepository: InvestmentRepository,
        private val dispatchers: DispatcherProvider = DefaultDispatcherProvider()
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return InvestmentViewModel(investmentRepository, dispatchers) as T
        }
    }
}

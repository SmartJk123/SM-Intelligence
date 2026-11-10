package com.example.smartmoney.ui.investment

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.smartmoney.core.coroutine.DefaultDispatcherProvider
import com.example.smartmoney.core.coroutine.DispatcherProvider
import com.example.smartmoney.domain.model.Account
import com.example.smartmoney.domain.model.BankAccount
import com.example.smartmoney.domain.model.Investment
import com.example.smartmoney.domain.model.InvestmentType
import com.example.smartmoney.domain.repository.AccountRepository
import com.example.smartmoney.domain.repository.BankAccountRepository
import com.example.smartmoney.domain.repository.InvestmentRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.math.BigDecimal
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
    private val accountRepository: AccountRepository? = null,
    private val bankAccountRepository: BankAccountRepository? = null,
    private val userId: String? = null,
    private val dispatchers: DispatcherProvider = DefaultDispatcherProvider()
) : ViewModel() {

    constructor(
        investmentRepository: InvestmentRepository,
        userId: String? = null,
        dispatchers: DispatcherProvider = DefaultDispatcherProvider()
    ) : this(
        investmentRepository = investmentRepository,
        accountRepository = null,
        bankAccountRepository = null,
        userId = userId,
        dispatchers = dispatchers
    )

    constructor(
        investmentRepository: InvestmentRepository,
        accountRepository: AccountRepository? = null,
        userId: String? = null,
        dispatchers: DispatcherProvider = DefaultDispatcherProvider()
    ) : this(
        investmentRepository = investmentRepository,
        accountRepository = accountRepository,
        bankAccountRepository = null,
        userId = userId,
        dispatchers = dispatchers
    )

    private val _messageState = MutableStateFlow(InvestmentMessageState())

    init {
        viewModelScope.launch {
            investmentRepository.loadInvestmentsForUser(userId)
        }
    }

    private val accountsFlow: Flow<List<Account>> = if (accountRepository != null && !userId.isNullOrBlank()) {
        accountRepository.getAccountsFlow(userId)
    } else {
        flowOf(emptyList())
    }

    private val bankAccountsFlow: Flow<List<BankAccount>> = if (bankAccountRepository != null) {
        bankAccountRepository.getBankAccounts()
    } else {
        flowOf(emptyList())
    }

    val uiState: StateFlow<InvestmentUiState> = combine(
        investmentRepository.getInvestments(userId),
        accountsFlow,
        bankAccountsFlow,
        _messageState
    ) { repoInvestments, accounts, bankAccounts, messages ->
        withContext(dispatchers.default) {
            // 1. Extract M-Shwari / investment accounts from the primary accounts table
            val mshwariFromAccounts = accounts.filter { isMshwariAccount(it) }
                .map { it.toMshwariInvestment() }

            // 2. Fallback to BankAccounts if primary accounts table yielded no M-Shwari
            val mshwariInvestments = if (mshwariFromAccounts.isNotEmpty()) {
                mshwariFromAccounts
            } else {
                bankAccounts.filter { isMshwariBankAccount(it) }
                    .map { it.toMshwariInvestment(userId ?: "") }
            }

            // 3. Deduplicate against any manual investment records with matching ID or M-Shwari naming
            val finalInvestments = if (mshwariInvestments.isNotEmpty()) {
                val mshwariIds = mshwariInvestments.map { it.id }.toSet()
                val nonDuplicateManual = repoInvestments.filterNot { manual ->
                    manual.id in mshwariIds ||
                    manual.name.contains("M-Shwari", ignoreCase = true) ||
                    manual.name.contains("MSHWARI", ignoreCase = true)
                }
                mshwariInvestments + nonDuplicateManual
            } else {
                repoInvestments
            }

            val portfolioValuation: Long? = if (finalInvestments.any { it.currentValueMinor == null }) {
                null
            } else {
                finalInvestments.sumOf { it.currentValueMinor ?: 0L }
            }

            InvestmentUiState(
                isLoading = false,
                investments = finalInvestments,
                totalPrincipalMinor = finalInvestments.sumOf { it.principalMinor },
                portfolioValuationMinor = portfolioValuation,
                totalHoldingsCount = finalInvestments.size,
                maturingSoonCount = finalInvestments.count { it.isMaturingSoon },
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
                userId = userId ?: "",
                name = name,
                type = type,
                principalMinor = principalMinor,
                currentValueMinor = currentValueMinor,
                valuationDate = valuationDate,
                maturityDate = maturityDate
            )
            investmentRepository.saveInvestment(investment, userId)
        }
    }

    fun deleteInvestment(id: String) {
        viewModelScope.launch(dispatchers.main) {
            investmentRepository.deleteInvestment(id, userId)
        }
    }

    fun clearMessages() {
        _messageState.value = InvestmentMessageState()
    }

    class Factory(
        private val investmentRepository: InvestmentRepository,
        private val accountRepository: AccountRepository? = null,
        private val bankAccountRepository: BankAccountRepository? = null,
        private val userId: String? = null,
        private val dispatchers: DispatcherProvider = DefaultDispatcherProvider()
    ) : ViewModelProvider.Factory {

        constructor(
            investmentRepository: InvestmentRepository,
            userId: String? = null,
            dispatchers: DispatcherProvider = DefaultDispatcherProvider()
        ) : this(
            investmentRepository = investmentRepository,
            accountRepository = null,
            bankAccountRepository = null,
            userId = userId,
            dispatchers = dispatchers
        )

        constructor(
            investmentRepository: InvestmentRepository,
            accountRepository: AccountRepository? = null,
            userId: String? = null,
            dispatchers: DispatcherProvider = DefaultDispatcherProvider()
        ) : this(
            investmentRepository = investmentRepository,
            accountRepository = accountRepository,
            bankAccountRepository = null,
            userId = userId,
            dispatchers = dispatchers
        )

        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return InvestmentViewModel(
                investmentRepository = investmentRepository,
                accountRepository = accountRepository,
                bankAccountRepository = bankAccountRepository,
                userId = userId,
                dispatchers = dispatchers
            ) as T
        }
    }

    companion object {
        fun isMshwariAccount(account: Account): Boolean {
            val inst = account.institution.uppercase()
            val name = account.accountName.uppercase()
            val accId = account.accountId.uppercase()
            return inst.contains("MSHWARI") || inst.contains("M-SHWARI") ||
                   name.contains("MSHWARI") || name.contains("M-SHWARI") ||
                   accId.contains("MSHWARI") ||
                   account.accountType.equals("INVESTMENT", ignoreCase = true)
        }

        fun isMshwariBankAccount(bankAccount: BankAccount): Boolean {
            val name = bankAccount.bankName.uppercase()
            val cardType = bankAccount.cardType.uppercase()
            val accNum = bankAccount.accountNumber.uppercase()
            return name.contains("MSHWARI") || name.contains("M-SHWARI") ||
                   accNum.contains("MSHWARI") ||
                   cardType.contains("INVESTMENT")
        }

        private fun Account.toMshwariInvestment(): Investment {
            val balanceMinor = this.availableBalance.multiply(BigDecimal(100)).toLong().coerceAtLeast(0L)
            val isLock = this.accountName.contains("Lock", ignoreCase = true) || this.institution.contains("Lock", ignoreCase = true)
            val parsedValDate = this.lastUpdated?.let {
                try {
                    LocalDate.parse(it.take(10))
                } catch (_: Exception) {
                    LocalDate.now()
                }
            } ?: LocalDate.now()

            val displayName = when {
                this.accountName.isNotBlank() && !this.accountName.equals("INVESTMENT", ignoreCase = true) -> this.accountName
                this.institution.isNotBlank() -> "${this.institution} Savings"
                else -> "M-Shwari Savings"
            }

            return Investment(
                id = this.id.ifBlank { "account_mshwari_${this.userId}" },
                userId = this.userId,
                name = displayName,
                type = if (isLock) InvestmentType.FIXED_DEPOSIT else InvestmentType.MONEY_MARKET,
                principalMinor = balanceMinor,
                currentValueMinor = balanceMinor,
                valuationDate = parsedValDate,
                maturityDate = null,
                isAutoSynced = true
            )
        }

        private fun BankAccount.toMshwariInvestment(userId: String): Investment {
            val balanceMinor = this.balance.multiply(BigDecimal(100)).toLong().coerceAtLeast(0L)
            val isLock = this.bankName.contains("Lock", ignoreCase = true)
            val displayName = when {
                this.bankName.isNotBlank() && !this.bankName.equals("Investment", ignoreCase = true) -> this.bankName
                else -> "M-Shwari Savings"
            }
            return Investment(
                id = this.id.ifBlank { "bank_account_mshwari_$userId" },
                userId = userId,
                name = displayName,
                type = if (isLock) InvestmentType.FIXED_DEPOSIT else InvestmentType.MONEY_MARKET,
                principalMinor = balanceMinor,
                currentValueMinor = balanceMinor,
                valuationDate = LocalDate.now(),
                maturityDate = null,
                isAutoSynced = true
            )
        }
    }
}

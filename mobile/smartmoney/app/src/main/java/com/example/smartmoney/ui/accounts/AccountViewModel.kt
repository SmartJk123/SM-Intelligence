package com.example.smartmoney.ui.accounts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.smartmoney.core.coroutine.DefaultDispatcherProvider
import com.example.smartmoney.core.coroutine.DispatcherProvider
import com.example.smartmoney.domain.model.Account
import com.example.smartmoney.domain.model.BankAccount
import com.example.smartmoney.domain.repository.AccountRepository
import com.example.smartmoney.domain.repository.BankAccountRepository
import com.example.smartmoney.domain.repository.TransactionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.math.BigDecimal
import com.example.smartmoney.data.remote.RetrofitClient

/**
 * Immutable UI state for the Accounts screen.
 * Consolidates all state into a single stream to eliminate fragmented Compose recompositions.
 */
data class BankIntegrationInfo(
    val environment: String = "SANDBOX",
    val isLive: Boolean = false,
    val webhookActive: Boolean = false
)

data class AccountUiState(
    val bankAccounts: List<BankAccount> = emptyList(),
    val isLoading: Boolean = false,
    val isAddingBankAccount: Boolean = false,
    val errorMessage: String? = null,
    val bankEnvironments: Map<String, BankIntegrationInfo> = emptyMap()
)

class AccountViewModel(
    private val repository: AccountRepository,
    private val bankAccountRepository: BankAccountRepository,
    private val userId: String,
    private val dispatchers: DispatcherProvider = DefaultDispatcherProvider(),
    private val transactionRepository: TransactionRepository? = null
) : ViewModel() {

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isAddingBankAccount = MutableStateFlow(false)
    val isAddingBankAccount: StateFlow<Boolean> = _isAddingBankAccount.asStateFlow()

    private val _bankAccountError = MutableStateFlow<String?>(null)
    val bankAccountError: StateFlow<String?> = _bankAccountError.asStateFlow()

    private val _bankEnvironments = MutableStateFlow<Map<String, BankIntegrationInfo>>(emptyMap())
    val bankEnvironments: StateFlow<Map<String, BankIntegrationInfo>> = _bankEnvironments.asStateFlow()

    // 30-second freshness guard to prevent redundant HTTP calls when warm-up has already synced Room
    private var lastSyncTimestamp: Long = 0L
    private val SYNC_COOLDOWN_MS = 30_000L

    val bankAccounts: StateFlow<List<BankAccount>> = bankAccountRepository.getBankAccounts()
        .distinctUntilChanged()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    val uiState: StateFlow<AccountUiState> = combine(
        bankAccounts,
        _isLoading,
        _isAddingBankAccount,
        _bankAccountError,
        _bankEnvironments
    ) { banks, loading, adding, error, envs ->
        AccountUiState(
            bankAccounts = banks,
            isLoading = loading,
            isAddingBankAccount = adding,
            errorMessage = error,
            bankEnvironments = envs
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AccountUiState()
    )

    // Backward-compatible flows for external consumers if any
    val accounts: StateFlow<List<Account>> = repository.getAccountsFlow(userId)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    val totalBalance: StateFlow<BigDecimal> = accounts.map { list ->
        list.filterNot { a ->
            a.institution.contains("M-Shwari", ignoreCase = true) ||
            a.accountName.contains("M-Shwari", ignoreCase = true) ||
            a.accountType.equals("INVESTMENT", ignoreCase = true)
        }.fold(BigDecimal.ZERO) { acc, account -> acc.add(account.availableBalance) }
    }.flowOn(dispatchers.default)
    .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = BigDecimal.ZERO
    )

    init {
        refreshAccounts(force = false)
        loadBankEnvironments()
    }

    /**
     * Probes the backend bank integration service to detect environment status (Sandbox vs Production)
     * for supported banks (KCB, NCBA, Stanbic, Equity).
     */
    fun loadBankEnvironments() {
        viewModelScope.launch(dispatchers.io) {
            val map = mutableMapOf<String, BankIntegrationInfo>()
            for (bank in listOf("kcb", "ncba", "stanbic", "equity")) {
                try {
                    val res = com.example.smartmoney.data.remote.RetrofitClient.bankIntegrationApi.getBankHealth(bank)
                    if (res.isSuccessful && res.body() != null) {
                        val body = res.body()!!
                        val env = body.environment ?: "SANDBOX"
                        val isLive = env.equals("PRODUCTION", ignoreCase = true)
                        map[bank] = BankIntegrationInfo(
                            environment = env,
                            isLive = isLive,
                            webhookActive = body.webhookStatus.equals("ACTIVE", ignoreCase = true) || isLive
                        )
                    } else {
                        map[bank] = BankIntegrationInfo(environment = "SANDBOX", isLive = false)
                    }
                } catch (_: Exception) {
                    map[bank] = BankIntegrationInfo(environment = "SANDBOX", isLive = false)
                }
            }
            _bankEnvironments.value = map
        }
    }

    /**
     * Refreshes accounts with a 30-second freshness guard to prevent duplicate HTTP traffic
     * post-authentication. Setting [force] = true bypasses throttling (e.g. pull-to-refresh).
     */
    fun refreshAccounts(force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (!force && (now - lastSyncTimestamp < SYNC_COOLDOWN_MS)) {
            return // Local Room cache is warm and authoritative
        }

        loadBankEnvironments()

        if (userId.isNotBlank()) {
            viewModelScope.launch(dispatchers.io) {
                _isLoading.value = true
                try {
                    val result = repository.syncAccounts(userId)
                    lastSyncTimestamp = System.currentTimeMillis()
                    result.onFailure { e ->
                        _bankAccountError.value = e.localizedMessage ?: "Failed to sync accounts"
                    }
                } catch (e: Exception) {
                    _bankAccountError.value = e.localizedMessage ?: "Failed to sync accounts"
                } finally {
                    _isLoading.value = false
                }
            }
        }
    }

    /**
     * Adds a new bank account via [BankAccountRepository] and handles success/error UI states.
     */
    fun addBankAccount(
        bankName: String,
        accountNumber: String,
        cardType: String,
        onSuccess: (() -> Unit)? = null,
        onError: ((String) -> Unit)? = null
    ) {
        viewModelScope.launch(dispatchers.io) {
            _isAddingBankAccount.value = true
            _bankAccountError.value = null

            val result = bankAccountRepository.addBankAccount(
                bankName = bankName,
                accountNumber = accountNumber,
                cardType = cardType
            )

            _isAddingBankAccount.value = false

            withContext(dispatchers.main) {
                result.onSuccess {
                    _bankAccountError.value = null
                    onSuccess?.invoke()
                }.onFailure { error ->
                    val message = error.localizedMessage ?: "Failed to link bank account"
                    _bankAccountError.value = message
                    onError?.invoke(message)
                }
            }
        }
    }

    fun clearBankAccountError() {
        _bankAccountError.value = null
    }

    fun simulateBankInflow(
        bankName: String,
        amount: String = "1000.00",
        accountId: String? = null,
        accountNumber: String? = null,
        onSuccess: (() -> Unit)? = null,
        onError: ((String) -> Unit)? = null
    ) {
        simulateBankTransaction(
            bankName = bankName,
            amount = amount,
            direction = "Credit",
            accountId = accountId,
            accountNumber = accountNumber,
            onSuccess = onSuccess,
            onError = onError
        )
    }

    fun simulateBankOutflow(
        bankName: String,
        amount: String = "500.00",
        accountId: String? = null,
        accountNumber: String? = null,
        onSuccess: (() -> Unit)? = null,
        onError: ((String) -> Unit)? = null
    ) {
        simulateBankTransaction(
            bankName = bankName,
            amount = amount,
            direction = "Debit",
            accountId = accountId,
            accountNumber = accountNumber,
            onSuccess = onSuccess,
            onError = onError
        )
    }

    fun simulateBankTransaction(
        bankName: String,
        amount: String = "1000.00",
        direction: String = "Credit",
        accountId: String? = null,
        accountNumber: String? = null,
        onSuccess: (() -> Unit)? = null,
        onError: ((String) -> Unit)? = null
    ) {
        viewModelScope.launch(dispatchers.io) {
            try {
                val isDebit = direction.equals("Debit", ignoreCase = true)
                val bankClean = bankName.trim().lowercase(java.util.Locale.ROOT)
                val bankUpper = bankName.trim().uppercase(java.util.Locale.ROOT)
                val narration = if (isDebit) "Simulated $bankUpper Outflow" else "Simulated $bankUpper Inflow"
                val req = com.example.smartmoney.data.remote.api.SimulateTransactionRequest(
                    bankId = bankClean,
                    amount = amount,
                    direction = direction,
                    narration = narration,
                    accountNumber = accountNumber
                )
                val response = com.example.smartmoney.data.remote.RetrofitClient.bankIntegrationApi.simulateTransaction(req)
                if (response.isSuccessful) {
                    val parsedAmt = try { BigDecimal(amount) } catch (_: Exception) { BigDecimal.ZERO }
                    val delta = if (isDebit) parsedAmt.negate() else parsedAmt
                    bankAccountRepository.adjustBankBalance(
                        bankName = bankUpper,
                        delta = delta,
                        accountId = accountId,
                        userId = userId
                    )
                    repository.syncAccounts(userId)
                    try {
                        transactionRepository?.syncTransactions(userId)
                    } catch (_: Exception) {
                        // Tolerate if sync fails
                    }
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

    fun simulateKcbInflow(
        amount: String = "1000.00",
        onSuccess: (() -> Unit)? = null,
        onError: ((String) -> Unit)? = null
    ) = simulateBankInflow(bankName = "KCB", amount = amount, onSuccess = onSuccess, onError = onError)

    fun simulateKcbOutflow(
        amount: String = "500.00",
        onSuccess: (() -> Unit)? = null,
        onError: ((String) -> Unit)? = null
    ) = simulateBankOutflow(bankName = "KCB", amount = amount, onSuccess = onSuccess, onError = onError)

    fun simulateKcbTransaction(
        amount: String = "1000.00",
        direction: String = "Credit",
        onSuccess: (() -> Unit)? = null,
        onError: ((String) -> Unit)? = null
    ) = simulateBankTransaction(bankName = "KCB", amount = amount, direction = direction, onSuccess = onSuccess, onError = onError)

    /**
     * Removes an account permanently from the remote backend and local database.
     */
    fun removeAccount(
        accountId: String,
        onSuccess: (() -> Unit)? = null,
        onError: ((String) -> Unit)? = null
    ) {
        viewModelScope.launch(dispatchers.io) {
            _isLoading.value = true
            val isBankAccount = bankAccounts.value.any { it.id == accountId || it.accountNumber == accountId }
            val result = if (isBankAccount) {
                bankAccountRepository.removeBankAccount(accountId)
            } else {
                repository.deleteAccount(accountId)
            }
            _isLoading.value = false

            withContext(dispatchers.main) {
                result.onSuccess {
                    onSuccess?.invoke()
                }.onFailure { error ->
                    onError?.invoke(error.localizedMessage ?: "Failed to remove account")
                }
            }
        }
    }

    class Factory(
        private val repository: AccountRepository,
        private val bankAccountRepository: BankAccountRepository,
        private val userId: String,
        private val dispatchers: DispatcherProvider = DefaultDispatcherProvider(),
        private val transactionRepository: TransactionRepository? = null
    ) : ViewModelProvider.Factory {

        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return AccountViewModel(
                repository = repository,
                bankAccountRepository = bankAccountRepository,
                userId = userId,
                dispatchers = dispatchers,
                transactionRepository = transactionRepository
            ) as T
        }
    }
}

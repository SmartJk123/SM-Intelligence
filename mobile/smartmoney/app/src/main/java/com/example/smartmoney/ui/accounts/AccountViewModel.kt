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

/**
 * Immutable UI state for the Accounts screen.
 * Consolidates all state into a single stream to eliminate fragmented Compose recompositions.
 */
data class AccountUiState(
    val bankAccounts: List<BankAccount> = emptyList(),
    val isLoading: Boolean = false,
    val isAddingBankAccount: Boolean = false,
    val errorMessage: String? = null
)

class AccountViewModel(
    private val repository: AccountRepository,
    private val bankAccountRepository: BankAccountRepository,
    private val userId: String,
    private val dispatchers: DispatcherProvider = DefaultDispatcherProvider()
) : ViewModel() {

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isAddingBankAccount = MutableStateFlow(false)
    val isAddingBankAccount: StateFlow<Boolean> = _isAddingBankAccount.asStateFlow()

    private val _bankAccountError = MutableStateFlow<String?>(null)
    val bankAccountError: StateFlow<String?> = _bankAccountError.asStateFlow()

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
        _bankAccountError
    ) { banks, loading, adding, error ->
        AccountUiState(
            bankAccounts = banks,
            isLoading = loading,
            isAddingBankAccount = adding,
            errorMessage = error
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
        list.fold(BigDecimal.ZERO) { acc, account -> acc.add(account.availableBalance) }
    }.flowOn(dispatchers.default)
    .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = BigDecimal.ZERO
    )

    init {
        refreshAccounts(force = false)
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
        viewModelScope.launch(dispatchers.io) {
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
                    val parsedAmt = try { BigDecimal(amount) } catch (_: Exception) { BigDecimal.ZERO }
                    val delta = if (isDebit) parsedAmt.negate() else parsedAmt
                    bankAccountRepository.adjustKcbBalance(delta)
                    repository.syncAccounts(userId)
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
        private val dispatchers: DispatcherProvider = DefaultDispatcherProvider()
    ) : ViewModelProvider.Factory {

        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return AccountViewModel(repository, bankAccountRepository, userId, dispatchers) as T
        }
    }
}

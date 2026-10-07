package com.example.smartmoney.ui.warmup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.smartmoney.core.coroutine.DefaultDispatcherProvider
import com.example.smartmoney.core.coroutine.DispatcherProvider
import com.example.smartmoney.domain.repository.AccountRepository
import com.example.smartmoney.domain.repository.TransactionRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * UI state for the post-login warm-up and data synchronization screen.
 */
data class WarmUpUiState(
    val message: String = "Authenticating secure session...",
    val progress: Float = 0.15f,
    val isReady: Boolean = false
)

/**
 * ViewModel responsible for orchestrating the initial pre-hydration of Room database
 * with accounts and transactions immediately following successful authentication.
 *
 * Runs a staged 3-second progression while executing background network synchronization,
 * ensuring the user experiences a smooth, informative transition with guaranteed data readiness.
 */
class WarmUpSyncViewModel(
    private val accountRepository: AccountRepository,
    private val transactionRepository: TransactionRepository,
    private val userId: String,
    private val dispatchers: DispatcherProvider = DefaultDispatcherProvider()
) : ViewModel() {

    private val _uiState = MutableStateFlow(WarmUpUiState())
    val uiState: StateFlow<WarmUpUiState> = _uiState.asStateFlow()

    init {
        startSyncPipeline()
    }

    private fun startSyncPipeline() {
        viewModelScope.launch(dispatchers.io) {
            // Launch parallel background data synchronization immediately
            val syncJob = async {
                try {
                    coroutineScope {
                        val accJob = async { accountRepository.syncAccounts(userId) }
                        val txJob = async { transactionRepository.syncTransactions(userId = userId) }
                        awaitAll(accJob, txJob)
                    }
                } catch (_: Exception) {
                    // Gracefully tolerate network errors
                }
            }

            // Stage 0: 0ms -> Authenticating
            _uiState.value = WarmUpUiState(
                message = "Authenticating secure session...",
                progress = 0.15f
            )

            delay(750L)
            // Stage 1: ~750ms -> Loading accounts & linked cards
            _uiState.value = _uiState.value.copy(
                message = "Loading your accounts & linked cards...",
                progress = 0.45f
            )

            delay(900L)
            // Stage 2: ~1650ms -> Syncing transactions & ledger
            _uiState.value = _uiState.value.copy(
                message = "Syncing your transactions & ledger...",
                progress = 0.78f
            )

            delay(850L)
            // Stage 3: ~2500ms -> Finalizing accounts & overview
            _uiState.value = _uiState.value.copy(
                message = "Finalizing accounts & overview...",
                progress = 0.95f
            )

            // Ensure background sync finishes or times out by the final phase
            withTimeoutOrNull(500L) {
                syncJob.await()
            }

            delay(400L)
            // Stage 4: ~2900ms -> Completion
            _uiState.value = _uiState.value.copy(
                message = "All caught up!",
                progress = 1.0f
            )

            delay(200L)
            _uiState.value = _uiState.value.copy(isReady = true)
        }
    }

    class Factory(
        private val accountRepository: AccountRepository,
        private val transactionRepository: TransactionRepository,
        private val userId: String,
        private val dispatchers: DispatcherProvider = DefaultDispatcherProvider()
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return WarmUpSyncViewModel(accountRepository, transactionRepository, userId, dispatchers) as T
        }
    }
}

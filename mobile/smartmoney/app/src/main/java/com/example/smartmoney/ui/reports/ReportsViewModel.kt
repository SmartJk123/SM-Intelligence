package com.example.smartmoney.ui.reports

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.smartmoney.core.reports.ReportFileManager
import com.example.smartmoney.core.reports.model.GeneratedReportResult
import com.example.smartmoney.core.reports.model.ReportFormat
import com.example.smartmoney.core.reports.model.ReportSummaryData
import com.example.smartmoney.core.reports.model.ReportTimeframe
import com.example.smartmoney.core.reports.model.ReportType
import com.example.smartmoney.core.reports.model.StatementRowData
import com.example.smartmoney.data.local.UserPreferencesRepository
import com.example.smartmoney.data.local.dao.AccountDao
import com.example.smartmoney.data.local.dao.TransactionDao
import com.example.smartmoney.data.local.entity.AccountEntity
import com.example.smartmoney.data.local.entity.TransactionEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.math.BigDecimal
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.YearMonth
import java.util.Date
import java.util.Locale

data class ReportsUiState(
    val reportType: ReportType = ReportType.ACCOUNT_STATEMENT,
    val timeframe: ReportTimeframe = ReportTimeframe.THIS_MONTH,
    val selectedAccountId: String? = null,
    val format: ReportFormat = ReportFormat.PDF,
    val accounts: List<AccountEntity> = emptyList(),
    val previewSummary: ReportSummaryData? = null,
    val isGenerating: Boolean = false,
    val lastGeneratedResult: GeneratedReportResult? = null,
    val errorMessage: String? = null
)

class ReportsViewModel(
    private val transactionDao: TransactionDao,
    private val accountDao: AccountDao,
    private val userPreferencesRepository: UserPreferencesRepository
) : ViewModel() {

    class Factory(
        private val transactionDao: TransactionDao,
        private val accountDao: AccountDao,
        private val userPreferencesRepository: UserPreferencesRepository
    ) : androidx.lifecycle.ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ReportsViewModel(transactionDao, accountDao, userPreferencesRepository) as T
        }
    }

    private val _uiState = MutableStateFlow(ReportsUiState())
    val uiState: StateFlow<ReportsUiState> = _uiState.asStateFlow()

    private val _accountsFlow = MutableStateFlow<List<AccountEntity>>(emptyList())
    private val _transactionsFlow = MutableStateFlow<List<TransactionEntity>>(emptyList())

    init {
        observeData()
    }

    private fun observeData() {
        val currentUserId = userPreferencesRepository.getUserId() ?: ""

        viewModelScope.launch {
            val accountsFlow = if (currentUserId.isNotBlank()) {
                accountDao.getAccountsForUser(currentUserId)
            } else {
                accountDao.getAccountsForUser("")
            }
            accountsFlow.collect { list ->
                _accountsFlow.value = list
                _uiState.value = _uiState.value.copy(accounts = list)
                recomputePreview()
            }
        }

        viewModelScope.launch {
            val txFlow = if (currentUserId.isNotBlank()) {
                transactionDao.getTransactionsForUser(currentUserId)
            } else {
                transactionDao.getAllTransactions()
            }
            txFlow.collect { list ->
                _transactionsFlow.value = list
                recomputePreview()
            }
        }
    }

    fun selectReportType(type: ReportType) {
        _uiState.value = _uiState.value.copy(reportType = type)
        recomputePreview()
    }

    fun selectTimeframe(timeframe: ReportTimeframe) {
        _uiState.value = _uiState.value.copy(timeframe = timeframe)
        recomputePreview()
    }

    fun selectAccount(accountId: String?) {
        _uiState.value = _uiState.value.copy(selectedAccountId = accountId)
        recomputePreview()
    }

    fun selectFormat(format: ReportFormat) {
        _uiState.value = _uiState.value.copy(format = format)
    }

    fun clearLastGenerated() {
        _uiState.value = _uiState.value.copy(lastGeneratedResult = null, errorMessage = null)
    }

    fun generateReport(context: Context, onComplete: (GeneratedReportResult) -> Unit) {
        val summary = _uiState.value.previewSummary ?: return
        val format = _uiState.value.format

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isGenerating = true, errorMessage = null)
            try {
                val result = ReportFileManager.generateReportFile(context, summary, format)
                _uiState.value = _uiState.value.copy(
                    isGenerating = false,
                    lastGeneratedResult = result
                )
                onComplete(result)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isGenerating = false,
                    errorMessage = e.message ?: "Failed to generate report"
                )
            }
        }
    }

    private fun recomputePreview() {
        viewModelScope.launch(Dispatchers.Default) {
            val transactions = _transactionsFlow.value
            val accounts = _accountsFlow.value
            val timeframe = _uiState.value.timeframe
            val selectedAccountId = _uiState.value.selectedAccountId
            val reportType = _uiState.value.reportType

            val (startDate, endDate, periodLabel) = calculateDateRange(timeframe)

            // Filter transactions by account
            var filtered = if (!selectedAccountId.isNullOrBlank()) {
                transactions.filter { it.accountId == selectedAccountId }
            } else {
                transactions
            }

            // Filter transactions by date
            filtered = filtered.filter { tx ->
                val txDate = parseTxDate(tx.timestamp)
                if (txDate != null) {
                    !txDate.isBefore(startDate) && !txDate.isAfter(endDate)
                } else true
            }

            val accountName = if (!selectedAccountId.isNullOrBlank()) {
                accounts.find { it.id == selectedAccountId || it.accountId == selectedAccountId }?.accountName
                    ?: "Selected Account"
            } else {
                "All Accounts (Consolidated)"
            }

            var totalInflow = BigDecimal.ZERO
            var totalOutflow = BigDecimal.ZERO

            val rows = filtered.map { tx ->
                val isCredit = tx.transactionType.equals("CREDIT", ignoreCase = true)
                if (isCredit) {
                    totalInflow = totalInflow.add(tx.amount)
                } else {
                    totalOutflow = totalOutflow.add(tx.amount)
                }

                val acc = accounts.find { it.id == tx.accountId || it.accountId == tx.accountId }
                val accLabel = acc?.accountName ?: "Wallet"

                StatementRowData(
                    date = tx.timestamp.take(10),
                    reference = tx.providerTransactionId ?: tx.id.take(10),
                    counterparty = tx.description ?: "Transaction",
                    category = "General",
                    amount = tx.amount,
                    direction = tx.transactionType,
                    accountName = accLabel,
                    balanceAfter = null
                )
            }

            val netFlow = totalInflow.subtract(totalOutflow)

            // Calculate account balance
            val closingBalance = if (!selectedAccountId.isNullOrBlank()) {
                accounts.find { it.id == selectedAccountId || it.accountId == selectedAccountId }?.availableBalance ?: BigDecimal.ZERO
            } else {
                accounts.fold(BigDecimal.ZERO) { acc, a -> acc.add(a.availableBalance) }
            }
            val openingBalance = closingBalance.subtract(netFlow)

            val userName = userPreferencesRepository.getUserName() ?: "SmartMoney User"
            val userEmail = userPreferencesRepository.getUserEmail()
            val generatedAt = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.US).format(Date())

            val summary = ReportSummaryData(
                title = reportType.title,
                accountName = accountName,
                periodLabel = periodLabel,
                generatedAt = generatedAt,
                userName = userName,
                userEmail = userEmail,
                openingBalance = openingBalance,
                closingBalance = closingBalance,
                totalInflow = totalInflow,
                totalOutflow = totalOutflow,
                netCashFlow = netFlow,
                transactionCount = rows.size,
                rows = rows
            )

            withContext(Dispatchers.Main) {
                _uiState.value = _uiState.value.copy(previewSummary = summary)
            }
        }
    }

    private fun calculateDateRange(timeframe: ReportTimeframe): Triple<LocalDate, LocalDate, String> {
        val now = LocalDate.now()
        return when (timeframe) {
            ReportTimeframe.THIS_MONTH -> {
                val start = now.withDayOfMonth(1)
                val end = YearMonth.from(now).atEndOfMonth()
                val label = SimpleDateFormat("MMMM yyyy", Locale.US).format(Date())
                Triple(start, end, label)
            }
            ReportTimeframe.LAST_MONTH -> {
                val prevMonth = now.minusMonths(1)
                val start = prevMonth.withDayOfMonth(1)
                val end = YearMonth.from(prevMonth).atEndOfMonth()
                Triple(start, end, "${prevMonth.month.name.lowercase().replaceFirstChar { it.uppercase() }} ${prevMonth.year}")
            }
            ReportTimeframe.LAST_3_MONTHS -> {
                val start = now.minusMonths(3)
                Triple(start, now, "Past 3 Months (${start.month.name.take(3)} - ${now.month.name.take(3)} ${now.year})")
            }
            ReportTimeframe.YEAR_TO_DATE -> {
                val start = LocalDate.of(now.year, 1, 1)
                Triple(start, now, "YTD ${now.year}")
            }
            ReportTimeframe.ALL_TIME -> {
                val start = LocalDate.of(2020, 1, 1)
                Triple(start, now.plusYears(1), "All Time History")
            }
        }
    }

    private fun parseTxDate(timestamp: String?): LocalDate? {
        if (timestamp.isNullOrBlank()) return null
        return try {
            val dateStr = timestamp.take(10)
            LocalDate.parse(dateStr)
        } catch (_: Exception) {
            null
        }
    }
}

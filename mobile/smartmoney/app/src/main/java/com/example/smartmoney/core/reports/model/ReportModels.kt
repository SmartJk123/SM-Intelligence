package com.example.smartmoney.core.reports.model

import java.io.File
import java.math.BigDecimal
import android.net.Uri

enum class ReportType(val title: String, val subtitle: String) {
    ACCOUNT_STATEMENT("Account Statement", "Official M-PESA & Bank transaction ledger"),
    BUDGET_PERFORMANCE("Budget Adherence", "Budget limits vs. actual spending audit"),
    NET_WORTH_SNAPSHOT("Net Worth Summary", "Liquid cash, MMF, investments, and runway"),
    TAX_INVOICE_SUMMARY("Tax & Invoices Summary", "Scanned receipts and vendor expenditure log")
}

enum class ReportFormat(val label: String, val mimeType: String, val extension: String) {
    PDF("PDF Document (.pdf)", "application/pdf", ".pdf"),
    CSV("Spreadsheet (.csv)", "text/csv", ".csv")
}

enum class ReportTimeframe(val label: String) {
    THIS_MONTH("This Month"),
    LAST_MONTH("Last Month"),
    LAST_3_MONTHS("Last 3 Months"),
    YEAR_TO_DATE("Year to Date"),
    ALL_TIME("All Time")
}

data class StatementRowData(
    val date: String,
    val reference: String,
    val counterparty: String,
    val category: String,
    val amount: BigDecimal,
    val direction: String, // "CREDIT" or "DEBIT"
    val accountName: String,
    val balanceAfter: BigDecimal? = null
)

data class ReportSummaryData(
    val title: String,
    val accountName: String,
    val periodLabel: String,
    val generatedAt: String,
    val userName: String,
    val userEmail: String? = null,
    val openingBalance: BigDecimal = BigDecimal.ZERO,
    val closingBalance: BigDecimal = BigDecimal.ZERO,
    val totalInflow: BigDecimal = BigDecimal.ZERO,
    val totalOutflow: BigDecimal = BigDecimal.ZERO,
    val netCashFlow: BigDecimal = BigDecimal.ZERO,
    val transactionCount: Int = 0,
    val rows: List<StatementRowData> = emptyList()
)

data class GeneratedReportResult(
    val file: File,
    val uri: Uri,
    val filename: String,
    val mimeType: String,
    val format: ReportFormat
)

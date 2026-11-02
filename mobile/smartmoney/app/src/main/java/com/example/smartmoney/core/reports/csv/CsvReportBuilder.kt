package com.example.smartmoney.core.reports.csv

import com.example.smartmoney.core.reports.model.ReportSummaryData
import java.text.DecimalFormat

/**
 * High-performance, RFC-4180 compliant CSV statement generator.
 * Produces clean, spreadsheet-friendly exports for Microsoft Excel, Google Sheets,
 * LibreOffice, and accounting software.
 */
object CsvReportBuilder {

    private val currencyFormat = DecimalFormat("#,##0.00")

    fun build(data: ReportSummaryData): String {
        val sb = StringBuilder()

        // 1. Metadata Block
        sb.append("SmartMoney Financial Report - ").append(escape(data.title)).append("\n")
        sb.append("Account:,").append(escape(data.accountName)).append("\n")
        sb.append("Period:,").append(escape(data.periodLabel)).append("\n")
        sb.append("Generated For:,").append(escape(data.userName))
        if (!data.userEmail.isNullOrBlank()) {
            sb.append(" (").append(escape(data.userEmail)).append(")")
        }
        sb.append("\n")
        sb.append("Generated At:,").append(escape(data.generatedAt)).append("\n")
        sb.append("\n")

        // 2. Executive Summary Metrics
        sb.append("Opening Balance (KES),").append(formatAmount(data.openingBalance)).append("\n")
        sb.append("Total Inflow (KES),").append(formatAmount(data.totalInflow)).append("\n")
        sb.append("Total Outflow (KES),").append(formatAmount(data.totalOutflow)).append("\n")
        sb.append("Net Cash Flow (KES),").append(formatAmount(data.netCashFlow)).append("\n")
        sb.append("Closing Balance (KES),").append(formatAmount(data.closingBalance)).append("\n")
        sb.append("Transaction Count,").append(data.transactionCount).append("\n")
        sb.append("\n")

        // 3. Tabulated Data Columns
        sb.append("Date,Reference Code,Account,Description / Counterparty,Category,Direction,Amount (KES),Balance (KES)\n")

        // 4. Data Rows
        for (row in data.rows) {
            sb.append(escape(row.date)).append(",")
            sb.append(escape(row.reference)).append(",")
            sb.append(escape(row.accountName)).append(",")
            sb.append(escape(row.counterparty)).append(",")
            sb.append(escape(row.category)).append(",")
            sb.append(escape(row.direction)).append(",")
            val sign = if (row.direction.equals("CREDIT", ignoreCase = true)) "+" else "-"
            sb.append(sign).append(formatAmount(row.amount)).append(",")
            sb.append(if (row.balanceAfter != null) formatAmount(row.balanceAfter) else "").append("\n")
        }

        return sb.toString()
    }

    private fun formatAmount(amount: java.math.BigDecimal): String {
        return currencyFormat.format(amount)
    }

    private fun escape(value: String): String {
        if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
            return "\"" + value.replace("\"", "\"\"") + "\""
        }
        return value
    }
}

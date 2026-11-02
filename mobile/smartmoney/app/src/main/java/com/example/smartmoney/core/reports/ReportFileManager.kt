package com.example.smartmoney.core.reports

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.example.smartmoney.core.reports.csv.CsvReportBuilder
import com.example.smartmoney.core.reports.model.GeneratedReportResult
import com.example.smartmoney.core.reports.model.ReportFormat
import com.example.smartmoney.core.reports.model.ReportSummaryData
import com.example.smartmoney.core.reports.pdf.PdfReportBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Manages the generation, disk caching, FileProvider content URIs,
 * and Android ShareSheet intents for financial reports.
 */
object ReportFileManager {

    suspend fun generateReportFile(
        context: Context,
        data: ReportSummaryData,
        format: ReportFormat
    ): GeneratedReportResult = withContext(Dispatchers.IO) {
        val reportsDir = File(context.cacheDir, "reports").apply {
            if (!exists()) mkdirs()
        }

        val dateSuffix = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val sanitizedAccount = data.accountName.replace(Regex("[^a-zA-Z0-9]"), "_").take(15)
        val filename = "SmartMoney_${sanitizedAccount}_$dateSuffix${format.extension}"
        val targetFile = File(reportsDir, filename)

        when (format) {
            ReportFormat.PDF -> {
                FileOutputStream(targetFile).use { outputStream ->
                    PdfReportBuilder.build(data, outputStream)
                }
            }
            ReportFormat.CSV -> {
                val csvContent = CsvReportBuilder.build(data)
                targetFile.writeText(csvContent, Charsets.UTF_8)
            }
        }

        val authority = "${context.packageName}.fileprovider"
        val contentUri = FileProvider.getUriForFile(context, authority, targetFile)

        GeneratedReportResult(
            file = targetFile,
            uri = contentUri,
            filename = filename,
            mimeType = format.mimeType,
            format = format
        )
    }

    fun createShareIntent(context: Context, result: GeneratedReportResult): Intent {
        return Intent(Intent.ACTION_SEND).apply {
            type = result.mimeType
            putExtra(Intent.EXTRA_STREAM, result.uri)
            putExtra(Intent.EXTRA_SUBJECT, "SmartMoney Report - ${result.filename}")
            putExtra(Intent.EXTRA_TEXT, "Here is your generated SmartMoney statement.")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    fun createViewIntent(context: Context, result: GeneratedReportResult): Intent {
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(result.uri, result.mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }
}

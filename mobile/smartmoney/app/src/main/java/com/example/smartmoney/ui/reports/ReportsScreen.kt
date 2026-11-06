package com.example.smartmoney.ui.reports

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.smartmoney.core.reports.ReportFileManager
import com.example.smartmoney.core.reports.model.ReportFormat
import com.example.smartmoney.core.reports.model.ReportSummaryData
import com.example.smartmoney.core.reports.model.ReportTimeframe
import com.example.smartmoney.core.reports.model.ReportType
import java.text.DecimalFormat

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(
    viewModel: ReportsViewModel,
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Reports & Statements",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Generate and export verified financial records",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                windowInsets = WindowInsets(0, 0, 0, 0)
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(scrollState)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // 1. Report Type Selection Cards
            ReportTypeSection(
                selectedType = uiState.reportType,
                onSelectType = { viewModel.selectReportType(it) }
            )

            // 2. Scope & Timeframe Filters
            ReportFilterSection(
                timeframe = uiState.timeframe,
                onSelectTimeframe = { viewModel.selectTimeframe(it) },
                accounts = uiState.accounts,
                selectedAccountId = uiState.selectedAccountId,
                onSelectAccount = { viewModel.selectAccount(it) }
            )

            // 3. Format Selector (PDF vs CSV)
            ReportFormatSection(
                selectedFormat = uiState.format,
                onSelectFormat = { viewModel.selectFormat(it) }
            )

            // 4. Live Statement Summary Preview Card
            uiState.previewSummary?.let { summary ->
                LiveSummaryPreviewCard(summary = summary)
            }

            // Error banner if any
            uiState.errorMessage?.let { error ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            // Success Card after generation
            uiState.lastGeneratedResult?.let { result ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Outlined.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Ready: ${result.filename}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilledTonalButton(
                                onClick = {
                                    val viewIntent = ReportFileManager.createViewIntent(context, result)
                                    context.startActivity(viewIntent)
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Outlined.Description, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Open")
                            }
                            Button(
                                onClick = {
                                    val shareIntent = ReportFileManager.createShareIntent(context, result)
                                    context.startActivity(android.content.Intent.createChooser(shareIntent, "Share Report"))
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Outlined.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Share")
                            }
                        }
                    }
                }
            }

            // 5. Action Buttons (Generate / Share)
            Button(
                onClick = {
                    viewModel.generateReport(context) { result ->
                        val viewIntent = ReportFileManager.createViewIntent(context, result)
                        try {
                            context.startActivity(viewIntent)
                        } catch (_: Exception) {
                            // Fallback if no direct viewer installed
                        }
                    }
                },
                enabled = !uiState.isGenerating && uiState.previewSummary != null,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                if (uiState.isGenerating) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("Generating Document...")
                } else {
                    Icon(Icons.Outlined.FileDownload, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Generate & Preview ${uiState.format.name}",
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            OutlinedButton(
                onClick = {
                    viewModel.generateReport(context) { result ->
                        val shareIntent = ReportFileManager.createShareIntent(context, result)
                        context.startActivity(android.content.Intent.createChooser(shareIntent, "Share Statement via"))
                    }
                },
                enabled = !uiState.isGenerating && uiState.previewSummary != null,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Outlined.Share, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Share via WhatsApp / Gmail / Drive")
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ReportTypeSection(
    selectedType: ReportType,
    onSelectType: (ReportType) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "1. SELECT REPORT TYPE",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        val types = listOf(
            Triple(ReportType.ACCOUNT_STATEMENT, Icons.AutoMirrored.Outlined.ReceiptLong, "M-PESA & Bank transaction ledger"),
            Triple(ReportType.BUDGET_PERFORMANCE, Icons.Outlined.Savings, "Budget limits vs. actual spend"),
            Triple(ReportType.NET_WORTH_SNAPSHOT, Icons.Outlined.AccountBalance, "Liquid cash, MMF & investments"),
            Triple(ReportType.TAX_INVOICE_SUMMARY, Icons.Outlined.Description, "Scanned receipts & tax log")
        )

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            types.forEach { (type, icon, desc) ->
                val isSelected = selectedType == type
                Surface(
                    onClick = { onSelectType(type) },
                    shape = RoundedCornerShape(12.dp),
                    color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                    border = if (isSelected) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                icon,
                                contentDescription = null,
                                tint = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = type.title,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = desc,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReportFilterSection(
    timeframe: ReportTimeframe,
    onSelectTimeframe: (ReportTimeframe) -> Unit,
    accounts: List<com.example.smartmoney.data.local.entity.AccountEntity>,
    selectedAccountId: String?,
    onSelectAccount: (String?) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = "2. STATEMENT TIMEFRAME",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(ReportTimeframe.entries) { tf ->
                FilterChip(
                    selected = timeframe == tf,
                    onClick = { onSelectTimeframe(tf) },
                    label = { Text(tf.label) }
                )
            }
        }

        if (accounts.isNotEmpty()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "ACCOUNT SCOPE",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    FilterChip(
                        selected = selectedAccountId == null,
                        onClick = { onSelectAccount(null) },
                        label = { Text("All Accounts") }
                    )
                }
                items(accounts) { acc ->
                    FilterChip(
                        selected = selectedAccountId == acc.id || selectedAccountId == acc.accountId,
                        onClick = { onSelectAccount(acc.id) },
                        label = { Text(acc.accountName) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ReportFormatSection(
    selectedFormat: ReportFormat,
    onSelectFormat: (ReportFormat) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "3. EXPORT FORMAT",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ReportFormat.entries.forEach { fmt ->
                val isSelected = selectedFormat == fmt
                Surface(
                    onClick = { onSelectFormat(fmt) },
                    shape = RoundedCornerShape(10.dp),
                    color = if (isSelected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                    border = if (isSelected) BorderStroke(1.5.dp, MaterialTheme.colorScheme.secondary) else null,
                    modifier = Modifier.weight(1f)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = fmt.label,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (fmt == ReportFormat.PDF) "Printable / Official" else "Excel / Google Sheets",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LiveSummaryPreviewCard(summary: ReportSummaryData) {
    val currencyFormat = remember { DecimalFormat("#,##0.00") }

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "STATEMENT PREVIEW",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Badge(containerColor = MaterialTheme.colorScheme.primaryContainer) {
                    Text(
                        text = "${summary.transactionCount} entries",
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "${summary.accountName}   •   ${summary.periodLabel}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

            // 4 Key Metrics
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Total Inflow (+)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        text = "KES ${currencyFormat.format(summary.totalInflow)}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF16A34A)
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Total Outflow (-)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        text = "KES ${currencyFormat.format(summary.totalOutflow)}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Net Cash Flow", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    val isPos = summary.netCashFlow >= java.math.BigDecimal.ZERO
                    Text(
                        text = "${if (isPos) "+" else ""}KES ${currencyFormat.format(summary.netCashFlow)}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (isPos) Color(0xFF16A34A) else MaterialTheme.colorScheme.error
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Closing Balance", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        text = "KES ${currencyFormat.format(summary.closingBalance)}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

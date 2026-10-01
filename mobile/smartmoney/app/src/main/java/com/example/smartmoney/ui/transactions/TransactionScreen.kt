package com.example.smartmoney.ui.transactions

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.smartmoney.domain.model.Transaction
import com.example.smartmoney.ui.components.BankLogo
import com.example.smartmoney.ui.components.findBankInText
import com.example.smartmoney.ui.theme.LocalDarkTheme
import com.example.smartmoney.ui.theme.SmartMoneyColors
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun TransactionScreen(
    viewModel: TransactionViewModel,
    onNavigateToInvoice: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val filteredTransactions by viewModel.filteredTransactions.collectAsState()
    val selectedFilter by viewModel.selectedFilter.collectAsState()
    val isSyncing by viewModel.isSyncing.collectAsState()
    val totalInflow by viewModel.totalInflow.collectAsState()
    val totalOutflow by viewModel.totalOutflow.collectAsState()

    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val topClearance = remember(statusBarTop) { statusBarTop + 64.dp }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp)
        ) {
            Spacer(modifier = Modifier.height(topClearance + 12.dp))

            when (val state = uiState) {
                is TransactionUiState.Loading -> {
                    LoadingTransactionsView()
                }
                is TransactionUiState.Success -> {
                    TransactionsListView(
                        filteredTransactions = filteredTransactions,
                        totalInflow = totalInflow,
                        totalOutflow = totalOutflow,
                        selectedFilter = selectedFilter,
                        onFilterChange = remember(viewModel) { { filter: String -> viewModel.setFilter(filter) } },
                        onAddInvoiceClick = onNavigateToInvoice
                    )
                }
                is TransactionUiState.Error -> {
                    ErrorTransactionsView(
                        message = state.message,
                        onRetry = remember(viewModel) { { viewModel.refreshTransactions() } }
                    )
                }
            }
        }

        // Sleek sync progress indicator pinned directly beneath the transparent top bar without shifting content layout
        if (isSyncing) {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = topClearance)
                    .height(2.dp)
                    .align(Alignment.TopCenter),
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
fun TransactionsListView(
    filteredTransactions: List<Transaction>,
    totalInflow: BigDecimal = BigDecimal.ZERO,
    totalOutflow: BigDecimal = BigDecimal.ZERO,
    selectedFilter: String = "ALL",
    onFilterChange: (String) -> Unit = {},
    onAddInvoiceClick: () -> Unit = {},
    onSimulateClick: (() -> Unit)? = null,
    onSimulateInflowClick: (() -> Unit)? = onSimulateClick,
    onSimulateOutflowClick: (() -> Unit)? = null
) {
    Column(modifier = Modifier.fillMaxSize()) {
        OverviewSummaryCard(inflow = totalInflow, outflow = totalOutflow)

        Spacer(modifier = Modifier.height(12.dp))

        // --- Custom Filter Row ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier
                    .background(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(24.dp)
                    )
                    .padding(4.dp)
            ) {
                listOf("ALL" to "All", "CREDIT" to "Inflow", "DEBIT" to "Outflow").forEach { (filterKey, label) ->
                    val isSelected = selectedFilter == filterKey
                    val containerColor = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent
                    val contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                    
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(containerColor)
                            .clickable { onFilterChange(filterKey) }
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = label,
                            color = contentColor,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (onSimulateInflowClick != null) {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.8f),
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .clickable { onSimulateInflowClick() }
                            .padding(end = 4.dp),
                        shadowElevation = 1.dp
                    ) {
                        Text(
                            text = "+ Inflow",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
                        )
                    }
                }

                if (onSimulateOutflowClick != null) {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f),
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .clickable { onSimulateOutflowClick() }
                            .padding(end = 8.dp),
                        shadowElevation = 1.dp
                    ) {
                        Text(
                            text = "- Outflow",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
                        )
                    }
                }

                // --- Add Invoice Floating Button Alternative ---
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier
                        .size(44.dp)
                        .clickable { onAddInvoiceClick() },
                    shadowElevation = 2.dp
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Add Invoice",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (filteredTransactions.isEmpty()) {
            EmptyTransactionsView()
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 100.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(
                    items = filteredTransactions,
                    key = { it.id }
                ) { transaction ->
                    TransactionCard(transaction = transaction)
                }
            }
        }
    }
}

@Composable
fun OverviewSummaryCard(inflow: BigDecimal, outflow: BigDecimal) {
    val isDark = LocalDarkTheme.current
    val containerBg = if (isDark) SmartMoneyColors.DarkSurfaceElevated else MaterialTheme.colorScheme.surface
    val themeGreen = if (isDark) Color(0xFF4ADE80) else SmartMoneyColors.DarkSlateGreen
    val themeRed = if (isDark) Color(0xFFFF6B6B) else Color(0xFF992B1C)

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = containerBg),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.ArrowUpward,
                        contentDescription = "Inflow",
                        tint = themeGreen,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Inflow",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelLarge
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = formatKesCurrencyNoPrefix(inflow),
                    color = themeGreen,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }

            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(40.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant)
            )

            Column(modifier = Modifier.weight(1f).padding(start = 20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.ArrowDownward,
                        contentDescription = "Outflow",
                        tint = themeRed,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Outflow",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelLarge
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = formatKesCurrencyNoPrefix(outflow),
                    color = themeRed,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun TransactionCard(transaction: Transaction) {
    val isCredit = remember(transaction.type) { transaction.type.equals("CREDIT", ignoreCase = true) }
    val isDark = LocalDarkTheme.current
    val amountColor = if (isCredit) {
        if (isDark) Color(0xFF4ADE80) else SmartMoneyColors.DarkSlateGreen
    } else {
        if (isDark) MaterialTheme.colorScheme.error else Color(0xFF992B1C)
    }
    
    val iconBg = if (isDark) MaterialTheme.colorScheme.surfaceVariant else SmartMoneyColors.PaleMintGreen.copy(alpha = 0.5f)
    val iconColor = if (isDark) MaterialTheme.colorScheme.onSurfaceVariant else SmartMoneyColors.DarkSlateGreen

    val detectedBank = remember(transaction.description) { findBankInText(transaction.description) }
    val formattedAmount = remember(transaction.amount, isCredit) { formatKesAmount(transaction.amount, isCredit) }
    val formattedTime = remember(transaction.timestamp) { formatTimestamp(transaction.timestamp) }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = Color.Transparent
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon
            if (detectedBank != null) {
                BankLogo(
                    bankName = detectedBank,
                    modifier = Modifier.size(48.dp),
                    shape = CircleShape
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(iconBg),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Receipt,
                        contentDescription = null,
                        tint = iconColor,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Details
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = transaction.description ?: "Transaction",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = formattedTime,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (transaction.status == "PENDING") {
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.tertiaryContainer, RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "PENDING",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Amount
            Text(
                text = formattedAmount,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = amountColor
            )
        }
    }
}

@Composable
fun LoadingTransactionsView() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Fetching transactions...",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun EmptyTransactionsView() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 60.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(24.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(80.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Receipt,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.size(40.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "No Transactions Found",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Your prepaid tokens, meter charges, and utility payments will appear here once they are processed.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 20.dp)
            )
        }
    }
}

@Composable
fun ErrorTransactionsView(
    message: String,
    onRetry: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(24.dp)
        ) {
            Text(
                text = "Oops! Something went wrong",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.error,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = onRetry,
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Retry Connection", modifier = Modifier.padding(vertical = 4.dp, horizontal = 12.dp))
            }
        }
    }
}

private val KesDecimalFormat = ThreadLocal.withInitial {
    DecimalFormat("#,##0.00").apply {
        roundingMode = RoundingMode.HALF_EVEN
    }
}

private val TimestampFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM dd, yyyy • hh:mm a")
    .withZone(ZoneId.systemDefault())

private fun formatKesAmount(amount: BigDecimal, isCredit: Boolean): String {
    val formatter = KesDecimalFormat.get() ?: DecimalFormat("#,##0.00")
    val prefix = if (isCredit) "+ KES " else "- KES "
    return prefix + formatter.format(amount)
}

private fun formatKesCurrencyNoPrefix(amount: BigDecimal): String {
    val formatter = KesDecimalFormat.get() ?: DecimalFormat("#,##0.00")
    return "KES " + formatter.format(amount)
}

private fun formatTimestamp(isoString: String): String {
    return try {
        val instant = Instant.parse(isoString)
        TimestampFormatter.format(instant)
    } catch (e: Exception) {
        isoString.replace("T", " ").replace("Z", "")
    }
}

@androidx.compose.ui.tooling.preview.Preview(showBackground = true, showSystemUi = true)
@Composable
private fun TransactionScreenPreview() {
    com.example.smartmoney.ui.theme.EnergyTheme {
        TransactionScreen(
            viewModel = TransactionViewModel()
        )
    }
}

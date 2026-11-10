package com.example.smartmoney.ui.cashflow

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.CompareArrows
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.ElectricBolt
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.WorkOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.smartmoney.domain.model.Transaction
import com.example.smartmoney.ui.home.HomeViewModel
import com.example.smartmoney.ui.home.analytics.model.CashFlowPoint
import com.example.smartmoney.ui.theme.LocalDarkTheme
import com.example.smartmoney.ui.theme.SmartMoneyColors
import com.example.smartmoney.ui.transactions.TransactionViewModel
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

private fun formatKes(amount: BigDecimal): String {
    val rounded = amount.setScale(0, RoundingMode.HALF_UP)
    return "KES " + DecimalFormat("#,##0").format(rounded)
}

private fun parseTxDate(timestamp: String?, zoneId: ZoneId): LocalDate? {
    if (timestamp.isNullOrBlank()) return null
    return try {
        if (timestamp.length >= 10 && timestamp[4] == '-' && timestamp[7] == '-') {
            LocalDate.parse(timestamp.substring(0, 10))
        } else {
            Instant.parse(timestamp).atZone(zoneId).toLocalDate()
        }
    } catch (_: Exception) {
        null
    }
}

private fun parseTxHour(timestamp: String?): Int {
    if (timestamp.isNullOrBlank()) return 12
    return try {
        val tIdx = timestamp.indexOf('T')
        val sIdx = timestamp.indexOf(' ')
        val timeStart = when {
            tIdx >= 0 -> tIdx + 1
            sIdx >= 0 -> sIdx + 1
            else -> return 12
        }
        if (timestamp.length >= timeStart + 2) {
            timestamp.substring(timeStart, timeStart + 2).toIntOrNull() ?: 12
        } else 12
    } catch (_: Exception) {
        12
    }
}

private fun resolveOutflowCategory(description: String?): String {
    if (description == null) return "Other Expenses"
    val lower = description.lowercase()
    return when {
        lower.contains("rent") || lower.contains("lease") || lower.contains("housing") -> "Rent & Housing"
        lower.contains("naivas") || lower.contains("carrefour") || lower.contains("quickmart") ||
                lower.contains("supermarket") || lower.contains("grocery") || lower.contains("food") -> "Groceries & Food"
        lower.contains("util") || lower.contains("power") || lower.contains("kplc") ||
                lower.contains("electric") || lower.contains("water") || lower.contains("internet") ||
                lower.contains("zuku") || lower.contains("safaricom") || lower.contains("airtime") ||
                lower.contains("wifi") || lower.contains("bill") -> "Utilities & Bills"
        lower.contains("uber") || lower.contains("fuel") || lower.contains("petrol") ||
                lower.contains("bolt") || lower.contains("matatu") || lower.contains("total") ||
                lower.contains("shell") || lower.contains("rubis") -> "Transport & Travel"
        lower.contains("din") || lower.contains("restaur") || lower.contains("cafe") ||
                lower.contains("java") || lower.contains("pizza") || lower.contains("kfc") ||
                lower.contains("artcaffe") || lower.contains("coffee") -> "Dining"
        lower.contains("shop") || lower.contains("cloth") || lower.contains("mall") ||
                lower.contains("jumia") || lower.contains("amazon") || lower.contains("shoe") -> "Shopping & Retail"
        lower.contains("health") || lower.contains("hosp") || lower.contains("pharm") ||
                lower.contains("med") || lower.contains("doctor") || lower.contains("clinic") -> "Healthcare"
        lower.contains("educ") || lower.contains("school") || lower.contains("fee") ||
                lower.contains("course") || lower.contains("uni") -> "Education"
        else -> "Other Expenses"
    }
}

private fun getOutflowCategoryStyle(category: String): Pair<ImageVector, Color> {
    return when (category) {
        "Rent & Housing" -> Icons.Outlined.AccountBalance to Color(0xFF657166)
        "Groceries & Food" -> Icons.Outlined.Restaurant to Color(0xFF22C55E)
        "Utilities & Bills" -> Icons.Outlined.ElectricBolt to Color(0xFFF59E0B)
        "Transport & Travel" -> Icons.Outlined.Speed to Color(0xFF38BDF8)
        "Dining" -> Icons.Outlined.Restaurant to Color(0xFFFB923C)
        "Shopping & Retail" -> Icons.Outlined.ShoppingBag to Color(0xFFEC4899)
        "Healthcare" -> Icons.Outlined.Shield to Color(0xFFEF4444)
        "Education" -> Icons.Outlined.Description to Color(0xFF8B5CF6)
        else -> Icons.Outlined.Payments to Color(0xFF94A3B8)
    }
}

private fun resolveInflowCategory(tx: Transaction): String {
    val desc = tx.description?.lowercase() ?: ""
    val src = tx.source.lowercase()
    return when {
        desc.contains("salary") || desc.contains("payroll") || desc.contains("wage") || desc.contains("employer") -> "Salary & Payroll"
        desc.contains("received from") || desc.contains("deposit") || src.contains("sms") || src.contains("mpesa") || desc.contains("m-pesa") -> "Mobile Money / M-PESA"
        desc.contains("invoice") || desc.contains("client") || desc.contains("consult") -> "Invoices & Business"
        desc.contains("interest") || desc.contains("dividend") || desc.contains("yield") || desc.contains("m-shwari") -> "Investments & Yields"
        desc.contains("bank") || desc.contains("transfer") || src.contains("bank") -> "Bank Transfers"
        else -> "Other Inflows"
    }
}

private fun getInflowCategoryStyle(category: String): Pair<ImageVector, Color> {
    return when (category) {
        "Salary & Payroll" -> Icons.Outlined.WorkOutline to Color(0xFF22C55E)
        "Mobile Money / M-PESA" -> Icons.Outlined.AccountBalanceWallet to Color(0xFF38BDF8)
        "Invoices & Business" -> Icons.Outlined.Payments to Color(0xFF8B5CF6)
        "Investments & Yields" -> Icons.Outlined.Savings to Color(0xFFF59E0B)
        "Bank Transfers" -> Icons.Outlined.AccountBalance to Color(0xFF657166)
        else -> Icons.Outlined.Payments to Color(0xFF94A3B8)
    }
}

/**
 * Filter scopes for the Cash Flow trajectory timeline.
 */
enum class CashFlowScope(val label: String) {
    DAY("24 Hours"),
    WEEK("7 Days"),
    MONTH("30 Days"),
    QUARTER("Quarter")
}

data class CashStreamItem(
    val title: String,
    val subtitle: String,
    val amount: BigDecimal,
    val percentage: Float,
    val icon: ImageVector,
    val color: Color
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CashFlowScreen(
    homeViewModel: HomeViewModel? = null,
    transactionViewModel: TransactionViewModel? = null,
    onBack: () -> Unit,
    onNavigateToTransactions: () -> Unit = {},
    onNavigateToReports: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val isDark = LocalDarkTheme.current
    val homeUiState by (homeViewModel?.uiState?.collectAsState() ?: remember { mutableStateOf(null) })
    val txState by (transactionViewModel?.filteredTransactions?.collectAsState() ?: remember { mutableStateOf(emptyList()) })

    var selectedScope by remember { mutableStateOf(CashFlowScope.MONTH) }
    var selectedMovementFilter by remember { mutableStateOf("ALL") }
    var showInfoDialog by remember { mutableStateOf(false) }

    val today = remember { LocalDate.now() }
    val currentMonth = remember { YearMonth.now() }
    val zoneId = remember { ZoneId.systemDefault() }

    // Pure dynamic calculation: All transactions sourced directly from the user's data stream
    val transactions = remember(txState) { txState }

    val monthTransactions = remember(transactions, currentMonth) {
        transactions.filter { tx ->
            val date = parseTxDate(tx.timestamp, zoneId)
            date != null && YearMonth.from(date) == currentMonth
        }
    }

    val totalInflow = remember(monthTransactions) {
        monthTransactions
            .filter { it.type.equals("CREDIT", ignoreCase = true) }
            .fold(BigDecimal.ZERO) { acc, t -> acc.add(t.amount.abs()) }
    }

    val totalOutflow = remember(monthTransactions) {
        monthTransactions
            .filter { it.type.equals("DEBIT", ignoreCase = true) || it.type.equals("EXPENSE", ignoreCase = true) }
            .fold(BigDecimal.ZERO) { acc, t -> acc.add(t.amount.abs()) }
    }

    val netCashFlow = remember(totalInflow, totalOutflow) {
        totalInflow.subtract(totalOutflow)
    }

    val hasTransactions = transactions.isNotEmpty()
    val isSurplus = netCashFlow >= BigDecimal.ZERO
    val retainedRatio = remember(totalInflow, netCashFlow) {
        if (totalInflow > BigDecimal.ZERO && isSurplus) {
            (netCashFlow.toDouble() / totalInflow.toDouble() * 100).coerceIn(0.0, 100.0)
        } else if (isSurplus && totalInflow > BigDecimal.ZERO) 100.0 else 0.0
    }

    val daysElapsed = remember { today.dayOfMonth.coerceAtLeast(1) }
    val daysInMonth = remember { currentMonth.lengthOfMonth() }
    val daysRemaining = remember(daysInMonth, daysElapsed) { (daysInMonth - daysElapsed).coerceAtLeast(0) }

    val dailyBurn = remember(totalOutflow, daysElapsed) {
        if (totalOutflow > BigDecimal.ZERO && daysElapsed > 0) {
            totalOutflow.divide(BigDecimal(daysElapsed), RoundingMode.HALF_UP)
        } else BigDecimal.ZERO
    }

    val dailyIncome = remember(totalInflow, daysElapsed) {
        if (totalInflow > BigDecimal.ZERO && daysElapsed > 0) {
            totalInflow.divide(BigDecimal(daysElapsed), RoundingMode.HALF_UP)
        } else BigDecimal.ZERO
    }

    val coverageRatio = remember(totalInflow, totalOutflow) {
        if (totalOutflow > BigDecimal.ZERO) {
            totalInflow.toDouble() / totalOutflow.toDouble()
        } else if (totalInflow > BigDecimal.ZERO) {
            Double.POSITIVE_INFINITY
        } else 0.0
    }

    val totalBalance = homeUiState?.totalBalance ?: BigDecimal.ZERO
    val reserveRunwayText = remember(totalBalance, dailyBurn) {
        if (dailyBurn > BigDecimal.ZERO && totalBalance > BigDecimal.ZERO) {
            val days = (totalBalance.toDouble() / dailyBurn.toDouble()).roundToInt()
            "$days Days"
        } else if (dailyBurn == BigDecimal.ZERO && totalBalance > BigDecimal.ZERO) {
            "Fully Funded"
        } else if (totalBalance <= BigDecimal.ZERO) {
            "0 Days"
        } else {
            "N/A"
        }
    }

    val mintGreen = if (isDark) Color(0xFF4ADE80) else Color(0xFF16A34A)
    val coralRed = if (isDark) Color(0xFFFF6B6B) else Color(0xFFEF4444)
    val cardBg = if (isDark) SmartMoneyColors.DarkSurface else MaterialTheme.colorScheme.surface
    val cardBgElevated = if (isDark) SmartMoneyColors.DarkSurfaceElevated else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Cash Flow Trajectory",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )

                        }
                        Text(
                            text = "Real-time liquidity velocity & forecast",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showInfoDialog = true }) {
                        Icon(
                            imageVector = Icons.Outlined.Info,
                            contentDescription = "Methodology & Info",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onNavigateToReports) {
                        Icon(
                            imageVector = Icons.Outlined.Description,
                            contentDescription = "Statement",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                ),
                windowInsets = WindowInsets(0, 0, 0, 0)
            )
        },
        modifier = modifier
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            Spacer(modifier = Modifier.height(12.dp))

            // 1. Hero KPI Card
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.elevatedCardColors(containerColor = cardBg),
                elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier.size(36.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Outlined.CompareArrows,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "NET CASH POSITION",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.2.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "${currentMonth.month.getDisplayName(TextStyle.FULL, Locale.US)} ${currentMonth.year}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                                )
                            }
                        }

                        // Pulse Pill
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = (if (isSurplus) mintGreen else coralRed).copy(alpha = if (isDark) 0.2f else 0.12f)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(7.dp)
                                        .clip(CircleShape)
                                        .background(if (isSurplus) mintGreen else coralRed)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (!hasTransactions) "No Movement" else if (isSurplus) "Surplus Flow" else "Deficit Watch",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSurplus) mintGreen else coralRed
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Net Flow Large Text
                    val sign = if (isSurplus && totalInflow > BigDecimal.ZERO) "+" else ""
                    Text(
                        text = "$sign${formatKes(netCashFlow)}",
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (isSurplus) mintGreen else coralRed,
                        letterSpacing = (-0.5).sp
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = if (!hasTransactions) {
                            "No transactions recorded for this billing cycle yet."
                        } else if (isSurplus) {
                            "Retaining ${"%.1f".format(retainedRatio)}% of incoming liquidity this cycle."
                        } else {
                            "Outflow exceeds incoming funds by ${formatKes(netCashFlow.abs())}."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(18.dp))

                    // Dual Inflow / Outflow KPI Pills
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Inflow tile
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = cardBgElevated,
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Outlined.ArrowDownward,
                                        contentDescription = null,
                                        tint = mintGreen,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Total Inflow",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "+${formatKes(totalInflow)}",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = mintGreen
                                )
                            }
                        }

                        // Outflow tile
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = cardBgElevated,
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Outlined.ArrowUpward,
                                        contentDescription = null,
                                        tint = coralRed,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Total Outflow",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "-${formatKes(totalOutflow)}",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = coralRed
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Proportional Visual Bar
                    val totalVolume = totalInflow.add(totalOutflow)
                    val inRatio = if (totalVolume > BigDecimal.ZERO) {
                        (totalInflow.toFloat() / totalVolume.toFloat()).coerceIn(0.05f, 0.95f)
                    } else 0.5f

                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Inflow (${if (totalVolume > BigDecimal.ZERO) (inRatio * 100).roundToInt() else 0}%)",
                                style = MaterialTheme.typography.labelSmall,
                                color = mintGreen,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Outflow (${if (totalVolume > BigDecimal.ZERO) ((1f - inRatio) * 100).roundToInt() else 0}%)",
                                style = MaterialTheme.typography.labelSmall,
                                color = coralRed,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(10.dp)
                                .clip(RoundedCornerShape(5.dp))
                        ) {
                            if (totalVolume > BigDecimal.ZERO) {
                                Box(
                                    modifier = Modifier
                                        .weight(inRatio)
                                        .fillMaxSize()
                                        .background(mintGreen)
                                )
                                Spacer(modifier = Modifier.width(2.dp))
                                Box(
                                    modifier = Modifier
                                        .weight(1f - inRatio)
                                        .fillMaxSize()
                                        .background(coralRed)
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 2. Timeframe Segmented Selector
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(cardBg)
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                CashFlowScope.values().forEach { scope ->
                    val isSelected = scope == selectedScope
                    val bgCol = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
                    val textCol = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(bgCol)
                            .clickable { selectedScope = scope }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = scope.label,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = textCol
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 3. Interactive Trajectory Chart Canvas Card (100% Dynamically Calculated)
            CashFlowInteractiveChartCard(
                scope = selectedScope,
                transactions = transactions,
                currentMonth = currentMonth,
                today = today,
                zoneId = zoneId,
                isDark = isDark,
                cardBg = cardBg
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 4. Cash Flow Health & Velocity Metrics Card (100% Dynamically Calculated)
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.elevatedCardColors(containerColor = cardBg),
                elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Speed,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Velocity & Liquidity Health",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    val coverageHelper = if (totalOutflow == BigDecimal.ZERO && totalInflow == BigDecimal.ZERO) {
                        "No transactions"
                    } else if (coverageRatio >= 1.2) {
                        "Strong Cushion"
                    } else if (coverageRatio >= 1.0) {
                        "Balanced Flow"
                    } else {
                        "Deficit Watch"
                    }

                    val coverageDisplay = if (coverageRatio == Double.POSITIVE_INFINITY) {
                        "Max Inflow"
                    } else {
                        "${"%.2f".format(coverageRatio)}x"
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        MetricTile(
                            label = "Coverage Ratio",
                            value = coverageDisplay,
                            helper = coverageHelper,
                            badgeColor = if (coverageRatio >= 1.0) mintGreen else coralRed,
                            modifier = Modifier.weight(1f),
                            bg = cardBgElevated
                        )
                        MetricTile(
                            label = "Daily Burn Rate",
                            value = formatKes(dailyBurn),
                            helper = "Avg daily outflow ($daysElapsed d)",
                            badgeColor = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f),
                            bg = cardBgElevated
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        MetricTile(
                            label = "Daily Inflow Rate",
                            value = formatKes(dailyIncome),
                            helper = "Avg incoming pace ($daysElapsed d)",
                            badgeColor = mintGreen,
                            modifier = Modifier.weight(1f),
                            bg = cardBgElevated
                        )
                        MetricTile(
                            label = "Reserve Runway",
                            value = reserveRunwayText,
                            helper = if (totalBalance > BigDecimal.ZERO) "Based on balance" else "Balance neutral",
                            badgeColor = if (totalBalance > BigDecimal.ZERO && dailyBurn <= totalBalance) mintGreen else coralRed,
                            modifier = Modifier.weight(1f),
                            bg = cardBgElevated
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 5. Inflow Streams & Outflow Allocation (100% Dynamically Grouped)
            CashFlowStreamsSection(
                transactions = transactions,
                totalInflow = totalInflow,
                totalOutflow = totalOutflow,
                cardBg = cardBg,
                cardBgElevated = cardBgElevated
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 6. 30-Day Liquidity Forecast Card (100% Dynamically Projected)
            val projectedNetEndOfPeriod = remember(netCashFlow, dailyIncome, dailyBurn, daysRemaining) {
                val netDaily = dailyIncome.subtract(dailyBurn)
                netCashFlow.add(netDaily.multiply(BigDecimal(daysRemaining)))
            }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
                        shape = RoundedCornerShape(20.dp)
                    ),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isDark) SmartMoneyColors.DarkSurfaceElevated else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Shield,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "30-Day Liquidity Forecast",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Predictive cash equilibrium projection",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = if (!hasTransactions) {
                            "No transactions recorded for this period yet. When transactions are logged, automated daily velocity and month-end liquidity projections will compute dynamically."
                        } else if (isSurplus) {
                            "Based on current velocity (+${formatKes(dailyIncome)}/day in vs -${formatKes(dailyBurn)}/day out across $daysElapsed days), your period-end net cash flow is projected to compound to +${formatKes(projectedNetEndOfPeriod)}."
                        } else {
                            "At current spending velocity (-${formatKes(dailyBurn)}/day out vs +${formatKes(dailyIncome)}/day in), outflows exceed inflows by ${formatKes(dailyBurn.subtract(dailyIncome))}/day. Projected period-end deficit is ${formatKes(projectedNetEndOfPeriod)}."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 20.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 7. Recent Cash Flow Movements Activity List (100% Real User Transactions)
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.elevatedCardColors(containerColor = cardBg),
                elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Cash Flow Activity",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        TextButton(onClick = onNavigateToTransactions) {
                            Text(
                                text = "View All",
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    // Filter Chips: ALL, INFLOW, OUTFLOW
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("ALL" to "All Streams", "IN" to "Inflows (+)", "OUT" to "Outflows (-)").forEach { (key, label) ->
                            val isSel = selectedMovementFilter == key
                            val bg = if (isSel) MaterialTheme.colorScheme.primary else cardBgElevated
                            val fg = if (isSel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant

                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = bg,
                                modifier = Modifier.clickable { selectedMovementFilter = key }
                            ) {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = fg,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    val recentItems = remember(transactions, selectedMovementFilter) {
                        val filtered = when (selectedMovementFilter) {
                            "IN" -> transactions.filter { it.type.equals("CREDIT", ignoreCase = true) }
                            "OUT" -> transactions.filter { it.type.equals("DEBIT", ignoreCase = true) || it.type.equals("EXPENSE", ignoreCase = true) }
                            else -> transactions
                        }
                        filtered.take(10)
                    }

                    if (recentItems.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Outlined.BarChart,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                    modifier = Modifier.size(36.dp)
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "No Cash Flow Activity Yet",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Incoming credits and outgoing debits will appear here dynamically.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    } else {
                        recentItems.forEachIndexed { index, tx ->
                            val isCredit = tx.type.equals("CREDIT", ignoreCase = true)
                            CashMovementRow(
                                transaction = tx,
                                isCredit = isCredit,
                                isDark = isDark,
                                mintGreen = mintGreen,
                                coralRed = coralRed
                            )
                            if (index < recentItems.size - 1) {
                                HorizontalDivider(
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f),
                                    modifier = Modifier.padding(vertical = 10.dp)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 8. Bottom Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onNavigateToTransactions,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.ReceiptLong,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("All Transactions")
                }

                Button(
                    onClick = onNavigateToReports,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Description,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Audit Statement")
                }
            }

            Spacer(modifier = Modifier.height(40.dp))
        }
    }

    if (showInfoDialog) {
        AlertDialog(
            onDismissRequest = { showInfoDialog = false },
            title = {
                Text(
                    text = "Cash Flow Methodology",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column {
                    Text(
                        text = "• Inflow represents all verified incoming credits from linked bank accounts and M-PESA deposits.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "• Outflow captures all confirmed debits, utility paybills, standing orders, and point-of-sale disbursements.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "• Net Velocity and Runway are evaluated dynamically from real transaction history to project period-end liquidity.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showInfoDialog = false }) {
                    Text("Got it")
                }
            }
        )
    }
}

@Composable
private fun MetricTile(
    label: String,
    value: String,
    helper: String,
    badgeColor: Color,
    modifier: Modifier = Modifier,
    bg: Color
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = bg,
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = helper,
                style = MaterialTheme.typography.labelSmall,
                color = badgeColor,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun CashMovementRow(
    transaction: Transaction,
    isCredit: Boolean,
    isDark: Boolean,
    mintGreen: Color,
    coralRed: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = CircleShape,
            color = (if (isCredit) mintGreen else coralRed).copy(alpha = if (isDark) 0.2f else 0.12f),
            modifier = Modifier.size(38.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = if (isCredit) Icons.Outlined.ArrowDownward else Icons.Outlined.ArrowUpward,
                    contentDescription = null,
                    tint = if (isCredit) mintGreen else coralRed,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = transaction.description ?: (if (isCredit) "Inflow Credit" else "Expense Debit"),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "${transaction.source} • ${transaction.timestamp.take(10)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        val prefix = if (isCredit) "+" else "-"
        Text(
            text = "$prefix${formatKes(transaction.amount)}",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = if (isCredit) mintGreen else coralRed
        )
    }
}

@Composable
private fun CashFlowInteractiveChartCard(
    scope: CashFlowScope,
    transactions: List<Transaction>,
    currentMonth: YearMonth,
    today: LocalDate,
    zoneId: ZoneId,
    isDark: Boolean,
    cardBg: Color
) {
    val greenColor = if (isDark) Color(0xFF4ADE80) else Color(0xFF16A34A)
    val redColor = if (isDark) Color(0xFFFF6B6B) else Color(0xFFEF4444)

    // Pure dynamic calculation of timeline points from the actual transaction list
    val points = remember(scope, transactions, currentMonth, today) {
        when (scope) {
            CashFlowScope.DAY -> {
                val activeDay = if (transactions.any { parseTxDate(it.timestamp, zoneId) == today }) {
                    today
                } else {
                    transactions.mapNotNull { parseTxDate(it.timestamp, zoneId) }.maxOrNull() ?: today
                }
                val dayTxs = transactions.filter { parseTxDate(it.timestamp, zoneId) == activeDay }
                val daySlots = listOf(
                    Triple("12 AM", "00:00 - 04:00", 0..3),
                    Triple("4 AM", "04:00 - 08:00", 4..7),
                    Triple("8 AM", "08:00 - 12:00", 8..11),
                    Triple("12 PM", "12:00 - 16:00", 12..15),
                    Triple("4 PM", "16:00 - 20:00", 16..19),
                    Triple("8 PM", "20:00 - 24:00", 20..23)
                )
                daySlots.map { (slotLabel, slotSub, hourRange) ->
                    val slotTxs = dayTxs.filter { parseTxHour(it.timestamp) in hourRange }
                    val sIn = slotTxs.filter { it.type.equals("CREDIT", ignoreCase = true) }
                        .fold(BigDecimal.ZERO) { acc, t -> acc.add(t.amount.abs()) }
                    val sOut = slotTxs.filter { it.type.equals("DEBIT", ignoreCase = true) || it.type.equals("EXPENSE", ignoreCase = true) }
                        .fold(BigDecimal.ZERO) { acc, t -> acc.add(t.amount.abs()) }
                    CashFlowPoint(
                        label = slotLabel,
                        subLabel = slotSub,
                        moneyIn = sIn,
                        moneyOut = sOut,
                        net = sIn.subtract(sOut)
                    )
                }
            }
            CashFlowScope.WEEK -> {
                val monday = today.with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))
                (0..6).map { i ->
                    val date = monday.plusDays(i.toLong())
                    val dName = date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.US)
                    val dSub = "${date.dayOfMonth} ${date.month.getDisplayName(TextStyle.SHORT, Locale.US)}"
                    val dayTxs = transactions.filter { parseTxDate(it.timestamp, zoneId) == date }
                    val dIn = dayTxs.filter { it.type.equals("CREDIT", ignoreCase = true) }
                        .fold(BigDecimal.ZERO) { acc, t -> acc.add(t.amount.abs()) }
                    val dOut = dayTxs.filter { it.type.equals("DEBIT", ignoreCase = true) || it.type.equals("EXPENSE", ignoreCase = true) }
                        .fold(BigDecimal.ZERO) { acc, t -> acc.add(t.amount.abs()) }
                    CashFlowPoint(
                        label = dName,
                        subLabel = dSub,
                        moneyIn = dIn,
                        moneyOut = dOut,
                        net = dIn.subtract(dOut)
                    )
                }
            }
            CashFlowScope.MONTH -> {
                val daysInMonth = currentMonth.lengthOfMonth()
                val monthNameShort = currentMonth.month.getDisplayName(TextStyle.SHORT, Locale.US)
                val weekRanges = listOf(
                    1 to (1..7),
                    2 to (8..14),
                    3 to (15..21),
                    4 to (22..28),
                    5 to (29..daysInMonth)
                )
                weekRanges.map { (wNum, range) ->
                    val wTxs = transactions.filter { tx ->
                        val date = parseTxDate(tx.timestamp, zoneId)
                        date != null && YearMonth.from(date) == currentMonth && date.dayOfMonth in range
                    }
                    val wIn = wTxs.filter { it.type.equals("CREDIT", ignoreCase = true) }
                        .fold(BigDecimal.ZERO) { acc, t -> acc.add(t.amount.abs()) }
                    val wOut = wTxs.filter { it.type.equals("DEBIT", ignoreCase = true) || it.type.equals("EXPENSE", ignoreCase = true) }
                        .fold(BigDecimal.ZERO) { acc, t -> acc.add(t.amount.abs()) }
                    CashFlowPoint(
                        label = "W$wNum",
                        subLabel = "${range.first}-${range.last} $monthNameShort",
                        moneyIn = wIn,
                        moneyOut = wOut,
                        net = wIn.subtract(wOut)
                    )
                }
            }
            CashFlowScope.QUARTER -> {
                listOf(
                    currentMonth.minusMonths(2),
                    currentMonth.minusMonths(1),
                    currentMonth
                ).map { ym ->
                    val mLabel = ym.month.getDisplayName(TextStyle.SHORT, Locale.US)
                    val mSub = "${ym.year}"
                    val mTxs = transactions.filter { tx ->
                        val d = parseTxDate(tx.timestamp, zoneId)
                        d != null && YearMonth.from(d) == ym
                    }
                    val mIn = mTxs.filter { it.type.equals("CREDIT", ignoreCase = true) }
                        .fold(BigDecimal.ZERO) { acc, t -> acc.add(t.amount.abs()) }
                    val mOut = mTxs.filter { it.type.equals("DEBIT", ignoreCase = true) || it.type.equals("EXPENSE", ignoreCase = true) }
                        .fold(BigDecimal.ZERO) { acc, t -> acc.add(t.amount.abs()) }
                    CashFlowPoint(
                        label = mLabel,
                        subLabel = mSub,
                        moneyIn = mIn,
                        moneyOut = mOut,
                        net = mIn.subtract(mOut)
                    )
                }
            }
        }
    }

    var activeIndex by remember(scope) { mutableStateOf<Int?>(null) }
    val animProgress = remember(scope) { Animatable(0f) }

    LaunchedEffect(scope) {
        animProgress.snapTo(0f)
        animProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing)
        )
    }

    val hasAnyFlowInScope = remember(points) {
        points.any { it.moneyIn > BigDecimal.ZERO || it.moneyOut > BigDecimal.ZERO }
    }

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = cardBg),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Inflow vs Outflow Velocity",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Touch to inspect timeline segments",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Legend
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(greenColor))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("In", style = MaterialTheme.typography.labelSmall, color = greenColor, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.width(10.dp))
                    Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(redColor))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Out", style = MaterialTheme.typography.labelSmall, color = redColor, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            if (!hasAnyFlowInScope) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Outlined.BarChart,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "No Cash Flow Data For This Period",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Inflows and outflows will appear as transactions occur in this timeframe.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                // Active Tooltip Inspector
                val inspectedPoint = activeIndex?.let { points.getOrNull(it) }
                AnimatedVisibility(
                    visible = inspectedPoint != null,
                    enter = fadeIn(tween(150)),
                    exit = fadeOut(tween(150))
                ) {
                    if (inspectedPoint != null) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = "${inspectedPoint.label} (${inspectedPoint.subLabel ?: ""})",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    val net = inspectedPoint.moneyIn.subtract(inspectedPoint.moneyOut)
                                    val netSign = if (net >= BigDecimal.ZERO) "+" else ""
                                    Text(
                                        text = "Net: $netSign${formatKes(net)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (net >= BigDecimal.ZERO) greenColor else redColor,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = "In: +${formatKes(inspectedPoint.moneyIn)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = greenColor
                                    )
                                    Text(
                                        text = "Out: -${formatKes(inspectedPoint.moneyOut)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = redColor
                                    )
                                }
                            }
                        }
                    }
                }

                val maxAmount = points.maxOfOrNull { maxOf(it.moneyIn.toDouble(), it.moneyOut.toDouble()) }
                    ?.takeIf { it > 0 } ?: 1000.0

                val gridColor = if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.06f)

                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .pointerInput(points) {
                            detectTapGestures(
                                onPress = { offset ->
                                    val w = size.width
                                    val count = points.size
                                    if (count > 1) {
                                        val idx = ((offset.x / w) * (count - 1)).roundToInt().coerceIn(0, count - 1)
                                        activeIndex = if (activeIndex == idx) null else idx
                                    }
                                }
                            )
                        }
                        .pointerInput(points) {
                            detectDragGestures(
                                onDrag = { change, _ ->
                                    val w = size.width
                                    val count = points.size
                                    if (count > 1) {
                                        val idx = ((change.position.x / w) * (count - 1)).roundToInt().coerceIn(0, count - 1)
                                        activeIndex = idx
                                    }
                                }
                            )
                        }
                ) {
                    val width = size.width
                    val height = size.height
                    if (width <= 0f || height <= 0f || points.size < 2) return@Canvas

                    val bottomPadding = 24.dp.toPx()
                    val topPadding = 12.dp.toPx()
                    val usableHeight = height - bottomPadding - topPadding
                    val stepX = width / (points.size - 1)

                    // Gridlines
                    drawLine(
                        color = gridColor,
                        start = Offset(0f, height - bottomPadding),
                        end = Offset(width, height - bottomPadding),
                        strokeWidth = 1.dp.toPx()
                    )
                    drawLine(
                        color = gridColor.copy(alpha = 0.4f),
                        start = Offset(0f, topPadding + usableHeight / 2f),
                        end = Offset(width, topPadding + usableHeight / 2f),
                        strokeWidth = 1.dp.toPx()
                    )

                    val inPoints = points.mapIndexed { idx, pt ->
                        val x = idx * stepX
                        val norm = ((pt.moneyIn.toDouble() / maxAmount) * animProgress.value).toFloat().coerceIn(0f, 1f)
                        val y = height - bottomPadding - (norm * usableHeight)
                        Offset(x, y)
                    }

                    val outPoints = points.mapIndexed { idx, pt ->
                        val x = idx * stepX
                        val norm = ((pt.moneyOut.toDouble() / maxAmount) * animProgress.value).toFloat().coerceIn(0f, 1f)
                        val y = height - bottomPadding - (norm * usableHeight)
                        Offset(x, y)
                    }

                    // Inflow Path (Green)
                    val inPath = Path()
                    val inFillPath = Path()
                    inPoints.forEachIndexed { i, pt ->
                        if (i == 0) {
                            inPath.moveTo(pt.x, pt.y)
                            inFillPath.moveTo(pt.x, height - bottomPadding)
                            inFillPath.lineTo(pt.x, pt.y)
                        } else {
                            val prev = inPoints[i - 1]
                            val cx = (prev.x + pt.x) / 2f
                            inPath.cubicTo(cx, prev.y, cx, pt.y, pt.x, pt.y)
                            inFillPath.cubicTo(cx, prev.y, cx, pt.y, pt.x, pt.y)
                        }
                    }
                    inFillPath.lineTo(inPoints.last().x, height - bottomPadding)
                    inFillPath.close()

                    drawPath(
                        path = inFillPath,
                        brush = Brush.verticalGradient(
                            colors = listOf(greenColor.copy(alpha = 0.25f), greenColor.copy(alpha = 0.02f)),
                            startY = topPadding,
                            endY = height - bottomPadding
                        )
                    )

                    drawPath(
                        path = inPath,
                        color = greenColor,
                        style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                    )

                    // Outflow Path (Red)
                    val outPath = Path()
                    val outFillPath = Path()
                    outPoints.forEachIndexed { i, pt ->
                        if (i == 0) {
                            outPath.moveTo(pt.x, pt.y)
                            outFillPath.moveTo(pt.x, height - bottomPadding)
                            outFillPath.lineTo(pt.x, pt.y)
                        } else {
                            val prev = outPoints[i - 1]
                            val cx = (prev.x + pt.x) / 2f
                            outPath.cubicTo(cx, prev.y, cx, pt.y, pt.x, pt.y)
                            outFillPath.cubicTo(cx, prev.y, cx, pt.y, pt.x, pt.y)
                        }
                    }
                    outFillPath.lineTo(outPoints.last().x, height - bottomPadding)
                    outFillPath.close()

                    drawPath(
                        path = outFillPath,
                        brush = Brush.verticalGradient(
                            colors = listOf(redColor.copy(alpha = 0.20f), redColor.copy(alpha = 0.02f)),
                            startY = topPadding,
                            endY = height - bottomPadding
                        )
                    )

                    drawPath(
                        path = outPath,
                        color = redColor,
                        style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                    )

                    // Active touch line and markers
                    activeIndex?.let { idx ->
                        if (idx in inPoints.indices && idx in outPoints.indices) {
                            val x = idx * stepX
                            drawLine(
                                color = (if (isDark) Color.White else Color.Black).copy(alpha = 0.35f),
                                start = Offset(x, topPadding),
                                end = Offset(x, height - bottomPadding),
                                strokeWidth = 1.5.dp.toPx()
                            )
                            drawCircle(color = greenColor, radius = 5.dp.toPx(), center = inPoints[idx])
                            drawCircle(color = redColor, radius = 5.dp.toPx(), center = outPoints[idx])
                        }
                    }
                }

                // Labels Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    points.forEachIndexed { idx, pt ->
                        val isSel = activeIndex == idx
                        Text(
                            text = pt.label,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSel) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CashFlowStreamsSection(
    transactions: List<Transaction>,
    totalInflow: BigDecimal,
    totalOutflow: BigDecimal,
    cardBg: Color,
    cardBgElevated: Color
) {
    var activeTab by remember { mutableStateOf("INFLOW") }

    // 100% Dynamically calculated stream breakdown from real user transactions
    val inflowStreams = remember(transactions, totalInflow) {
        val creditTxs = transactions.filter { it.type.equals("CREDIT", ignoreCase = true) }
        if (creditTxs.isEmpty()) {
            emptyList()
        } else {
            val groups = creditTxs.groupBy { resolveInflowCategory(it) }
            val allInflowTotal = creditTxs.fold(BigDecimal.ZERO) { acc, t -> acc.add(t.amount.abs()) }
            groups.entries.map { (category, txList) ->
                val sum = txList.fold(BigDecimal.ZERO) { acc, t -> acc.add(t.amount.abs()) }
                val pct = if (allInflowTotal > BigDecimal.ZERO) {
                    ((sum.toDouble() / allInflowTotal.toDouble()) * 100).toFloat()
                } else 0f
                val (icon, color) = getInflowCategoryStyle(category)
                CashStreamItem(
                    title = category,
                    subtitle = "${txList.size} ${if (txList.size == 1) "transaction" else "transactions"}",
                    amount = sum,
                    percentage = pct,
                    icon = icon,
                    color = color
                )
            }.sortedByDescending { it.amount }
        }
    }

    val outflowStreams = remember(transactions, totalOutflow) {
        val debitTxs = transactions.filter { it.type.equals("DEBIT", ignoreCase = true) || it.type.equals("EXPENSE", ignoreCase = true) }
        if (debitTxs.isEmpty()) {
            emptyList()
        } else {
            val groups = debitTxs.groupBy { resolveOutflowCategory(it.description) }
            val allOutflowTotal = debitTxs.fold(BigDecimal.ZERO) { acc, t -> acc.add(t.amount.abs()) }
            groups.entries.map { (category, txList) ->
                val sum = txList.fold(BigDecimal.ZERO) { acc, t -> acc.add(t.amount.abs()) }
                val pct = if (allOutflowTotal > BigDecimal.ZERO) {
                    ((sum.toDouble() / allOutflowTotal.toDouble()) * 100).toFloat()
                } else 0f
                val (icon, color) = getOutflowCategoryStyle(category)
                CashStreamItem(
                    title = category,
                    subtitle = "${txList.size} ${if (txList.size == 1) "transaction" else "transactions"}",
                    amount = sum,
                    percentage = pct,
                    icon = icon,
                    color = color
                )
            }.sortedByDescending { it.amount }
        }
    }

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = cardBg),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Cash Flow Streams",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                // Toggle tabs
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(cardBgElevated)
                        .padding(2.dp)
                ) {
                    listOf("INFLOW" to "Inflow", "OUTFLOW" to "Outflow").forEach { (tabKey, label) ->
                        val isSel = activeTab == tabKey
                        val bg = if (isSel) MaterialTheme.colorScheme.surface else Color.Transparent
                        val fg = if (isSel) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(bg)
                                .clickable { activeTab = tabKey }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium,
                                color = fg
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            val currentStreams = if (activeTab == "INFLOW") inflowStreams else outflowStreams

            if (currentStreams.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = if (activeTab == "INFLOW") "No Inflow Streams Recorded Yet" else "No Expense Outflows Recorded Yet",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Categorized transactions will populate this breakdown dynamically.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                currentStreams.forEachIndexed { index, item ->
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = item.color.copy(alpha = 0.15f),
                                modifier = Modifier.size(34.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = item.icon,
                                        contentDescription = null,
                                        tint = item.color,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(10.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = item.subtitle,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = formatKes(item.amount),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "${"%.1f".format(item.percentage)}%",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = item.color
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        LinearProgressIndicator(
                            progress = { (item.percentage / 100f).coerceIn(0f, 1f) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = item.color,
                            trackColor = cardBgElevated
                        )

                        if (index < currentStreams.size - 1) {
                            Spacer(modifier = Modifier.height(14.dp))
                        }
                    }
                }
            }
        }
    }
}

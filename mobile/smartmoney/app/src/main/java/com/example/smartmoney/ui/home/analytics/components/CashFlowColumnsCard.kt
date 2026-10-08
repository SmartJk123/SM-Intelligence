package com.example.smartmoney.ui.home.analytics.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.smartmoney.ui.home.analytics.model.CashFlowPoint
import com.example.smartmoney.ui.home.analytics.model.CashFlowSummary
import com.example.smartmoney.ui.home.analytics.model.CashFlowTimeframe
import com.example.smartmoney.ui.home.analytics.model.WeeklyCashFlow
import com.example.smartmoney.ui.theme.LocalDarkTheme
import com.example.smartmoney.ui.theme.SmartMoneyColors
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import kotlin.math.roundToInt

private fun formatWholeKes(amount: BigDecimal): String {
    val rounded = amount.setScale(0, RoundingMode.HALF_UP)
    return "KES " + DecimalFormat("#,##0").format(rounded)
}

@Composable
fun CashFlowColumnsCard(
    weeklyCashFlows: List<WeeklyCashFlow>,
    totalMoneyIn: BigDecimal,
    totalMoneyOut: BigDecimal,
    netCashFlow: BigDecimal,
    hasTransactions: Boolean,
    dayCashFlowPoints: List<CashFlowPoint> = emptyList(),
    daySummary: CashFlowSummary = CashFlowSummary(),
    weekCashFlowPoints: List<CashFlowPoint> = emptyList(),
    weekSummary: CashFlowSummary = CashFlowSummary(),
    modifier: Modifier = Modifier
) {
    val isDark = LocalDarkTheme.current
    var selectedTimeframe by remember { mutableStateOf(CashFlowTimeframe.MONTH) }
    var selectedWeekIndex by remember { mutableStateOf<Int?>(null) }
    val barAnimProgress = remember(selectedTimeframe) { Animatable(0f) }

    LaunchedEffect(selectedTimeframe, weeklyCashFlows) {
        barAnimProgress.snapTo(0f)
        barAnimProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 650, easing = FastOutSlowInEasing)
        )
    }

    val greenColor = if (isDark) Color(0xFF4ADE80) else Color(0xFF22C55E)
    val redColor = if (isDark) Color(0xFFFF6B6B) else Color(0xFFEF4444)

    val (currentMoneyIn, currentMoneyOut, currentNet) = when (selectedTimeframe) {
        CashFlowTimeframe.DAY -> Triple(daySummary.totalMoneyIn, daySummary.totalMoneyOut, daySummary.netCashFlow)
        CashFlowTimeframe.WEEK -> Triple(weekSummary.totalMoneyIn, weekSummary.totalMoneyOut, weekSummary.netCashFlow)
        CashFlowTimeframe.MONTH -> Triple(totalMoneyIn, totalMoneyOut, netCashFlow)
    }

    val subtitle = when (selectedTimeframe) {
        CashFlowTimeframe.DAY -> "24-hour cash inflows and outflows"
        CashFlowTimeframe.WEEK -> "Daily cash flow this week"
        CashFlowTimeframe.MONTH -> "Weekly cash inflows and outflows"
    }

    ElevatedCard(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = if (isDark) SmartMoneyColors.DarkSurfaceElevated else MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            // Header Row: Title & Subtitle on left, Timeframe Segmented Toggle on right
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Cash flow",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                CashFlowTimeframeToggle(
                    selectedTimeframe = selectedTimeframe,
                    onSelectTimeframe = {
                        selectedTimeframe = it
                        selectedWeekIndex = null
                    }
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Summary Badges Row (Money In, Money Out, Net Flow)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = greenColor.copy(alpha = if (isDark) 0.18f else 0.12f),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)) {
                        Text(
                            text = "Money in",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = formatWholeKes(currentMoneyIn),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = greenColor,
                            maxLines = 1
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = redColor.copy(alpha = if (isDark) 0.18f else 0.12f),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)) {
                        Text(
                            text = "Money out",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = formatWholeKes(currentMoneyOut),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = redColor,
                            maxLines = 1
                        )
                    }
                }

                val netColor = if (currentNet >= BigDecimal.ZERO) greenColor else redColor
                val netPrefix = if (currentNet > BigDecimal.ZERO) "+" else ""
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)) {
                        Text(
                            text = "Net flow",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = netPrefix + formatWholeKes(currentNet),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = netColor,
                            maxLines = 1
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Chart area based on selected timeframe
            if (!hasTransactions) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.BarChart,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "No Cash Flow Data Yet",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Cash inflows and outflows will appear here as transactions occur.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                when (selectedTimeframe) {
                    CashFlowTimeframe.DAY -> {
                        CashFlowDualLineChart(
                            points = dayCashFlowPoints,
                            greenColor = greenColor,
                            redColor = redColor,
                            isDark = isDark
                        )
                    }
                    CashFlowTimeframe.WEEK -> {
                        CashFlowDualLineChart(
                            points = weekCashFlowPoints,
                            greenColor = greenColor,
                            redColor = redColor,
                            isDark = isDark
                        )
                    }
                    CashFlowTimeframe.MONTH -> {
                        // Month: Grouped Bar Chart with Interactive Tooltip
                        val activeWeek = selectedWeekIndex?.let { weeklyCashFlows.getOrNull(it) }
                        AnimatedVisibility(
                            visible = activeWeek != null,
                            enter = fadeIn(tween(180)),
                            exit = fadeOut(tween(180))
                        ) {
                            if (activeWeek != null) {
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
                                                text = "${activeWeek.weekLabel} (${activeWeek.dateRangeLabel})",
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            val netSign = if (activeWeek.net > BigDecimal.ZERO) "+" else ""
                                            Text(
                                                text = "Net: $netSign${formatWholeKes(activeWeek.net)}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = if (activeWeek.net >= BigDecimal.ZERO) greenColor else redColor,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }

                                        Column(horizontalAlignment = Alignment.End) {
                                            Text(
                                                text = "In: +${formatWholeKes(activeWeek.moneyIn)}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = greenColor,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = "Out: -${formatWholeKes(activeWeek.moneyOut)}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = redColor,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        val textMeasurer = rememberTextMeasurer()
                        val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                        val selectedLabelColor = MaterialTheme.colorScheme.onSurface
                        val gridColor = if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.06f)

                        val maxAmount = remember(weeklyCashFlows) {
                            val maxVal = weeklyCashFlows.maxOfOrNull { maxOf(it.moneyIn, it.moneyOut) } ?: BigDecimal.ZERO
                            val d = maxVal.toDouble()
                            if (d <= 0.0) 10000.0 else d * 1.15
                        }

                        Canvas(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(180.dp)
                                .pointerInput(weeklyCashFlows) {
                                    detectTapGestures { offset ->
                                        val w = size.width
                                        val numWeeks = weeklyCashFlows.size
                                        if (numWeeks > 0) {
                                            val slotWidth = w / numWeeks
                                            val tappedIndex = (offset.x / slotWidth).toInt().coerceIn(0, numWeeks - 1)
                                            selectedWeekIndex = if (selectedWeekIndex == tappedIndex) null else tappedIndex
                                        }
                                    }
                                }
                        ) {
                            val width = size.width
                            val height = size.height
                            if (width <= 0f || height <= 0f || weeklyCashFlows.isEmpty()) return@Canvas

                            val bottomPadding = 24.dp.toPx()
                            val chartHeight = height - bottomPadding
                            val numWeeks = weeklyCashFlows.size
                            val slotWidth = width / numWeeks
                            val barWidth = (slotWidth * 0.28f).coerceAtMost(16.dp.toPx())
                            val barSpacing = 4.dp.toPx()
                            val cornerRad = CornerRadius(4.dp.toPx(), 4.dp.toPx())

                            // Baseline
                            drawLine(
                                color = gridColor,
                                start = Offset(0f, chartHeight),
                                end = Offset(width, chartHeight),
                                strokeWidth = 1.dp.toPx()
                            )

                            weeklyCashFlows.forEachIndexed { index, week ->
                                val slotCenterX = (index * slotWidth) + (slotWidth / 2f)
                                val inBarX = slotCenterX - barSpacing / 2f - barWidth
                                val outBarX = slotCenterX + barSpacing / 2f

                                val inFraction = ((week.moneyIn.toDouble() / maxAmount) * barAnimProgress.value).toFloat().coerceIn(0f, 1f)
                                val outFraction = ((week.moneyOut.toDouble() / maxAmount) * barAnimProgress.value).toFloat().coerceIn(0f, 1f)

                                val inBarHeight = (chartHeight * inFraction).coerceAtLeast(if (week.moneyIn > BigDecimal.ZERO) 3.dp.toPx() else 0f)
                                val outBarHeight = (chartHeight * outFraction).coerceAtLeast(if (week.moneyOut > BigDecimal.ZERO) 3.dp.toPx() else 0f)

                                val isSelected = selectedWeekIndex == index

                                // Draw Money In Column
                                val inAlpha = if (selectedWeekIndex == null || isSelected) 1f else 0.4f
                                if (inBarHeight > 0f) {
                                    drawRoundRect(
                                        color = greenColor.copy(alpha = inAlpha),
                                        topLeft = Offset(inBarX, chartHeight - inBarHeight),
                                        size = Size(barWidth, inBarHeight),
                                        cornerRadius = cornerRad
                                    )
                                }

                                // Draw Money Out Column
                                val outAlpha = if (selectedWeekIndex == null || isSelected) 1f else 0.4f
                                if (outBarHeight > 0f) {
                                    drawRoundRect(
                                        color = redColor.copy(alpha = outAlpha),
                                        topLeft = Offset(outBarX, chartHeight - outBarHeight),
                                        size = Size(barWidth, outBarHeight),
                                        cornerRadius = cornerRad
                                    )
                                }

                                // X-axis label
                                val labelText = "W${week.weekNumber}"
                                val textResult = textMeasurer.measure(
                                    text = labelText,
                                    style = TextStyle(
                                        color = if (isSelected) selectedLabelColor else labelColor,
                                        fontSize = 11.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                    )
                                )
                                val labelX = slotCenterX - (textResult.size.width / 2f)
                                val labelY = chartHeight + 6.dp.toPx()
                                drawText(
                                    textLayoutResult = textResult,
                                    topLeft = Offset(labelX, labelY)
                                )
                            }
                        }
                    }
                }
            }

            // Legend Indicator Row at bottom
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(greenColor))
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = "Money in",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(redColor))
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = "Money out",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/**
 * Capsule segmented toggle pill for switching between Day, Week, and Month timeframes.
 */
@Composable
private fun CashFlowTimeframeToggle(
    selectedTimeframe: CashFlowTimeframe,
    onSelectTimeframe: (CashFlowTimeframe) -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = LocalDarkTheme.current
    val containerBg = if (isDark) Color(0xFF1E2421) else Color(0xFFE8ECE9)
    val activeBg = if (isDark) SmartMoneyColors.DarkSlateGreen else Color.White
    val activeTextColor = if (isDark) SmartMoneyColors.PaleMintGreen else SmartMoneyColors.DarkSlateGreen
    val inactiveTextColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = containerBg,
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CashFlowTimeframe.values().forEach { timeframe ->
                val isSelected = timeframe == selectedTimeframe
                Surface(
                    onClick = { onSelectTimeframe(timeframe) },
                    shape = RoundedCornerShape(16.dp),
                    color = if (isSelected) activeBg else Color.Transparent,
                    shadowElevation = if (isSelected) 1.dp else 0.dp
                ) {
                    Text(
                        text = timeframe.label,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSelected) activeTextColor else inactiveTextColor,
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }
}

/**
 * Dual-line graph with touch scrubbing for the Day (intraday) and Week (7-day) cash flow views.
 * Shows Money In (green curve) and Money Out (red curve) with subtle vertical gradients.
 */
@Composable
private fun CashFlowDualLineChart(
    points: List<CashFlowPoint>,
    greenColor: Color,
    redColor: Color,
    isDark: Boolean,
    modifier: Modifier = Modifier
) {
    if (points.isEmpty()) return

    var scrubbedIndex by remember(points) { mutableStateOf<Int?>(null) }
    val animProgress = remember(points) { Animatable(0f) }

    LaunchedEffect(points) {
        animProgress.snapTo(0f)
        animProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing)
        )
    }

    val textMeasurer = rememberTextMeasurer()
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val selectedLabelColor = MaterialTheme.colorScheme.onSurface
    val gridColor = if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.06f)
    val scrubLineColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)

    val maxAmount = remember(points) {
        val maxVal = points.maxOfOrNull { maxOf(it.moneyIn, it.moneyOut) } ?: BigDecimal.ZERO
        val d = maxVal.toDouble()
        if (d <= 0.0) 10000.0 else d * 1.2
    }

    val activePoint = scrubbedIndex?.let { points.getOrNull(it) }

    Column(modifier = modifier.fillMaxWidth()) {
        // Active scrubbed point tooltip
        AnimatedVisibility(
            visible = activePoint != null,
            enter = fadeIn(tween(150)),
            exit = fadeOut(tween(150))
        ) {
            if (activePoint != null) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            val headerTitle = if (activePoint.subLabel != null) {
                                "${activePoint.label} (${activePoint.subLabel})"
                            } else activePoint.label
                            Text(
                                text = headerTitle,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            val netSign = if (activePoint.net > BigDecimal.ZERO) "+" else ""
                            Text(
                                text = "Net: $netSign${formatWholeKes(activePoint.net)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (activePoint.net >= BigDecimal.ZERO) greenColor else redColor,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "In: +${formatWholeKes(activePoint.moneyIn)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = greenColor,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Out: -${formatWholeKes(activePoint.moneyOut)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = redColor,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

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
                                scrubbedIndex = if (scrubbedIndex == idx) null else idx
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
                                scrubbedIndex = idx
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

            // Baseline & Gridlines
            drawLine(
                color = gridColor,
                start = Offset(0f, height - bottomPadding),
                end = Offset(width, height - bottomPadding),
                strokeWidth = 1.dp.toPx()
            )
            drawLine(
                color = gridColor.copy(alpha = 0.5f),
                start = Offset(0f, topPadding + usableHeight / 2f),
                end = Offset(width, topPadding + usableHeight / 2f),
                strokeWidth = 1.dp.toPx()
            )

            // Calculate points Y coordinates
            val inPoints = points.mapIndexed { index, pt ->
                val x = index * stepX
                val norm = ((pt.moneyIn.toDouble() / maxAmount) * animProgress.value).toFloat().coerceIn(0f, 1f)
                val y = height - bottomPadding - (norm * usableHeight)
                Offset(x, y)
            }

            val outPoints = points.mapIndexed { index, pt ->
                val x = index * stepX
                val norm = ((pt.moneyOut.toDouble() / maxAmount) * animProgress.value).toFloat().coerceIn(0f, 1f)
                val y = height - bottomPadding - (norm * usableHeight)
                Offset(x, y)
            }

            // 1. Draw Inflow curve (Green)
            val inPath = Path()
            val inFillPath = Path()
            inPoints.forEachIndexed { i, pt ->
                if (i == 0) {
                    inPath.moveTo(pt.x, pt.y)
                    inFillPath.moveTo(pt.x, height - bottomPadding)
                    inFillPath.lineTo(pt.x, pt.y)
                } else {
                    val prev = inPoints[i - 1]
                    val cx1 = (prev.x + pt.x) / 2f
                    val cx2 = (prev.x + pt.x) / 2f
                    inPath.cubicTo(cx1, prev.y, cx2, pt.y, pt.x, pt.y)
                    inFillPath.cubicTo(cx1, prev.y, cx2, pt.y, pt.x, pt.y)
                }
            }
            inFillPath.lineTo(inPoints.last().x, height - bottomPadding)
            inFillPath.close()

            // Inflow gradient fill
            drawPath(
                path = inFillPath,
                brush = Brush.verticalGradient(
                    colors = listOf(greenColor.copy(alpha = 0.22f), Color.Transparent),
                    startY = topPadding,
                    endY = height - bottomPadding
                )
            )
            // Inflow stroke
            drawPath(
                path = inPath,
                color = greenColor,
                style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
            )

            // 2. Draw Outflow curve (Red)
            val outPath = Path()
            val outFillPath = Path()
            outPoints.forEachIndexed { i, pt ->
                if (i == 0) {
                    outPath.moveTo(pt.x, pt.y)
                    outFillPath.moveTo(pt.x, height - bottomPadding)
                    outFillPath.lineTo(pt.x, pt.y)
                } else {
                    val prev = outPoints[i - 1]
                    val cx1 = (prev.x + pt.x) / 2f
                    val cx2 = (prev.x + pt.x) / 2f
                    outPath.cubicTo(cx1, prev.y, cx2, pt.y, pt.x, pt.y)
                    outFillPath.cubicTo(cx1, prev.y, cx2, pt.y, pt.x, pt.y)
                }
            }
            outFillPath.lineTo(outPoints.last().x, height - bottomPadding)
            outFillPath.close()

            // Outflow gradient fill
            drawPath(
                path = outFillPath,
                brush = Brush.verticalGradient(
                    colors = listOf(redColor.copy(alpha = 0.16f), Color.Transparent),
                    startY = topPadding,
                    endY = height - bottomPadding
                )
            )
            // Outflow stroke
            drawPath(
                path = outPath,
                color = redColor,
                style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
            )

            // 3. Draw anchor points
            points.forEachIndexed { i, _ ->
                val inPt = inPoints[i]
                val outPt = outPoints[i]

                drawCircle(color = Color.White, radius = 4.dp.toPx(), center = inPt)
                drawCircle(color = greenColor, radius = 2.5.dp.toPx(), center = inPt)

                drawCircle(color = Color.White, radius = 4.dp.toPx(), center = outPt)
                drawCircle(color = redColor, radius = 2.5.dp.toPx(), center = outPt)
            }

            // 4. Draw Scrubber Vertical Line if user is scrubbing
            val scrubIdx = scrubbedIndex
            if (scrubIdx != null && scrubIdx in points.indices) {
                val scrubX = scrubIdx * stepX
                drawLine(
                    color = scrubLineColor,
                    start = Offset(scrubX, topPadding),
                    end = Offset(scrubX, height - bottomPadding),
                    strokeWidth = 1.5.dp.toPx()
                )

                // Highlighted dots on both curves
                val inPt = inPoints[scrubIdx]
                val outPt = outPoints[scrubIdx]
                drawCircle(color = greenColor.copy(alpha = 0.35f), radius = 7.dp.toPx(), center = inPt)
                drawCircle(color = Color.White, radius = 4.5.dp.toPx(), center = inPt)
                drawCircle(color = greenColor, radius = 3.dp.toPx(), center = inPt)

                drawCircle(color = redColor.copy(alpha = 0.35f), radius = 7.dp.toPx(), center = outPt)
                drawCircle(color = Color.White, radius = 4.5.dp.toPx(), center = outPt)
                drawCircle(color = redColor, radius = 3.dp.toPx(), center = outPt)
            }

            // 5. Draw X-axis labels
            points.forEachIndexed { index, pt ->
                val x = index * stepX
                val isSelected = scrubbedIndex == index
                val textResult = textMeasurer.measure(
                    text = pt.label,
                    style = TextStyle(
                        color = if (isSelected) selectedLabelColor else labelColor,
                        fontSize = 11.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                    )
                )
                val labelX = (x - (textResult.size.width / 2f)).coerceIn(0f, width - textResult.size.width)
                val labelY = height - bottomPadding + 6.dp.toPx()
                drawText(
                    textLayoutResult = textResult,
                    topLeft = Offset(labelX, labelY)
                )
            }
        }
    }
}

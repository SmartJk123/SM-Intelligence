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
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.smartmoney.ui.home.analytics.model.BudgetPacingPoint
import com.example.smartmoney.ui.home.analytics.model.PacingStatus
import com.example.smartmoney.ui.theme.LocalDarkTheme
import com.example.smartmoney.ui.theme.SmartMoneyColors
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

private fun formatWholeKes(amount: BigDecimal): String {
    val rounded = amount.setScale(0, RoundingMode.HALF_UP)
    return "KES " + DecimalFormat("#,##0").format(rounded)
}

private fun formatCompactKes(value: Double): String {
    return when {
        value >= 1_000_000 -> String.format(Locale.US, "%.1fM", value / 1_000_000)
        value >= 1_000 -> String.format(Locale.US, "%.0fk", value / 1_000)
        else -> String.format(Locale.US, "%.0f", value)
    }
}

@Composable
fun BudgetPacingGraphCard(
    pacingPoints: List<BudgetPacingPoint>,
    monthlyBudgetLimit: BigDecimal,
    budgetSpent: BigDecimal,
    budgetRemaining: BigDecimal,
    pacingStatus: PacingStatus,
    hasBudget: Boolean,
    onOpenBudgetsClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val isDark = LocalDarkTheme.current
    var scrubbedPointIndex by remember { mutableStateOf<Int?>(null) }
    val animProgress = remember(pacingPoints) { Animatable(0f) }

    LaunchedEffect(pacingPoints) {
        scrubbedPointIndex = null
        animProgress.snapTo(0f)
        animProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 750, easing = FastOutSlowInEasing)
        )
    }

    val greenColor = if (isDark) Color(0xFF4ADE80) else Color(0xFF22C55E)
    val amberColor = if (isDark) Color(0xFFFBBF24) else Color(0xFFF59E0B)
    val redColor = if (isDark) Color(0xFFFF6B6B) else Color(0xFFEF4444)

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
            // Header Row: Title & Pacing Status Pill
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Budget pacing",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Cumulative spending versus uniform budget pace",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (hasBudget) {
                    val (statusBg, statusColor) = when (pacingStatus) {
                        PacingStatus.AHEAD_OF_BUDGET -> greenColor.copy(alpha = 0.15f) to greenColor
                        PacingStatus.ON_PACE -> if (isDark) SmartMoneyColors.PaleMintGreen.copy(alpha = 0.15f) to SmartMoneyColors.PaleMintGreen else SmartMoneyColors.DarkSlateGreen.copy(alpha = 0.12f) to SmartMoneyColors.DarkSlateGreen
                        PacingStatus.BEHIND_BUDGET -> redColor.copy(alpha = 0.15f) to redColor
                        PacingStatus.NO_BUDGET -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
                    }

                    Surface(
                        shape = RoundedCornerShape(50),
                        color = statusBg
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(statusColor)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = pacingStatus.displayName,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = statusColor
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            if (!hasBudget) {
                // Empty state when no budget is configured
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.AccountBalanceWallet,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "No Monthly Budget Configured",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Set up category budgets to compare your cumulative spending against a uniform monthly budget pace.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Button(
                        onClick = onOpenBudgetsClick,
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isDark) SmartMoneyColors.PaleMintGreen else SmartMoneyColors.DarkSlateGreen,
                            contentColor = if (isDark) SmartMoneyColors.DarkSlateGreen else Color.White
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Set Up Budgets",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }
            } else {
                // Metrics readout: Actual | Monthly Limit | Remaining / Over budget
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    MetricBox(
                        modifier = Modifier.weight(1f),
                        label = "Actual",
                        value = formatWholeKes(budgetSpent),
                        valueColor = MaterialTheme.colorScheme.onSurface
                    )
                    MetricBox(
                        modifier = Modifier.weight(1f),
                        label = "Monthly limit",
                        value = formatWholeKes(monthlyBudgetLimit),
                        valueColor = if (isDark) SmartMoneyColors.PaleMintGreen else SmartMoneyColors.DarkSlateGreen
                    )
                    val isOverBudget = budgetRemaining < BigDecimal.ZERO
                    MetricBox(
                        modifier = Modifier.weight(1f),
                        label = if (isOverBudget) "Over budget" else "Remaining",
                        value = if (isOverBudget) {
                            "-${formatWholeKes(budgetRemaining.abs())}"
                        } else {
                            formatWholeKes(budgetRemaining)
                        },
                        valueColor = if (isOverBudget) redColor else greenColor
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Interactive Scrubber Detail Card
                val activePoint = scrubbedPointIndex?.let { pacingPoints.getOrNull(it) }
                AnimatedVisibility(
                    visible = activePoint != null,
                    enter = fadeIn(tween(140)),
                    exit = fadeOut(tween(140))
                ) {
                    if (activePoint != null) {
                        val dateStr = activePoint.date.format(DateTimeFormatter.ofPattern("d MMM", Locale.US))
                        val plannedVal = activePoint.plannedCumulative
                        val actualVal = activePoint.actualCumulative

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
                                        text = dateStr,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    if (actualVal != null) {
                                        val diff = actualVal.subtract(plannedVal)
                                        val diffSign = if (diff > BigDecimal.ZERO) "+" else ""
                                        val (diffText, diffColor) = when {
                                            diff > BigDecimal.ZERO -> "Behind pace: $diffSign${formatWholeKes(diff)}" to redColor
                                            diff < BigDecimal.ZERO -> "Ahead of pace: ${formatWholeKes(diff)}" to greenColor
                                            else -> "On pace: KES 0" to greenColor
                                        }
                                        Text(
                                            text = diffText,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = diffColor,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    } else {
                                        Text(
                                            text = "Target pace (Day ${activePoint.dayOfMonth})",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                Column(horizontalAlignment = Alignment.End) {
                                    if (actualVal != null) {
                                        Text(
                                            text = "Actual: ${formatWholeKes(actualVal)}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            fontWeight = FontWeight.Bold
                                        )
                                    } else {
                                        Text(
                                            text = "Actual: --",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Text(
                                        text = "Planned: ${formatWholeKes(plannedVal)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }

                // Cumulative Spending vs Planned Pace Canvas Line Chart
                val chartLineColor = if (isDark) SmartMoneyColors.PaleMintGreen else SmartMoneyColors.DarkSlateGreen
                val plannedGuideColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                val gridLineColor = if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.06f)
                val scrubLineColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                val axisLabelColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)

                val maxGraphVal = remember(monthlyBudgetLimit, pacingPoints) {
                    val maxActual = pacingPoints.mapNotNull { it.actualCumulative }.maxOrNull() ?: BigDecimal.ZERO
                    val mx = maxOf(maxActual.toDouble(), monthlyBudgetLimit.toDouble())
                    if (mx <= 0.0) 60000.0 else mx * 1.15
                }

                val textMeasurer = rememberTextMeasurer()

                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(190.dp)
                        .pointerInput(pacingPoints) {
                            detectTapGestures(
                                onPress = { offset ->
                                    val leftPad = 42.dp.toPx()
                                    val rightPad = 12.dp.toPx()
                                    val chartW = size.width - leftPad - rightPad
                                    val count = pacingPoints.size
                                    if (count > 1 && chartW > 0f) {
                                        val relX = (offset.x - leftPad).coerceIn(0f, chartW)
                                        val idx = ((relX / chartW) * (count - 1)).roundToInt().coerceIn(0, count - 1)
                                        scrubbedPointIndex = if (scrubbedPointIndex == idx) null else idx
                                    }
                                }
                            )
                        }
                        .pointerInput(pacingPoints) {
                            detectDragGestures(
                                onDrag = { change, _ ->
                                    val leftPad = 42.dp.toPx()
                                    val rightPad = 12.dp.toPx()
                                    val chartW = size.width - leftPad - rightPad
                                    val count = pacingPoints.size
                                    if (count > 1 && chartW > 0f) {
                                        val relX = (change.position.x - leftPad).coerceIn(0f, chartW)
                                        val idx = ((relX / chartW) * (count - 1)).roundToInt().coerceIn(0, count - 1)
                                        scrubbedPointIndex = idx
                                    }
                                }
                            )
                        }
                ) {
                    val width = size.width
                    val height = size.height
                    if (width <= 0f || height <= 0f || pacingPoints.size < 2) return@Canvas

                    val leftPadding = 42.dp.toPx()
                    val rightPadding = 12.dp.toPx()
                    val topPadding = 14.dp.toPx()
                    val bottomPadding = 24.dp.toPx()

                    val chartWidth = width - leftPadding - rightPadding
                    val usableHeight = height - topPadding - bottomPadding
                    val baselineY = height - bottomPadding
                    val numDays = pacingPoints.size
                    val stepX = chartWidth / (numDays - 1)

                    // 1. Draw Horizontal Gridlines & Y-Axis Labels
                    val yDivisions = 3
                    for (i in 0..yDivisions) {
                        val fraction = i.toFloat() / yDivisions
                        val y = baselineY - (fraction * usableHeight)
                        val valueAtY = maxGraphVal * fraction

                        drawLine(
                            color = gridLineColor,
                            start = Offset(leftPadding, y),
                            end = Offset(width - rightPadding, y),
                            strokeWidth = 1.dp.toPx(),
                            pathEffect = if (i > 0) PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f) else null
                        )

                        val labelStr = formatCompactKes(valueAtY)
                        val textResult = textMeasurer.measure(
                            text = labelStr,
                            style = TextStyle(
                                color = axisLabelColor,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Medium
                            )
                        )
                        val textX = leftPadding - textResult.size.width - 6.dp.toPx()
                        val textY = (y - textResult.size.height / 2f).coerceIn(0f, height - textResult.size.height)
                        drawText(
                            textLayoutResult = textResult,
                            topLeft = Offset(textX, textY)
                        )
                    }

                    // 2. Draw Planned Uniform Pace Guide Line (Dashed)
                    val plannedPath = Path()
                    val dashedEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f), 0f)

                    pacingPoints.forEachIndexed { index, pt ->
                        val x = leftPadding + index * stepX
                        val normY = (pt.plannedCumulative.toDouble() / maxGraphVal).toFloat().coerceIn(0f, 1f)
                        val y = baselineY - (normY * usableHeight)
                        if (index == 0) plannedPath.moveTo(x, y) else plannedPath.lineTo(x, y)
                    }

                    drawPath(
                        path = plannedPath,
                        color = plannedGuideColor,
                        style = Stroke(width = 2.dp.toPx(), pathEffect = dashedEffect)
                    )

                    // 3. Draw Actual Cumulative Spending Curve with gradient underfill
                    val actualPoints = pacingPoints.filter { it.actualCumulative != null }
                    if (actualPoints.isNotEmpty()) {
                        val actualPath = Path()
                        val actualFillPath = Path()

                        val firstPt = actualPoints.first()
                        val firstX = leftPadding + (firstPt.dayOfMonth - 1) * stepX
                        val firstNormY = ((firstPt.actualCumulative!!.toDouble() * animProgress.value) / maxGraphVal).toFloat().coerceIn(0f, 1f)
                        val firstY = baselineY - (firstNormY * usableHeight)

                        actualPath.moveTo(firstX, firstY)
                        actualFillPath.moveTo(firstX, baselineY)
                        actualFillPath.lineTo(firstX, firstY)

                        var prevX = firstX
                        var prevY = firstY

                        for (i in 1 until actualPoints.size) {
                            val pt = actualPoints[i]
                            val x = leftPadding + (pt.dayOfMonth - 1) * stepX
                            val actualVal = pt.actualCumulative!!.toDouble() * animProgress.value
                            val normY = (actualVal / maxGraphVal).toFloat().coerceIn(0f, 1f)
                            val y = baselineY - (normY * usableHeight)

                            val cx = prevX + (x - prevX) / 2f
                            actualPath.cubicTo(cx, prevY, cx, y, x, y)
                            actualFillPath.cubicTo(cx, prevY, cx, y, x, y)

                            prevX = x
                            prevY = y
                        }

                        actualFillPath.lineTo(prevX, baselineY)
                        actualFillPath.close()

                        // Gradient underfill
                        drawPath(
                            path = actualFillPath,
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    chartLineColor.copy(alpha = 0.22f),
                                    chartLineColor.copy(alpha = 0.02f),
                                    Color.Transparent
                                ),
                                startY = topPadding,
                                endY = baselineY
                            )
                        )

                        // Solid stroke line
                        drawPath(
                            path = actualPath,
                            color = chartLineColor,
                            style = Stroke(
                                width = 3.dp.toPx(),
                                cap = StrokeCap.Round,
                                join = StrokeJoin.Round
                            )
                        )

                        // Prominent current progress pulse marker on last evaluated day
                        val lastPt = actualPoints.last()
                        val lastX = leftPadding + (lastPt.dayOfMonth - 1) * stepX
                        val lastActual = lastPt.actualCumulative!!.toDouble() * animProgress.value
                        val lastNormY = (lastActual / maxGraphVal).toFloat().coerceIn(0f, 1f)
                        val lastY = baselineY - (lastNormY * usableHeight)

                        drawCircle(
                            color = chartLineColor.copy(alpha = 0.28f),
                            radius = 8.dp.toPx(),
                            center = Offset(lastX, lastY)
                        )
                        drawCircle(
                            color = Color.White,
                            radius = 5.dp.toPx(),
                            center = Offset(lastX, lastY)
                        )
                        drawCircle(
                            color = chartLineColor,
                            radius = 3.dp.toPx(),
                            center = Offset(lastX, lastY)
                        )
                    }

                    // 4. Draw X-Axis Day Labels
                    val keyDays = listOf(1, 5, 10, 15, 20, 25, numDays).distinct().filter { it in 1..numDays }
                    keyDays.forEach { dayNum ->
                        val x = leftPadding + (dayNum - 1) * stepX
                        val textResult = textMeasurer.measure(
                            text = "$dayNum",
                            style = TextStyle(
                                color = axisLabelColor,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium
                            )
                        )
                        val textX = (x - (textResult.size.width / 2f)).coerceIn(leftPadding, width - textResult.size.width)
                        val textY = baselineY + 6.dp.toPx()
                        drawText(
                            textLayoutResult = textResult,
                            topLeft = Offset(textX, textY)
                        )
                    }

                    // 5. Draw Interactive Scrubber Guideline & Intersection Dots
                    val scrubIdx = scrubbedPointIndex
                    if (scrubIdx != null && scrubIdx in pacingPoints.indices) {
                        val scrubX = leftPadding + scrubIdx * stepX
                        drawLine(
                            color = scrubLineColor,
                            start = Offset(scrubX, topPadding),
                            end = Offset(scrubX, baselineY),
                            strokeWidth = 1.5.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 4f), 0f)
                        )

                        val pt = pacingPoints[scrubIdx]

                        // Scrub dot on Planned pace line
                        val planNormY = (pt.plannedCumulative.toDouble() / maxGraphVal).toFloat().coerceIn(0f, 1f)
                        val planY = baselineY - (planNormY * usableHeight)
                        drawCircle(color = Color.White, radius = 4.dp.toPx(), center = Offset(scrubX, planY))
                        drawCircle(color = plannedGuideColor, radius = 2.5.dp.toPx(), center = Offset(scrubX, planY))

                        // Scrub dot on Actual spending line (if day reached)
                        if (pt.actualCumulative != null) {
                            val actNormY = (pt.actualCumulative.toDouble() / maxGraphVal).toFloat().coerceIn(0f, 1f)
                            val actY = baselineY - (actNormY * usableHeight)
                            drawCircle(color = Color.White, radius = 5.5.dp.toPx(), center = Offset(scrubX, actY))
                            drawCircle(color = chartLineColor, radius = 3.5.dp.toPx(), center = Offset(scrubX, actY))
                        }
                    }
                }

                // Legend row below chart
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(12.dp, 3.dp).background(chartLineColor))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Actual spending",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.width(18.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(12.dp, 2.dp).background(plannedGuideColor))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Planned pace",
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
private fun MetricBox(
    modifier: Modifier = Modifier,
    label: String,
    value: String,
    valueColor: Color
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    ) {
        Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium,
                fontSize = 11.sp
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = valueColor,
                maxLines = 1
            )
        }
    }
}

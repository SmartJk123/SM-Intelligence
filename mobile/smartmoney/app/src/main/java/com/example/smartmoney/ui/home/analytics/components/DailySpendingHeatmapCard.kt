package com.example.smartmoney.ui.home.analytics.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarViewMonth
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.smartmoney.ui.home.analytics.model.DailySpending
import com.example.smartmoney.ui.theme.LocalDarkTheme
import com.example.smartmoney.ui.theme.SmartMoneyColors
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

private fun formatWholeKes(amount: BigDecimal): String {
    val rounded = amount.setScale(0, RoundingMode.HALF_UP)
    return "KES " + DecimalFormat("#,##0").format(rounded)
}

@Composable
fun DailySpendingHeatmapCard(
    month: YearMonth,
    dailySpendings: Map<Int, DailySpending>,
    highestSpendingDay: DailySpending?,
    hasTransactions: Boolean,
    modifier: Modifier = Modifier
) {
    val isDark = LocalDarkTheme.current
    var selectedDayOfMonth by remember { mutableStateOf<Int?>(null) }

    val daysInMonth = month.lengthOfMonth()
    val firstDayOffset = month.atDay(1).dayOfWeek.value - 1 // 0 for Mon, 6 for Sun

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
            // Header
            Text(
                text = "Daily spending",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "Spending intensity across the month",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Highest Spending Day Callout
            if (highestSpendingDay != null && highestSpendingDay.amount > BigDecimal.ZERO) {
                Spacer(modifier = Modifier.height(14.dp))
                val dayFormatter = DateTimeFormatter.ofPattern("d MMM", Locale.US)
                val dayStr = highestSpendingDay.date.format(dayFormatter)

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (isDark) SmartMoneyColors.PaleMintGreen.copy(alpha = 0.15f) else SmartMoneyColors.PaleMintGreen.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.EmojiEvents,
                            contentDescription = null,
                            tint = if (isDark) SmartMoneyColors.PaleMintGreen else SmartMoneyColors.DarkSlateGreen,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "$dayStr · ${formatWholeKes(highestSpendingDay.amount)} · Highest spending day",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (isDark) SmartMoneyColors.PaleMintGreen else SmartMoneyColors.DarkSlateGreen
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Calendar Heatmap Grid
            val dayHeaders = listOf("M", "T", "W", "T", "F", "S", "S")

            // Day Headers Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                dayHeaders.forEach { header ->
                    Text(
                        text = header,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Calendar Days Grid (Rows of 7)
            val totalSlots = firstDayOffset + daysInMonth
            val numRows = (totalSlots + 6) / 7

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                for (row in 0 until numRows) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        for (col in 0..6) {
                            val slotIndex = (row * 7) + col
                            val dayNumber = slotIndex - firstDayOffset + 1

                            if (dayNumber in 1..daysInMonth) {
                                val spending = dailySpendings[dayNumber]
                                val intensity = spending?.intensityLevel ?: 0
                                val isSelected = selectedDayOfMonth == dayNumber

                                val cellColor = when (intensity) {
                                    1 -> if (isDark) Color(0xFF1B382B) else Color(0xFFD1FAE5)
                                    2 -> if (isDark) Color(0xFF059669) else Color(0xFF34D399)
                                    3 -> if (isDark) SmartMoneyColors.DarkSlateGreen else Color(0xFF047857)
                                    else -> if (isDark) Color.White.copy(alpha = 0.05f) else Color.Black.copy(alpha = 0.04f)
                                }

                                val textColor = when (intensity) {
                                    3 -> Color.White
                                    2 -> if (isDark) Color.White else Color(0xFF065F46)
                                    1 -> if (isDark) SmartMoneyColors.PaleMintGreen else Color(0xFF065F46)
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                }

                                val borderModifier = if (isSelected) {
                                    Modifier.border(
                                        width = 2.dp,
                                        color = if (isDark) Color.White else SmartMoneyColors.DarkSlateGreen,
                                        shape = RoundedCornerShape(6.dp)
                                    )
                                } else Modifier

                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .aspectRatio(1f)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(cellColor)
                                        .then(borderModifier)
                                        .clickable {
                                            selectedDayOfMonth = if (selectedDayOfMonth == dayNumber) null else dayNumber
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "$dayNumber",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontSize = 11.sp,
                                        fontWeight = if (intensity > 0 || isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = textColor
                                    )
                                }
                            } else {
                                // Empty placeholder for offset days
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .aspectRatio(1f)
                                )
                            }
                        }
                    }
                }
            }

            // Interactive Day Detail Tooltip Card
            val selectedSpending = selectedDayOfMonth?.let { dailySpendings[it] }
            AnimatedVisibility(
                visible = selectedSpending != null,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                if (selectedSpending != null) {
                    Spacer(modifier = Modifier.height(14.dp))
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            val dayFormatter = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.US)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = selectedSpending.date.format(dayFormatter),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = formatWholeKes(selectedSpending.amount),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = if (selectedSpending.amount > BigDecimal.ZERO) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Text(
                                text = "${selectedSpending.transactionCount} transaction${if (selectedSpending.transactionCount == 1) "" else "s"}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            if (selectedSpending.categoryBreakdown.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                selectedSpending.categoryBreakdown.forEach { (cat, amt) ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 2.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = cat,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = formatWholeKes(amt),
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Intensity Legend Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Intensity",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    IntensityKey(
                        label = "0",
                        color = if (isDark) Color.White.copy(alpha = 0.05f) else Color.Black.copy(alpha = 0.04f)
                    )
                    IntensityKey(
                        label = "1–999",
                        color = if (isDark) Color(0xFF1B382B) else Color(0xFFD1FAE5)
                    )
                    IntensityKey(
                        label = "1k–2.5k",
                        color = if (isDark) Color(0xFF059669) else Color(0xFF34D399)
                    )
                    IntensityKey(
                        label = "2.5k+",
                        color = if (isDark) SmartMoneyColors.DarkSlateGreen else Color(0xFF047857)
                    )
                }
            }
        }
    }
}

@Composable
private fun IntensityKey(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(color)
        )
        Spacer(modifier = Modifier.width(3.dp))
        Text(
            text = label,
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Medium
        )
    }
}

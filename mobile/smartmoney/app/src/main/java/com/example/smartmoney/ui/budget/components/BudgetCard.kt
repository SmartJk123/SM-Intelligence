package com.example.smartmoney.ui.budget.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.smartmoney.core.util.CurrencyUtils
import com.example.smartmoney.domain.model.BudgetStatus
import com.example.smartmoney.domain.model.BudgetSummary
import com.example.smartmoney.ui.theme.LocalDarkTheme
import com.example.smartmoney.ui.theme.SmartMoneyColors
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun BudgetCard(
    summary: BudgetSummary,
    accountName: String? = null,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = LocalDarkTheme.current
    val budget = summary.budget

    val cardBg = if (isDark) SmartMoneyColors.DarkSurfaceElevated else Color.White
    val textPrimary = if (isDark) SmartMoneyColors.DarkTextPrimary else SmartMoneyColors.TextPrimary
    val textMuted = if (isDark) SmartMoneyColors.DarkInactive.copy(alpha = 0.8f) else SmartMoneyColors.TextMuted.copy(alpha = 0.8f)
    val cardBorder = if (isDark) SmartMoneyColors.DarkBorderLine else SmartMoneyColors.BorderLine.copy(alpha = 0.7f)

    val visual = BudgetCategoryVisuals.getVisual(budget.category)

    val (statusLabel, statusBg, statusFg) = when (summary.status) {
        BudgetStatus.OVER_BUDGET -> Triple(
            "Over Budget",
            if (isDark) Color(0xFF4A1818) else SmartMoneyColors.LightSalmon.copy(alpha = 0.5f),
            if (isDark) Color(0xFFFF6B6B) else Color(0xFFC53030)
        )
        BudgetStatus.APPROACHING_LIMIT -> Triple(
            "Near Limit (${budget.threshold}%)",
            if (isDark) Color(0xFF423512) else SmartMoneyColors.LightPeach,
            if (isDark) Color(0xFFFDE047) else Color(0xFFB45309)
        )
        BudgetStatus.WITHIN_BUDGET -> Triple(
            "On Track",
            if (isDark) Color(0xFF133629) else SmartMoneyColors.PaleMintGreen,
            if (isDark) Color(0xFF4ADE80) else SmartMoneyColors.DarkSlateGreen
        )
    }

    val progressFraction = (summary.percentUsed / 100f).coerceIn(0f, 1f)
    val animatedProgress by animateFloatAsState(
        targetValue = progressFraction,
        animationSpec = tween(durationMillis = 650, easing = FastOutSlowInEasing),
        label = "budget_progress"
    )

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        border = BorderStroke(1.dp, cardBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp)
        ) {
            // =========================================================================
            // 1. HEADER ROW: Category Icon Badge + Title/Subtitle + Status & Actions
            // =========================================================================
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    // Category Icon Container
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(visual.currentBg()),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = visual.icon,
                            contentDescription = budget.category,
                            tint = visual.currentTint(),
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Text(
                            text = budget.category,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        val accountLabel = if (!budget.accountId.isNullOrBlank()) {
                            accountName ?: "Specific Account"
                        } else {
                            "All Connected Accounts"
                        }
                        Text(
                            text = accountLabel,
                            fontSize = 12.sp,
                            color = textMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Status Badge & Action Buttons
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Status Pill
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = statusBg
                    ) {
                        Text(
                            text = statusLabel,
                            color = statusFg,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                        )
                    }

                    // Edit Icon Button
                    IconButton(
                        onClick = onEdit,
                        modifier = Modifier.size(30.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Edit,
                            contentDescription = "Edit budget",
                            tint = if (isDark) SmartMoneyColors.PaleMintGreen else SmartMoneyColors.DarkSlateGreen,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    // Delete Icon Button
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(30.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Delete,
                            contentDescription = "Delete budget",
                            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.85f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // =========================================================================
            // 2. FINANCIAL STATS ROW: Spent Amount vs Budget Limit
            // =========================================================================
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Column {
                    Text(
                        text = "Spent (${summary.percentUsed.toInt()}%)",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = textMuted
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = CurrencyUtils.formatMinor(summary.spentMinor),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (summary.status == BudgetStatus.OVER_BUDGET) statusFg else textPrimary
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "Budget Limit",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = textMuted
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = CurrencyUtils.formatMinor(budget.allocatedMinor),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = textPrimary
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // =========================================================================
            // 3. PRECISION PROGRESS GAUGE WITH ALERT THRESHOLD NOTCH
            // =========================================================================
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(if (isDark) SmartMoneyColors.DarkSurface else Color(0xFFE2E8F0))
            ) {
                val totalWidth = maxWidth
                val progressColor = when (summary.status) {
                    BudgetStatus.OVER_BUDGET -> Color(0xFFEF4444)
                    BudgetStatus.APPROACHING_LIMIT -> Color(0xFFF59E0B)
                    BudgetStatus.WITHIN_BUDGET -> Color(0xFF10B981)
                }

                // Active Progress Fill
                Box(
                    modifier = Modifier
                        .fillMaxWidth(animatedProgress)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(5.dp))
                        .background(progressColor)
                )

                // Alert Threshold Marker Pin
                val thresholdFraction = (budget.threshold / 100f).coerceIn(0.1f, 0.98f)
                val notchOffsetX = totalWidth * thresholdFraction

                Box(
                    modifier = Modifier
                        .offset(x = notchOffsetX - 1.dp)
                        .width(2.dp)
                        .fillMaxHeight()
                        .background(if (isDark) Color.White.copy(alpha = 0.75f) else SmartMoneyColors.DarkSlateGreen.copy(alpha = 0.75f))
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // =========================================================================
            // 4. FOOTER ROW: Safe-to-Spend Balance & Cycle Timeline
            // =========================================================================
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Remaining or Overspent label
                if (summary.status == BudgetStatus.OVER_BUDGET) {
                    val overspentMinor = (summary.spentMinor - budget.allocatedMinor).coerceAtLeast(0L)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Outlined.ErrorOutline,
                            contentDescription = null,
                            tint = Color(0xFFEF4444),
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Exceeded by ${CurrencyUtils.formatMinor(overspentMinor)}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFEF4444)
                        )
                    }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(if (summary.status == BudgetStatus.APPROACHING_LIMIT) Color(0xFFF59E0B) else Color(0xFF10B981))
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = "${CurrencyUtils.formatMinor(summary.remainingMinor)} remaining",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = textPrimary
                        )
                    }
                }

                // Date Cycle Timeline
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.CalendarToday,
                        contentDescription = null,
                        tint = textMuted,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    val dateFormatter = DateTimeFormatter.ofPattern("MMM d", Locale.getDefault())
                    val dateSpan = "${budget.start.format(dateFormatter)} – ${budget.end.format(dateFormatter)}"
                    Text(
                        text = dateSpan,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = textMuted
                    )
                }
            }
        }
    }
}

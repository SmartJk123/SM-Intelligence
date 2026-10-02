package com.example.smartmoney.ui.investment.components

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.smartmoney.core.util.CurrencyUtils
import com.example.smartmoney.domain.model.Investment
import com.example.smartmoney.domain.model.InvestmentType
import com.example.smartmoney.ui.theme.LocalDarkTheme
import com.example.smartmoney.ui.theme.SmartMoneyColors
import java.util.Locale

@Composable
fun InvestmentCard(
    investment: Investment,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = LocalDarkTheme.current

    val cardBg = if (isDark) SmartMoneyColors.DarkSurface else Color.White
    val textPrimary = if (isDark) SmartMoneyColors.DarkTextPrimary else SmartMoneyColors.TextPrimary
    val textMuted = if (isDark) SmartMoneyColors.DarkInactive else SmartMoneyColors.TextMuted

    val (typeBg, typeFg) = when (investment.type) {
        InvestmentType.MONEY_MARKET -> if (isDark) Pair(Color(0xFF1E3A5F), Color(0xFF93C5FD)) else Pair(SmartMoneyColors.PowderBlue.copy(alpha = 0.35f), SmartMoneyColors.DarkSlateGreen)
        InvestmentType.TREASURY_BILL -> if (isDark) Pair(Color(0xFF133629), Color(0xFF86EFAC)) else Pair(SmartMoneyColors.PaleMintGreen, SmartMoneyColors.DarkSlateGreen)
        InvestmentType.FIXED_DEPOSIT -> if (isDark) Pair(Color(0xFF3B1E54), Color(0xFFD8B4FE)) else Pair(SmartMoneyColors.LightPeach, SmartMoneyColors.DarkSlateGreen)
        InvestmentType.OTHER -> if (isDark) Pair(Color(0xFF334155), Color(0xFFCBD5E1)) else Pair(SmartMoneyColors.PaleSageGreen.copy(alpha = 0.5f), SmartMoneyColors.DarkSlateGreen)
    }

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            // Header Row: Name & Instrument Type Pill
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = investment.name,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = textPrimary,
                    modifier = Modifier.weight(1f).padding(end = 8.dp)
                )

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(typeBg)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = investment.type.displayName,
                        color = typeFg,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Values Grid: Principal vs Current Valuation
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    Text("Principal", fontSize = 11.sp, color = textMuted)
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = CurrencyUtils.formatMinor(investment.principalMinor),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp,
                        color = textPrimary
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text("Current Valuation", fontSize = 11.sp, color = textMuted)
                    Spacer(modifier = Modifier.height(2.dp))
                    if (investment.currentValueMinor != null) {
                        Text(
                            text = CurrencyUtils.formatMinor(investment.currentValueMinor),
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = textPrimary
                        )
                        Text(
                            text = "As of ${investment.valuationDate}",
                            fontSize = 10.sp,
                            color = textMuted
                        )
                    } else {
                        Text(
                            text = "Valuation pending",
                            fontWeight = FontWeight.Medium,
                            fontSize = 13.sp,
                            color = if (isDark) Color(0xFFFDE047) else Color(0xFF854D0E)
                        )
                    }
                }
            }

            // Return / Gain or Loss Pill (if valuation exists)
            val gainLossMajor = investment.gainOrLossMajor
            val gainLossMinor = investment.gainOrLossMinor
            if (gainLossMajor != null && gainLossMinor != null) {
                Spacer(modifier = Modifier.height(10.dp))
                val isPositive = gainLossMajor >= 0
                val percentGain = if (investment.principalMajor > 0) {
                    (gainLossMajor / investment.principalMajor) * 100.0
                } else 0.0

                val sign = if (isPositive) "+" else ""
                val gainText = "$sign${CurrencyUtils.formatMinor(gainLossMinor)} ($sign${String.format(Locale.US, "%.1f", percentGain)}%)"
                val gainColor = if (isPositive) Color(0xFF10B981) else Color(0xFFEF4444)

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Return: ", fontSize = 11.sp, color = textMuted)
                    Text(
                        text = gainText,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = gainColor
                    )
                }
            }

            // Maturity Notification Pill (if within 90 days)
            if (investment.maturityDate != null) {
                Spacer(modifier = Modifier.height(12.dp))
                val isMaturingSoon = investment.isMaturingSoon
                val isMatured = investment.isMatured
                val pillBg = when {
                    isMatured -> if (isDark) Color(0xFF3B1E1E) else Color(0xFFFDE8E8)
                    isMaturingSoon -> if (isDark) Color(0xFF423512) else Color(0xFFFEF08A)
                    else -> if (isDark) SmartMoneyColors.DarkBorderLine else Color(0xFFF1F5F9)
                }
                val pillFg = when {
                    isMatured -> if (isDark) Color(0xFFFF6B6B) else Color(0xFFE02424)
                    isMaturingSoon -> if (isDark) Color(0xFFFDE047) else Color(0xFF854D0E)
                    else -> textMuted
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(pillBg)
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.AccessTime,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = pillFg
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = when {
                            isMatured -> "Matured on ${investment.maturityDate}"
                            isMaturingSoon -> "Matures on ${investment.maturityDate} (${investment.daysUntilMaturity} days left)"
                            else -> "Maturity date: ${investment.maturityDate}"
                        },
                        fontSize = 11.sp,
                        fontWeight = if (isMaturingSoon || isMatured) FontWeight.SemiBold else FontWeight.Normal,
                        color = pillFg
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(
                color = if (isDark) SmartMoneyColors.DarkBorderLine else SmartMoneyColors.BorderLine
            )
            Spacer(modifier = Modifier.height(6.dp))

            // Action Buttons
            Row(
                horizontalArrangement = Arrangement.End,
                modifier = Modifier.fillMaxWidth()
            ) {
                TextButton(onClick = onEdit) {
                    Icon(
                        imageVector = Icons.Outlined.Edit,
                        contentDescription = "Edit investment",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Edit", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                }
                Spacer(modifier = Modifier.width(8.dp))
                TextButton(
                    onClick = onDelete,
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Delete,
                        contentDescription = "Delete investment",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Delete", fontSize = 12.sp)
                }
            }
        }
    }
}

package com.example.smartmoney.ui.investment.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.smartmoney.core.util.CurrencyUtils
import com.example.smartmoney.ui.theme.LocalDarkTheme
import com.example.smartmoney.ui.theme.SmartMoneyColors

@Composable
fun InvestmentSummaryHeader(
    totalPrincipalMinor: Long,
    portfolioValuationMinor: Long?,
    holdingsCount: Int,
    maturingSoonCount: Int,
    modifier: Modifier = Modifier
) {
    val isDark = LocalDarkTheme.current

    val cardBg = if (isDark) SmartMoneyColors.DarkSurface else SmartMoneyColors.SlateBackground
    val textPrimary = if (isDark) SmartMoneyColors.DarkTextPrimary else SmartMoneyColors.TextPrimary
    val textMuted = if (isDark) SmartMoneyColors.DarkInactive else SmartMoneyColors.TextMuted

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            // Principal Card
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = cardBg),
                modifier = Modifier.weight(1f)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("Total Principal", fontSize = 11.sp, color = textMuted)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = CurrencyUtils.formatMinor(totalPrincipalMinor),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = textPrimary
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text("$holdingsCount tracked holdings", fontSize = 10.sp, color = textMuted)
                }
            }

            // Portfolio Valuation Card (Rules from blueprint: null if any holding is unrecorded)
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = cardBg),
                modifier = Modifier.weight(1f)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("Portfolio Valuation", fontSize = 11.sp, color = textMuted)
                    Spacer(modifier = Modifier.height(4.dp))
                    if (portfolioValuationMinor != null) {
                        Text(
                            text = CurrencyUtils.formatMinor(portfolioValuationMinor),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text("All valuations current", fontSize = 10.sp, color = Color(0xFF10B981))
                    } else {
                        Text(
                            text = "Unavailable",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isDark) Color(0xFFFDE047) else Color(0xFF854D0E)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text("Holding valuation pending", fontSize = 10.sp, color = if (isDark) Color(0xFFFDE047) else Color(0xFF854D0E))
                    }
                }
            }
        }

        // 90-Day Maturity Alert Banner
        if (maturingSoonCount > 0) {
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (isDark) Color(0xFF423512) else Color(0xFFFEF08A).copy(alpha = 0.8f)
                    )
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.WarningAmber,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = if (isDark) Color(0xFFFDE047) else Color(0xFF854D0E)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "Upcoming Maturity Alert",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isDark) Color(0xFFFDE047) else Color(0xFF854D0E)
                    )
                    Text(
                        text = "$maturingSoonCount investment holding${if (maturingSoonCount > 1) "s mature" else " matures"} within the next 90 days.",
                        fontSize = 11.sp,
                        color = if (isDark) Color(0xFFE2E8F0) else Color(0xFF713F12)
                    )
                }
            }
        }
    }
}

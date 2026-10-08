package com.example.smartmoney.ui.raha.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material.icons.outlined.WorkOutline
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
import com.example.smartmoney.domain.model.RahaAction
import com.example.smartmoney.ui.theme.LocalDarkTheme
import com.example.smartmoney.ui.theme.SmartMoneyColors

@Composable
fun RahaActionCard(
    action: RahaAction,
    onActionClick: (RahaAction) -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = LocalDarkTheme.current
    val cardBg = if (isDark) Color(0xFF1E2620) else Color(0xFFF1F6F3)
    val borderColor = if (isDark) Color(0xFF3B483D) else SmartMoneyColors.BorderLine

    val icon = when (action.targetRoute.lowercase()) {
        "accounts" -> Icons.Outlined.AccountBalance
        "budgets" -> Icons.Outlined.Savings
        "invoice" -> Icons.AutoMirrored.Outlined.ReceiptLong
        "investments" -> Icons.Outlined.WorkOutline
        else -> Icons.AutoMirrored.Outlined.ReceiptLong
    }

    val actionButtonText = when (action.targetRoute.lowercase()) {
        "accounts" -> "View Accounts"
        "budgets" -> "Open Budgets"
        "invoice" -> "Add Invoice"
        "investments" -> "Open Portfolio"
        else -> "Open Screen"
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(cardBg)
            .border(1.dp, borderColor, RoundedCornerShape(14.dp))
            .padding(14.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            BoxIcon(
                icon = icon,
                isDark = isDark
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = action.title ?: "Suggested Action",
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.SemiBold,
                        color = if (isDark) SmartMoneyColors.DarkTextPrimary else SmartMoneyColors.TextPrimary,
                        fontSize = 14.sp
                    )
                )

                if (!action.description.isNullOrBlank()) {
                    Text(
                        text = action.description,
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = if (isDark) SmartMoneyColors.DarkTextSecondary.copy(alpha = 0.75f) else SmartMoneyColors.TextMuted,
                            fontSize = 12.sp
                        )
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(SmartMoneyColors.AzurePrimary)
                .clickable { onActionClick(action) }
                .padding(vertical = 8.dp, horizontal = 14.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = actionButtonText,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            )
            Spacer(modifier = Modifier.width(6.dp))
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.ArrowForward,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
private fun BoxIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isDark: Boolean
) {
    androidx.compose.foundation.layout.Box(
        modifier = Modifier
            .size(36.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (isDark) Color(0xFF2E3D31) else Color(0xFFDAEBE3)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (isDark) Color(0xFFDAEBE3) else SmartMoneyColors.AzurePrimary,
            modifier = Modifier.size(20.dp)
        )
    }
}

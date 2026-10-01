package com.example.smartmoney.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.smartmoney.R

/**
 * Returns the matching drawable resource ID for a bank by name or alias,
 * or null if not recognized.
 */
@DrawableRes
fun getBankLogoResId(bankName: String?): Int? {
    if (bankName.isNullOrBlank()) return null
    val normalized = bankName.lowercase().trim()
    return when {
        normalized.contains("equity") -> R.drawable.equity
        normalized.contains("kcb") -> R.drawable.kcb
        normalized.contains("ncba") -> R.drawable.ncba
        normalized.contains("stanbic") -> R.drawable.stanbic
        else -> null
    }
}

/**
 * Detects if a text (e.g. transaction description) mentions one of the supported banks.
 */
fun findBankInText(text: String?): String? {
    if (text.isNullOrBlank()) return null
    val lower = text.lowercase()
    return when {
        lower.contains("equity") -> "Equity"
        lower.contains("kcb") -> "KCB"
        lower.contains("ncba") -> "NCBA"
        lower.contains("stanbic") -> "Stanbic"
        else -> null
    }
}

/**
 * Displays a clean, bounded bank logo for the given bank name,
 * with graceful fallback to a generic banking icon.
 */
@Composable
fun BankLogo(
    bankName: String?,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(8.dp),
    contentDescription: String? = bankName
) {
    val logoResId = getBankLogoResId(bankName)
    if (logoResId != null) {
        Box(
            modifier = modifier
                .clip(shape)
                .background(Color.White)
                .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), shape)
                .padding(2.dp),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(id = logoResId),
                contentDescription = contentDescription,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )
        }
    } else {
        Box(
            modifier = modifier
                .clip(shape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.AccountBalance,
                contentDescription = contentDescription,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.fillMaxSize(0.6f)
            )
        }
    }
}

/**
 * Row composable displaying the bank logo alongside the bank's name.
 */
@Composable
fun BankLabelWithLogo(
    bankName: String,
    modifier: Modifier = Modifier,
    logoSize: androidx.compose.ui.unit.Dp = 24.dp,
    textStyle: TextStyle = MaterialTheme.typography.bodyMedium
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        BankLogo(
            bankName = bankName,
            modifier = Modifier.size(logoSize)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = bankName,
            style = textStyle,
            fontWeight = FontWeight.SemiBold
        )
    }
}

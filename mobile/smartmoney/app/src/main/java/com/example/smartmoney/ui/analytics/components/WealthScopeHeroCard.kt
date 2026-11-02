package com.example.smartmoney.ui.analytics.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.smartmoney.ui.analytics.model.WealthComposition
import com.example.smartmoney.ui.analytics.model.WealthScopeFilter
import java.text.NumberFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WealthScopeHeroCard(
    composition: WealthComposition?,
    currentScope: WealthScopeFilter,
    onScopeChanged: (WealthScopeFilter) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Wealth Scope & Liquidity", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            
            // Scope Chips
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                WealthScopeFilter.entries.forEach { scope ->
                    FilterChip(
                        selected = currentScope == scope,
                        onClick = { onScopeChanged(scope) },
                        label = { 
                            Text(
                                when (scope) {
                                    WealthScopeFilter.PURE_LIQUID -> "Pure Liquid"
                                    WealthScopeFilter.LIQUID_PLUS_MMF -> "+ MMF"
                                    WealthScopeFilter.TOTAL_NET_WORTH -> "Total Net Worth"
                                }
                            ) 
                        }
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            val activeAmount = when (currentScope) {
                WealthScopeFilter.PURE_LIQUID -> composition?.pureLiquidCash
                WealthScopeFilter.LIQUID_PLUS_MMF -> (composition?.pureLiquidCash ?: java.math.BigDecimal.ZERO) + (composition?.mmfHoldings ?: java.math.BigDecimal.ZERO)
                WealthScopeFilter.TOTAL_NET_WORTH -> composition?.totalNetWorth
            } ?: java.math.BigDecimal.ZERO
            
            val formatter = NumberFormat.getCurrencyInstance(Locale("en", "KE"))
            formatter.currency = java.util.Currency.getInstance("KES")
            
            Text(
                text = formatter.format(activeAmount),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Composition bar
            val total = composition?.totalNetWorth?.toFloat() ?: 1f
            val liquidWeight = if (total > 0f) (composition?.pureLiquidCash?.toFloat() ?: 0f) / total else 0f
            val mmfWeight = if (total > 0f) (composition?.mmfHoldings?.toFloat() ?: 0f) / total else 0f
            val fixedWeight = if (total > 0f) (composition?.fixedTermHoldings?.toFloat() ?: 0f) / total else 0f
            
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(12.dp)
                    .clip(RoundedCornerShape(6.dp))
            ) {
                if (liquidWeight > 0f) {
                    Box(modifier = Modifier.weight(liquidWeight).fillMaxHeight().background(Color(0xFF4CAF50)))
                }
                if (mmfWeight > 0f) {
                    Box(modifier = Modifier.weight(mmfWeight).fillMaxHeight().background(Color(0xFF00BCD4)))
                }
                if (fixedWeight > 0f) {
                    Box(modifier = Modifier.weight(fixedWeight).fillMaxHeight().background(Color(0xFF607D8B)))
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("${String.format("%.1f", composition?.liquidRatio ?: 0f)}% Highly Liquid", style = MaterialTheme.typography.bodySmall)
                Text("■ Cash ■ MMF ■ Fixed", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

package com.example.smartmoney.ui.analytics.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.smartmoney.ui.analytics.model.MaturityMilestone
import java.text.NumberFormat
import java.util.Locale

@Composable
fun InvestmentMaturityCard(
    milestones: List<MaturityMilestone>,
    rolloverEnabled: Boolean,
    onRolloverToggled: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Upcoming Investment Maturities", style = MaterialTheme.typography.titleMedium)
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Compound Yield / Auto-Rollover", style = MaterialTheme.typography.bodyMedium)
                Switch(checked = rolloverEnabled, onCheckedChange = onRolloverToggled)
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            if (milestones.isEmpty()) {
                Text("No upcoming maturities.", style = MaterialTheme.typography.bodyMedium)
            } else {
                val formatter = NumberFormat.getCurrencyInstance(Locale("en", "KE"))
                formatter.currency = java.util.Currency.getInstance("KES")
                
                milestones.forEach { milestone ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(milestone.investmentName, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                            Text("In ${milestone.daysRemaining} days (${milestone.maturityDate})", style = MaterialTheme.typography.bodySmall)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(formatter.format(milestone.payoutAmount), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            Text("+ ${formatter.format(milestone.gainAmount)} profit", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                        }
                    }
                    Divider()
                }
            }
        }
    }
}

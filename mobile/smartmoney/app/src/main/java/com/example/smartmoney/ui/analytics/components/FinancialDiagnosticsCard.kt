package com.example.smartmoney.ui.analytics.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.smartmoney.ui.analytics.model.FinancialDiagnostics
import java.text.NumberFormat
import java.util.Locale

@Composable
fun FinancialDiagnosticsCard(
    diagnostics: FinancialDiagnostics?,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Financial Diagnostics", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(16.dp))
            
            if (diagnostics != null) {
                val formatter = NumberFormat.getCurrencyInstance(Locale("en", "KE"))
                formatter.currency = java.util.Currency.getInstance("KES")
                
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Emergency Runway:", style = MaterialTheme.typography.bodyMedium)
                    Text("${String.format("%.1f", diagnostics.emergencyRunwayMonths)} Months", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Savings Velocity:", style = MaterialTheme.typography.bodyMedium)
                    Text("${String.format("%.1f", diagnostics.savingsRatePercent)}% preserved", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Monthly Burn Rate:", style = MaterialTheme.typography.bodyMedium)
                    Text(formatter.format(diagnostics.monthlyBurnRate), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                }
            } else {
                Text("Loading diagnostics...", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

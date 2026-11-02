package com.example.smartmoney.ui.analytics.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.smartmoney.ui.analytics.model.BudgetCutPreset
import java.text.NumberFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BudgetCutsSimulatorCard(
    currentPreset: BudgetCutPreset,
    onPresetChanged: (BudgetCutPreset) -> Unit,
    extraSavings1Y: java.math.BigDecimal?,
    runwayExtensionMonths: Double?,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Budget Cuts Simulator", style = MaterialTheme.typography.titleMedium)
            Text("What if you reduce discretionary spending?", style = MaterialTheme.typography.bodySmall)
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Preset Chips
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                BudgetCutPreset.entries.forEach { preset ->
                    FilterChip(
                        selected = currentPreset == preset,
                        onClick = { onPresetChanged(preset) },
                        label = { Text(preset.displayName) }
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            if (currentPreset != BudgetCutPreset.CURRENT_PACE && extraSavings1Y != null && runwayExtensionMonths != null) {
                val formatter = NumberFormat.getCurrencyInstance(Locale("en", "KE"))
                formatter.currency = java.util.Currency.getInstance("KES")
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("Extra Savings (1Y)", style = MaterialTheme.typography.bodySmall)
                        Text("+ ${formatter.format(extraSavings1Y)}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }
                    Column(horizontalAlignment = androidx.compose.ui.Alignment.End) {
                        Text("Runway Added", style = MaterialTheme.typography.bodySmall)
                        Text("+ ${String.format("%.1f", runwayExtensionMonths)} Months", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }
                }
            } else if (currentPreset == BudgetCutPreset.CURRENT_PACE) {
                Text("Select a cut scenario to view projected benefits.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

package com.example.smartmoney.ui.investment.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.smartmoney.domain.model.Investment
import com.example.smartmoney.domain.model.InvestmentType
import com.example.smartmoney.ui.theme.LocalDarkTheme
import com.example.smartmoney.ui.theme.SmartMoneyColors
import java.time.LocalDate
import kotlin.math.roundToLong

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InvestmentFormBottomSheet(
    initialInvestment: Investment? = null,
    onDismiss: () -> Unit,
    onSave: (
        id: String,
        name: String,
        type: InvestmentType,
        principalMinor: Long,
        currentValueMinor: Long?,
        valuationDate: LocalDate,
        maturityDate: LocalDate?
    ) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val isDark = LocalDarkTheme.current
    val scrollState = rememberScrollState()

    var name by remember { mutableStateOf(initialInvestment?.name ?: "") }
    var selectedType by remember { mutableStateOf(initialInvestment?.type ?: InvestmentType.MONEY_MARKET) }
    var principal by remember {
        mutableStateOf(
            initialInvestment?.let { (it.principalMinor / 100.0).toString() } ?: ""
        )
    }
    var currentValue by remember {
        mutableStateOf(
            initialInvestment?.currentValueMinor?.let { (it / 100.0).toString() } ?: ""
        )
    }
    var valuationDate by remember {
        mutableStateOf(initialInvestment?.valuationDate ?: LocalDate.now())
    }
    var maturityDate by remember {
        mutableStateOf(initialInvestment?.maturityDate)
    }

    var isTypeDropdownExpanded by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val now = LocalDate.now()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = if (isDark) SmartMoneyColors.DarkSurface else MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
                .verticalScroll(scrollState)
        ) {
            Text(
                text = if (initialInvestment == null) "Add Investment Record" else "Edit Investment Record",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "Track portfolio principal, valuation trajectory and maturity alerts",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 1. Name Field
            OutlinedTextField(
                value = name,
                onValueChange = {
                    name = it
                    errorMessage = null
                },
                label = { Text("Investment Name (e.g. Sanlam MMF)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(14.dp))

            // 2. Type Dropdown
            ExposedDropdownMenuBox(
                expanded = isTypeDropdownExpanded,
                onExpandedChange = { isTypeDropdownExpanded = it }
            ) {
                OutlinedTextField(
                    value = selectedType.displayName,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Investment Instrument Type") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isTypeDropdownExpanded) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                )
                ExposedDropdownMenu(
                    expanded = isTypeDropdownExpanded,
                    onDismissRequest = { isTypeDropdownExpanded = false }
                ) {
                    InvestmentType.entries.forEach { type ->
                        DropdownMenuItem(
                            text = { Text(type.displayName) },
                            onClick = {
                                selectedType = type
                                isTypeDropdownExpanded = false
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 3. Principal Amount
            OutlinedTextField(
                value = principal,
                onValueChange = {
                    principal = it
                    errorMessage = null
                },
                label = { Text("Principal Invested (KES)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(14.dp))

            // 4. Current Valuation (Optional)
            OutlinedTextField(
                value = currentValue,
                onValueChange = {
                    currentValue = it
                    errorMessage = null
                },
                label = { Text("Current Valuation (KES) · Optional") },
                supportingText = { Text("Leave blank if latest valuation is unavailable or pending") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(14.dp))

            // 5. Maturity Date Presets
            Text(
                text = "Maturity Date: ${maturityDate?.toString() ?: "Open-ended (No fixed date)"}",
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(6.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (maturityDate == null) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                    modifier = Modifier.clickable { maturityDate = null }
                ) {
                    Text(
                        text = "None (MMF)",
                        fontSize = 11.sp,
                        fontWeight = if (maturityDate == null) FontWeight.Bold else FontWeight.Normal,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                    modifier = Modifier.clickable { maturityDate = now.plusDays(91) }
                ) {
                    Text(
                        text = "+91 Days",
                        fontSize = 11.sp,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                    modifier = Modifier.clickable { maturityDate = now.plusDays(182) }
                ) {
                    Text(
                        text = "+182 Days",
                        fontSize = 11.sp,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                    modifier = Modifier.clickable { maturityDate = now.plusYears(1) }
                ) {
                    Text(
                        text = "+1 Year",
                        fontSize = 11.sp,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }

            if (errorMessage != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = errorMessage!!,
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Submit Button
            Button(
                onClick = {
                    val pDouble = principal.toDoubleOrNull()
                    val cvDouble = if (currentValue.isNotBlank()) currentValue.toDoubleOrNull() else null

                    if (name.isBlank()) {
                        errorMessage = "Investment name cannot be empty."
                        return@Button
                    }
                    if (pDouble == null || pDouble <= 0) {
                        errorMessage = "Please enter a valid positive principal amount."
                        return@Button
                    }
                    if (currentValue.isNotBlank() && (cvDouble == null || cvDouble < 0)) {
                        errorMessage = "Valuation amount must be a positive number."
                        return@Button
                    }

                    val principalMinor = (pDouble * 100.0).roundToLong()
                    val currentValueMinor = cvDouble?.let { (it * 100.0).roundToLong() }

                    onSave(
                        initialInvestment?.id ?: "",
                        name.trim(),
                        selectedType,
                        principalMinor,
                        currentValueMinor,
                        valuationDate,
                        maturityDate
                    )
                    onDismiss()
                },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
            ) {
                Text(
                    text = if (initialInvestment == null) "Save Investment Record" else "Update Record",
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

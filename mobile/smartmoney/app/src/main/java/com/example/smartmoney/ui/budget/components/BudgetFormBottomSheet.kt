package com.example.smartmoney.ui.budget.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.smartmoney.domain.model.Budget
import com.example.smartmoney.ui.components.BankLogo
import com.example.smartmoney.ui.theme.LocalDarkTheme
import com.example.smartmoney.ui.theme.SmartMoneyColors
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import kotlin.math.roundToLong

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BudgetFormBottomSheet(
    initialBudget: Budget? = null,
    accountOptions: List<Pair<String, String>> = emptyList(), // Pair(id, name e.g. "NCBA · **** 1234")
    onDismiss: () -> Unit,
    onSave: (
        id: String,
        category: String,
        allocatedMinor: Long,
        start: LocalDate,
        end: LocalDate,
        threshold: Int,
        accountId: String?
    ) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val isDark = LocalDarkTheme.current
    val scrollState = rememberScrollState()

    val now = LocalDate.now()
    val defaultStart = initialBudget?.start ?: now.with(TemporalAdjusters.firstDayOfMonth())
    val defaultEnd = initialBudget?.end ?: now.with(TemporalAdjusters.lastDayOfMonth())

    var category by remember { mutableStateOf(initialBudget?.category ?: "") }
    var allocated by remember {
        mutableStateOf(
            initialBudget?.let { (it.allocatedMinor / 100.0).toString() } ?: ""
        )
    }
    var threshold by remember {
        mutableStateOf(initialBudget?.threshold?.toString() ?: "85")
    }
    var selectedAccountId by remember {
        mutableStateOf(initialBudget?.accountId ?: "")
    }
    var startDate by remember { mutableStateOf(defaultStart) }
    var endDate by remember { mutableStateOf(defaultEnd) }

    var isAccountDropdownOpen by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val quickCategories = listOf("Groceries", "Utilities & Power", "Shopping", "Dining & Leisure", "Transport", "Rent")

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
                text = if (initialBudget == null) "Add Budget Allocation" else "Edit Budget Allocation",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "Set financial spending limits and alert notifications",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 1. Category Field
            OutlinedTextField(
                value = category,
                onValueChange = {
                    category = it
                    errorMessage = null
                },
                label = { Text("Category (e.g. Shopping, Rent)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Quick Category Chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                quickCategories.forEach { cat ->
                    val isSelected = category.equals(cat, ignoreCase = true)
                    val visual = BudgetCategoryVisuals.getVisual(cat)
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (isSelected) {
                            if (isDark) SmartMoneyColors.PaleMintGreen else SmartMoneyColors.DarkSlateGreen
                        } else {
                            if (isDark) SmartMoneyColors.DarkSurfaceElevated else SmartMoneyColors.PaleMintGreen.copy(alpha = 0.4f)
                        },
                        modifier = Modifier.clickable {
                            category = cat
                            errorMessage = null
                        }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = visual.icon,
                                contentDescription = null,
                                tint = if (isSelected) {
                                    if (isDark) SmartMoneyColors.DarkSlateGreen else Color.White
                                } else {
                                    if (isDark) SmartMoneyColors.DarkInactive else SmartMoneyColors.DarkSlateGreen
                                },
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = cat,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) {
                                    if (isDark) SmartMoneyColors.DarkSlateGreen else Color.White
                                } else {
                                    if (isDark) SmartMoneyColors.DarkTextPrimary else SmartMoneyColors.TextPrimary
                                }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 2. Allocation (KES) Field
            OutlinedTextField(
                value = allocated,
                onValueChange = {
                    allocated = it
                    errorMessage = null
                },
                label = { Text("Allocation Limit (KES)") },
                prefix = {
                    Text(
                        text = "KES ",
                        fontWeight = FontWeight.Bold,
                        color = if (isDark) SmartMoneyColors.PaleMintGreen else SmartMoneyColors.DarkSlateGreen
                    )
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(14.dp))

            // 3. Warning Threshold Field
            OutlinedTextField(
                value = threshold,
                onValueChange = {
                    threshold = it
                    errorMessage = null
                },
                label = { Text("Alert Warning Threshold (%)") },
                supportingText = { Text("Triggers a warning when spend reaches this percent (e.g. 85%)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(14.dp))

            // 4. Account Scope Selector (Dropdown)
            ExposedDropdownMenuBox(
                expanded = isAccountDropdownOpen,
                onExpandedChange = { isAccountDropdownOpen = it }
            ) {
                val selectedName = accountOptions.find { it.first == selectedAccountId }?.second
                    ?: if (selectedAccountId.isBlank()) "All linked accounts" else "Specific Account"

                OutlinedTextField(
                    value = selectedName,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Account Scope") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isAccountDropdownOpen) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                )

                ExposedDropdownMenu(
                    expanded = isAccountDropdownOpen,
                    onDismissRequest = { isAccountDropdownOpen = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("All linked accounts (Default)") },
                        onClick = {
                            selectedAccountId = ""
                            isAccountDropdownOpen = false
                        }
                    )
                    accountOptions.forEach { (id, name) ->
                        DropdownMenuItem(
                            leadingIcon = {
                                val bankName = name.split("·").firstOrNull()?.trim() ?: name
                                BankLogo(bankName = bankName, modifier = Modifier.size(24.dp))
                            },
                            text = { Text(name) },
                            onClick = {
                                selectedAccountId = id
                                isAccountDropdownOpen = false
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 5. Date Cycle Presets
            Text(
                text = "Budget Period: $startDate to $endDate",
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
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                    modifier = Modifier.clickable {
                        startDate = now.with(TemporalAdjusters.firstDayOfMonth())
                        endDate = now.with(TemporalAdjusters.lastDayOfMonth())
                    }
                ) {
                    Text(
                        text = "This Month",
                        fontSize = 11.sp,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                    modifier = Modifier.clickable {
                        startDate = now
                        endDate = now.plusDays(30)
                    }
                ) {
                    Text(
                        text = "Next 30 Days",
                        fontSize = 11.sp,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                    modifier = Modifier.clickable {
                        startDate = now.with(TemporalAdjusters.firstDayOfMonth())
                        endDate = now.plusMonths(3).with(TemporalAdjusters.lastDayOfMonth())
                    }
                ) {
                    Text(
                        text = "Quarterly",
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
                    val allocDouble = allocated.toDoubleOrNull()
                    val threshInt = threshold.toIntOrNull()

                    if (category.isBlank()) {
                        errorMessage = "Please enter or select a budget category."
                        return@Button
                    }
                    if (allocDouble == null || allocDouble <= 0) {
                        errorMessage = "Please enter a valid positive allocation amount."
                        return@Button
                    }
                    if (threshInt == null || threshInt !in 1..100) {
                        errorMessage = "Warning threshold must be between 1 and 100%."
                        return@Button
                    }

                    val allocatedMinor = (allocDouble * 100.0).roundToLong()

                    onSave(
                        initialBudget?.id ?: "",
                        category.trim(),
                        allocatedMinor,
                        startDate,
                        endDate,
                        threshInt,
                        selectedAccountId.ifBlank { null }
                    )
                    onDismiss()
                },
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isDark) SmartMoneyColors.PaleMintGreen else SmartMoneyColors.DarkSlateGreen,
                    contentColor = if (isDark) SmartMoneyColors.DarkSlateGreen else Color.White
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
            ) {
                Text(
                    text = if (initialBudget == null) "Create Budget Allocation" else "Save Changes",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            }
        }
    }
}

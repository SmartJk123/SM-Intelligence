package com.example.smartmoney.ui.budget

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.smartmoney.core.util.CurrencyUtils
import com.example.smartmoney.domain.model.Budget
import com.example.smartmoney.domain.model.BudgetStatus
import com.example.smartmoney.domain.model.BudgetSummary
import com.example.smartmoney.ui.accounts.AccountViewModel
import com.example.smartmoney.ui.budget.components.BudgetCard
import com.example.smartmoney.ui.budget.components.BudgetFormBottomSheet
import com.example.smartmoney.ui.theme.LocalDarkTheme
import com.example.smartmoney.ui.theme.SmartMoneyColors
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

enum class BudgetFilter(val label: String) {
    ALL("All"),
    ON_TRACK("On Track"),
    NEAR_LIMIT("Near Limit"),
    OVER_BUDGET("Over Budget")
}

@Composable
fun BudgetScreen(
    viewModel: BudgetViewModel,
    accountViewModel: AccountViewModel? = null,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val bankAccounts by accountViewModel?.bankAccounts?.collectAsState() ?: remember {
        mutableStateOf(emptyList())
    }

    val isDark = LocalDarkTheme.current

    var isBottomSheetOpen by remember { mutableStateOf(false) }
    var budgetToEdit by remember { mutableStateOf<Budget?>(null) }
    var budgetToDeleteId by remember { mutableStateOf<String?>(null) }
    var selectedFilter by remember { mutableStateOf(BudgetFilter.ALL) }

    val accountOptions = remember(bankAccounts) {
        bankAccounts.map {
            it.id to "${it.bankName} · ${it.maskedAccountNumber}"
        }
    }

    // Filtered budget list
    val filteredBudgets = remember(uiState.budgets, selectedFilter) {
        when (selectedFilter) {
            BudgetFilter.ALL -> uiState.budgets
            BudgetFilter.ON_TRACK -> uiState.budgets.filter { it.status == BudgetStatus.WITHIN_BUDGET }
            BudgetFilter.NEAR_LIMIT -> uiState.budgets.filter { it.status == BudgetStatus.APPROACHING_LIMIT }
            BudgetFilter.OVER_BUDGET -> uiState.budgets.filter { it.status == BudgetStatus.OVER_BUDGET }
        }
    }

    Scaffold(
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    budgetToEdit = null
                    isBottomSheetOpen = true
                },
                icon = { Icon(Icons.Default.Add, contentDescription = "Add Budget") },
                text = { Text("New Budget", fontWeight = FontWeight.Bold) },
                containerColor = if (isDark) SmartMoneyColors.PaleMintGreen else SmartMoneyColors.DarkSlateGreen,
                contentColor = if (isDark) SmartMoneyColors.DarkSlateGreen else Color.White
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        if (uiState.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    color = if (isDark) SmartMoneyColors.PaleMintGreen else SmartMoneyColors.DarkSlateGreen
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 96.dp)
            ) {
                // =========================================================================
                // 1. STATUS FILTER PILL ROW & SECTION HEADER
                // =========================================================================
                if (uiState.budgets.isNotEmpty()) {
                    item {
                        BudgetStatusFilterRow(
                            selectedFilter = selectedFilter,
                            onFilterSelect = { selectedFilter = it },
                            budgets = uiState.budgets,
                            isDark = isDark
                        )
                        Spacer(modifier = Modifier.height(16.dp))

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Category Allocations",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isDark) SmartMoneyColors.DarkTextPrimary else SmartMoneyColors.TextPrimary
                            )
                            Text(
                                text = "${filteredBudgets.size} of ${uiState.budgets.size} displayed",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = if (isDark) SmartMoneyColors.DarkInactive else SmartMoneyColors.TextMuted
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                    }
                }

                // =========================================================================
                // 3. BUDGET LIST OR EMPTY STATE
                // =========================================================================
                if (uiState.budgets.isEmpty()) {
                    item {
                        InspiringBudgetEmptyState(
                            onAddPreset = { categoryName, defaultAmountMinor ->
                                val now = LocalDate.now()
                                budgetToEdit = Budget(
                                    id = "",
                                    category = categoryName,
                                    allocatedMinor = defaultAmountMinor,
                                    start = now.with(TemporalAdjusters.firstDayOfMonth()),
                                    end = now.with(TemporalAdjusters.lastDayOfMonth()),
                                    threshold = 85
                                )
                                isBottomSheetOpen = true
                            },
                            onAddCustom = {
                                budgetToEdit = null
                                isBottomSheetOpen = true
                            },
                            isDark = isDark
                        )
                    }
                } else if (filteredBudgets.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No budgets match the '${selectedFilter.label}' filter.",
                                fontSize = 14.sp,
                                color = if (isDark) SmartMoneyColors.DarkInactive else SmartMoneyColors.TextMuted
                            )
                        }
                    }
                } else {
                    items(filteredBudgets, key = { it.budget.id }) { summary ->
                        val matchedAccount = bankAccounts.find { it.id == summary.budget.accountId }
                        val accountName = matchedAccount?.let { "${it.bankName} · ${it.maskedAccountNumber}" }

                        BudgetCard(
                            summary = summary,
                            accountName = accountName,
                            onEdit = {
                                budgetToEdit = summary.budget
                                isBottomSheetOpen = true
                            },
                            onDelete = {
                                budgetToDeleteId = summary.budget.id
                            }
                        )
                    }
                }
            }
        }

        // Add / Edit Bottom Sheet Form
        if (isBottomSheetOpen) {
            BudgetFormBottomSheet(
                initialBudget = budgetToEdit,
                accountOptions = accountOptions,
                onDismiss = {
                    isBottomSheetOpen = false
                    budgetToEdit = null
                },
                onSave = { id, category, allocatedMinor, start, end, threshold, accountId ->
                    viewModel.saveBudget(
                        id = id,
                        category = category,
                        allocatedMinor = allocatedMinor,
                        start = start,
                        end = end,
                        threshold = threshold,
                        accountId = accountId
                    )
                }
            )
        }

        // Delete Confirmation Dialog
        if (budgetToDeleteId != null) {
            AlertDialog(
                onDismissRequest = { budgetToDeleteId = null },
                shape = RoundedCornerShape(18.dp),
                containerColor = if (isDark) SmartMoneyColors.DarkSurfaceElevated else MaterialTheme.colorScheme.surface,
                title = {
                    Text(
                        "Delete Budget Allocation?",
                        fontWeight = FontWeight.Bold,
                        color = if (isDark) SmartMoneyColors.DarkTextPrimary else SmartMoneyColors.TextPrimary
                    )
                },
                text = {
                    Text(
                        "Are you sure you want to remove this budget allocation? Historical transactions will remain preserved.",
                        fontSize = 14.sp,
                        color = if (isDark) SmartMoneyColors.DarkInactive else SmartMoneyColors.TextMuted
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            budgetToDeleteId?.let { viewModel.deleteBudget(it) }
                            budgetToDeleteId = null
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        ),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Delete", fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { budgetToDeleteId = null }) {
                        Text(
                            "Cancel",
                            color = if (isDark) SmartMoneyColors.DarkTextPrimary else SmartMoneyColors.TextPrimary
                        )
                    }
                }
            )
        }
    }
}

/**
 * Interactive filter pill row for filtering active budgets by status.
 */
@Composable
private fun BudgetStatusFilterRow(
    selectedFilter: BudgetFilter,
    onFilterSelect: (BudgetFilter) -> Unit,
    budgets: List<BudgetSummary>,
    isDark: Boolean,
    modifier: Modifier = Modifier
) {
    val onTrackCount = remember(budgets) { budgets.count { it.status == BudgetStatus.WITHIN_BUDGET } }
    val nearLimitCount = remember(budgets) { budgets.count { it.status == BudgetStatus.APPROACHING_LIMIT } }
    val overBudgetCount = remember(budgets) { budgets.count { it.status == BudgetStatus.OVER_BUDGET } }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        BudgetFilter.values().forEach { filter ->
            val isSelected = filter == selectedFilter
            val count = when (filter) {
                BudgetFilter.ALL -> budgets.size
                BudgetFilter.ON_TRACK -> onTrackCount
                BudgetFilter.NEAR_LIMIT -> nearLimitCount
                BudgetFilter.OVER_BUDGET -> overBudgetCount
            }

            val pillBg = when {
                isSelected && isDark -> SmartMoneyColors.DarkSurfaceElevated
                isSelected && !isDark -> SmartMoneyColors.DarkSlateGreen
                isDark -> SmartMoneyColors.DarkSurface
                else -> Color.White
            }

            val pillText = when {
                isSelected -> Color.White
                isDark -> SmartMoneyColors.DarkInactive
                else -> SmartMoneyColors.TextPrimary
            }

            val border = if (isSelected) {
                BorderStroke(1.dp, if (isDark) SmartMoneyColors.PaleMintGreen else SmartMoneyColors.DarkSlateGreen)
            } else {
                BorderStroke(1.dp, if (isDark) SmartMoneyColors.DarkBorderLine else SmartMoneyColors.BorderLine.copy(alpha = 0.7f))
            }

            Surface(
                shape = RoundedCornerShape(50),
                color = pillBg,
                border = border,
                modifier = Modifier.clickable { onFilterSelect(filter) }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = filter.label,
                        fontSize = 12.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = pillText
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                        shape = CircleShape,
                        color = if (isSelected) Color.White.copy(alpha = 0.25f) else (if (isDark) SmartMoneyColors.DarkSurfaceElevated else SmartMoneyColors.PaleSageGreen.copy(alpha = 0.5f))
                    ) {
                        Text(
                            text = count.toString(),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = pillText,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Inspiring, user-guiding empty state with preset quick-chips for one-tap budget creation.
 */
@Composable
private fun InspiringBudgetEmptyState(
    onAddPreset: (category: String, defaultAmountMinor: Long) -> Unit,
    onAddCustom: () -> Unit,
    isDark: Boolean,
    modifier: Modifier = Modifier
) {
    val cardBg = if (isDark) SmartMoneyColors.DarkSurfaceElevated else Color.White
    val border = BorderStroke(1.dp, if (isDark) SmartMoneyColors.DarkBorderLine else SmartMoneyColors.BorderLine.copy(alpha = 0.7f))

    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        border = border,
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
        ) {
            // Glowing circular icon badge
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(if (isDark) SmartMoneyColors.DarkSurface else SmartMoneyColors.PaleMintGreen),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .clip(CircleShape)
                        .background(if (isDark) SmartMoneyColors.DarkSurfaceElevated else Color.White),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Savings,
                        contentDescription = null,
                        tint = if (isDark) SmartMoneyColors.PaleMintGreen else SmartMoneyColors.DarkSlateGreen,
                        modifier = Modifier.size(30.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            Text(
                text = "Take Control of Your Spending",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                color = if (isDark) SmartMoneyColors.DarkTextPrimary else SmartMoneyColors.TextPrimary
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Set smart monthly limits for groceries, power, dining, and shopping to avoid surprises and build healthy financial habits.",
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                color = if (isDark) SmartMoneyColors.DarkInactive else SmartMoneyColors.TextMuted,
                modifier = Modifier.padding(horizontal = 12.dp)
            )

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "Tap a preset to start instantly:",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = if (isDark) SmartMoneyColors.DarkInactive else SmartMoneyColors.DarkSlateGreen
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Quick-start preset chips row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                QuickPresetChip(
                    icon = Icons.Outlined.ShoppingCart,
                    label = "Groceries (15k)",
                    onClick = { onAddPreset("Groceries", 15_000_00L) },
                    isDark = isDark
                )
                QuickPresetChip(
                    icon = Icons.Outlined.Bolt,
                    label = "Utilities (5k)",
                    onClick = { onAddPreset("Utilities & Power", 5_000_00L) },
                    isDark = isDark
                )
                QuickPresetChip(
                    icon = Icons.Outlined.Restaurant,
                    label = "Dining (8k)",
                    onClick = { onAddPreset("Dining & Leisure", 8_000_00L) },
                    isDark = isDark
                )
                QuickPresetChip(
                    icon = Icons.Outlined.ShoppingBag,
                    label = "Shopping (10k)",
                    onClick = { onAddPreset("Shopping", 10_000_00L) },
                    isDark = isDark
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = onAddCustom,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isDark) SmartMoneyColors.PaleMintGreen else SmartMoneyColors.DarkSlateGreen,
                    contentColor = if (isDark) SmartMoneyColors.DarkSlateGreen else Color.White
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Create Custom Budget", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun QuickPresetChip(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    isDark: Boolean
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = if (isDark) SmartMoneyColors.DarkSurface else SmartMoneyColors.PaleMintGreen.copy(alpha = 0.5f),
        border = BorderStroke(1.dp, if (isDark) SmartMoneyColors.DarkBorderLine else SmartMoneyColors.PaleSageGreen),
        modifier = Modifier.clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isDark) SmartMoneyColors.PaleMintGreen else SmartMoneyColors.DarkSlateGreen,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (isDark) SmartMoneyColors.DarkTextPrimary else SmartMoneyColors.TextPrimary
            )
        }
    }
}

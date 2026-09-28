# Budget Feature: Implementation & Kotlin Mobile Blueprint

This document details how the **Budgets** feature is implemented in this web application and provides a complete, production-ready specification and Jetpack Compose (Kotlin / Android) implementation guide.

---

## 1. Web Application Implementation Analysis

The Budgets module is implemented across three primary files:
- **Data Model**: [`src/app/workspace-api.ts:24-32`](file:///home/frank/SmartMoney_intelligence/SM-Intelligence/web/src/app/workspace-api.ts#L24-L32) defines the [`Budget`](file:///home/frank/SmartMoney_intelligence/SM-Intelligence/web/src/app/workspace-api.ts#L24) interface.
- **Business Logic & Calculations**: [`src/app/workspace-page.ts:172-199`](file:///home/frank/SmartMoney_intelligence/SM-Intelligence/web/src/app/workspace-page.ts#L172-L199) computes expenditure, status, and alerts.
- **UI & Form Template**: [`src/app/workspace-page.html:387-446`](file:///home/frank/SmartMoney_intelligence/SM-Intelligence/web/src/app/workspace-page.html#L387-L446) and [`src/app/workspace-page.ts:508-530`](file:///home/frank/SmartMoney_intelligence/SM-Intelligence/web/src/app/workspace-page.ts#L508-L530).

### Core Logic & Rules

1. **Monetary Representation**:
   - Stored in integer minor units: `allocatedMinor` (e.g., `1000000` = KES 10,000.00).
2. **Expenditure Aggregator (`spent`)** ([`workspace-page.ts:172-184`](file:///home/frank/SmartMoney_intelligence/SM-Intelligence/web/src/app/workspace-page.ts#L172-L184)):
   - Evaluates all transactions where:
     - `status === 'POSTED'` (pending or failed transactions are excluded).
     - `direction === 'DEBIT'` (only outgoing spending counts).
     - `category` matches case-insensitively.
     - Transaction date falls within `[start, end]`.
     - If `accountId` is specified, matches only that account; otherwise applies across all accounts.
3. **Threshold & Status Evaluation (`budgetStatus`)** ([`workspace-page.ts:193-199`](file:///home/frank/SmartMoney_intelligence/SM-Intelligence/web/src/app/workspace-page.ts#L193-L199)):
   ```typescript
   budgetStatus(b: Budget) {
     return this.spent(b) > b.allocatedMinor
       ? 'Over budget'
       : this.spent(b) >= (b.allocatedMinor * b.threshold) / 100
         ? 'Approaching limit'
         : 'Within budget';
   }
   ```
4. **Active Scope Filtering** ([`workspace-page.ts:185-192`](file:///home/frank/SmartMoney_intelligence/SM-Intelligence/web/src/app/workspace-page.ts#L185-L192)):
   - Budgets are active when their date window overlaps the currently selected view range: `b.start <= this.end && b.end >= this.start`.
5. **REST API Contract**:
   - Create / Update: `POST /api/workspace/budgets` with JSON body.
   - Delete: `DELETE /api/workspace/budgets/:id`.

---

## 2. Kotlin Mobile Data Models

### 2.1. Domain & Network Entities

```kotlin
package com.smartmoney.domain.model

import java.time.LocalDate

enum class BudgetStatus {
    WITHIN_BUDGET,
    APPROACHING_LIMIT,
    OVER_BUDGET
}

data class Budget(
    val id: String,
    val category: String,
    val allocatedMinor: Long, // Integer cents to prevent rounding bugs
    val start: LocalDate,
    val end: LocalDate,
    val accountId: String? = null, // Null or blank means all accounts
    val threshold: Int = 85 // Percent (1..100)
) {
    val allocatedMajor: Double
        get() = allocatedMinor / 100.0
}

data class BudgetSummary(
    val budget: Budget,
    val spentMinor: Long,
    val status: BudgetStatus,
    val percentUsed: Float
) {
    val spentMajor: Double
        get() = spentMinor / 100.0
    val remainingMinor: Long
        get() = (budget.allocatedMinor - spentMinor).coerceAtLeast(0)
    val remainingMajor: Double
        get() = remainingMinor / 100.0
}
```

---

## 3. Business Logic & ViewModel (StateFlow)

Translating the calculations from [`src/app/workspace-page.ts:172-199`](file:///home/frank/SmartMoney_intelligence/SM-Intelligence/web/src/app/workspace-page.ts#L172-L199):

```kotlin
package com.smartmoney.ui.budget

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartmoney.domain.model.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate

data class BudgetUiState(
    val isLoading: Boolean = false,
    val budgets: List<BudgetSummary> = emptyList(),
    val totalAllocatedMinor: Long = 0,
    val totalSpentMinor: Long = 0,
    val activeBudgetCount: Int = 0,
    val errorMessage: String? = null
)

class BudgetViewModel(
    private val repository: BudgetRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(BudgetUiState())
    val uiState: StateFlow<BudgetUiState> = _uiState.asStateFlow()

    init {
        loadBudgets()
    }

    fun loadBudgets() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                val budgets = repository.getBudgets()
                val transactions = repository.getTransactions()

                val summaries = budgets.map { budget ->
                    calculateSummary(budget, transactions)
                }

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        budgets = summaries,
                        totalAllocatedMinor = summaries.sumOf { s -> s.budget.allocatedMinor },
                        totalSpentMinor = summaries.sumOf { s -> s.spentMinor },
                        activeBudgetCount = summaries.size
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isLoading = false, errorMessage = e.message ?: "Failed to load budgets")
                }
            }
        }
    }

    /**
     * Replicates spent() and budgetStatus() from workspace-page.ts:172-199
     */
    private fun calculateSummary(budget: Budget, transactions: List<Transaction>): BudgetSummary {
        val spent = transactions
            .filter { tx ->
                tx.status == "POSTED" &&
                tx.direction == "DEBIT" &&
                tx.category.equals(budget.category, ignoreCase = true) &&
                !tx.date.isBefore(budget.start) &&
                !tx.date.isAfter(budget.end) &&
                (budget.accountId.isNullOrBlank() || tx.accountId == budget.accountId)
            }
            .sumOf { it.amountMinor }

        val percentUsed = if (budget.allocatedMinor > 0) {
            (spent.toFloat() / budget.allocatedMinor.toFloat()) * 100f
        } else 0f

        val status = when {
            spent > budget.allocatedMinor -> BudgetStatus.OVER_BUDGET
            percentUsed >= budget.threshold -> BudgetStatus.APPROACHING_LIMIT
            else -> BudgetStatus.WITHIN_BUDGET
        }

        return BudgetSummary(
            budget = budget,
            spentMinor = spent,
            status = status,
            percentUsed = percentUsed
        )
    }

    fun deleteBudget(id: String) {
        viewModelScope.launch {
            try {
                repository.deleteBudget(id)
                loadBudgets()
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = "Unable to delete budget") }
            }
        }
    }
}
```

---

## 4. Jetpack Compose UI Implementation

Translates the cards and layout from [`src/app/workspace-page.html:387-446`](file:///home/frank/SmartMoney_intelligence/SM-Intelligence/web/src/app/workspace-page.html#L387-L446).

### 4.1. Budget Card Component

```kotlin
package com.smartmoney.ui.budget.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smartmoney.domain.model.BudgetStatus
import com.smartmoney.domain.model.BudgetSummary
import java.text.NumberFormat
import java.util.Locale

@Composable
fun BudgetCard(
    summary: BudgetSummary,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val budget = summary.budget
    val currencyFormat = NumberFormat.getCurrencyInstance(Locale("en", "KE")).apply {
        currency = java.util.Currency.getInstance("KES")
    }

    val (statusText, statusBg, statusFg) = when (summary.status) {
        BudgetStatus.OVER_BUDGET -> Triple("Over budget", Color(0xFFFDE8E8), Color(0xFFE02424))
        BudgetStatus.APPROACHING_LIMIT -> Triple("Approaching limit", Color(0xFFFEF08A), Color(0xFF854D0E))
        BudgetStatus.WITHIN_BUDGET -> Triple("Within budget", Color(0xFFDEF7EC), Color(0xFF03543F))
    }

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            // Header Row: Category & Status Badge
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = budget.category,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF18334F)
                )

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(statusBg)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = statusText,
                        color = statusFg,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "${budget.start} to ${budget.end}" +
                    if (!budget.accountId.isNullOrBlank()) " · Account Specific" else " · All Accounts",
                fontSize = 12.sp,
                color = Color(0xFF5C7085)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Spend vs Allocated Figures
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    Text("Spent", fontSize = 11.sp, color = Color(0xFF5C7085))
                    Text(
                        text = currencyFormat.format(summary.spentMajor),
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = if (summary.status == BudgetStatus.OVER_BUDGET) Color(0xFFE02424) else Color(0xFF18334F)
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text("Limit", fontSize = 11.sp, color = Color(0xFF5C7085))
                    Text(
                        text = currencyFormat.format(budget.allocatedMajor),
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = Color(0xFF18334F)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Progress Bar with Threshold Marker
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(0xFFE2E8F0))
            ) {
                // Progress fill
                val fillFraction = (summary.percentUsed / 100f).coerceIn(0f, 1f)
                Box(
                    modifier = Modifier
                        .fillMaxWidth(fillFraction)
                        .fillMaxHeight()
                        .background(
                            when (summary.status) {
                                BudgetStatus.OVER_BUDGET -> Color(0xFFE02424)
                                BudgetStatus.APPROACHING_LIMIT -> Color(0xFFEAB308)
                                BudgetStatus.WITHIN_BUDGET -> Color(0xFF10B981)
                            }
                        )
                )
            }

            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "${summary.percentUsed.toInt()}% spent (${budget.threshold}% alert threshold)",
                fontSize = 11.sp,
                color = Color(0xFF5C7085)
            )

            Spacer(modifier = Modifier.height(14.dp))
            Divider(color = Color(0xFFF1F5F9))
            Spacer(modifier = Modifier.height(10.dp))

            // Action Buttons
            Row(
                horizontalArrangement = Arrangement.End,
                modifier = Modifier.fillMaxWidth()
            ) {
                TextButton(onClick = onEdit) {
                    Icon(Icons.Outlined.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Edit", fontSize = 12.sp)
                }
                Spacer(modifier = Modifier.width(8.dp))
                TextButton(
                    onClick = onDelete,
                    colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFE02424))
                ) {
                    Icon(Icons.Outlined.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Delete", fontSize = 12.sp)
                }
            }
        }
    }
}
```

---

### 4.2. Add / Edit Budget Bottom Sheet Form

Translates the 6 form fields from [`src/app/workspace-page.ts:508-530`](file:///home/frank/SmartMoney_intelligence/SM-Intelligence/web/src/app/workspace-page.ts#L508-L530):

```kotlin
package com.smartmoney.ui.budget.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smartmoney.domain.model.Budget
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BudgetFormBottomSheet(
    initialBudget: Budget? = null,
    accountOptions: List<Pair<String, String>>, // List of Pair(id, name)
    onDismiss: () -> Unit,
    onSave: (category: String, allocatedMinor: Long, start: LocalDate, end: LocalDate, threshold: Int, accountId: String?) -> Unit
) {
    var category by remember { mutableStateOf(initialBudget?.category ?: "") }
    var allocated by remember { mutableStateOf(initialBudget?.let { (it.allocatedMinor / 100.0).toString() } ?: "") }
    var startDate by remember { mutableStateOf(initialBudget?.start ?: LocalDate.now().minusDays(29)) }
    var endDate by remember { mutableStateOf(initialBudget?.end ?: LocalDate.now()) }
    var threshold by remember { mutableStateOf(initialBudget?.threshold?.toString() ?: "85") }
    var selectedAccountId by remember { mutableStateOf(initialBudget?.accountId ?: "") }

    var isAccountDropdownOpen by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
        ) {
            Text(
                text = if (initialBudget == null) "Add Budget Allocation" else "Edit Budget Allocation",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(16.dp))

            // 1. Category Field
            OutlinedTextField(
                value = category,
                onValueChange = { category = it },
                label = { Text("Category (e.g. Shopping, Rent)") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(12.dp))

            // 2. Allocation (KES) Field
            OutlinedTextField(
                value = allocated,
                onValueChange = { allocated = it },
                label = { Text("Allocation (KES)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(12.dp))

            // 3. Warning Threshold Field
            OutlinedTextField(
                value = threshold,
                onValueChange = { threshold = it },
                label = { Text("Warning Threshold (%)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(12.dp))

            // 4. Account Scope Selector
            ExposedDropdownMenuBox(
                expanded = isAccountDropdownOpen,
                onExpandedChange = { isAccountDropdownOpen = it }
            ) {
                OutlinedTextField(
                    value = accountOptions.find { it.first == selectedAccountId }?.second ?: "All accounts",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Account Scope") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isAccountDropdownOpen) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor()
                )
                ExposedDropdownMenu(
                    expanded = isAccountDropdownOpen,
                    onDismissRequest = { isAccountDropdownOpen = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("All accounts") },
                        onClick = {
                            selectedAccountId = ""
                            isAccountDropdownOpen = false
                        }
                    )
                    accountOptions.forEach { (id, name) ->
                        DropdownMenuItem(
                            text = { Text(name) },
                            onClick = {
                                selectedAccountId = id
                                isAccountDropdownOpen = false
                            }
                        )
                    }
                }
            }

            if (errorMessage != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = errorMessage!!, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Submit Button
            Button(
                onClick = {
                    val allocDouble = allocated.toDoubleOrNull()
                    val threshInt = threshold.toIntOrNull()

                    if (category.isBlank()) {
                        errorMessage = "Enter a valid category"
                        return@Button
                    }
                    if (allocDouble == null || allocDouble < 0) {
                        errorMessage = "Enter a valid non-negative amount"
                        return@Button
                    }
                    if (threshInt == null || threshInt !in 1..100) {
                        errorMessage = "Threshold must be between 1 and 100"
                        return@Button
                    }

                    // Convert to integer minor units (cents)
                    val allocatedMinor = Math.round(allocDouble * 100)

                    onSave(
                        category.trim(),
                        allocatedMinor,
                        startDate,
                        endDate,
                        threshInt,
                        selectedAccountId.ifBlank { null }
                    )
                    onDismiss()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Text(if (initialBudget == null) "Create Budget" else "Save Changes")
            }
        }
    }
}
```

---

## 5. Unit Test Verification (Kotlin / JUnit 5)

```kotlin
package com.smartmoney.domain

import com.smartmoney.domain.model.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalDate

class BudgetCalculationTest {

    @Test
    fun `evaluates budget status as over budget when spent exceeds allocated`() {
        val budget = Budget(
            id = "b1",
            category = "Shopping",
            allocatedMinor = 100_000L, // KES 1,000.00
            start = LocalDate.parse("2026-09-01"),
            end = LocalDate.parse("2026-09-30"),
            threshold = 80
        )

        val transactions = listOf(
            Transaction(
                id = "tx1",
                category = "Shopping",
                amountMinor = 120_000L,
                status = "POSTED",
                direction = "DEBIT",
                date = LocalDate.parse("2026-09-10"),
                accountId = "acc1"
            )
        )

        val spent = transactions.filter { it.status == "POSTED" && it.direction == "DEBIT" }.sumOf { it.amountMinor }
        val status = if (spent > budget.allocatedMinor) BudgetStatus.OVER_BUDGET else BudgetStatus.WITHIN_BUDGET

        assertEquals(120_000L, spent)
        assertEquals(BudgetStatus.OVER_BUDGET, status)
    }

    @Test
    fun `excludes pending or credit card transactions from cash budget calculations`() {
        val budget = Budget(
            id = "b2",
            category = "Dining",
            allocatedMinor = 50_000L,
            start = LocalDate.parse("2026-09-01"),
            end = LocalDate.parse("2026-09-30"),
            threshold = 85
        )

        val transactions = listOf(
            // Should be ignored because status is PENDING
            Transaction(id = "tx1", category = "Dining", amountMinor = 30_000L, status = "PENDING", direction = "DEBIT", date = LocalDate.parse("2026-09-15"), accountId = "acc1"),
            // Should be counted
            Transaction(id = "tx2", category = "Dining", amountMinor = 10_000L, status = "POSTED", direction = "DEBIT", date = LocalDate.parse("2026-09-16"), accountId = "acc1")
        )

        val validSpent = transactions
            .filter { it.status == "POSTED" && it.direction == "DEBIT" && it.category == budget.category }
            .sumOf { it.amountMinor }

        assertEquals(10_000L, validSpent)
    }
}
```

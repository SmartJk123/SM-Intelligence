# Investment Feature: Implementation & Kotlin Mobile Blueprint

This document details how the **Investments** monitoring feature is implemented in this web application and provides a complete, production-ready specification and Jetpack Compose (Kotlin / Android) implementation guide.

---

## 1. Web Application Implementation Analysis

The Investments module is implemented across three primary files:
- **Data Model**: [`src/app/workspace-api.ts:33-41`](file:///home/frank/SmartMoney_intelligence/SM-Intelligence/web/src/app/workspace-api.ts#L33-L41) defines the [`Investment`](file:///home/frank/SmartMoney_intelligence/SM-Intelligence/web/src/app/workspace-api.ts#L33) interface.
- **Business Logic & Calculations**: [`src/app/workspace-page.ts:200-208`](file:///home/frank/SmartMoney_intelligence/SM-Intelligence/web/src/app/workspace-page.ts#L200-L208) computes total principal, portfolio valuation, and 90-day maturity alerts.
- **UI & Form Template**: [`src/app/workspace-page.html:447-515`](file:///home/frank/SmartMoney_intelligence/SM-Intelligence/web/src/app/workspace-page.html#L447-L515) and [`src/app/workspace-page.ts:531-559`](file:///home/frank/SmartMoney_intelligence/SM-Intelligence/web/src/app/workspace-page.ts#L531-L559).

### Core Architectural Rules

1. **Portfolio Valuation Integrity (`portfolio`)** ([`workspace-page.ts:200-205`](file:///home/frank/SmartMoney_intelligence/SM-Intelligence/web/src/app/workspace-page.ts#L200-L205)):
   ```typescript
   get portfolio() {
     const rows = this.data()?.investments ?? [];
     return rows.some((i) => i.currentValueMinor === null)
       ? null
       : rows.reduce((s, i) => s + (i.currentValueMinor ?? 0), 0);
   }
   ```
   **Rule**: If any holding has an unrecorded or unknown valuation (`currentValueMinor === null`), the aggregate portfolio valuation evaluates to `null`. The UI displays **"Valuation unavailable"** instead of fabricating an incomplete or misleading total.
2. **Strict Segregation from Bank Cash**:
   - Recorded investment valuations are separate from liquid deposit accounts.
   - Investment holdings are **never** added to Available Cash or counted as bank balances.
   - The application does not trade or execute investments; it acts as a clarity and maturity tracking portfolio.
3. **Maturity Alerts** ([`workspace-page.ts:234-246`](file:///home/frank/SmartMoney_intelligence/SM-Intelligence/web/src/app/workspace-page.ts#L234-L246)):
   - Evaluates holdings with a future `maturityDate`.
   - Generates an actionable warning if an instrument matures within the next **90 days**.
4. **REST API Contract**:
   - Save / Update: `POST /api/workspace/investments`.
   - Delete: `DELETE /api/workspace/investments/:id`.

---

## 2. Kotlin Mobile Data Models

### 2.1. Domain Entities & Enums

```kotlin
package com.smartmoney.domain.model

import java.time.LocalDate
import java.time.temporal.ChronoUnit

enum class InvestmentType(val displayName: String) {
    MONEY_MARKET("Money Market"),
    FIXED_DEPOSIT("Fixed Deposit"),
    TREASURY_BILL("Treasury Bill"),
    OTHER("Other");

    companion object {
        fun fromString(value: String): InvestmentType =
            values().find { it.displayName.equals(value, ignoreCase = true) } ?: OTHER
    }
}

data class Investment(
    val id: String,
    val name: String,
    val type: InvestmentType,
    val principalMinor: Long,          // In integer cents (KES)
    val currentValueMinor: Long? = null, // Null if unrecorded or unavailable
    val valuationDate: LocalDate,
    val maturityDate: LocalDate? = null  // Null if no fixed maturity date
) {
    val principalMajor: Double
        get() = principalMinor / 100.0

    val currentValueMajor: Double?
        get() = currentValueMinor?.let { it / 100.0 }

    val gainOrLossMinor: Long?
        get() = currentValueMinor?.let { it - principalMinor }

    val daysUntilMaturity: Long?
        get() = maturityDate?.let { ChronoUnit.DAYS.between(LocalDate.now(), it) }

    val isMatured: Boolean
        get() = daysUntilMaturity?.let { it < 0 } ?: false

    val isMaturingSoon: Boolean
        get() = daysUntilMaturity?.let { it in 0..90 } ?: false
}
```

---

## 3. Business Logic & ViewModel (StateFlow)

Translating the aggregate portfolio calculations from [`src/app/workspace-page.ts:200-246`](file:///home/frank/SmartMoney_intelligence/SM-Intelligence/web/src/app/workspace-page.ts#L200-L246):

```kotlin
package com.smartmoney.ui.investment

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartmoney.domain.model.Investment
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class InvestmentUiState(
    val isLoading: Boolean = false,
    val investments: List<Investment> = emptyList(),
    val totalPrincipalMinor: Long = 0,
    val portfolioValuationMinor: Long? = null, // Null if any investment has null valuation
    val totalHoldingsCount: Int = 0,
    val maturingSoonCount: Int = 0,
    val errorMessage: String? = null
)

class InvestmentViewModel(
    private val repository: InvestmentRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(InvestmentUiState())
    val uiState: StateFlow<InvestmentUiState> = _uiState.asStateFlow()

    init {
        loadInvestments()
    }

    fun loadInvestments() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                val list = repository.getInvestments()

                // Rule: If any investment has null currentValue, overall portfolio valuation is null
                val portfolioValuation: Long? = if (list.any { it.currentValueMinor == null }) {
                    null
                } else {
                    list.sumOf { it.currentValueMinor ?: 0L }
                }

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        investments = list,
                        totalPrincipalMinor = list.sumOf { item -> item.principalMinor },
                        portfolioValuationMinor = portfolioValuation,
                        totalHoldingsCount = list.size,
                        maturingSoonCount = list.count { item -> item.isMaturingSoon }
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isLoading = false, errorMessage = e.message ?: "Failed to load investments")
                }
            }
        }
    }

    fun deleteInvestment(id: String) {
        viewModelScope.launch {
            try {
                repository.deleteInvestment(id)
                loadInvestments()
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = "Unable to delete investment") }
            }
        }
    }
}
```

---

## 4. Jetpack Compose UI Implementation

Translates the cards and layout from [`src/app/workspace-page.html:447-515`](file:///home/frank/SmartMoney_intelligence/SM-Intelligence/web/src/app/workspace-page.html#L447-L515).

### 4.1. Summary Banner (Principal vs Valuation)

```kotlin
package com.smartmoney.ui.investment.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.NumberFormat
import java.util.Locale

@Composable
fun InvestmentSummaryHeader(
    totalPrincipalMinor: Long,
    portfolioValuationMinor: Long?,
    holdingsCount: Int,
    modifier: Modifier = Modifier
) {
    val currencyFormat = NumberFormat.getCurrencyInstance(Locale("en", "KE")).apply {
        currency = java.util.Currency.getInstance("KES")
    }

    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp)
    ) {
        // Principal Card
        Column(
            modifier = Modifier
                .weight(1f)
                .background(Color(0xFFF8FBFF), RoundedCornerShape(12.dp))
                .padding(14.dp)
        ) {
            Text("Total Principal", fontSize = 11.sp, color = Color(0xFF5C7085))
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = currencyFormat.format(totalPrincipalMinor / 100.0),
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF18334F)
            )
            Text("$holdingsCount tracked holdings", fontSize = 10.sp, color = Color(0xFF5C7085))
        }

        // Portfolio Valuation Card
        Column(
            modifier = Modifier
                .weight(1f)
                .background(Color(0xFFF8FBFF), RoundedCornerShape(12.dp))
                .padding(14.dp)
        ) {
            Text("Portfolio Valuation", fontSize = 11.sp, color = Color(0xFF5C7085))
            Spacer(modifier = Modifier.height(4.dp))
            if (portfolioValuationMinor != null) {
                Text(
                    text = currencyFormat.format(portfolioValuationMinor / 100.0),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1E75E9)
                )
                Text("All valuations current", fontSize = 10.sp, color = Color(0xFF03543F))
            } else {
                Text(
                    text = "Unavailable",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF854D0E)
                )
                Text("Holding valuation pending", fontSize = 10.sp, color = Color(0xFF854D0E))
            }
        }
    }
}
```

---

### 4.2. Investment Card Component

```kotlin
package com.smartmoney.ui.investment.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccessTime
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
import com.smartmoney.domain.model.Investment
import java.text.NumberFormat
import java.util.Locale

@Composable
fun InvestmentCard(
    investment: Investment,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val currencyFormat = NumberFormat.getCurrencyInstance(Locale("en", "KE")).apply {
        currency = java.util.Currency.getInstance("KES")
    }

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            // Header Row: Name & Instrument Type Pill
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = investment.name,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF18334F)
                )

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFFEBF3FE))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = investment.type.displayName,
                        color = Color(0xFF1765C8),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Values Grid
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    Text("Principal", fontSize = 11.sp, color = Color(0xFF5C7085))
                    Text(
                        text = currencyFormat.format(investment.principalMajor),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        color = Color(0xFF18334F)
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text("Current Valuation", fontSize = 11.sp, color = Color(0xFF5C7085))
                    if (investment.currentValueMajor != null) {
                        Text(
                            text = currencyFormat.format(investment.currentValueMajor),
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = Color(0xFF18334F)
                        )
                        Text(
                            text = "As of ${investment.valuationDate}",
                            fontSize = 10.sp,
                            color = Color(0xFF5C7085)
                        )
                    } else {
                        Text(
                            text = "Valuation pending",
                            fontWeight = FontWeight.Medium,
                            fontSize = 13.sp,
                            color = Color(0xFF854D0E)
                        )
                    }
                }
            }

            // Maturity Notification Pill (if within 90 days)
            if (investment.maturityDate != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (investment.isMaturingSoon) Color(0xFFFEF08A) else Color(0xFFF1F5F9))
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.AccessTime,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = if (investment.isMaturingSoon) Color(0xFF854D0E) else Color(0xFF5C7085)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (investment.isMaturingSoon) {
                            "Matures on ${investment.maturityDate} (${investment.daysUntilMaturity} days left)"
                        } else {
                            "Maturity date: ${investment.maturityDate}"
                        },
                        fontSize = 11.sp,
                        fontWeight = if (investment.isMaturingSoon) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (investment.isMaturingSoon) Color(0xFF854D0E) else Color(0xFF5C7085)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Divider(color = Color(0xFFF1F5F9))
            Spacer(modifier = Modifier.height(8.dp))

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

### 4.3. Add / Edit Investment Bottom Sheet Form

Translates the form fields from [`src/app/workspace-page.ts:531-559`](file:///home/frank/SmartMoney_intelligence/SM-Intelligence/web/src/app/workspace-page.ts#L531-L559):

```kotlin
package com.smartmoney.ui.investment.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smartmoney.domain.model.Investment
import com.smartmoney.domain.model.InvestmentType
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InvestmentFormBottomSheet(
    initialInvestment: Investment? = null,
    onDismiss: () -> Unit,
    onSave: (name: String, type: InvestmentType, principalMinor: Long, currentValueMinor: Long?, valuationDate: LocalDate, maturityDate: LocalDate?) -> Unit
) {
    var name by remember { mutableStateOf(initialInvestment?.name ?: "") }
    var selectedType by remember { mutableStateOf(initialInvestment?.type ?: InvestmentType.MONEY_MARKET) }
    var principal by remember { mutableStateOf(initialInvestment?.let { (it.principalMinor / 100.0).toString() } ?: "") }
    var currentValue by remember { mutableStateOf(initialInvestment?.currentValueMinor?.let { (it / 100.0).toString() } ?: "") }
    var valuationDate by remember { mutableStateOf(initialInvestment?.valuationDate ?: LocalDate.now()) }
    var maturityDate by remember { mutableStateOf(initialInvestment?.maturityDate) }

    var isTypeDropdownExpanded by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
        ) {
            Text(
                text = if (initialInvestment == null) "Add Investment Record" else "Edit Investment Record",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(16.dp))

            // 1. Name Field
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Investment Name (e.g. Sanlam MMF)") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(12.dp))

            // 2. Type Dropdown
            ExposedDropdownMenuBox(
                expanded = isTypeDropdownExpanded,
                onExpandedChange = { isTypeDropdownExpanded = it }
            ) {
                OutlinedTextField(
                    value = selectedType.displayName,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Investment Type") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isTypeDropdownExpanded) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor()
                )
                ExposedDropdownMenu(
                    expanded = isTypeDropdownExpanded,
                    onDismissRequest = { isTypeDropdownExpanded = false }
                ) {
                    InvestmentType.values().forEach { type ->
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
            Spacer(modifier = Modifier.height(12.dp))

            // 3. Principal Amount
            OutlinedTextField(
                value = principal,
                onValueChange = { principal = it },
                label = { Text("Principal (KES)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(12.dp))

            // 4. Current Valuation (Optional)
            OutlinedTextField(
                value = currentValue,
                onValueChange = { currentValue = it },
                label = { Text("Current Valuation (KES) · Optional") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth()
            )

            if (errorMessage != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = errorMessage!!, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Submit Button
            Button(
                onClick = {
                    val pDouble = principal.toDoubleOrNull()
                    val cvDouble = if (currentValue.isNotBlank()) currentValue.toDoubleOrNull() else null

                    if (name.isBlank()) {
                        errorMessage = "Investment name cannot be empty"
                        return@Button
                    }
                    if (pDouble == null || pDouble < 0) {
                        errorMessage = "Enter a valid positive principal amount"
                        return@Button
                    }
                    if (currentValue.isNotBlank() && (cvDouble == null || cvDouble < 0)) {
                        errorMessage = "Valuation amount must be positive"
                        return@Button
                    }

                    val principalMinor = Math.round(pDouble * 100)
                    val currentValueMinor = cvDouble?.let { Math.round(it * 100) }

                    onSave(
                        name.trim(),
                        selectedType,
                        principalMinor,
                        currentValueMinor,
                        valuationDate,
                        maturityDate
                    )
                    onDismiss()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Text(if (initialInvestment == null) "Save Investment" else "Update Record")
            }
        }
    }
}
```

---

## 5. Unit Test Verification (Kotlin / JUnit 5)

```kotlin
package com.smartmoney.domain

import com.smartmoney.domain.model.Investment
import com.smartmoney.domain.model.InvestmentType
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.time.LocalDate

class InvestmentCalculationTest {

    @Test
    fun `evaluates portfolio valuation as null when any holding valuation is unknown`() {
        val holdings = listOf(
            Investment(
                id = "inv1",
                name = "MMF Fund",
                type = InvestmentType.MONEY_MARKET,
                principalMinor = 1_000_000L,
                currentValueMinor = 1_045_000L,
                valuationDate = LocalDate.now()
            ),
            Investment(
                id = "inv2",
                name = "Fixed Deposit",
                type = InvestmentType.FIXED_DEPOSIT,
                principalMinor = 500_000L,
                currentValueMinor = null, // Unknown valuation
                valuationDate = LocalDate.now()
            )
        )

        val portfolioValuation: Long? = if (holdings.any { it.currentValueMinor == null }) {
            null
        } else {
            holdings.sumOf { it.currentValueMinor ?: 0L }
        }

        assertNull(portfolioValuation, "Portfolio valuation must evaluate to null to avoid incomplete estimates")
    }

    @Test
    fun `flags upcoming maturity when maturity date is within 90 days`() {
        val holding = Investment(
            id = "inv3",
            name = "Treasury Bill",
            type = InvestmentType.TREASURY_BILL,
            principalMinor = 2_000_000L,
            currentValueMinor = 2_150_000L,
            valuationDate = LocalDate.now(),
            maturityDate = LocalDate.now().plusDays(45) // In 45 days
        )

        assertTrue(holding.isMaturingSoon)
        assertEquals(45L, holding.daysUntilMaturity)
    }
}
```

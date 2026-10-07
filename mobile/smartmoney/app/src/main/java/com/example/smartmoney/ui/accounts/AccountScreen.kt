package com.example.smartmoney.ui.accounts
import androidx.compose.ui.graphics.Color

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import com.example.smartmoney.core.util.CurrencyUtils
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.smartmoney.domain.model.Account
import com.example.smartmoney.domain.model.BankAccount
import com.example.smartmoney.ui.components.BankLogo
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.text.DecimalFormat
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.statusBars

import androidx.compose.material3.pulltorefresh.PullToRefreshBox

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountScreen(
    viewModel: AccountViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val bankAccounts = uiState.bankAccounts
    val isLoading = uiState.isLoading
    var showLinkDialog by remember { mutableStateOf(false) }
    var accountToDelete by remember { mutableStateOf<Pair<String, String>?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val topClearance = statusBarTop + 64.dp

    PullToRefreshBox(
        isRefreshing = isLoading,
        onRefresh = { viewModel.refreshAccounts(force = true) },
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = topClearance, bottom = 100.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {


            // Section 1: Linked Bank Accounts Header
            item {
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Linked Bank Accounts",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (bankAccounts.isNotEmpty()) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text(
                                text = "${bankAccounts.size} Connected",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
            }

            // Section 2: Linked Bank Accounts Items or Empty State
            if (bankAccounts.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.AccountBalance,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "No linked bank accounts yet. Tap '+' to link an account.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                items(
                    items = bankAccounts,
                    key = { "bank_${it.id}" }
                ) { bankAccount ->
                    val accId = bankAccount.id
                    val bName = bankAccount.bankName
                    val masked = bankAccount.maskedAccountNumber
                    val isKcb = remember(bName) { bName.equals("KCB", ignoreCase = true) }

                    val onDelete = remember(accId, bName, masked) {
                        {
                            val label = "$bName ($masked)"
                            accountToDelete = accId to label
                        }
                    }

                    val onSimulateInflow: (() -> Unit)? = remember(accId, isKcb) {
                        if (isKcb) {
                            {
                                viewModel.simulateKcbInflow(
                                    amount = "1000.00",
                                    onSuccess = {
                                        scope.launch {
                                            snackbarHostState.showSnackbar("⚡ Simulated KCB inflow of KES 1,000 received!")
                                        }
                                    },
                                    onError = { error ->
                                        scope.launch {
                                            snackbarHostState.showSnackbar("Simulation failed: $error")
                                        }
                                    }
                                )
                            }
                        } else null
                    }

                    val onSimulateOutflow: (() -> Unit)? = remember(accId, isKcb) {
                        if (isKcb) {
                            {
                                viewModel.simulateKcbOutflow(
                                    amount = "500.00",
                                    onSuccess = {
                                        scope.launch {
                                            snackbarHostState.showSnackbar("⚡ Simulated KCB outflow of KES 500 paid!")
                                        }
                                    },
                                    onError = { error ->
                                        scope.launch {
                                            snackbarHostState.showSnackbar("Simulation failed: $error")
                                        }
                                    }
                                )
                            }
                        } else null
                    }

                    BankAccountCard(
                        bankAccount = bankAccount,
                        onDelete = onDelete,
                        onSimulateInflow = onSimulateInflow,
                        onSimulateOutflow = onSimulateOutflow
                    )
                }
            }

            // Bottom Spacer to prevent FAB overlaying last card
            item {
                Spacer(modifier = Modifier.height(64.dp))
            }
        }

        FloatingActionButton(
            onClick = { showLinkDialog = true },
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(bottom = 80.dp, end = 24.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "Link Bank Account"
            )
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 80.dp)
        )
    }

        // Link Bank Account Modal Dialog
        if (showLinkDialog) {
            LinkBankAccountDialog(
                onDismiss = { showLinkDialog = false },
                onSave = { bank, accNumber, cardType ->
                    viewModel.addBankAccount(
                        bankName = bank,
                        accountNumber = accNumber,
                        cardType = cardType,
                        onSuccess = {
                            scope.launch {
                                snackbarHostState.showSnackbar("Bank account linked successfully!")
                            }
                        },
                        onError = { message ->
                            scope.launch {
                                snackbarHostState.showSnackbar("Error: $message")
                            }
                        }
                    )
                    showLinkDialog = false
                }
            )
        }

        // Delete Account Confirmation Dialog
        if (accountToDelete != null) {
            val (id, displayName) = accountToDelete!!
            AlertDialog(
                onDismissRequest = { accountToDelete = null },
                title = {
                    Text(
                        text = "Remove Account?",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Text(
                        text = "Are you sure you want to remove \"$displayName\"? This will permanently delete the account and its records from the database.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                },
                confirmButton = {
                    Button(
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        ),
                        onClick = {
                            val targetId = id
                            val targetName = displayName
                            accountToDelete = null
                            viewModel.removeAccount(
                                accountId = targetId,
                                onSuccess = {
                                    scope.launch {
                                        snackbarHostState.showSnackbar("Account \"$targetName\" removed successfully.")
                                    }
                                },
                                onError = { message ->
                                    scope.launch {
                                        snackbarHostState.showSnackbar("Failed to remove account: $message")
                                    }
                                }
                            )
                        }
                    ) {
                        Text("Delete", color = MaterialTheme.colorScheme.onError)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { accountToDelete = null }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LinkBankAccountDialog(
    onDismiss: () -> Unit,
    onSave: (bank: String, accNumber: String, cardType: String) -> Unit
) {
    val bankOptions = listOf("NCBA", "Equity", "KCB", "Stanbic")
    var bankExpanded by remember { mutableStateOf(false) }
    var selectedBank by remember { mutableStateOf("") }

    var accountNumber by remember { mutableStateOf("") }

    val cardTypeOptions = listOf("Debit", "Credit")
    var selectedTypeIndex by remember { mutableIntStateOf(0) }

    var validationError by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Link Bank Account",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // 1. Select Bank Dropdown
                ExposedDropdownMenuBox(
                    expanded = bankExpanded,
                    onExpandedChange = { bankExpanded = it },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = selectedBank,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Select Bank") },
                        leadingIcon = if (selectedBank.isNotBlank()) {
                            {
                                BankLogo(
                                    bankName = selectedBank,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        } else null,
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = bankExpanded)
                        },
                        colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                        modifier = Modifier
                            .menuAnchor(androidx.compose.material3.ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                            .fillMaxWidth()
                    )

                    ExposedDropdownMenu(
                        expanded = bankExpanded,
                        onDismissRequest = { bankExpanded = false }
                    ) {
                        bankOptions.forEach { bank ->
                            DropdownMenuItem(
                                leadingIcon = {
                                    BankLogo(
                                        bankName = bank,
                                        modifier = Modifier.size(28.dp)
                                    )
                                },
                                text = { Text(bank, fontWeight = FontWeight.Medium) },
                                onClick = {
                                    selectedBank = bank
                                    bankExpanded = false
                                    validationError = null
                                }
                            )
                        }
                    }
                }

                // 2. Account Number TextField (numeric keyboard)
                OutlinedTextField(
                    value = accountNumber,
                    onValueChange = { input ->
                        if (input.all { it.isDigit() }) {
                            accountNumber = input
                            validationError = null
                        }
                    },
                    label = { Text("Account Number") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // 3. Card Type Segmented Button
                Column {
                    Text(
                        text = "Card Type",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    SingleChoiceSegmentedButtonRow(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        cardTypeOptions.forEachIndexed { index, label ->
                            SegmentedButton(
                                shape = SegmentedButtonDefaults.itemShape(
                                    index = index,
                                    count = cardTypeOptions.size
                                ),
                                onClick = {
                                    selectedTypeIndex = index
                                    validationError = null
                                },
                                selected = index == selectedTypeIndex
                            ) {
                                Text(label)
                            }
                        }
                    }
                }

                // Validation Error Message
                if (validationError != null) {
                    Text(
                        text = validationError ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (selectedBank.isBlank()) {
                        validationError = "Please select a bank."
                    } else if (accountNumber.isBlank()) {
                        validationError = "Please enter your account number."
                    } else {
                        onSave(selectedBank, accountNumber, cardTypeOptions[selectedTypeIndex])
                    }
                }
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

/**
 * Material 3 Card displaying a linked bank account.
 * Displays Bank Name, masked Account Number (e.g., "**** 1234"), and Card Type badge.
 */
@Composable
fun BankAccountCard(
    bankAccount: BankAccount,
    onDelete: () -> Unit = {},
    onSimulateInflow: (() -> Unit)? = null,
    onSimulateOutflow: (() -> Unit)? = null
) {
    val isKcb = remember(bankAccount.bankName) { bankAccount.bankName.equals("KCB", ignoreCase = true) }
    val maskedNumber = remember(bankAccount.accountNumber) { bankAccount.maskedAccountNumber }
    val formattedBalance = remember(bankAccount.balance) { CurrencyUtils.formatKes(bankAccount.balance) }

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // -------------------------------------------------------------
            // 1. TOP HEADER ROW: Bank Identity, Card Type & Delete Action
            // -------------------------------------------------------------
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    BankLogo(
                        bankName = bankAccount.bankName,
                        modifier = Modifier.size(40.dp),
                        shape = RoundedCornerShape(10.dp)
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Text(
                            text = bankAccount.bankName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = maskedNumber,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }


                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Text(
                            text = bankAccount.cardType,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }

                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Delete,
                            contentDescription = "Remove bank account",
                            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.75f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Subtle divider for clean visual hierarchy
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                thickness = 1.dp
            )

            Spacer(modifier = Modifier.height(12.dp))

            // -------------------------------------------------------------
            // 2. BALANCE & STATUS SECTION
            // -------------------------------------------------------------
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Column {
                    Text(
                        text = "AVAILABLE BALANCE",
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = formattedBalance,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                // Status Indicator
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if (isKcb) Color(0xFF4CAF50).copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(if (isKcb) Color(0xFF4CAF50) else MaterialTheme.colorScheme.primary)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = if (isKcb) "BUNI Live IPN" else "Connected",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = if (isKcb) Color(0xFF2E7D32) else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // -------------------------------------------------------------
            // 3. OPTIONAL KCB SANDBOX SIMULATION ACTIONS STRIP
            // -------------------------------------------------------------
            if (isKcb && (onSimulateInflow != null || onSimulateOutflow != null)) {
                Spacer(modifier = Modifier.height(14.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (onSimulateInflow != null) {
                        Surface(
                            onClick = onSimulateInflow,
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFF4CAF50).copy(alpha = 0.12f),
                            border = BorderStroke(1.dp, Color(0xFF4CAF50).copy(alpha = 0.35f)),
                            modifier = Modifier.weight(1f)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 7.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ArrowUpward,
                                    contentDescription = null,
                                    tint = Color(0xFF2E7D32),
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "+ Inflow (KES 1k)",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF2E7D32)
                                )
                            }
                        }
                    }

                    if (onSimulateOutflow != null) {
                        Surface(
                            onClick = onSimulateOutflow,
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.error.copy(alpha = 0.1f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.35f)),
                            modifier = Modifier.weight(1f)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 7.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ArrowDownward,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "- Outflow (KES 500)",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AccountCard(
    account: Account,
    onDelete: () -> Unit = {}
) {
    val formattedBalance = remember(account.currency, account.availableBalance) {
        "${account.currency} ${account.availableBalance.toPlainString()}"
    }
    val institutionSubtitle = remember(account.institution, account.accountType) {
        "${account.institution} • ${account.accountType}"
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BankLogo(
                bankName = account.institution,
                modifier = Modifier.size(44.dp),
                shape = RoundedCornerShape(10.dp)
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = account.accountName,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = institutionSubtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = formattedBalance,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Outlined.Delete,
                    contentDescription = "Remove account",
                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
                )
            }
        }
    }
}

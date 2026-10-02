package com.example.smartmoney.ui.investment

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.WorkOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.smartmoney.domain.model.Investment
import com.example.smartmoney.ui.investment.components.InvestmentCard
import com.example.smartmoney.ui.investment.components.InvestmentFormBottomSheet
import com.example.smartmoney.ui.investment.components.InvestmentSummaryHeader
import com.example.smartmoney.ui.theme.LocalDarkTheme
import com.example.smartmoney.ui.theme.SmartMoneyColors

@Composable
fun InvestmentScreen(
    viewModel: InvestmentViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val isDark = LocalDarkTheme.current

    var isBottomSheetOpen by remember { mutableStateOf(false) }
    var investmentToEdit by remember { mutableStateOf<Investment?>(null) }
    var investmentToDeleteId by remember { mutableStateOf<String?>(null) }

    Scaffold(
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    investmentToEdit = null
                    isBottomSheetOpen = true
                },
                icon = { Icon(Icons.Default.Add, contentDescription = "Add Investment") },
                text = { Text("New Holding", fontWeight = FontWeight.Bold) },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
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
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 88.dp)
            ) {
                // 1. Metric Summary Banner
                item {
                    InvestmentSummaryHeader(
                        totalPrincipalMinor = uiState.totalPrincipalMinor,
                        portfolioValuationMinor = uiState.portfolioValuationMinor,
                        holdingsCount = uiState.totalHoldingsCount,
                        maturingSoonCount = uiState.maturingSoonCount
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                }

                // 2. Section Header
                item {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Tracked Holdings",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = "${uiState.investments.size} instruments",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }

                // 3. Investments List or Empty State
                if (uiState.investments.isEmpty()) {
                    item {
                        InvestmentEmptyState(
                            onAddInvestment = {
                                investmentToEdit = null
                                isBottomSheetOpen = true
                            },
                            isDark = isDark
                        )
                    }
                } else {
                    items(uiState.investments, key = { it.id }) { investment ->
                        InvestmentCard(
                            investment = investment,
                            onEdit = {
                                investmentToEdit = investment
                                isBottomSheetOpen = true
                            },
                            onDelete = {
                                investmentToDeleteId = investment.id
                            }
                        )
                    }
                }
            }
        }

        // Add / Edit Bottom Sheet Form
        if (isBottomSheetOpen) {
            InvestmentFormBottomSheet(
                initialInvestment = investmentToEdit,
                onDismiss = {
                    isBottomSheetOpen = false
                    investmentToEdit = null
                },
                onSave = { id, name, type, principalMinor, currentValueMinor, valuationDate, maturityDate ->
                    viewModel.saveInvestment(
                        id = id,
                        name = name,
                        type = type,
                        principalMinor = principalMinor,
                        currentValueMinor = currentValueMinor,
                        valuationDate = valuationDate,
                        maturityDate = maturityDate
                    )
                }
            )
        }

        // Delete Confirmation Dialog
        if (investmentToDeleteId != null) {
            AlertDialog(
                onDismissRequest = { investmentToDeleteId = null },
                title = { Text("Delete Investment Record?") },
                text = { Text("Are you sure you want to remove this investment holding from your portfolio?") },
                confirmButton = {
                    Button(
                        onClick = {
                            investmentToDeleteId?.let { viewModel.deleteInvestment(it) }
                            investmentToDeleteId = null
                        }
                    ) {
                        Text("Delete")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { investmentToDeleteId = null }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

@Composable
private fun InvestmentEmptyState(
    onAddInvestment: () -> Unit,
    isDark: Boolean = false
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isDark) SmartMoneyColors.DarkSurface else Color.White
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.WorkOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "No Investment Records",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Track your money market funds, fixed deposits, treasury bills and monitor 90-day maturity windows.",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            Spacer(modifier = Modifier.height(20.dp))
            Button(
                onClick = onAddInvestment,
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Add First Investment")
            }
        }
    }
}

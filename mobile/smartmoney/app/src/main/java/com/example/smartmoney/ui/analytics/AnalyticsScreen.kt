package com.example.smartmoney.ui.analytics
import androidx.compose.ui.unit.sp


import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.smartmoney.ui.analytics.components.*
import com.example.smartmoney.ui.analytics.model.AnalyticsUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalyticsScreen(
    viewModel: AnalyticsViewModel,
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Financial Analytics", style = MaterialTheme.typography.titleMedium) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                windowInsets = WindowInsets(0, 0, 0, 0) // Prevents double status bar padding if parent already applies it
            )
        },
        modifier = Modifier
    ) { paddingValues ->
        if (uiState.isLoading) {
            Box(modifier = Modifier.fillMaxSize().padding(paddingValues), contentAlignment = androidx.compose.ui.Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                WealthScopeHeroCard(
                    composition = uiState.composition,
                    currentScope = uiState.scopeFilter,
                    onScopeChanged = { viewModel.setWealthScope(it) }
                )
                
                ProjectionTrajectoryCard(
                    trajectory = uiState.trajectory,
                    currentHorizon = uiState.timeHorizon,
                    onHorizonChanged = { viewModel.setTimeHorizon(it) }
                )
                
                BudgetCutsSimulatorCard(
                    currentPreset = uiState.budgetCutPreset,
                    onPresetChanged = { viewModel.setBudgetCutPreset(it) },
                    extraSavings1Y = uiState.diagnostics?.projectedExtraSavings1Y,
                    runwayExtensionMonths = uiState.diagnostics?.runwayExtensionMonths
                )
                
                InvestmentMaturityCard(
                    milestones = uiState.milestones,
                    rolloverEnabled = uiState.rolloverMaturities,
                    onRolloverToggled = { viewModel.toggleRolloverMaturities(it) }
                )
                
                FinancialDiagnosticsCard(
                    diagnostics = uiState.diagnostics
                )
            }
        }
    }
}

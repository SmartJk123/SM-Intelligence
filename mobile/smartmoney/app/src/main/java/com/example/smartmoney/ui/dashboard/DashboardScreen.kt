package com.example.smartmoney.ui.dashboard

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.material3.Scaffold
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.IconButton
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.smartmoney.data.local.UserProfileManager
import com.example.smartmoney.domain.model.Notification
import com.example.smartmoney.ui.accounts.AccountScreen
import com.example.smartmoney.ui.accounts.AccountViewModel
import com.example.smartmoney.ui.accounts.LinkBankAccountDialog
import com.example.smartmoney.ui.budget.BudgetScreen
import com.example.smartmoney.ui.budget.BudgetViewModel
import com.example.smartmoney.ui.components.AppTopBar
import com.example.smartmoney.ui.components.LocalTopBarVisible
import com.example.smartmoney.ui.components.MainResponsiveShell
import com.example.smartmoney.ui.home.HomeScreen
import com.example.smartmoney.ui.home.HomeUiState
import com.example.smartmoney.ui.home.HomeViewModel
import com.example.smartmoney.ui.investment.InvestmentScreen
import com.example.smartmoney.ui.investment.InvestmentViewModel
import com.example.smartmoney.ui.menu.MenuScreen
import com.example.smartmoney.ui.more.MoreScreen
import com.example.smartmoney.ui.navigation.Screen
import com.example.smartmoney.ui.notifications.NotificationViewModel
import com.example.smartmoney.ui.notifications.NotificationsScreen
import com.example.smartmoney.ui.onboarding.AccountOnboardingBottomSheet
import com.example.smartmoney.ui.raha.RahaViewModel
import com.example.smartmoney.ui.theme.SmartMoneyColors
import com.example.smartmoney.ui.transactions.TransactionScreen
import com.example.smartmoney.ui.transactions.TransactionViewModel
import com.example.smartmoney.ui.analytics.AnalyticsViewModel
import com.example.smartmoney.ui.analytics.AnalyticsScreen
import com.example.smartmoney.ui.cashflow.CashFlowScreen
import com.example.smartmoney.ui.reports.ReportsViewModel
import com.example.smartmoney.ui.reports.ReportsScreen
import kotlinx.coroutines.launch

@androidx.compose.material3.ExperimentalMaterial3Api
@Composable
fun DashboardScreen(
    userName: String,
    userEmail: String? = null,
    userId: String? = null,
    accountViewModel: AccountViewModel,
    transactionViewModel: TransactionViewModel,
    budgetViewModel: BudgetViewModel,
    investmentViewModel: InvestmentViewModel,
    notificationViewModel: NotificationViewModel? = null,
    homeViewModel: HomeViewModel? = null,
    rahaViewModel: RahaViewModel? = null,
    analyticsViewModel: AnalyticsViewModel? = null,
    reportsViewModel: ReportsViewModel? = null,
    isDarkMode: Boolean = false,
    onToggleDarkMode: (Boolean) -> Unit = {},
    onLogout: () -> Unit
) {
    val navController = rememberNavController()
    val bottomBarPagerState = rememberPagerState(
        initialPage = 0,
        pageCount = { Screen.bottomBarScreens.size }
    )
    val coroutineScope = rememberCoroutineScope()
    val profileBitmap by UserProfileManager.profileBitmap.collectAsState()

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val navRoute = navBackStackEntry?.destination?.route ?: Screen.Dashboard.route
    val unreadNotifCount by (notificationViewModel?.unreadCount?.collectAsState() ?: remember { mutableStateOf(0) })
    val activeBanner by (notificationViewModel?.activeBanner?.collectAsState() ?: remember { mutableStateOf(null) })
    val homeUiState by (homeViewModel?.uiState?.collectAsState() ?: remember { mutableStateOf(HomeUiState.DEFAULT) })
    val investmentUiState by investmentViewModel.uiState.collectAsState()
    val mshwariInvestmentBalance = remember(homeUiState.bankAccounts) {
        homeUiState.bankAccounts
            .filter { it.bankName.contains("M-Shwari", ignoreCase = true) || it.cardType.equals("Investment", ignoreCase = true) }
            .fold(java.math.BigDecimal.ZERO) { acc, b -> acc.add(b.balance) }
    }
    val totalInvestmentBalance = remember(investmentUiState, mshwariInvestmentBalance) {
        val minor = investmentUiState.portfolioValuationMinor ?: investmentUiState.totalPrincipalMinor
        val explicitInvestments = java.math.BigDecimal(minor).divide(java.math.BigDecimal(100))
        val alreadyInRepo = investmentUiState.investments.any {
            it.name.contains("M-Shwari", ignoreCase = true) || it.name.contains("MSHWARI", ignoreCase = true)
        }
        if (alreadyInRepo) {
            explicitInvestments
        } else {
            explicitInvestments.add(mshwariInvestmentBalance)
        }
    }
    var showLinkDialog by remember { mutableStateOf(false) }
    val showOnboardingSheet = homeUiState.shouldShowWelcomeSheet && !homeUiState.isLoading

    // Automatically trigger transaction sync in the background only after settling on the Transactions tab
    LaunchedEffect(bottomBarPagerState.settledPage) {
        if (bottomBarPagerState.settledPage == 2) {
            transactionViewModel.refreshTransactions()
        }
    }

    // Handle system Back button: if on Accounts, Activity, or More tab, return to Overview tab
    BackHandler(enabled = navRoute == Screen.Dashboard.route && bottomBarPagerState.currentPage != 0) {
        coroutineScope.launch {
            bottomBarPagerState.animateScrollToPage(0)
        }
    }

    fun collectPageData(route: String): Map<String, String> {
        val result = mutableMapOf<String, String>()
        result["screenRoute"] = route
        when (route) {
            Screen.Dashboard.route -> {
                result["totalLiquidBalance"] = homeUiState.totalBalance.toString()
                result["totalInvestments"] = totalInvestmentBalance.toString()
                result["monthlyIncome"] = homeUiState.totalCashIn.toString()
                result["monthlyExpenses"] = homeUiState.totalCashOut.toString()
                result["netSavings"] = (homeUiState.totalCashIn.subtract(homeUiState.totalCashOut)).toString()
                result["accountsCount"] = homeUiState.bankAccounts.size.toString()
                result["unreadNotifications"] = unreadNotifCount.toString()
            }
            Screen.Accounts.route -> {
                val accState = accountViewModel.uiState.value
                val totalBal = accState.bankAccounts.fold(java.math.BigDecimal.ZERO) { acc, b -> acc.add(b.balance) }
                result["totalBalance"] = totalBal.toString()
                result["accountsCount"] = accState.bankAccounts.size.toString()
                if (accState.bankAccounts.isNotEmpty()) {
                    result["accountsSummary"] = accState.bankAccounts.joinToString("; ") {
                        "${it.bankName} (${it.cardType}): KES ${it.balance}"
                    }
                }
            }
            Screen.Transactions.route -> {
                val txState = transactionViewModel.uiState.value
                if (txState is com.example.smartmoney.ui.transactions.TransactionUiState.Success) {
                    val txList = txState.transactions
                    result["transactionCount"] = txList.size.toString()
                    if (txList.isNotEmpty()) {
                        result["recentTransactions"] = txList.take(5).joinToString("; ") {
                            "${it.description ?: "Transaction"}: KES ${it.amount} (${it.type})"
                        }
                    }
                }
            }
            Screen.Budgets.route -> {
                val bState = budgetViewModel.uiState.value
                result["budgetsCount"] = bState.budgets.size.toString()
                val totalAllocated = java.math.BigDecimal(bState.totalAllocatedMinor).divide(java.math.BigDecimal(100))
                val totalSpent = java.math.BigDecimal(bState.totalSpentMinor).divide(java.math.BigDecimal(100))
                result["totalBudgetLimit"] = totalAllocated.toString()
                result["totalBudgetSpent"] = totalSpent.toString()
                result["totalBudgetRemaining"] = (totalAllocated.subtract(totalSpent)).coerceAtLeast(java.math.BigDecimal.ZERO).toString()
                if (bState.budgets.isNotEmpty()) {
                    result["budgetsSummary"] = bState.budgets.joinToString("; ") {
                        "${it.budget.category}: Limit KES ${it.budget.allocatedMajor}, Spent KES ${it.spentMajor}"
                    }
                }
            }
            Screen.Investments.route -> {
                result["totalInvestments"] = totalInvestmentBalance.toString()
                result["mshwariBalance"] = mshwariInvestmentBalance.toString()
                result["investmentsCount"] = investmentUiState.investments.size.toString()
                if (investmentUiState.investments.isNotEmpty()) {
                    result["investmentsSummary"] = investmentUiState.investments.joinToString("; ") {
                        "${it.name} (${it.type}): KES ${it.currentValueMajor ?: it.principalMajor}"
                    }
                }
            }
            Screen.CashFlow.route -> {
                result["monthlyIncome"] = homeUiState.totalCashIn.toString()
                result["monthlyExpenses"] = homeUiState.totalCashOut.toString()
                result["netSavings"] = (homeUiState.totalCashIn.subtract(homeUiState.totalCashOut)).toString()
            }
            Screen.Analytics.route -> {
                result["monthlyIncome"] = homeUiState.totalCashIn.toString()
                result["monthlyExpenses"] = homeUiState.totalCashOut.toString()
            }
            Screen.Reports.route -> {
                result["reportTypes"] = "Monthly Statements, Tax Summary, Income vs Expenses"
            }
            Screen.Notifications.route -> {
                result["unreadCount"] = unreadNotifCount.toString()
                val totalCount = notificationViewModel?.notifications?.value?.size ?: 0
                result["totalCount"] = totalCount.toString()
            }
            Screen.Settings.route -> {
                result["userName"] = userName
                result["userEmail"] = userEmail ?: ""
            }
            Screen.Invoice.route -> {
                result["features"] = "Manual invoicing, OCR camera receipt scan, VAT calculator"
            }
            Screen.Menu.route -> {
                result["sections"] = "Accounts, Transactions, Budgets, Investments, Cash Flow, Analytics, Reports, Settings"
            }
        }
        return result
    }

    MainResponsiveShell(
        navController = navController,
        userName = userName,
        pagerState = bottomBarPagerState,
        unreadNotificationCount = unreadNotifCount,
        activeBannerNotification = activeBanner,
        onBannerDismiss = {
            notificationViewModel?.dismissBanner()
        },
        onBannerClick = { notification ->
            notificationViewModel?.markAsRead(notification.id)
            notificationViewModel?.dismissBanner()
            if (navRoute != Screen.Notifications.route) {
                navController.navigate(Screen.Notifications.route)
            }
        },
        onSignOut = onLogout,
        rahaViewModel = rahaViewModel,
        pageDataProvider = { route -> collectPageData(route) }
    ) { paddingValues ->
        NavHost(
            navController = navController,
            startDestination = Screen.Dashboard.route,
            enterTransition = {
                slideInHorizontally(
                    initialOffsetX = { fullWidth -> fullWidth },
                    animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing)
                ) + fadeIn(animationSpec = tween(durationMillis = 350))
            },
            exitTransition = {
                slideOutHorizontally(
                    targetOffsetX = { fullWidth -> -fullWidth / 4 },
                    animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing)
                ) + fadeOut(animationSpec = tween(durationMillis = 350))
            },
            popEnterTransition = {
                slideInHorizontally(
                    initialOffsetX = { fullWidth -> -fullWidth / 4 },
                    animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing)
                ) + fadeIn(animationSpec = tween(durationMillis = 350))
            },
            popExitTransition = {
                slideOutHorizontally(
                    targetOffsetX = { fullWidth -> fullWidth },
                    animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing)
                ) + fadeOut(animationSpec = tween(durationMillis = 350))
            },
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            composable(Screen.Dashboard.route) {
                val currentPage = bottomBarPagerState.currentPage
                val currentScreen = Screen.bottomBarScreens.getOrNull(currentPage)
                val isOverviewPage = currentScreen == Screen.Dashboard
                val currentTitle = currentScreen?.title ?: "Overview"

                val isTopBarVisible = LocalTopBarVisible.current
                // Only the Home page (Overview) and More page (Menu) have a vanishing top bar on scroll.
                // All other pages (Accounts, Transactions) keep the top bar fixed.
                val shouldVanishOnScroll = currentScreen == Screen.Dashboard || currentScreen == Screen.Menu
                val isTopBarShowing = if (shouldVanishOnScroll) isTopBarVisible else true

                Box(modifier = Modifier.fillMaxSize()) {
                    HorizontalPager(
                        state = bottomBarPagerState,
                        modifier = Modifier.fillMaxSize(),
                        beyondViewportPageCount = 1
                    ) { page ->
                        when (page) {
                            0 -> HomeScreen(
                                uiState = homeUiState,
                                totalInvestmentBalance = totalInvestmentBalance,
                                userName = userName,
                                onLinkAccountClick = { showLinkDialog = true },
                                unreadNotificationCount = unreadNotifCount,
                                onNotificationsClick = { navController.navigate(Screen.Notifications.route) },
                                onSettingsClick = { navController.navigate(Screen.Settings.route) },
                                onProfileClick = { navController.navigate(Screen.Settings.route) },
                                onGlowFinished = { homeViewModel?.clearGlow() },
                                onPreviousMonth = { homeViewModel?.previousMonth() },
                                onNextMonth = { homeViewModel?.nextMonth() },
                                onOpenBudgetsClick = { navController.navigate(Screen.Budgets.route) }
                            )
                            1 -> AccountScreen(viewModel = accountViewModel)
                            2 -> TransactionScreen(
                                viewModel = transactionViewModel,
                                onNavigateToInvoice = { navController.navigate(Screen.Invoice.route) }
                            )
                            3 -> MenuScreen(
                                userName = userName,
                                userEmail = userEmail,
                                onNavigate = { screen ->
                                    if (screen in Screen.bottomBarScreens) {
                                        val targetIndex = Screen.bottomBarScreens.indexOf(screen)
                                        if (targetIndex >= 0) {
                                            coroutineScope.launch {
                                                bottomBarPagerState.animateScrollToPage(targetIndex)
                                            }
                                        }
                                    } else {
                                        navController.navigate(screen.route) {
                                            launchSingleTop = true
                                        }
                                    }
                                },
                                onSignOut = onLogout
                            )
                        }
                    }

                    AnimatedVisibility(
                        visible = isTopBarShowing,
                        enter = slideInVertically(
                            initialOffsetY = { -it },
                            animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing)
                        ) + fadeIn(animationSpec = tween(260)),
                        exit = slideOutVertically(
                            targetOffsetY = { -it },
                            animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing)
                        ) + fadeOut(animationSpec = tween(260)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.TopCenter)
                    ) {
                        AppTopBar(
                            title = currentTitle,
                            userName = userName,
                            profileBitmap = profileBitmap,
                            isOverview = isOverviewPage,
                            canNavigateBack = false,
                            onProfileClick = { navController.navigate(Screen.Settings.route) },
                            onNotificationsClick = { navController.navigate(Screen.Notifications.route) },
                            onSettingsClick = { navController.navigate(Screen.Settings.route) },
                            unreadNotificationCount = unreadNotifCount,
                            containerColor = Color.Transparent,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
            composable(Screen.Accounts.route) {
                LaunchedEffect(Unit) {
                    navController.popBackStack(Screen.Dashboard.route, inclusive = false)
                    bottomBarPagerState.scrollToPage(1)
                }
            }
            composable(Screen.Transactions.route) {
                LaunchedEffect(Unit) {
                    navController.popBackStack(Screen.Dashboard.route, inclusive = false)
                    bottomBarPagerState.scrollToPage(2)
                }
            }
            composable(Screen.Menu.route) {
                LaunchedEffect(Unit) {
                    navController.popBackStack(Screen.Dashboard.route, inclusive = false)
                    bottomBarPagerState.scrollToPage(3)
                }
            }
            composable(Screen.CashFlow.route) {
                CashFlowScreen(
                    homeViewModel = homeViewModel,
                    transactionViewModel = transactionViewModel,
                    onBack = { navController.popBackStack() },
                    onNavigateToTransactions = {
                        navController.popBackStack(Screen.Dashboard.route, inclusive = false)
                        coroutineScope.launch {
                            bottomBarPagerState.scrollToPage(2)
                        }
                    },
                    onNavigateToReports = {
                        if (reportsViewModel != null) {
                            navController.navigate(Screen.Reports.route)
                        }
                    }
                )
            }
            composable(Screen.Budgets.route) {
                BudgetScreen(
                    viewModel = budgetViewModel,
                    accountViewModel = accountViewModel,
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Screen.Investments.route) {
                InvestmentScreen(
                    viewModel = investmentViewModel,
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Screen.Analytics.route) {
                if (analyticsViewModel != null) {
                    AnalyticsScreen(
                        viewModel = analyticsViewModel,
                        onBack = { navController.popBackStack() }
                    )
                } else {
                    FeaturePlaceholderScreen(Screen.Analytics, onBack = { navController.popBackStack() })
                }
            }
            composable(Screen.Reports.route) {
                if (reportsViewModel != null) {
                    ReportsScreen(
                        viewModel = reportsViewModel,
                        onBack = { navController.popBackStack() }
                    )
                } else {
                    FeaturePlaceholderScreen(Screen.Reports, onBack = { navController.popBackStack() })
                }
            }
            composable(Screen.Notifications.route) {
                if (notificationViewModel != null) {
                    NotificationsScreen(
                        viewModel = notificationViewModel,
                        onBack = { navController.popBackStack() }
                    )
                } else {
                    FeaturePlaceholderScreen(Screen.Notifications, onBack = { navController.popBackStack() })
                }
            }
            composable(Screen.Settings.route) {
                MoreScreen(
                    userName = userName,
                    userEmail = userEmail,
                    userId = userId,
                    isDarkMode = isDarkMode,
                    onToggleDarkMode = onToggleDarkMode,
                    onLogout = onLogout
                )
            }
            composable(Screen.Invoice.route) {
                com.example.smartmoney.ui.invoice.InvoiceScreen(
                    onBack = { navController.popBackStack() },
                    onSubmitSuccess = { pendingTx ->
                        // Add the transaction manually via view model or repository
                        // then pop back stack
                        transactionViewModel.addPendingTransaction(pendingTx)
                        navController.popBackStack()
                    }
                )
            }
        }
    }

    if (showOnboardingSheet) {
        AccountOnboardingBottomSheet(
            onDismiss = { homeViewModel?.dismissWelcomeSheet() },
            onLinkAccountClick = {
                homeViewModel?.dismissWelcomeSheet()
                showLinkDialog = true
            }
        )
    }

    if (showLinkDialog) {
        LinkBankAccountDialog(
            onDismiss = { showLinkDialog = false },
            onSave = { bank, accNumber, cardType ->
                homeViewModel?.linkBankAccount(
                    bankName = bank,
                    accountNumber = accNumber,
                    cardType = cardType
                ) ?: accountViewModel.addBankAccount(
                    bankName = bank,
                    accountNumber = accNumber,
                    cardType = cardType
                )
                showLinkDialog = false
            }
        )
    }
}

@Composable
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
private fun FeaturePlaceholderScreen(
    screen: Screen,
    onBack: () -> Unit = {}
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(screen.title, style = MaterialTheme.typography.titleMedium) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                windowInsets = WindowInsets(0, 0, 0, 0)
            )
        }
    ) { paddingValues ->
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = screen.icon,
                        contentDescription = screen.title,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(32.dp)
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = screen.title,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "${screen.title} module is ready for your financial workflows.",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(24.dp))
                OutlinedButton(
                    onClick = onBack,
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = "Back",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Back")
                }
            }
        }
    }
}
}

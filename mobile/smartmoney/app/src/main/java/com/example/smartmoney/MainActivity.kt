package com.example.smartmoney

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.smartmoney.data.local.UserProfileManager
import com.example.smartmoney.ui.accounts.AccountViewModel
import com.example.smartmoney.ui.auth.AuthViewModel
import com.example.smartmoney.ui.auth.LoginScreen
import com.example.smartmoney.ui.auth.SignUpScreen
import com.example.smartmoney.ui.onboarding.OnboardingScreen
import com.example.smartmoney.ui.budget.BudgetViewModel
import com.example.smartmoney.ui.dashboard.DashboardScreen
import com.example.smartmoney.ui.home.HomeViewModel
import com.example.smartmoney.ui.investment.InvestmentViewModel
import com.example.smartmoney.ui.notifications.NotificationViewModel
import com.example.smartmoney.ui.raha.RahaViewModel
import com.example.smartmoney.ui.theme.EnergyTheme
import com.example.smartmoney.ui.transactions.TransactionViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    @androidx.compose.material3.ExperimentalMaterial3Api
    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val appContainer = (application as SmartMoneyApplication).container

        setContent {
            var isDarkMode by rememberSaveable { mutableStateOf(false) }

            EnergyTheme(darkTheme = isDarkMode, dynamicColor = false) {
                val mainViewModel: MainViewModel = viewModel(
                    factory = MainViewModel.Factory(appContainer.userPreferencesRepository, appContainer.authRepository)
                )

                val authViewModel: AuthViewModel = viewModel(
                    factory = AuthViewModel.Factory(appContainer.authRepository)
                )

                val startDestination by mainViewModel.startDestination.collectAsState()
                
                // Keep splash screen on screen until start destination is resolved
                splashScreen.setKeepOnScreenCondition {
                    mainViewModel.isLoading.value
                }

                // If loading, don't compose main UI yet
                if (startDestination == null) return@EnergyTheme

                var isSplashFinished by rememberSaveable { mutableStateOf(false) }

                if (!isSplashFinished) {
                    com.example.smartmoney.ui.splash.SplashScreen(
                        onSplashFinished = { isSplashFinished = true }
                    )
                } else {
                    // Local state for manually switching between Login and SignUp after onboarding
                    var showLogin by rememberSaveable { mutableStateOf(startDestination == "login") }

                    when (startDestination) {
                        "onboarding" -> {
                            OnboardingScreen(
                                onFinish = {
                                    mainViewModel.completeOnboarding()
                                    // Since auth logic flows dynamically, when onboarding finishes,
                                    // the startDestination automatically changes because hasSeenOnboarding becomes true.
                                // It will evaluate to "login", but let's reset showLogin to false so it goes to sign-up
                                // Wait, actually if we set hasSeenOnboarding to true and are not logged in, it will evaluate to "login" by default in MainViewModel.
                                // Let's explicitly control showLogin.
                            }
                        )
                    }
                    else -> {
                        val isLoggedIn by authViewModel.isLoggedIn.collectAsState()
                        var isWarmUpCompleted by rememberSaveable { mutableStateOf(startDestination == "dashboard") }

                        LaunchedEffect(isLoggedIn) {
                            if (!isLoggedIn) {
                                isWarmUpCompleted = false
                            }
                        }

                        if (isLoggedIn) {
                            val currentUserId = authViewModel.currentUserId() ?: "default-user"
                            val userName = authViewModel.currentUserName() ?: "User"
                            val userEmail = authViewModel.currentUserEmail()

                            LaunchedEffect(currentUserId) {
                                if (currentUserId.isNotBlank()) {
                                    UserProfileManager.switchUser(this@MainActivity, currentUserId)
                                }
                            }

                            val accountViewModel: AccountViewModel = viewModel(
                                key = "account_$currentUserId",
                                factory = AccountViewModel.Factory(
                                    repository = appContainer.accountRepository,
                                    bankAccountRepository = appContainer.bankAccountRepository,
                                    userId = currentUserId
                                )
                            )

                            val transactionViewModel: TransactionViewModel = viewModel(
                                key = "transaction_$currentUserId",
                                factory = TransactionViewModel.Factory(
                                    repository = appContainer.transactionRepository,
                                    userId = currentUserId
                                )
                            )

                            val budgetViewModel: BudgetViewModel = viewModel(
                                key = "budget_$currentUserId",
                                factory = BudgetViewModel.Factory(
                                    budgetRepository = appContainer.budgetRepository,
                                    transactionRepository = appContainer.transactionRepository,
                                    userId = currentUserId
                                )
                            )

                            val investmentViewModel: InvestmentViewModel = viewModel(
                                key = "investment_$currentUserId",
                                factory = InvestmentViewModel.Factory(appContainer.investmentRepository)
                            )

                            val notificationViewModel: NotificationViewModel = viewModel(
                                key = "notification_$currentUserId",
                                factory = NotificationViewModel.Factory(
                                    repository = appContainer.notificationRepository,
                                    userId = currentUserId
                                )
                            )

                            val homeViewModel: HomeViewModel = viewModel(
                                key = "home_$currentUserId",
                                factory = HomeViewModel.Factory(
                                    accountRepository = appContainer.accountRepository,
                                    bankAccountRepository = appContainer.bankAccountRepository,
                                    transactionRepository = appContainer.transactionRepository,
                                    budgetRepository = appContainer.budgetRepository,
                                    userId = currentUserId
                                )
                            )

                            val rahaViewModel: RahaViewModel = viewModel(
                                key = "raha_$currentUserId",
                                factory = RahaViewModel.Factory(appContainer.rahaRepository)
                            )

                            if (!isWarmUpCompleted) {
                                val warmUpViewModel: com.example.smartmoney.ui.warmup.WarmUpSyncViewModel = viewModel(
                                    key = "warmup_$currentUserId",
                                    factory = com.example.smartmoney.ui.warmup.WarmUpSyncViewModel.Factory(
                                        accountRepository = appContainer.accountRepository,
                                        transactionRepository = appContainer.transactionRepository,
                                        userId = currentUserId
                                    )
                                )
                                com.example.smartmoney.ui.warmup.WarmUpSyncScreen(
                                    viewModel = warmUpViewModel,
                                    onWarmUpComplete = { isWarmUpCompleted = true }
                                )
                            } else {
                                DashboardScreen(
                                    userName = userName,
                                    userEmail = userEmail,
                                    userId = currentUserId,
                                    accountViewModel = accountViewModel,
                                    transactionViewModel = transactionViewModel,
                                    budgetViewModel = budgetViewModel,
                                    investmentViewModel = investmentViewModel,
                                    notificationViewModel = notificationViewModel,
                                    homeViewModel = homeViewModel,
                                    rahaViewModel = rahaViewModel,
                                    isDarkMode = isDarkMode,
                                    onToggleDarkMode = { isDarkMode = it },
                                    onLogout = {
                                        val db = appContainer.database
                                        CoroutineScope(Dispatchers.IO).launch {
                                            try {
                                                db.clearAllTables()
                                            } catch (e: Exception) {
                                                e.printStackTrace()
                                            }
                                            UserProfileManager.clearSession()
                                        }
                                        authViewModel.signOut()
                                        isWarmUpCompleted = false
                                        // After logout, showLogin will default based on what it was last, let's force true
                                        showLogin = true 
                                    }
                                )
                            }
                        } else if (showLogin) {
                            LoginScreen(
                                authViewModel = authViewModel,
                                onBackToSignUp = {
                                    showLogin = false
                                    authViewModel.clearState()
                                },
                                onLoginSuccess = {
                                    showLogin = false
                                }
                            )
                        } else {
                            SignUpScreen(
                                authViewModel = authViewModel,
                                onLoginClick = {
                                    showLogin = true
                                    authViewModel.clearState()
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
}

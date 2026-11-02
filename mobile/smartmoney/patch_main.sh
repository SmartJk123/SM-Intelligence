sed -i 's/import com.example.smartmoney.ui.transactions.TransactionViewModel/import com.example.smartmoney.ui.transactions.TransactionViewModel\nimport com.example.smartmoney.ui.analytics.AnalyticsViewModel\nimport com.example.smartmoney.ui.analytics.AnalyticsViewModelFactory/' /home/frank/AndroidStudioProjects/smartmoney/app/src/main/java/com/example/smartmoney/MainActivity.kt

sed -i '/val rahaViewModel: RahaViewModel = viewModel(/i \
                            val analyticsViewModel: AnalyticsViewModel = viewModel(\
                                key = "analytics_$currentUserId",\
                                factory = AnalyticsViewModelFactory(\
                                    bankAccountRepository = appContainer.bankAccountRepository,\
                                    investmentRepository = appContainer.investmentRepository,\
                                    transactionRepository = appContainer.transactionRepository,\
                                    budgetRepository = appContainer.budgetRepository\
                                )\
                            )\
' /home/frank/AndroidStudioProjects/smartmoney/app/src/main/java/com/example/smartmoney/MainActivity.kt

sed -i 's/rahaViewModel = rahaViewModel,/rahaViewModel = rahaViewModel,\n                                    analyticsViewModel = analyticsViewModel,/' /home/frank/AndroidStudioProjects/smartmoney/app/src/main/java/com/example/smartmoney/MainActivity.kt

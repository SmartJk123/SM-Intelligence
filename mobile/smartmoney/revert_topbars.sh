sed -i '/topBar = {/,/        },/d' /home/frank/AndroidStudioProjects/smartmoney/app/src/main/java/com/example/smartmoney/ui/budget/BudgetScreen.kt
sed -i 's/@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)//' /home/frank/AndroidStudioProjects/smartmoney/app/src/main/java/com/example/smartmoney/ui/budget/BudgetScreen.kt

sed -i '/topBar = {/,/        },/d' /home/frank/AndroidStudioProjects/smartmoney/app/src/main/java/com/example/smartmoney/ui/investment/InvestmentScreen.kt
sed -i 's/@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)//' /home/frank/AndroidStudioProjects/smartmoney/app/src/main/java/com/example/smartmoney/ui/investment/InvestmentScreen.kt

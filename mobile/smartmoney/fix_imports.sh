# Fix AnalyticsScreen.kt
sed -i '1d' /home/frank/AndroidStudioProjects/smartmoney/app/src/main/java/com/example/smartmoney/ui/analytics/AnalyticsScreen.kt
sed -i '/package com.example.smartmoney.ui.analytics/a \
import androidx.compose.ui.unit.sp\
' /home/frank/AndroidStudioProjects/smartmoney/app/src/main/java/com/example/smartmoney/ui/analytics/AnalyticsScreen.kt

# Fix DashboardScreen.kt
sed -i '1,4d' /home/frank/AndroidStudioProjects/smartmoney/app/src/main/java/com/example/smartmoney/ui/dashboard/DashboardScreen.kt
sed -i '/package com.example.smartmoney.ui.dashboard/a \
import androidx.compose.material3.ExperimentalMaterial3Api\
import androidx.compose.material3.Scaffold\
import androidx.compose.foundation.layout.Row\
import androidx.compose.material3.IconButton\
' /home/frank/AndroidStudioProjects/smartmoney/app/src/main/java/com/example/smartmoney/ui/dashboard/DashboardScreen.kt

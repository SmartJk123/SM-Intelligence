import re

with open('/home/frank/AndroidStudioProjects/smartmoney/app/src/main/java/com/example/smartmoney/ui/dashboard/DashboardScreen.kt', 'r') as f:
    content = f.read()

pattern1 = r'val homeUiState by \(homeViewModel\?\.uiState\?\.collectAsState\(\) \?: remember \{ mutableStateOf\(HomeUiState\.DEFAULT\) \}\)'

replacement1 = """val homeUiState by (homeViewModel?.uiState?.collectAsState() ?: remember { mutableStateOf(HomeUiState.DEFAULT) })
    val investmentUiState by investmentViewModel.uiState.collectAsState()
    val totalInvestmentBalance = remember(investmentUiState) {
        val minor = investmentUiState.portfolioValuationMinor ?: investmentUiState.totalPrincipalMinor
        java.math.BigDecimal(minor).divide(java.math.BigDecimal(100))
    }"""

content = re.sub(pattern1, replacement1, content, count=1)

with open('/home/frank/AndroidStudioProjects/smartmoney/app/src/main/java/com/example/smartmoney/ui/dashboard/DashboardScreen.kt', 'w') as f:
    f.write(content)

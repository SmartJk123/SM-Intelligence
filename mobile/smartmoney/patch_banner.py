import re

with open('/home/frank/AndroidStudioProjects/smartmoney/app/src/main/java/com/example/smartmoney/ui/home/HomeScreen.kt', 'r') as f:
    content = f.read()

pattern = re.compile(
    r'private fun TotalBalanceBannerCard\('
    r'.*?'
    r'// 3\. Bottom Row: Total Cash In',
    re.DOTALL
)

replacement = """private fun TotalBalanceBannerCard(
    uiState: HomeUiState,
    totalInvestmentBalance: java.math.BigDecimal,
    isBalanceVisible: Boolean,
    onToggleBalanceVisibility: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = LocalDarkTheme.current
    var isShowingInvestments by remember { mutableStateOf(false) }

    ElevatedCard(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1.586f),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = if (isDark) SmartMoneyColors.DarkSurfaceElevated else MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 18.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // 1. Top Row: Title + Toggle Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isShowingInvestments) "Total Investments" else "Total Balance",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Surface(
                    onClick = { isShowingInvestments = !isShowingInvestments },
                    shape = RoundedCornerShape(50),
                    color = if (isDark) SmartMoneyColors.DarkSurface else MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = androidx.compose.material.icons.Icons.Default.SwapHoriz,
                            contentDescription = "Switch view",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (isShowingInvestments) "View Card" else "View Investments",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Medium,
                            fontSize = 11.sp
                        )
                    }
                }
            }

            // 2. Middle Row: Big Balance amount + visibility toggle
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Start
            ) {
                val displayBalance = remember(isBalanceVisible, uiState.totalBalance, totalInvestmentBalance, isShowingInvestments) {
                    if (isBalanceVisible) {
                        formatKesCurrency(if (isShowingInvestments) totalInvestmentBalance else uiState.totalBalance)
                    } else {
                        "••••••••"
                    }
                }

                Text(
                    text = displayBalance,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.width(10.dp))

                Surface(
                    onClick = onToggleBalanceVisibility,
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.size(26.dp)
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        Icon(
                            imageVector = if (isBalanceVisible) Icons.Outlined.Visibility else Icons.Outlined.VisibilityOff,
                            contentDescription = if (isBalanceVisible) "Hide balance" else "Show balance",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
            }

            // 3. Bottom Row: Total Cash In"""

new_content = pattern.sub(replacement, content, count=1)
with open('/home/frank/AndroidStudioProjects/smartmoney/app/src/main/java/com/example/smartmoney/ui/home/HomeScreen.kt', 'w') as f:
    f.write(new_content)


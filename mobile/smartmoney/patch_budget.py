import re

with open('/home/frank/AndroidStudioProjects/smartmoney/app/src/main/java/com/example/smartmoney/ui/budget/BudgetScreen.kt', 'r') as f:
    content = f.read()

content = re.sub(
    r'    accountViewModel: AccountViewModel\? = null,\n    modifier: Modifier = Modifier',
    r'    accountViewModel: AccountViewModel? = null,\n    onBack: () -> Unit = {},\n    modifier: Modifier = Modifier',
    content, count=1
)

content = re.sub(
    r'    Scaffold\(\n        floatingActionButton = \{',
    r'''    Scaffold(
        topBar = {
            @OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
            TopAppBar(
                title = { Text("Budgets", style = MaterialTheme.typography.titleMedium) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(androidx.compose.material.icons.Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                windowInsets = WindowInsets(0, 0, 0, 0)
            )
        },
        floatingActionButton = {''',
    content, count=1
)

with open('/home/frank/AndroidStudioProjects/smartmoney/app/src/main/java/com/example/smartmoney/ui/budget/BudgetScreen.kt', 'w') as f:
    f.write(content)

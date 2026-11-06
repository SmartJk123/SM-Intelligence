import re

with open('/home/frank/AndroidStudioProjects/smartmoney/app/src/main/java/com/example/smartmoney/ui/analytics/AnalyticsScreen.kt', 'r') as f:
    content = f.read()

pattern = re.compile(
    r'    Scaffold\(\n'
    r'        modifier = Modifier\.statusBarsPadding\(\)\n'
    r'    \) \{ paddingValues ->',
    re.MULTILINE
)

replacement = """    Scaffold(
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
        modifier = Modifier.statusBarsPadding()
    ) { paddingValues ->"""

new_content = pattern.sub(replacement, content, count=1)

with open('/home/frank/AndroidStudioProjects/smartmoney/app/src/main/java/com/example/smartmoney/ui/analytics/AnalyticsScreen.kt', 'w') as f:
    f.write(new_content)

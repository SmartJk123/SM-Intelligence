import re

with open('/home/frank/AndroidStudioProjects/smartmoney/app/src/main/java/com/example/smartmoney/ui/dashboard/DashboardScreen.kt', 'r') as f:
    content = f.read()

pattern = re.compile(
    r'private fun FeaturePlaceholderScreen\(\n'
    r'    screen: Screen,\n'
    r'    onBack: \(\) -> Unit = \{\}\n'
    r'\) \{\n'
    r'    Box\(\n'
    r'        modifier = Modifier\n'
    r'            \.fillMaxSize\(\)\n'
    r'            \.padding\(24\.dp\),\n'
    r'        contentAlignment = Alignment\.Center\n'
    r'    \) \{'
)

replacement = """@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
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
                        Icon(androidx.compose.material.icons.Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
    ) {"""

new_content = pattern.sub(replacement, content, count=1)
# we also need to close the Scaffold block
new_content = re.sub(
    r'                \)\n            \}\n        \}\n    \}\n\}\n',
    r'                )\n            }\n        }\n    }\n    }\n}\n',
    new_content, count=1)

with open('/home/frank/AndroidStudioProjects/smartmoney/app/src/main/java/com/example/smartmoney/ui/dashboard/DashboardScreen.kt', 'w') as f:
    f.write(new_content)

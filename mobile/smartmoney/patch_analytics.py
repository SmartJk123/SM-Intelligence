import re

with open('/home/frank/AndroidStudioProjects/smartmoney/app/src/main/java/com/example/smartmoney/ui/analytics/AnalyticsScreen.kt', 'r') as f:
    content = f.read()

pattern = re.compile(
    r'    Scaffold\(\n'
    r'        topBar = \{\n'
    r'            TopAppBar\(\n'
    r'                title = \{ Text\("Financial Analytics"\) \},\n'
    r'                navigationIcon = \{\n'
    r'                    IconButton\(onClick = onBack\) \{\n'
    r'                        Icon\(Icons\.AutoMirrored\.Filled\.ArrowBack, contentDescription = "Back"\)\n'
    r'                    \}\n'
    r'                \}\n'
    r'            \)\n'
    r'        \}\n'
    r'    \) \{ paddingValues ->',
    re.MULTILINE
)

replacement = """    Scaffold(
        modifier = Modifier.statusBarsPadding()
    ) { paddingValues ->"""

new_content = pattern.sub(replacement, content, count=1)

with open('/home/frank/AndroidStudioProjects/smartmoney/app/src/main/java/com/example/smartmoney/ui/analytics/AnalyticsScreen.kt', 'w') as f:
    f.write(new_content)

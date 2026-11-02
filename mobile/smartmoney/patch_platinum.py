import re

with open('/home/frank/AndroidStudioProjects/smartmoney/app/src/main/java/com/example/smartmoney/ui/home/HomeScreen.kt', 'r') as f:
    content = f.read()

pattern = re.compile(
    r'    var isShowingInvestments by remember \{ mutableStateOf\(false\) \}\n'
    r'\n'
    r'    ElevatedCard\(\n'
    r'        modifier = modifier\n'
    r'            \.fillMaxWidth\(\)\n'
    r'            \.aspectRatio\(1\.586f\),\n'
    r'        shape = RoundedCornerShape\(20\.dp\),\n'
    r'        colors = CardDefaults\.elevatedCardColors\(\n'
    r'            containerColor = if \(isDark\) SmartMoneyColors\.DarkSurfaceElevated else MaterialTheme\.colorScheme\.surface\n'
    r'        \),\n'
    r'        elevation = CardDefaults\.elevatedCardElevation\(defaultElevation = 2\.dp\)\n'
    r'    \) \{\n'
    r'        Column\(\n'
    r'            modifier = Modifier\n'
    r'                \.fillMaxSize\(\)\n'
    r'                \.padding\(horizontal = 18\.dp, vertical = 14\.dp\),\n',
    re.MULTILINE
)

replacement = """    var isShowingInvestments by remember { mutableStateOf(false) }

    val platinumBrush = remember(isDark) {
        if (isDark) {
            Brush.linearGradient(
                colors = listOf(
                    Color(0xFF4A4A4A),
                    Color(0xFF2C2C2C),
                    Color(0xFF383838),
                    Color(0xFF505050)
                ),
                start = Offset.Zero,
                end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
            )
        } else {
            Brush.linearGradient(
                colors = listOf(
                    Color(0xFFE5E4E2), // Platinum base
                    Color(0xFFF8F8F8), // Highlight
                    Color(0xFFD3D3D3), // Silver
                    Color(0xFFE8E8E8)  // Light metallic reflection
                ),
                start = Offset.Zero,
                end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
            )
        }
    }

    ElevatedCard(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1.586f),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = Color.Transparent
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(platinumBrush)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 18.dp, vertical = 14.dp),
"""

new_content = pattern.sub(replacement, content, count=1)
with open('/home/frank/AndroidStudioProjects/smartmoney/app/src/main/java/com/example/smartmoney/ui/home/HomeScreen.kt', 'w') as f:
    f.write(new_content)

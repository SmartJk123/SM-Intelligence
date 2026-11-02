import re

with open('/home/frank/AndroidStudioProjects/smartmoney/app/src/main/java/com/example/smartmoney/ui/home/HomeScreen.kt', 'r') as f:
    content = f.read()

pattern = re.compile(
    r'    val platinumBrush = remember\(isDark\) \{\n'
    r'        if \(isDark\) \{\n'
    r'            Brush\.linearGradient\(\n'
    r'                colors = listOf\(\n'
    r'                    Color\(0xFF4A4A4A\),\n'
    r'                    Color\(0xFF2C2C2C\),\n'
    r'                    Color\(0xFF383838\),\n'
    r'                    Color\(0xFF505050\)\n'
    r'                \),\n'
    r'                start = Offset\.Zero,\n'
    r'                end = Offset\(Float\.POSITIVE_INFINITY, Float\.POSITIVE_INFINITY\)\n'
    r'            \)\n'
    r'        \} else \{\n'
    r'            Brush\.linearGradient\(\n'
    r'                colors = listOf\(\n'
    r'                    Color\(0xFFE5E4E2\), // Platinum base\n'
    r'                    Color\(0xFFF8F8F8\), // Highlight\n'
    r'                    Color\(0xFFD3D3D3\), // Silver\n'
    r'                    Color\(0xFFE8E8E8\)  // Light metallic reflection\n'
    r'                \),\n'
    r'                start = Offset\.Zero,\n'
    r'                end = Offset\(Float\.POSITIVE_INFINITY, Float\.POSITIVE_INFINITY\)\n'
    r'            \)\n'
    r'        \}\n'
    r'    \}'
)

replacement = """    val targetColors = remember(isDark, isShowingInvestments) {
        val baseDark = listOf(Color(0xFF4A4A4A), Color(0xFF2C2C2C), Color(0xFF383838), Color(0xFF505050))
        val baseLight = listOf(Color(0xFFE5E4E2), Color(0xFFF8F8F8), Color(0xFFD3D3D3), Color(0xFFE8E8E8))
        val base = if (isDark) baseDark else baseLight
        if (isShowingInvestments) base.reversed() else base
    }

    val c0 by animateColorAsState(targetValue = targetColors[0], animationSpec = tween(600, easing = FastOutSlowInEasing))
    val c1 by animateColorAsState(targetValue = targetColors[1], animationSpec = tween(600, easing = FastOutSlowInEasing))
    val c2 by animateColorAsState(targetValue = targetColors[2], animationSpec = tween(600, easing = FastOutSlowInEasing))
    val c3 by animateColorAsState(targetValue = targetColors[3], animationSpec = tween(600, easing = FastOutSlowInEasing))

    val platinumBrush = remember(c0, c1, c2, c3) {
        Brush.linearGradient(
            colors = listOf(c0, c1, c2, c3),
            start = Offset.Zero,
            end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
        )
    }"""

new_content = pattern.sub(replacement, content, count=1)

with open('/home/frank/AndroidStudioProjects/smartmoney/app/src/main/java/com/example/smartmoney/ui/home/HomeScreen.kt', 'w') as f:
    f.write(new_content)

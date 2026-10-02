import re

with open("app/src/main/java/com/example/smartmoney/ui/home/HomeScreen.kt", "r") as f:
    content = f.read()

new_graph_code = """@Composable
private fun CashFlowTrendGraphCard(summaryData: HomeFinancialSummary) {
    val isDark = LocalDarkTheme.current
    val trendPoints = summaryData.trend

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Cash Flow Trend",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "7-Day Activity",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (isDark) SmartMoneyColors.DarkSurfaceElevated else SmartMoneyColors.PaleMintGreen
                ) {
                    Text(
                        text = "Weekly",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isDark) Color.White else SmartMoneyColors.DarkSlateGreen,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Legend
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(Color(0xFF4CAF50)))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Cash In", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                
                Spacer(modifier = Modifier.width(16.dp))
                
                Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(Color(0xFFF44336)))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Cash Out", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Canvas Line Chart
            CashFlowLineGraphCanvas(
                points = trendPoints,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            // X-Axis Day Labels
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 32.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                trendPoints.forEach { point ->
                    Text(
                        text = point.dayLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

/**
 * Native Jetpack Compose Canvas rendering a smooth cubic-bezier line graph
 * for both Cash In and Cash Out with a Y-axis showing 1k increments.
 */
@Composable
private fun CashFlowLineGraphCanvas(
    points: List<TrendPoint>,
    modifier: Modifier = Modifier
) {
    if (points.size < 2) return

    val cashInPath = remember { Path() }
    val cashOutPath = remember { Path() }
    val cashInFillPath = remember { Path() }
    val cashOutFillPath = remember { Path() }
    
    val textMeasurer = rememberTextMeasurer()
    val isDark = LocalDarkTheme.current
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val gridColor = Color.LightGray.copy(alpha = 0.3f)

    val cashInColor = Color(0xFF4CAF50)
    val cashOutColor = Color(0xFFF44336)

    val floatIn = remember(points) { points.map { it.cashIn.toFloat() } }
    val floatOut = remember(points) { points.map { it.cashOut.toFloat() } }
    
    val maxVal = remember(floatIn, floatOut) {
        val maxI = floatIn.maxOrNull() ?: 0f
        val maxO = floatOut.maxOrNull() ?: 0f
        var mx = maxOf(maxI, maxO)
        // round up to nearest 1000
        val remainder = mx % 1000f
        if (remainder > 0) {
            mx += (1000f - remainder)
        }
        if (mx == 0f) 1000f else mx
    }

    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        if (width <= 0f || height <= 0f) return@Canvas

        val topPadding = 8.dp.toPx()
        val bottomPadding = 16.dp.toPx()
        val leftPadding = 32.dp.toPx() // Space for Y axis labels
        
        val usableHeight = height - topPadding - bottomPadding
        val usableWidth = width - leftPadding

        val stepX = usableWidth / (points.size - 1)

        // 1. Draw horizontal grid lines and Y-axis labels in 1k increments
        val increment = 1000f
        val numLines = (maxVal / increment).toInt()
        
        for (i in 0..numLines) {
            val currentValue = i * increment
            val normalizedY = currentValue / maxVal
            val gridY = height - bottomPadding - (normalizedY * usableHeight)
            
            // Draw grid line
            drawLine(
                color = gridColor,
                start = Offset(leftPadding, gridY),
                end = Offset(width, gridY),
                strokeWidth = 1.dp.toPx()
            )
            
            // Draw text label
            val textLayoutResult = textMeasurer.measure(
                text = "${(currentValue / 1000).toInt()}k",
                style = TextStyle(
                    color = labelColor,
                    fontSize = 10.sp
                )
            )
            drawText(
                textLayoutResult = textLayoutResult,
                topLeft = Offset(0f, gridY - (textLayoutResult.size.height / 2f))
            )
        }

        // Helper to draw a line
        fun drawTrend(
            amounts: List<Float>, 
            path: Path, 
            fillPath: Path, 
            lineColor: Color
        ) {
            path.reset()
            fillPath.reset()

            var prevX = leftPadding
            var prevY = 0f

            amounts.forEachIndexed { index, amount ->
                val x = leftPadding + (index * stepX)
                val normalizedY = amount / maxVal
                val y = height - bottomPadding - (normalizedY * usableHeight)

                if (index == 0) {
                    path.moveTo(x, y)
                } else {
                    val controlX = prevX + (x - prevX) / 2f
                    path.cubicTo(
                        x1 = controlX,
                        y1 = prevY,
                        x2 = controlX,
                        y2 = y,
                        x3 = x,
                        y3 = y
                    )
                }
                prevX = x
                prevY = y
            }

            fillPath.addPath(path)
            fillPath.lineTo(prevX, height - bottomPadding)
            fillPath.lineTo(leftPadding, height - bottomPadding)
            fillPath.close()

            drawPath(
                path = fillPath,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        lineColor.copy(alpha = 0.25f),
                        lineColor.copy(alpha = 0.0f)
                    ),
                    startY = topPadding,
                    endY = height - bottomPadding
                )
            )

            drawPath(
                path = path,
                color = lineColor,
                style = Stroke(
                    width = 3.dp.toPx(),
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
            )

            // Draw anchor points
            val outerRadius = 4.dp.toPx()
            val innerRadius = 2.dp.toPx()
            amounts.forEachIndexed { index, amount ->
                val x = leftPadding + (index * stepX)
                val normalizedY = amount / maxVal
                val y = height - bottomPadding - (normalizedY * usableHeight)
                val center = Offset(x, y)

                drawCircle(color = Color.White, radius = outerRadius, center = center)
                drawCircle(color = lineColor, radius = innerRadius, center = center)
            }
        }
        
        drawTrend(floatOut, cashOutPath, cashOutFillPath, cashOutColor)
        drawTrend(floatIn, cashInPath, cashInFillPath, cashInColor)
    }
}"""

# regex to find CashFlowTrendGraphCard to IncomeLineGraphCanvas to LinkedAccountsSummarySection
pattern = re.compile(r'@Composable\s+private fun CashFlowTrendGraphCard\(.*?private fun LinkedAccountsSummarySection', re.DOTALL)
new_content = pattern.sub(new_graph_code + "\n\n/**\n * Summary section at the bottom of the Home screen displaying linked bank accounts.\n * Shows a compact horizontal scroll row (LazyRow) of bank names, card types, and masked numbers.\n */\n@Composable\nprivate fun LinkedAccountsSummarySection", content)

with open("app/src/main/java/com/example/smartmoney/ui/home/HomeScreen.kt", "w") as f:
    f.write(new_content)

print("Done")

# 04. Analytics Engine & Native Canvas Visualizations

This document explains the mathematical algorithms, data structures, and native Jetpack Compose Canvas graphics engines that power the financial analytics and visual charts on the Home / Overview screen.

---

## 🧮 1. The Pure Calculation Engine: `OverviewAnalyticsCalculator`

All financial aggregations on the Home screen are computed by [`OverviewAnalyticsCalculator.kt`](file:///home/frank/AndroidStudioProjects/smartmoney/app/src/main/java/com/example/smartmoney/ui/home/analytics/calculator/OverviewAnalyticsCalculator.kt).

### Design Philosophy
1. **Zero Android Framework Dependencies:** Written in pure Kotlin. It has no references to Android Views, Contexts, or Compose state.
2. **Strict Financial Precision:** All currency calculations use `BigDecimal` with explicit rounding modes (`RoundingMode.HALF_UP`), eliminating floating-point inaccuracies.
3. **Thread Decoupling:** Executed exclusively on `Dispatchers.Default` in `HomeViewModel`, preventing UI thread stutters.

---

### Step-by-Step Calculation Breakdown

#### 1. Temporal Filtering
Transactions are parsed against the user's local timezone (`ZoneId.systemDefault()`) and filtered to match the currently selected `YearMonth`:
```kotlin
val monthTransactions = transactions.filter { tx ->
    val txDate = parseDate(tx.timestamp, zoneId)
    txDate != null && YearMonth.from(txDate) == selectedMonth
}
```

#### 2. Multi-Timeframe Cash Flow Aggregations
* **Day Timeline (Intraday):** Bins the target day into six 4-hour slots:
  ```kotlin
  val daySlots = listOf(
      Triple("12 AM", "00:00 - 04:00", 0..3),
      Triple("4 AM", "04:00 - 08:00", 4..7),
      Triple("8 AM", "08:00 - 12:00", 8..11),
      Triple("12 PM", "12:00 - 16:00", 12..15),
      Triple("4 PM", "16:00 - 20:00", 16..19),
      Triple("8 PM", "20:00 - 24:00", 20..23)
  )
  ```
* **Week Timeline (7 Days):** Aggregates daily debits and credits from Monday through Sunday.
* **Month Timeline (5 Weeks):** Aggregates expenditures across five discrete week brackets:
  * Week 1: Days 1–7
  * Week 2: Days 8–14
  * Week 3: Days 15–21
  * Week 4: Days 22–28
  * Week 5: Days 29–end of month

#### 3. Category Detection Heuristics
Each debit transaction is classified into a spending category via string matching on `transaction.description`:
```kotlin
private fun resolveCategory(description: String?): String {
    if (description.isNullOrBlank()) return "Other"
    val lower = description.lowercase()
    return when {
        lower.contains("rent") || lower.contains("apartment") -> "Rent"
        lower.contains("naivas") || lower.contains("carrefour") || lower.contains("quickmart") || lower.contains("food") -> "Groceries"
        lower.contains("uber") || lower.contains("bolt") || lower.contains("fuel") || lower.contains("shell") -> "Transport"
        lower.contains("kplc") || lower.contains("token") || lower.contains("water") || lower.contains("internet") -> "Utilities"
        lower.contains("kfc") || lower.contains("java") || lower.contains("artcaffe") || lower.contains("restaurant") -> "Dining"
        lower.contains("hospital") || lower.contains("pharmacy") || lower.contains("chemist") -> "Healthcare"
        lower.contains("school") || lower.contains("fees") || lower.contains("tuition") -> "Education"
        lower.contains("supermarket") || lower.contains("store") -> "Shopping"
        else -> "Other"
    }
}
```

#### 4. Daily Spending Calendar Heatmap Matrix
For each day $d \in [1, \text{daysInMonth}]$, debits are summed and mapped to an intensity level from 0 to 3:
```kotlin
val intensity = when {
    dayAmt == BigDecimal.ZERO -> 0
    dayAmt < BigDecimal(1000) -> 1
    dayAmt < BigDecimal(2500) -> 2
    else -> 3
}
```

#### 5. Cumulative Budget Pacing Engine
Compares actual cumulative spending velocity against a uniform linear pace:
* Total budget ceiling: $B = \sum \text{allocatedMajor}$.
* Ideal linear daily spending pace:
  $$\text{Daily Pace} = \frac{B}{\text{Days in Month}}$$
* Target planned spend for day $d$:
  $$\text{Planned}(d) = \text{Daily Pace} \times d$$
* Actual cumulative spend up to day $d$:
  $$\text{Actual}(d) = \sum_{i=1}^{d} \text{DailySpend}(i)$$
* Pacing status evaluation:
  $$\text{Status} = \begin{cases}
  \text{NO\_BUDGET} & \text{if } B = 0 \\
  \text{BEHIND\_BUDGET} & \text{if } \text{Actual} > B \\
  \text{AHEAD\_OF\_BUDGET} & \text{if } \text{Actual} \le \text{Planned} \times 0.95 \\
  \text{ON\_PACE} & \text{if } \text{Actual} \le \text{Planned} \times 1.05 \\
  \text{BEHIND\_BUDGET} & \text{otherwise}
  \end{cases}$$

---

## 🎨 2. Native Compose Canvas Visualizations

All graphs and animations on the Home screen are implemented strictly using **Jetpack Compose Native Canvas (`androidx.compose.foundation.Canvas`)**, avoiding third-party charting libraries like MPAndroidChart or Vico.

### A. Smooth Cubic Bezier Curves (`CashFlowLineGraphCanvas`)
In [`HomeScreen.kt`](file:///home/frank/AndroidStudioProjects/smartmoney/app/src/main/java/com/example/smartmoney/ui/home/HomeScreen.kt#L1125-L1298):

1. **Point Normalization:**
   Maps the 7 data points into coordinate space $(x_i, y_i)$ where $x$ is evenly distributed across canvas width and $y$ is scaled against the peak cash flow value.
2. **Cubic Spline Smoothing:**
   Instead of drawing jagged straight lines (`lineTo`), the canvas interpolates control points between consecutive points $(x_0, y_0)$ and $(x_1, y_1)$:
   ```kotlin
   val controlX1 = (prevX + currentX) / 2f
   val controlY1 = prevY
   val controlX2 = (prevX + currentX) / 2f
   val controlY2 = currentY
   path.cubicTo(controlX1, controlY1, controlX2, controlY2, currentX, currentY)
   ```
3. **Gradient Area Fill:**
   Closes the path down to the canvas baseline $(x_n, \text{height})$ and draws a soft vertical gradient brush (`Brush.verticalGradient`) underneath the line.

### B. Multi-Pass Neon Bloom Card Glow (`TopCardBottomGlowLine`)
In [`HomeScreen.kt`](file:///home/frank/AndroidStudioProjects/smartmoney/app/src/main/java/com/example/smartmoney/ui/home/HomeScreen.kt#L323-L440):

Because Android hardware Canvas does not support arbitrary live Gaussian blurs cheaply on older devices, the glow uses a **concentric multi-layer stroke technique**:
1. **Layer 1: Outer Atmospheric Halo:** Stroke width `16.dp`, alpha `0.20 * glowAlpha` (wide, diffused aura).
2. **Layer 2: Intermediate Radiance:** Stroke width `8.dp`, alpha `0.45 * glowAlpha` (concentrated illumination).
3. **Layer 3: Core Neon Filament:** Stroke width `2.5.dp`, alpha `0.95 * glowAlpha` (crisp, high-luminance pastel filament line).
4. **Curved Contour Clipping:** Uses `Path.addRoundRect()` with matching `32.dp` corner radius, clipping the glow strictly along the rounded bottom contour of the credit card.

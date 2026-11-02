# Module 03: Modern UI with Jetpack Compose

Welcome to Module 03! In this module, we dive into **Jetpack Compose** — Android's modern, declarative UI toolkit. We will examine how SmartMoney builds performant, interactive, and beautifully animated UIs.

---

## 1. Declarative UI vs Imperative XML

Historically, Android UI was built using XML layouts and manipulated imperatively in Java/Kotlin:
```kotlin
// Old Imperative Style (XML + findViewById)
val textView = findViewById<TextView>(R.id.balanceText)
textView.text = "KES 5,000"
textView.visibility = View.VISIBLE
```
In declarative UI, you describe **what the UI should look like for a given state**, rather than manually mutating views:
```kotlin
// Modern Declarative Style (Jetpack Compose)
@Composable
fun BalanceCard(balance: BigDecimal, isVisible: Boolean) {
    if (isVisible) {
        Text(text = balance.formatToKes())
    }
}
```
When state changes, Compose automatically **recomposes** (re-executes) only the functions whose inputs have changed.

---

## 2. Anatomy of a Composable Function

A Composable function is marked with the `@Composable` annotation.

Key characteristics:
1. **Can only be called from other Composables**.
2. **Transforms data into UI hierarchy**.
3. **Can run concurrently and in any order** — Composables should be free of side-effects!

### Modifiers: The Building Blocks
Modifiers allow you to decorate or augment a Composable (sizing, padding, clicks, gestures, drawing).
```kotlin
Box(
    modifier = Modifier
        .fillMaxWidth()
        .navigationBarsPadding()
        .height(76.dp)
        .padding(horizontal = 16.dp)
)
```
> [!NOTE]
> **Modifier order matters!**
> `Modifier.padding(16.dp).background(Color.Blue)` gives you an inner colored box with outer padding.
> `Modifier.background(Color.Blue).padding(16.dp)` colors the entire area, with content inset by 16.dp.

---

## 3. State & State Hoisting

In Compose, UI components should be **stateless** whenever possible. State should be "hoisted" up to the caller or ViewModel.

### State Hoisting Pattern:
Look at [`AppBottomNavigationBar.kt`](file:///home/frank/AndroidStudioProjects/smartmoney/app/src/main/java/com/example/smartmoney/ui/components/AppBottomNavigationBar.kt#L42-L48):
```kotlin
@Composable
fun AppBottomNavigationBar(
    currentRoute: String,         // State flows DOWN
    onNavigate: (Screen) -> Unit, // Events flow UP
    isCollapsed: Boolean = false,
    isScrolling: Boolean = false,
    modifier: Modifier = Modifier
)
```
- The bar does **not** decide what screen to navigate to internally.
- It merely emits an event: `onNavigate(screen)`.
- The parent component (or NavController) controls the state.

---

## 4. Advanced Animations in SmartMoney

SmartMoney features sleek, physics-based UI transitions. Look at how the bottom bar morphs when scrolling in [`AppBottomNavigationBar.kt`](file:///home/frank/AndroidStudioProjects/smartmoney/app/src/main/java/com/example/smartmoney/ui/components/AppBottomNavigationBar.kt#L70-L109):

### `updateTransition` & Spring Physics:
```kotlin
val transition = updateTransition(
    targetState = isCollapsed,
    label = "BottomNavMorphTransition"
)

val horizontalMargin by transition.animateDp(
    transitionSpec = {
        spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow
        )
    }
) { collapsed ->
    if (collapsed) 28.dp else 0.dp
}
```
Using **spring physics** ensures smooth animations that adapt naturally without jarring jumps if the scroll direction suddenly reverses.

### Hardware Acceleration with `graphicsLayer`:
```kotlin
Surface(
    modifier = Modifier.graphicsLayer { alpha = barAlpha }
)
```
> [!TIP]
> Changing `alpha` inside a `graphicsLayer` block performs the animation directly on the GPU without triggering full layout recalculations or recompositions on every frame.

---

## 5. Navigation Compose & Screen Routing

In SmartMoney, all app screens are modeled as a strongly typed sealed hierarchy in [`Screen.kt`](file:///home/frank/AndroidStudioProjects/smartmoney/app/src/main/java/com/example/smartmoney/ui/navigation/Screen.kt#L23-L36):

```kotlin
sealed class Screen(
    val route: String,
    val title: String,
    val icon: ImageVector,
    val group: NavGroup = NavGroup.NONE,
    val showInBottomBar: Boolean = false
) {
    object Dashboard : Screen(
        route = "dashboard",
        title = "Overview",
        icon = Icons.Outlined.Dashboard,
        showInBottomBar = true
    )
    object Transactions : Screen(
        route = "transactions",
        title = "Transactions",
        icon = Icons.AutoMirrored.Outlined.ReceiptLong,
        showInBottomBar = true
    )
    // ...
}
```

### Why this is better than raw string routes:
- **No typos**: You navigate using `Screen.Transactions.route` rather than typing `"transactions"`.
- **Metadata attached**: Each screen knows its own icon, title, and whether it belongs in the bottom navigation bar (`showInBottomBar`).

---

## 6. Theming & Dark Mode Support

SmartMoney uses **Material 3 (M3)** with custom theming:
- [`ui/theme/Color.kt`](file:///home/frank/AndroidStudioProjects/smartmoney/app/src/main/java/com/example/smartmoney/ui/theme/Color.kt): Central color palette (`AzureDark`, `DarkSlateGreen`, `PaleMintGreen`).
- [`ui/theme/Theme.kt`](file:///home/frank/AndroidStudioProjects/smartmoney/app/src/main/java/com/example/smartmoney/ui/theme/Theme.kt): Dynamic theming and `CompositionLocal` providers like `LocalDarkTheme.current`.

```kotlin
val isDark = LocalDarkTheme.current
val containerColor = if (isDark) SmartMoneyColors.DarkSlateGreen else Color.White
```

---

## 🏋️ Practice Exercises

1. **State Hoisting Inspection**:
   Open [`TransactionScreen.kt`](file:///home/frank/AndroidStudioProjects/smartmoney/app/src/main/java/com/example/smartmoney/ui/transactions/TransactionScreen.kt). Find how transactions are passed down to list items and how item clicks are handled.
2. **Experiment with Modifiers**:
   Try creating a simple Composable card with rounded corners, a border, and a shadow using `Modifier.clip(RoundedCornerShape(12.dp))` and `shadow(4.dp)`.
3. **Navigation Route Extension**:
   If you wanted to add a "Loan Calculator" screen to SmartMoney, what entries would you add to [`Screen.kt`](file:///home/frank/AndroidStudioProjects/smartmoney/app/src/main/java/com/example/smartmoney/ui/navigation/Screen.kt)?

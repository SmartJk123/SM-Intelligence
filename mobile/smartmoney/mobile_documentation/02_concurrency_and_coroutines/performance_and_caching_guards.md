# Performance & Caching Guards: Anti-Thrashing, Cooldowns & Distinct Emissions

In financial applications with high-frequency updates (e.g. simulated transactions, incoming bank IPN webhooks, user balance checks), naive reactive architectures can trigger **network thrashing**, **database lock contention**, and **recomposition storms**.

SmartMoney implements three specific algorithmic guards to eliminate unnecessary computational overhead.

---

## 1. Freshness Guards & Sync Cooldowns

When a user signs in, [`WarmUpSyncViewModel.kt`](file:///home/frank/AndroidStudioProjects/smartmoney/app/src/main/java/com/example/smartmoney/ui/warmup/WarmUpSyncViewModel.kt) performs an initial sync that warms the Room SQLite cache with accounts, transactions, and categories.

If the user immediately navigates to `AccountScreen` or `HomeScreen`, triggering another HTTP fetch would waste network bandwidth and battery. To prevent this, ViewModels employ a **freshness cooldown guard**:

```kotlin
// Inside AccountViewModel.kt
private var lastSyncTimestamp: Long = 0L
private val SYNC_COOLDOWN_MS = 30_000L // 30-second cooldown

fun refreshAccounts(force: Boolean = false) {
    val now = System.currentTimeMillis()
    if (!force && (now - lastSyncTimestamp < SYNC_COOLDOWN_MS)) {
        // Cache is warm! Skip network roundtrip; Room will emit current state.
        return
    }

    viewModelScope.launch {
        _isLoading.value = true
        try {
            bankAccountRepository.syncBankAccounts()
            lastSyncTimestamp = System.currentTimeMillis()
        } finally {
            _isLoading.value = false
        }
    }
}
```

### Engineering Rationale:
* **Passive Observation**: The screen **already** observes Room via `StateFlow`. If new data arrives via push notification or background worker, Room emits automatically.
* **Pull-to-Refresh**: If the user explicitly pulls down to refresh, `refreshAccounts(force = true)` bypasses the cooldown and executes a fresh fetch.

---

## 2. Eliminating Redundant Emissions: `distinctUntilChanged()`

In Room Database, any write to a table (even updating a non-observed column or touching the table metadata) triggers the SQLite Invalidation Tracker. This can cause Room DAOs to re-query and re-emit a list that is **identical in content** to the previous list.

Without filtering, downstream collectors would re-run expensive calculations:

```kotlin
val bankAccounts: StateFlow<List<BankAccount>> = bankAccountRepository.getBankAccounts()
    .distinctUntilChanged() // 🛡️ CRITICAL GUARD
    .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList()
    )
```

### How `distinctUntilChanged()` Works:
It compares each incoming emission $E_{n}$ against $E_{n-1}$ using Kotlin structural equality (`equals`). If the account list elements and attributes have not changed, $E_{n}$ is dropped immediately. Downstream `combine` blocks and Compose recompositions are completely skipped.

---

## 3. Anti-Thrashing Debounce in User Inputs

When searching transactions or filtering categories, user keystrokes occur in rapid bursts (e.g., 5 keystrokes in 300ms). Triggering a SQLite `LIKE` query or remote search on every single keystroke creates lag and freezes the keyboard.

SmartMoney uses coroutine debouncing:

```kotlin
private val searchQuery = MutableStateFlow("")

val filteredTransactions: StateFlow<List<Transaction>> = searchQuery
    .debounce(300L) // Waits for 300ms of user silence
    .distinctUntilChanged()
    .flatMapLatest { query ->
        transactionRepository.searchTransactions(query)
    }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
```

* `debounce(300L)`: Discards intermediate keystrokes until the user stops typing for 300ms.
* `flatMapLatest`: If a previous search query coroutine was still running when a new query arrives, the previous search is cancelled mid-flight, prioritizing the latest query.

---

## 4. Offloading Heavy Analytics to `Dispatchers.Default`

In [`OverviewAnalyticsCalculator.kt`](file:///home/frank/AndroidStudioProjects/smartmoney/app/src/main/java/com/example/smartmoney/ui/home/analytics/calculator/OverviewAnalyticsCalculator.kt), calculating full-month Cash Flow groupings, Daily Heatmap spending intensities, Category percentages, and Budget pacing coordinates across hundreds of transactions is computationally intensive.

We ensure that calculation runs on the background worker thread pool (`Dispatchers.Default`), never blocking `Dispatchers.Main`:

```kotlin
suspend fun calculateAnalytics(
    transactions: List<Transaction>,
    budgets: List<Budget>,
    month: YearMonth,
    dispatchers: DispatcherProvider
): OverviewAnalyticsData = withContext(dispatchers.default) {
    // Heavy math, sorting, group-by, and bezier calculations happen here!
    val cashFlow = calculateWeeklyCashFlow(transactions, month)
    val spendingByCategory = calculateCategorySpending(transactions, month)
    val dailyHeatmap = calculateDailySpending(transactions, month)
    val budgetPacing = calculateBudgetPacing(transactions, budgets, month)

    OverviewAnalyticsData(
        cashFlow = cashFlow,
        spendingByCategory = spendingByCategory,
        dailySpending = dailyHeatmap,
        budgetPacing = budgetPacing
    )
}
```

The Main thread remains 100% free to draw smooth 120Hz/60Hz Jetpack Compose UI animations.

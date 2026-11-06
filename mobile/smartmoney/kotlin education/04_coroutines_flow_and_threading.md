# Module 04: Concurrency with Coroutines & Kotlin Flows

Welcome to Module 04! Asynchronous programming is the backbone of mobile engineering. If an app performs network requests or database queries on the **Main (UI) Thread**, Android will drop frames, stutter, and eventually crash with an **Application Not Responding (ANR)** error.

In this module, we explore how SmartMoney handles asynchronous tasks using **Kotlin Coroutines** and reactive **Kotlin Flows**.

---

## 1. The Main Thread vs Background Threads

Android runs an event loop on the **Main Thread** (often called the UI Thread). This thread has only **16.6 milliseconds** per frame to measure, lay out, and draw the screen to hit 60 FPS (and only 8.3ms for 120Hz displays).

| Thread | Role in SmartMoney | Example Work |
| :--- | :--- | :--- |
| **Main (UI)** | Renders Compose UI and receives touch gestures | Rendering `HomeScreen`, clicking buttons |
| **IO** | Disk access & network operations | Querying Room SQLite, Supabase calls, Retrofit requests |
| **Default** | Heavy CPU computation | Calculating 7-day trend summaries (`TransactionTrendCalculator`) |

---

## 2. Dispatcher Abstraction: `DispatcherProvider`

Instead of hardcoding `Dispatchers.IO` directly into classes, SmartMoney defines an interface in [`DispatcherProvider.kt`](file:///home/frank/AndroidStudioProjects/smartmoney/app/src/main/java/com/example/smartmoney/core/coroutine/DispatcherProvider.kt):

```kotlin
interface DispatcherProvider {
    val main: CoroutineDispatcher
    val io: CoroutineDispatcher
    val default: CoroutineDispatcher
    val unconfined: CoroutineDispatcher
}
```

### 💡 Why this design pattern is crucial:
When writing unit tests for your ViewModels, background threads can cause tests to finish before the async work completes. By injecting `DispatcherProvider`, tests can swap in `TestDispatcherProvider` (`Dispatchers.Unconfined`), making your tests run instantly and deterministically without sleeps or delays!

---

## 3. Coroutine Scopes: `viewModelScope`

A coroutine must run within a **`CoroutineScope`** that controls its lifecycle. If the user leaves a screen, any background work started by that screen should be cancelled to prevent memory leaks and wasted battery.

In [`TransactionViewModel.kt`](file:///home/frank/AndroidStudioProjects/smartmoney/app/src/main/java/com/example/smartmoney/ui/transactions/TransactionViewModel.kt#L187-L200):
```kotlin
fun refreshTransactions() {
    scope.launch(dispatchers.io) {
        _isSyncing.value = true
        val result = repository.syncTransactions(userId = userId)
        withContext(dispatchers.main) {
            _isSyncing.value = false
            result.onFailure { error ->
                _errorState.value = error.localizedMessage
            }
        }
    }
}
```
* `scope.launch(dispatchers.io)`: Spawns the coroutine on an IO worker thread.
* `withContext(dispatchers.main)`: Smoothly switches back to the Main thread to update UI state variables safely.
* When the ViewModel is cleared (screen destroyed), `viewModelScope` automatically cancels all ongoing child coroutines.

---

## 4. Kotlin Flow: Cold vs Hot Streams

### What is a `Flow`?
A `Flow` is a stream of values that are computed asynchronously. Think of it like an asynchronous collection or conveyor belt of data.

* **Cold Flow (`Flow<T>`)**: Does not produce values until someone begins collecting it. (e.g., Room database query flows).
* **Hot Flow (`StateFlow<T>`)**: Always holds a current value and immediately emits it to new collectors. (e.g., UI state exposed by ViewModels).

---

## 5. Deconstructing the Reactive Pipeline in `TransactionViewModel`

Let's dissect the reactive pipeline in [`TransactionViewModel.kt`](file:///home/frank/AndroidStudioProjects/smartmoney/app/src/main/java/com/example/smartmoney/ui/transactions/TransactionViewModel.kt#L50-L75):

```kotlin
val uiState: StateFlow<TransactionUiState> = combine(
    repository.getTransactionsFlow(userId = userId),
    _errorState
) { cachedTransactions, errorMessage ->
    withContext(dispatchers.default) {
        if (errorMessage != null && cachedTransactions.isEmpty()) {
            TransactionUiState.Error(errorMessage)
        } else {
            TransactionUiState.Success(cachedTransactions)
        }
    }
}
.catch { e ->
    emit(TransactionUiState.Success(SAMPLE_TRANSACTIONS))
}
.stateIn(
    scope = scope,
    started = SharingStarted.WhileSubscribed(5_000),
    initialValue = TransactionUiState.Success(SAMPLE_TRANSACTIONS)
)
```

### Breaking Down Each Operator:
1. **`combine(...)`**: Listens to two flows simultaneously. Whenever *either* the database transactions change *or* an error occurs, the block re-runs with the latest values of both.
2. **`withContext(dispatchers.default)`**: Offloads state creation and filtering from the Main Thread to a background CPU thread.
3. **`.catch { ... }`**: Catches unexpected runtime exceptions in the upstream flow and emits a fallback state rather than crashing the app.
4. **`.stateIn(...)`**: Converts the cold flow into a hot `StateFlow` that the UI can observe.

### 🌟 The Secret of `SharingStarted.WhileSubscribed(5_000)`:
Why `5_000` milliseconds?
* When the user rotates their phone, the Activity is destroyed and recreated in ~200ms.
* If we used `SharingStarted.Eagerly`, the flow would run forever in the background even if the app was closed.
* If we stopped immediately on disconnect, screen rotation would tear down and restart the database query unnecessarily.
* `WhileSubscribed(5_000)` keeps the stream active for a **5-second grace period**. If the user just rotated the phone, the state is preserved seamlessly! If the user exits the app, the stream stops 5 seconds later, saving CPU and battery.

---

## 6. Flow Transformation: `flowOn` and CPU Offloading

Look at the trend computation in [`TransactionViewModel.kt`](file:///home/frank/AndroidStudioProjects/smartmoney/app/src/main/java/com/example/smartmoney/ui/transactions/TransactionViewModel.kt#L129-L138):

```kotlin
private val trendCalculation: StateFlow<TransactionTrendCalculation> = allTransactions
    .map { list ->
        TransactionTrendCalculator.calculateTrendSummary(list)
    }
    .flowOn(dispatchers.default)
    .stateIn(...)
```
- `TransactionTrendCalculator.calculateTrendSummary` processes all transactions, groups by day, and computes cash-flow math.
- `.flowOn(dispatchers.default)` guarantees that this math calculation runs on background CPU worker threads, keeping screen scrolling buttery smooth at 60/120 FPS.

---

## 🏋️ Practice Exercises

1. **Dispatcher Inspection**:
   In [`SmartMoneyApplication.kt`](file:///home/frank/AndroidStudioProjects/smartmoney/app/src/main/java/com/example/smartmoney/SmartMoneyApplication.kt#L27), what dispatcher is passed into `CoroutineScope(...)`? Why is `Dispatchers.IO` appropriate there?
2. **Flow Transformation Exercise**:
   Given a `Flow<List<Transaction>>`, how would you create a flow that emits only transactions with an amount greater than 1,000? (Hint: use `.map { list -> list.filter { ... } }`).
3. **Trace the State in Compose**:
   In [`TransactionScreen.kt`](file:///home/frank/AndroidStudioProjects/smartmoney/app/src/main/java/com/example/smartmoney/ui/transactions/TransactionScreen.kt), find how `viewModel.uiState` or `viewModel.filteredTransactions` is converted into Compose state using `collectAsState()` or `collectAsStateWithLifecycle()`.

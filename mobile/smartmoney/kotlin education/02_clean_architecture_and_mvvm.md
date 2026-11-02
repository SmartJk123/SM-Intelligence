# Module 02: Clean Architecture & The MVVM Pattern

Welcome to Module 02! In this module, we break down how professional Android applications are structured. We will explore **Clean Architecture** and the **Model-View-ViewModel (MVVM)** pattern through the architecture of SmartMoney.

---

## 1. Why Clean Architecture?

Early Android apps often stuffed database queries, network calls, and UI logic into a single giant `Activity` file (often nicknamed "God Objects"). This caused:
* Frequent crashes during screen rotations and configuration changes.
* Code that was impossible to unit test.
* Bugs whenever API formats or database schemas changed.

Clean Architecture solves this by enforcing **Separation of Concerns** across distinct layers:

```
┌─────────────────────────────────────────────────────────────┐
│ 1. UI Layer (Presentation)                                  │
│    - Jetpack Compose Screens                                │
│    - ViewModels                                             │
│    - UiState models                                         │
└───────────────────────────────┬─────────────────────────────┘
                                │ Calls
                                ▼
┌─────────────────────────────────────────────────────────────┐
│ 2. Domain Layer (Pure Business Logic)                       │
│    - Domain Models (Transaction, Account)                   │
│    - Repository Interfaces (TransactionRepository)          │
│    - Calculators / Business Rules                           │
└───────────────────────────────▲─────────────────────────────┘
                                │ Implements
                                │
┌───────────────────────────────┴─────────────────────────────┐
│ 3. Data Layer (Data Sources & Persistence)                  │
│    - Repository Implementations                             │
│    - Room Database & DAOs (Local cache)                     │
│    - Retrofit & Supabase Clients (Remote data)              │
└─────────────────────────────────────────────────────────────┘
                                ▲
                                │ Wired By
┌───────────────────────────────┴─────────────────────────────┐
│ 4. Core Layer (Cross-Cutting Infrastructure)                │
│    - AppContainer (Dependency Injection)                    │
│    - DispatcherProvider (Threading)                         │
└─────────────────────────────────────────────────────────────┘
```

---

## 2. The 4 Layers in SmartMoney

### Layer 1: Domain (`domain/`) — Pure Kotlin
* **Rule**: The domain layer should **never** import Android framework classes (like `android.content.Context` or `android.widget.*`).
* **Why**: It represents your pure business logic. If you ever ported SmartMoney to iOS via Kotlin Multiplatform (KMP), the domain layer could be reused without changing a single line!
* **Files**:
  - [`domain/model/Transaction.kt`](file:///home/frank/AndroidStudioProjects/smartmoney/app/src/main/java/com/example/smartmoney/domain/model/Transaction.kt)
  - [`domain/repository/TransactionRepository.kt`](file:///home/frank/AndroidStudioProjects/smartmoney/app/src/main/java/com/example/smartmoney/domain/repository/TransactionRepository.kt)
  - [`domain/util/TransactionTrendCalculator.kt`](file:///home/frank/AndroidStudioProjects/smartmoney/app/src/main/java/com/example/smartmoney/domain/util/TransactionTrendCalculator.kt)

### Layer 2: Data (`data/`) — The Implementation
* **Rule**: The data layer implements the domain repository interfaces. It decides *where* data comes from: a local SQLite/Room database, an in-memory cache, or a remote REST/Supabase API.
* **Files**:
  - [`data/repository/TransactionRepositoryImpl.kt`](file:///home/frank/AndroidStudioProjects/smartmoney/app/src/main/java/com/example/smartmoney/data/repository/TransactionRepositoryImpl.kt)
  - [`data/remote/RetrofitClient.kt`](file:///home/frank/AndroidStudioProjects/smartmoney/app/src/main/java/com/example/smartmoney/data/remote/RetrofitClient.kt)
  - [`data/local/database/AppDatabase.kt`](file:///home/frank/AndroidStudioProjects/smartmoney/app/src/main/java/com/example/smartmoney/data/local/database/AppDatabase.kt)

### Layer 3: UI (`ui/`) — ViewModels & Jetpack Compose
* **Rule**: UI code only renders state and forwards user interactions to the ViewModel. It never executes SQL or network requests directly.
* **Files**:
  - [`ui/transactions/TransactionViewModel.kt`](file:///home/frank/AndroidStudioProjects/smartmoney/app/src/main/java/com/example/smartmoney/ui/transactions/TransactionViewModel.kt)
  - [`ui/transactions/TransactionScreen.kt`](file:///home/frank/AndroidStudioProjects/smartmoney/app/src/main/java/com/example/smartmoney/ui/transactions/TransactionScreen.kt)

### Layer 4: Core (`core/`) — Infrastructure & Wiring
* **Rule**: Houses shared utilities, threading contracts, and application-level dependency injection.
* **Files**:
  - [`core/di/AppContainer.kt`](file:///home/frank/AndroidStudioProjects/smartmoney/app/src/main/java/com/example/smartmoney/core/di/AppContainer.kt)
  - [`core/coroutine/DispatcherProvider.kt`](file:///home/frank/AndroidStudioProjects/smartmoney/app/src/main/java/com/example/smartmoney/core/coroutine/DispatcherProvider.kt)

---

## 3. Dependency Inversion: Interface vs Implementation

Notice in SmartMoney:
1. The **Domain** defines the interface:
   ```kotlin
   // domain/repository/TransactionRepository.kt
   interface TransactionRepository {
       fun getTransactionsFlow(userId: String?): Flow<List<Transaction>>
       suspend fun syncTransactions(userId: String?): Result<Unit>
   }
   ```
2. The **ViewModel** depends **only** on the interface:
   ```kotlin
   // ui/transactions/TransactionViewModel.kt
   class TransactionViewModel(
       private val repository: TransactionRepository?,
       ...
   )
   ```
3. The **Data Layer** implements that interface:
   ```kotlin
   // data/repository/TransactionRepositoryImpl.kt
   class TransactionRepositoryImpl(...) : TransactionRepository { ... }
   ```

### 💡 Why this is powerful:
- When writing unit tests for `TransactionViewModel`, you do not need a real network connection or database. You can supply a `FakeTransactionRepository` that returns instant test data.
- If you switch from Supabase to Firebase or GraphQL tomorrow, the ViewModel and UI do not need to change at all.

---

## 4. Manual Dependency Injection (`AppContainer`)

Instead of requiring heavy third-party reflection libraries (like Dagger or Hilt), SmartMoney uses a clean, maintainable pattern known as **Manual Dependency Injection / Service Locator via `AppContainer`**.

### How it works:
1. [`AppContainer.kt`](file:///home/frank/AndroidStudioProjects/smartmoney/app/src/main/java/com/example/smartmoney/core/di/AppContainer.kt) defines the dependencies required across the app:
   ```kotlin
   interface AppContainer {
       val database: AppDatabase
       val transactionRepository: TransactionRepository
       val authRepository: AuthRepository
       ...
   }
   ```
2. `DefaultAppContainer` uses Kotlin's `by lazy` delegate so that objects (like `database` or `authRepository`) are created **only when first accessed**:
   ```kotlin
   override val database: AppDatabase by lazy {
       AppDatabase.getDatabase(context)
   }
   ```
3. The root [`SmartMoneyApplication.kt`](file:///home/frank/AndroidStudioProjects/smartmoney/app/src/main/java/com/example/smartmoney/SmartMoneyApplication.kt) instantiates this container once during app startup:
   ```kotlin
   class SmartMoneyApplication : Application() {
       lateinit var container: AppContainer
           private set

       override fun onCreate() {
           super.onCreate()
           container = DefaultAppContainer(this)
       }
   }
   ```

---

## 5. The ViewModel & `ViewModelProvider.Factory`

### What is a ViewModel?
Android activities and screens can be destroyed and recreated at any time (e.g., when the user rotates their phone, changes system dark mode, or minimizes the app).

A `ViewModel` **survives** these lifecycle recreations. Its job is to:
1. Hold and manage UI state.
2. Communicate with repositories on background threads.
3. Expose state to the UI via `StateFlow`.

### Why does `TransactionViewModel` need a `Factory`?
Android's default system can only instantiate ViewModels that have **zero constructor arguments**.

Because `TransactionViewModel` requires dependencies (`repository`, `userId`, `dispatchers`), we provide a **Factory**:
```kotlin
// In TransactionViewModel.kt
class Factory(
    private val repository: TransactionRepository?,
    private val userId: String?,
    private val dispatchers: DispatcherProvider = DefaultDispatcherProvider()
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return TransactionViewModel(repository, userId, dispatchers) as T
    }
}
```
In your Activity or Composable, you instantiate the ViewModel like this:
```kotlin
val app = context.applicationContext as SmartMoneyApplication
val viewModel: TransactionViewModel = viewModel(
    factory = TransactionViewModel.Factory(
        repository = app.container.transactionRepository,
        userId = app.container.authRepository.currentUserId()
    )
)
```

---

## 🏋️ Practice Exercises

1. **Architecture Audit**:
   Open [`HomeViewModel.kt`](file:///home/frank/AndroidStudioProjects/smartmoney/app/src/main/java/com/example/smartmoney/ui/home/HomeViewModel.kt). Which repositories from the `domain/` layer does it depend on?
2. **Add a Mock**:
   Imagine you want to test the UI when the user has 0 transactions. How would you create a `FakeTransactionRepository` implementing `TransactionRepository`?
3. **Trace the Wire**:
   Open [`MainActivity.kt`](file:///home/frank/AndroidStudioProjects/smartmoney/app/src/main/java/com/example/smartmoney/MainActivity.kt). Find where `container` is accessed from `SmartMoneyApplication`.

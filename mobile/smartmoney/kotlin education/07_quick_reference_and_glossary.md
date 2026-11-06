# Module 07: Quick Reference, Glossary & Codebase Map

A fast lookup guide for Kotlin syntax, Android terminology, and a directory map linking directly to the SmartMoney source code.

---

## ⚡ Kotlin Syntax Cheat Sheet

| Syntax | Description | Example |
| :--- | :--- | :--- |
| `val name = "SmartMoney"` | Read-only reference (immutable) | `val id: String` |
| `var count = 0` | Mutable variable | `var isSyncing = false` |
| `String?` | Nullable type (can be null) | `val description: String? = null` |
| `user?.name` | Safe call operator (skips if null) | `authRepository.currentUserId()?.length` |
| `value ?: default` | Elvis operator (fall back if null) | `currentUserId ?: "default-user"` |
| `"${amount} KES"` | String interpolation template | `println("Total: ${transaction.amount}")` |
| `data class Person(...)` | Model with auto `equals`, `hashCode`, `toString`, `copy` | [`Transaction.kt`](file:///home/frank/AndroidStudioProjects/smartmoney/app/src/main/java/com/example/smartmoney/domain/model/Transaction.kt) |
| `sealed interface UiState` | Restricted hierarchy for exhaustive `when` matching | [`TransactionUiState.kt`](file:///home/frank/AndroidStudioProjects/smartmoney/app/src/main/java/com/example/smartmoney/ui/transactions/TransactionUiState.kt) |
| `by lazy { ... }` | Initializes property only upon first access | `override val database by lazy { ... }` |
| `fun Int.isEven(): Boolean` | Extension function on existing type | `fun BigDecimal.formatToKes(): String` |
| `list.map { it.amount }` | Lambda expression (`it` is implicit single argument) | `transactions.filter { it.type == "CREDIT" }` |
| `companion object { ... }` | Static factory or constants inside a class | `companion object { fun create() { ... } }` |

---

## 📖 Android Vocabulary & Glossary

* **ANR (Application Not Responding)**: An error thrown by the Android OS when the app blocks the Main (UI) thread for more than 5 seconds. Avoided by running heavy tasks on `Dispatchers.IO` or `Dispatchers.Default`.
* **Composable**: A Kotlin function annotated with `@Composable` that describes a piece of UI in Jetpack Compose.
* **Recomposition**: The process where Jetpack Compose re-executes a Composable function when its underlying state changes to update the screen.
* **State Hoisting**: A design pattern where state is moved up to the caller to make a Composable stateless, reusable, and easily testable.
* **Coroutine**: A lightweight thread that can suspend execution without blocking the underlying OS thread.
* **Flow**: A reactive stream of values computed asynchronously (similar to RxJava Observables or JS Observables).
* **StateFlow**: A Flow that holds a current value and immediately emits it to collectors. Ideal for managing UI state in ViewModels.
* **Room DAO (Data Access Object)**: An interface where you define SQL queries using Kotlin functions and annotations (`@Query`, `@Insert`).
* **Clean Architecture**: Dividing code into Core, Data, Domain, and UI layers so business logic remains independent of UI and third-party libraries.
* **Dependency Injection (DI)**: Supplying an object with its dependencies rather than letting the object construct them itself (implemented via `AppContainer.kt`).

---

## 🗺️ SmartMoney Codebase Map

Quick links to explore the SmartMoney implementation:

### 1. Application & Configuration
- **Application Class**: [`SmartMoneyApplication.kt`](file:///home/frank/AndroidStudioProjects/smartmoney/app/src/main/java/com/example/smartmoney/SmartMoneyApplication.kt) — Root dependency container initialization.
- **Main Activity**: [`MainActivity.kt`](file:///home/frank/AndroidStudioProjects/smartmoney/app/src/main/java/com/example/smartmoney/MainActivity.kt) — Android lifecycle host and root NavHost setup.
- **Build Configuration**: [`app/build.gradle.kts`](file:///home/frank/AndroidStudioProjects/smartmoney/app/build.gradle.kts) — Dependencies, KSP, and build configuration.

### 2. Core Infrastructure (`core/`)
- **Dependency Container**: [`core/di/AppContainer.kt`](file:///home/frank/AndroidStudioProjects/smartmoney/app/src/main/java/com/example/smartmoney/core/di/AppContainer.kt)
- **Coroutine Dispatchers**: [`core/coroutine/DispatcherProvider.kt`](file:///home/frank/AndroidStudioProjects/smartmoney/app/src/main/java/com/example/smartmoney/core/coroutine/DispatcherProvider.kt)
- **Notification Manager**: [`core/notification/NotificationHelper.kt`](file:///home/frank/AndroidStudioProjects/smartmoney/app/src/main/java/com/example/smartmoney/core/notification/NotificationHelper.kt)
- **Currency Utilities**: [`core/util/CurrencyUtils.kt`](file:///home/frank/AndroidStudioProjects/smartmoney/app/src/main/java/com/example/smartmoney/core/util/CurrencyUtils.kt)

### 3. Pure Business Logic (`domain/`)
- **Models**:
  - [`domain/model/Transaction.kt`](file:///home/frank/AndroidStudioProjects/smartmoney/app/src/main/java/com/example/smartmoney/domain/model/Transaction.kt)
  - [`domain/model/Account.kt`](file:///home/frank/AndroidStudioProjects/smartmoney/app/src/main/java/com/example/smartmoney/domain/model/Account.kt)
  - [`domain/model/Budget.kt`](file:///home/frank/AndroidStudioProjects/smartmoney/app/src/main/java/com/example/smartmoney/domain/model/Budget.kt)
- **Repository Contracts**:
  - [`domain/repository/TransactionRepository.kt`](file:///home/frank/AndroidStudioProjects/smartmoney/app/src/main/java/com/example/smartmoney/domain/repository/TransactionRepository.kt)
  - [`domain/repository/AuthRepository.kt`](file:///home/frank/AndroidStudioProjects/smartmoney/app/src/main/java/com/example/smartmoney/domain/repository/AuthRepository.kt)
- **Calculations & Use Cases**:
  - [`domain/util/TransactionTrendCalculator.kt`](file:///home/frank/AndroidStudioProjects/smartmoney/app/src/main/java/com/example/smartmoney/domain/util/TransactionTrendCalculator.kt)

### 4. Data Layer & Storage (`data/`)
- **Local Cache (Room Database)**:
  - [`data/local/database/AppDatabase.kt`](file:///home/frank/AndroidStudioProjects/smartmoney/app/src/main/java/com/example/smartmoney/data/local/database/AppDatabase.kt)
  - [`data/local/UserProfileManager.kt`](file:///home/frank/AndroidStudioProjects/smartmoney/app/src/main/java/com/example/smartmoney/data/local/UserProfileManager.kt)
- **Remote Networking**:
  - [`data/remote/RetrofitClient.kt`](file:///home/frank/AndroidStudioProjects/smartmoney/app/src/main/java/com/example/smartmoney/data/remote/RetrofitClient.kt)
- **Repository Implementations**:
  - [`data/repository/TransactionRepositoryImpl.kt`](file:///home/frank/AndroidStudioProjects/smartmoney/app/src/main/java/com/example/smartmoney/data/repository/TransactionRepositoryImpl.kt)
  - [`data/repository/AuthRepositoryImpl.kt`](file:///home/frank/AndroidStudioProjects/smartmoney/app/src/main/java/com/example/smartmoney/data/repository/AuthRepositoryImpl.kt)

### 5. UI & Jetpack Compose (`ui/`)
- **Navigation & Shell**:
  - [`ui/navigation/Screen.kt`](file:///home/frank/AndroidStudioProjects/smartmoney/app/src/main/java/com/example/smartmoney/ui/navigation/Screen.kt)
  - [`ui/components/AppBottomNavigationBar.kt`](file:///home/frank/AndroidStudioProjects/smartmoney/app/src/main/java/com/example/smartmoney/ui/components/AppBottomNavigationBar.kt)
  - [`ui/components/AppTopBar.kt`](file:///home/frank/AndroidStudioProjects/smartmoney/app/src/main/java/com/example/smartmoney/ui/components/AppTopBar.kt)
- **Screens & ViewModels**:
  - [`ui/home/HomeScreen.kt`](file:///home/frank/AndroidStudioProjects/smartmoney/app/src/main/java/com/example/smartmoney/ui/home/HomeScreen.kt) & [`ui/home/HomeViewModel.kt`](file:///home/frank/AndroidStudioProjects/smartmoney/app/src/main/java/com/example/smartmoney/ui/home/HomeViewModel.kt)
  - [`ui/transactions/TransactionScreen.kt`](file:///home/frank/AndroidStudioProjects/smartmoney/app/src/main/java/com/example/smartmoney/ui/transactions/TransactionScreen.kt) & [`ui/transactions/TransactionViewModel.kt`](file:///home/frank/AndroidStudioProjects/smartmoney/app/src/main/java/com/example/smartmoney/ui/transactions/TransactionViewModel.kt)
  - [`ui/invoice/InvoiceScreen.kt`](file:///home/frank/AndroidStudioProjects/smartmoney/app/src/main/java/com/example/smartmoney/ui/invoice/InvoiceScreen.kt) & [`ui/invoice/InvoiceViewModel.kt`](file:///home/frank/AndroidStudioProjects/smartmoney/app/src/main/java/com/example/smartmoney/ui/invoice/InvoiceViewModel.kt)
- **Design System & Theme**:
  - [`ui/theme/Color.kt`](file:///home/frank/AndroidStudioProjects/smartmoney/app/src/main/java/com/example/smartmoney/ui/theme/Color.kt)
  - [`ui/theme/Theme.kt`](file:///home/frank/AndroidStudioProjects/smartmoney/app/src/main/java/com/example/smartmoney/ui/theme/Theme.kt)
  - [`ui/theme/Type.kt`](file:///home/frank/AndroidStudioProjects/smartmoney/app/src/main/java/com/example/smartmoney/ui/theme/Type.kt)

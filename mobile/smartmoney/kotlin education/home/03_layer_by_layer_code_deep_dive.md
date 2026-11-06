# 03. Layer-by-Layer Code Deep Dive

This document conducts a line-by-line and component-by-component architectural inspection of every file and class powering the Home / Overview screen across the **UI Layer**, **Domain Layer**, **Data Layer**, and **Core Layer**.

---

## 🎨 1. UI Layer (Presentation)

### A. `HomeUiState.kt`
* **File:** [`ui/home/HomeUiState.kt`](file:///home/frank/AndroidStudioProjects/smartmoney/app/src/main/java/com/example/smartmoney/ui/home/HomeUiState.kt)
* **Annotation:** `@Immutable` informs the Compose compiler that all properties are stable, enabling smart recomposition skipping.
* **Key Fields:**
  * `totalBalance: BigDecimal`: Net available liquid balance aggregated across all accounts.
  * `totalCashIn: BigDecimal` & `totalCashOut: BigDecimal`: Total monthly credit and debit movements.
  * `trend: List<TrendPoint>`: 7-day day-of-week inflow and outflow data points.
  * `bankAccounts: List<BankAccount>`: List of linked bank cards for the top carousel and summary strip.
  * `glowEvent: TopCardGlowEvent?`: Active neon glow animation event with a unique timestamp ID.
  * `selectedMonth: YearMonth`: Selected month for all Overview analytics.
  * `analytics: OverviewAnalyticsData`: Multi-dimension dataset (Cash flow, categories, heatmap, pacing).

### B. `HomeViewModel.kt`
* **File:** [`ui/home/HomeViewModel.kt`](file:///home/frank/AndroidStudioProjects/smartmoney/app/src/main/java/com/example/smartmoney/ui/home/HomeViewModel.kt)
* **Constructor Dependencies:**
  ```kotlin
  class HomeViewModel(
      private val accountRepository: AccountRepository,
      private val bankAccountRepository: BankAccountRepository,
      private val transactionRepository: TransactionRepository,
      private val budgetRepository: BudgetRepository? = null,
      private val userId: String,
      private val dispatchers: DispatcherProvider = DefaultDispatcherProvider()
  ) : ViewModel()
  ```
* **Stage 1 Stream Aggregation (`rawDataFlow`):**
  Uses Kotlin's `combine` operator to merge 4 distinct repository flows into an internal `RawFinancialSnapshot`.
* **Stage 2 Heavy Calculation (`uiState`):**
  Combines `rawDataFlow` with UI state triggers (`_selectedMonth`, `_hasDismissedWelcomeSheet`, `_isSyncing`, `_glowEvent`).
  * `.flowOn(dispatchers.default)`: Offloads heavy BigDecimal aggregations, regex string evaluations, and bezier point computations to background worker threads.
  * `.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HomeUiState.DEFAULT)`: Converts the cold flow into an active `StateFlow` shared with Compose.
* **Sync Loop (`startPeriodicSync()`):**
  Runs an infinite `while (isActive)` loop delayed by 6,000 milliseconds on `dispatchers.io`, triggering background synchronization of transactions and accounts without blocking the UI.

### C. `HomeScreen.kt`
* **File:** [`ui/home/HomeScreen.kt`](file:///home/frank/AndroidStudioProjects/smartmoney/app/src/main/java/com/example/smartmoney/ui/home/HomeScreen.kt)
* **Core Composables:**
  1. **`TotalBalanceBannerCard` (Lines 442–600):**
     * Renders a physical credit card form factor (aspect ratio `1.586f`).
     * Features dynamic platinum gradient animations (`Brush.linearGradient`) adapting between Light and Dark themes.
     * Houses the privacy eye toggle and the Total Balance / Total Investments switcher.
  2. **`TopCardBottomGlowLine` (Lines 323–440):**
     * Multi-pass Canvas shader that draws a 4-layer neon contour bloom (Outer Atmospheric Halo, Intermediate Bloom, and Neon Core Filament).
  3. **`ConnectedAppsBannerCard` (Lines 605–790):**
     * Page 1 of the top card carousel displaying linked banks and active status.
  4. **`CashFlowTrendGraphCard` & `CashFlowLineGraphCanvas` (Lines 990–1298):**
     * High-performance native Compose Canvas drawing cubic Bezier curves (`Path.cubicTo()`) with gradient shading under each curve.
  5. **`LinkedAccountsSummarySection` (Lines 1432–1530):**
     * Horizontal `LazyRow` displaying individual `LinkedAccountMiniCard` widgets.

### D. `DashboardScreen.kt`
* **File:** [`ui/dashboard/DashboardScreen.kt`](file:///home/frank/AndroidStudioProjects/smartmoney/app/src/main/java/com/example/smartmoney/ui/dashboard/DashboardScreen.kt)
* **Role:**
  * Hosts a `HorizontalPager` across the primary bottom navigation screens:
    * Page 0: `HomeScreen`
    * Page 1: `AccountScreen`
    * Page 2: `TransactionScreen`
    * Page 3: `MenuScreen`
  * Passes `totalInvestmentBalance` computed from `InvestmentViewModel` into `HomeScreen`.
  * Controls top bar vanishing animation on scroll specifically for the Overview and Menu tabs.

---

## 🏛️ 2. Domain Layer (Contracts & Business Logic)

### A. Repository Contracts
1. **[`AccountRepository.kt`](file:///home/frank/AndroidStudioProjects/smartmoney/app/src/main/java/com/example/smartmoney/domain/repository/AccountRepository.kt):**
   ```kotlin
   fun getAccountsFlow(userId: String): Flow<List<Account>>
   suspend fun syncAccounts(userId: String): Result<Unit>
   ```
2. **[`BankAccountRepository.kt`](file:///home/frank/AndroidStudioProjects/smartmoney/app/src/main/java/com/example/smartmoney/domain/repository/BankAccountRepository.kt):**
   ```kotlin
   fun getBankAccounts(): Flow<List<BankAccount>>
   suspend fun addBankAccount(bankName: String, accountNumber: String, cardType: String): Result<Unit>
   ```
3. **[`TransactionRepository.kt`](file:///home/frank/AndroidStudioProjects/smartmoney/app/src/main/java/com/example/smartmoney/domain/repository/TransactionRepository.kt):**
   ```kotlin
   fun getTransactionsFlow(userId: String?, accountId: String? = null): Flow<List<Transaction>>
   suspend fun syncTransactions(userId: String?, accountId: String? = null): Result<Unit>
   ```
4. **[`BudgetRepository.kt`](file:///home/frank/AndroidStudioProjects/smartmoney/app/src/main/java/com/example/smartmoney/domain/repository/BudgetRepository.kt):**
   ```kotlin
   fun getBudgets(): Flow<List<Budget>>
   ```

### B. `TransactionTrendCalculator.kt`
* **File:** [`domain/util/TransactionTrendCalculator.kt`](file:///home/frank/AndroidStudioProjects/smartmoney/app/src/main/java/com/example/smartmoney/domain/util/TransactionTrendCalculator.kt)
* **Single-Pass $O(N)$ Aggregator:**
  * Evaluates incoming transactions against the user's local timezone.
  * Bins transactions into standard 7-day Monday through Sunday trend buckets.
  * Emits `TransactionTrendCalculation(totalInflow, totalOutflow, trendPoints, hasTransactions)`.

---

## 💾 3. Data Layer (Persistence & Network)

### A. Local SQLite Persistence (Room DAOs)
* **[`AccountDao.kt`](file:///home/frank/AndroidStudioProjects/smartmoney/app/src/main/java/com/example/smartmoney/data/local/dao/AccountDao.kt):**
  * `getAccountsForUser(userId: String): Flow<List<AccountEntity>>`
  * `upsertAccounts(accounts: List<AccountEntity>)`
* **[`TransactionDao.kt`](file:///home/frank/AndroidStudioProjects/smartmoney/app/src/main/java/com/example/smartmoney/data/local/dao/TransactionDao.kt):**
  * `getTransactionsForUser(userId: String): Flow<List<TransactionEntity>>`
  * `upsertTransactions(transactions: List<TransactionEntity>)`

### B. Repository Implementations
* **[`AccountRepositoryImpl.kt`](file:///home/frank/AndroidStudioProjects/smartmoney/app/src/main/java/com/example/smartmoney/data/repository/AccountRepositoryImpl.kt):**
  Streams data from `localDao` and exposes `syncAccounts()` which calls `remoteDataSource.fetchAccounts(userId)` and upserts the result.
* **[`TransactionRepositoryImpl.kt`](file:///home/frank/AndroidStudioProjects/smartmoney/app/src/main/java/com/example/smartmoney/data/repository/TransactionRepositoryImpl.kt):**
  * Implements the **Triple-Sync Strategy**:
    1. Synchronizes `syncActivityFeed(targetUserId)` via `RetrofitClient.activityApi.getActivity()`.
    2. Synchronizes KCB Bank integration via `RetrofitClient.bankIntegrationApi.getKcbTransactions()`.
    3. Synchronizes account-specific transactions via `remoteDataSource.fetchTransactions()`.
  * Filters transactions strictly against accounts owned by the user to prevent cross-account contamination.
  * Ingests system and in-app notifications for new money movements.
* **[`BankAccountRepositoryImpl.kt`](file:///home/frank/AndroidStudioProjects/smartmoney/app/src/main/java/com/example/smartmoney/data/repository/BankAccountRepositoryImpl.kt):**
  * Implements `addBankAccount(...)` by calling `RetrofitClient.bankIntegrationApi.linkAccount(...)` on port `8090` and provisioning the account in `accounts-service` on port `8082`.

### C. Remote APIs & Interceptors ([`RetrofitClient.kt`](file:///home/frank/AndroidStudioProjects/smartmoney/app/src/main/java/com/example/smartmoney/data/remote/RetrofitClient.kt))
* **Bearer Token Interceptor:** Automatically attaches `Authorization: Bearer <token>` to every request using `tokenProvider`.
* **Microservices Proxied:**
  * Port `8082`: `accounts-service` (`AccountApi`)
  * Port `8083`: `transactions-service` (`TransactionApi` & `ActivityFeedApi`)
  * Port `8090`: `bank-integration-service` (`BankIntegrationApi`)

---

## ⚙️ 4. Core Layer (Dependency Injection & Threading)

### A. `AppContainer.kt` & `DefaultAppContainer.kt`
* **Files:** [`core/di/AppContainer.kt`](file:///home/frank/AndroidStudioProjects/smartmoney/app/src/main/java/com/example/smartmoney/core/di/AppContainer.kt) & [`core/di/DefaultAppContainer.kt`](file:///home/frank/AndroidStudioProjects/smartmoney/app/src/main/java/com/example/smartmoney/core/di/DefaultAppContainer.kt)
* **Singletons Provisioned:**
  * `database: SmartMoneyDatabase` (Room database instance)
  * `accountRepository: AccountRepository`
  * `bankAccountRepository: BankAccountRepository`
  * `transactionRepository: TransactionRepository`
  * `budgetRepository: BudgetRepository`
  * `notificationRepository: NotificationRepository`

### B. `MainActivity.kt` Scoped Instantiation
* **File:** [`MainActivity.kt`](file:///home/frank/AndroidStudioProjects/smartmoney/app/src/main/java/com/example/smartmoney/MainActivity.kt#L161-L172)
* **Scoped Factory Creation:**
  ```kotlin
  val homeViewModel: HomeViewModel = viewModel(
      key = "home_$currentUserId",
      factory = HomeViewModel.Factory(
          accountRepository = appContainer.accountRepository,
          bankAccountRepository = appContainer.bankAccountRepository,
          transactionRepository = appContainer.transactionRepository,
          budgetRepository = appContainer.budgetRepository,
          userId = currentUserId
      )
  )
  ```
  The ViewModel key `home_$currentUserId` ensures that if a user logs out and another user signs in, an entirely new ViewModel instance is created, completely isolating memory and state between users.

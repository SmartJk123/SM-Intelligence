# 02. Screen Values & Network Request Pipeline

This document provides an exhaustive, value-by-value explanation of **every single metric, card, and chart displayed on the Home / Overview screen**. For each visual value, it documents its UI appearance, upstream state properties, mathematical derivation, local database query, and remote HTTP network request pipeline.

---

## 📋 Comprehensive Catalog of Screen Values

```
                                  HOME SCREEN (OVERVIEW)
┌──────────────────────────────────────────────────────────────────────────────────┐
│ [1] Total Balance Banner Card                                                    │
│     ├── Total Balance / Investments: KES 124,500.00                              │
│     ├── Visibility Toggle: Eye Icon (Hide / Show)                                │
│     └── [2] Metric Badges: Inflow (+KES 45,000.00)  |  Outflow (-KES 28,300.00) │
│     └── [3] Ambient Glow: Pulsing Green (Inflow) / Red (Outflow)                 │
├──────────────────────────────────────────────────────────────────────────────────┤
│ [4] Top Card Page 1: Connected Accounts Carousel                                 │
│     ├── Account Count: "2 Accounts" | "Real-time" Active Pill                     │
│     └── Mini Bank Cards: KCB (KES 84,500) • Stanbic (KES 40,000)                 │
├──────────────────────────────────────────────────────────────────────────────────┤
│ [5] 7-Day Cash Flow Trend Graph Card                                             │
│     └── Native Compose Canvas: Dual Inflow / Outflow Bezier Splines (Mon–Sun)    │
├──────────────────────────────────────────────────────────────────────────────────┤
│ [6] Interactive Month Selector: "< October 2026 >"                               │
├──────────────────────────────────────────────────────────────────────────────────┤
│ [7] Cash Flow Columns Card                                                       │
│     ├── Timeframe Switcher: [ Day ] | [ Week ] | [ Month ]                       │
│     └── Inflow vs Outflow Vertical Bar Columns & Net Cash Flow Indicator         │
├──────────────────────────────────────────────────────────────────────────────────┤
│ [8] Spending by Category Donut Chart Card                                        │
│     ├── Circular Donut Segmented by Category (Rent, Groceries, Dining, etc.)     │
│     └── Category Legend with Percentages and Transaction Counts                  │
├──────────────────────────────────────────────────────────────────────────────────┤
│ [9] Daily Spending Calendar Heatmap Card                                         │
│     ├── 30-Day Calendar Grid with Spending Intensity Shading (0 to 3)            │
│     └── Highest Spending Day Callout Badge                                       │
├──────────────────────────────────────────────────────────────────────────────────┤
│ [10] Budget Pacing Graph Card                                                    │
│      ├── Cumulative Actual Spending vs Planned Linear Budget Pace Line           │
│      └── Health Badge: "On pace" / "Ahead of budget" / "Behind budget"           │
├──────────────────────────────────────────────────────────────────────────────────┤
│ [11] Onboarding & Setup Card: "Get Started" Banner (Active when accounts = 0)   │
└──────────────────────────────────────────────────────────────────────────────────┘
```

---

## 1. Total Account Balance & Total Investments

### A. Visual Representation on Screen
* **Widget:** `TotalBalanceBannerCard` in [`HomeScreen.kt`](file:///home/frank/AndroidStudioProjects/smartmoney/app/src/main/java/com/example/smartmoney/ui/home/HomeScreen.kt#L442-L600).
* **Display Format:** Prominent headline font (28 sp, extra bold).
  * In normal mode: Formatted Kenyan Shillings (e.g., `KES 124,500.00`).
  * In privacy mode (when user taps the eye icon): Masked string `••••••••`.
* **View Switcher:** A rounded pill button (`View Investments` ⇄ `View Card`) toggles between banking liquid balance and investment portfolio balance.

### B. Upstream State & Computation
* **State Properties:**
  * `uiState.totalBalance: BigDecimal` (from `HomeUiState`)
  * `totalInvestmentBalance: BigDecimal` (from `DashboardScreen` via `InvestmentViewModel`)
* **Mathematical Derivation in `HomeViewModel.kt` (Lines 102–107):**
  ```kotlin
  val calculatedBalance = accounts.fold(BigDecimal.ZERO) { acc, a -> acc.add(a.availableBalance) }
  val finalBalance = if (calculatedBalance > BigDecimal.ZERO || accounts.isNotEmpty()) {
      calculatedBalance
  } else {
      bankAccounts.fold(BigDecimal.ZERO) { acc, b -> acc.add(b.balance) }
  }
  ```
  1. Computes the sum of all `availableBalance` values across linked `Account` models.
  2. If the user has linked direct bank cards (`BankAccount`) but verified accounts haven't indexed yet, falls back to summing `bankAccount.balance`.

### C. Local Database Query
* **DAO:** [`AccountDao.kt`](file:///home/frank/AndroidStudioProjects/smartmoney/app/src/main/java/com/example/smartmoney/data/local/dao/AccountDao.kt)
* **SQL Query:**
  ```sql
  SELECT * FROM accounts WHERE userId = :userId
  ```
  Emits a reactive `Flow<List<AccountEntity>>`. Whenever account records update, Room re-emits immediately.

### D. Remote Network Request Pipeline
* **Trigger:** Initiated on screen launch via `HomeViewModel.refresh()` and every 6 seconds by `HomeViewModel.startPeriodicSync()`.
* **Execution:**
  1. `AccountRepositoryImpl.syncAccounts(userId)` runs on `Dispatchers.IO`.
  2. Calls `AccountRemoteDataSource.fetchAccounts(userId)`.
  3. Retrofit issues the HTTP request:
     ```http
     GET /api/accounts/user/{userId} HTTP/1.1
     Host: <apiBaseUrl>:8082
     Authorization: Bearer <jwt_token>
     Accept: application/json
     ```
  4. **Backend Service:** Spring Boot `accounts-service` on port `8082`.
  5. **Response Body (HTTP 200 JSON):**
     ```json
     [
       {
         "id": "acc_01",
         "userId": "usr_frank_123",
         "accountId": "KCB-0982341",
         "accountName": "KCB Checking",
         "accountType": "DEPOSIT",
         "currency": "KES",
         "availableBalance": 84500.00,
         "currentBalance": 84500.00,
         "institution": "kcb"
       }
     ]
     ```
  6. **Local Persistence:** Converted to `AccountEntity` and saved into Room via `localDao.upsertAccounts(entities)`.

### E. Investment Balance Request
* **Endpoint:** `GET /api/investments/portfolio` (port `8084` or `apiBaseUrl`).
* **Computation:** `minor = portfolioValuationMinor ?: totalPrincipalMinor`, converted to major units via `.divide(BigDecimal(100))`.

---

## 2. Total Cash In & Total Cash Out (MetricItem Badges)

### A. Visual Representation on Screen
* **Widget:** `MetricItem` rows nested inside `TotalBalanceBannerCard`.
* **Inflow Badge:** Mint-green container with an upward arrow icon (`+KES 45,000.00`).
* **Outflow Badge:** Coral-red/salmon container with a downward arrow icon (`-KES 28,300.00`).

### B. Upstream State & Computation
* **State Properties:**
  * `uiState.totalCashIn: BigDecimal`
  * `uiState.totalCashOut: BigDecimal`
* **Calculation Engine:** [`TransactionTrendCalculator.calculateTrendSummary()`](file:///home/frank/AndroidStudioProjects/smartmoney/app/src/main/java/com/example/smartmoney/domain/util/TransactionTrendCalculator.kt#L58-L100)
  ```kotlin
  val isCredit = tx.type.equals("CREDIT", ignoreCase = true)
  val isDebit = tx.type.equals("DEBIT", ignoreCase = true) || tx.type.equals("EXPENSE", ignoreCase = true)
  val amount = tx.amount.abs()

  if (isCredit) {
      totalInflow = totalInflow.add(amount)
  } else if (isDebit) {
      totalOutflow = totalOutflow.add(amount)
  }
  ```
  Executes an $O(N)$ single-pass aggregation over all transactions for the user.

### C. Local Database Query
* **DAO:** [`TransactionDao.kt`](file:///home/frank/AndroidStudioProjects/smartmoney/app/src/main/java/com/example/smartmoney/data/local/dao/TransactionDao.kt)
* **SQL Query:**
  ```sql
  SELECT * FROM transactions WHERE userId = :userId ORDER BY timestamp DESC
  ```

### D. Multi-Gateway Remote Synchronization Pipeline
When `TransactionRepositoryImpl.syncTransactions(userId)` executes, it synchronizes across **three distinct remote endpoints**:

#### Pipeline 1: User Activity Feed (Token-Scoped)
* **Endpoint:** `GET /api/transactions/activity`
* **Service:** `transactions-service` on port `8083`.
* **Request:**
  ```http
  GET /api/transactions/activity HTTP/1.1
  Host: <apiBaseUrl>:8083
  Authorization: Bearer <jwt_token>
  ```
* **Payload:** Returns all multi-bank activity for the signed-in user (including live NCBA webhook events).
* **Storage:** Mapped to `TransactionEntity(id = "activity_${item.id}", ...)` and upserted to Room.

#### Pipeline 2: Real-time Bank Integration Service
* **Endpoint:** `GET /api/bank/transactions/kcb?userId={userId}`
* **Service:** `bank-integration-service` on port `8090`.
* **Verification:** Matches transaction account numbers strictly against accounts verified in the local `accounts` table to prevent data cross-contamination.

#### Pipeline 3: Standard Microservice Transactions
* **Endpoint:** `GET /api/transactions/accounts/{accountId}`
* **Service:** `transactions-service` on port `8083`.
* **Mapping:** Converts `TransactionDto` into `TransactionEntity` and upserts to Room.

---

## 3. Ambient Inflow/Outflow Top Card Glow Animation

### A. Visual Representation on Screen
* **Widget:** `TopCardBottomGlowLine` in [`HomeScreen.kt`](file:///home/frank/AndroidStudioProjects/smartmoney/app/src/main/java/com/example/smartmoney/ui/home/HomeScreen.kt#L323-L440).
* **Inflow Effect:** High-radiance mint-green neon contour bloom (`#4ADE80`) pulsing across the bottom border of the top balance card.
* **Outflow Effect:** Crimson neon bloom (`#EF4444`) pulsing when funds are spent.

### B. Upstream State & Event Detection
* **State Property:** `uiState.glowEvent: TopCardGlowEvent?`
* **Detection Engine in `HomeViewModel.kt` (Lines 111–141):**
  `HomeViewModel` remembers `lastKnownBalance` and `lastKnownTxIds`. When Room emits a new snapshot:
  ```kotlin
  if (finalBalance > previousBalance) {
      _glowEvent.value = TopCardGlowEvent(type = TopCardGlowType.INFLOW_GREEN)
  } else if (finalBalance < previousBalance) {
      _glowEvent.value = TopCardGlowEvent(type = TopCardGlowType.OUTFLOW_RED)
  }
  ```
  If balance remains identical but new transaction IDs were inserted:
  * Any new `CREDIT` transaction triggers `INFLOW_GREEN`.
  * Any new `DEBIT` transaction triggers `OUTFLOW_RED`.
* **Lifecycle:** When the Compose animation completes its pulse, `HomeScreen` calls `homeViewModel.clearGlow()`, resetting `_glowEvent` to `null`.

---

## 4. Connected Accounts Count & Carousel Cards

### A. Visual Representation on Screen
* **Widget:** `ConnectedAppsBannerCard` in [`HomeScreen.kt`](file:///home/frank/AndroidStudioProjects/smartmoney/app/src/main/java/com/example/smartmoney/ui/home/HomeScreen.kt#L605-L790) (Page 1 of the top carousel).
* **Summary Row:** Total count badge (e.g., `2`) and a green pulsing `Real-time` badge.
* **Scrollable Internal List:** Credit-card styled mini cards (`BankFlowMiniCard`) showing the institution's official logo, account title, masked number (`**** 4321`), card type (`Debit`/`Credit`), and balance.

### B. Upstream State & Computation
* **State Property:** `uiState.bankAccounts: List<BankAccount>`
* **Sourced via:** `bankAccountRepository.getBankAccounts()`.
* **Mapping:** Maps Room `AccountEntity` records into domain `BankAccount` objects.

### C. Bank Account Linking Request Pipeline
When the user taps **"Link Account"** in [`LinkBankAccountDialog`](file:///home/frank/AndroidStudioProjects/smartmoney/app/src/main/java/com/example/smartmoney/ui/accounts/LinkBankAccountDialog.kt):
1. `HomeViewModel.linkBankAccount(bankName, accountNumber, cardType)` executes on `Dispatchers.IO`.
2. Calls `BankAccountRepositoryImpl.addBankAccount(...)`.
3. If linking a supported Kenyan bank (KCB, Stanbic, NCBA, Equity):
   ```http
   POST /api/bank/accounts/link HTTP/1.1
   Host: <apiBaseUrl>:8090
   Authorization: Bearer <jwt_token>
   Content-Type: application/json

   {
     "bankId": "kcb",
     "accountNumber": "1234567890",
     "userId": "usr_frank_123",
     "accountName": "KCB Debit Account",
     "accountId": "acc_uuid_generated",
     "cardType": "Debit"
   }
   ```
4. Concurrently provisions the account in `accounts-service` (`POST /api/accounts` on `:8082`).
5. Persists the entity into Room SQLite.
6. Reactive Room emission triggers `HomeViewModel` to dismiss the welcome onboarding sheet and display the new bank card immediately.

---

## 5. 7-Day Cash Flow Trend Graph Card

### A. Visual Representation on Screen
* **Widget:** `CashFlowTrendGraphCard` & `CashFlowLineGraphCanvas` in [`HomeScreen.kt`](file:///home/frank/AndroidStudioProjects/smartmoney/app/src/main/java/com/example/smartmoney/ui/home/HomeScreen.kt#L990-L1298).
* **Canvas Rendering:** A native Jetpack Compose Canvas rendering two smooth cubic Bezier lines without third-party chart dependencies:
  * Green line + soft gradient fill: Daily Cash In.
  * Orange/Red line + soft gradient fill: Daily Cash Out.
  * X-Axis: 7 day-of-week markers (`Mon`, `Tue`, `Wed`, `Thu`, `Fri`, `Sat`, `Sun`).
  * Y-Axis: Dynamic labels scaled in 1,000 KES increments.

### B. Upstream State & Computation
* **State Property:** `uiState.trend: List<TrendPoint>`
* **Calculation Engine in `TransactionTrendCalculator.kt` (Lines 71–125):**
  1. Identifies the timestamp for 7 days ago.
  2. Bins every transaction into its corresponding `DayOfWeek` (Monday through Sunday).
  3. Sums `inflow` and `outflow` per day.
  4. Returns `List<TrendPoint>` ordered chronologically from Monday to Sunday.

---

## 6. Interactive Month Navigation Header

### A. Visual Representation on Screen
* **Widget:** `AnalyticsMonthHeader` in [`OverviewAnalyticsSection.kt`](file:///home/frank/AndroidStudioProjects/smartmoney/app/src/main/java/com/example/smartmoney/ui/home/analytics/components/AnalyticsMonthHeader.kt).
* **Controls:** 
  * Left chevron: Decrements month (`homeViewModel.previousMonth()`).
  * Center text: Formatted month and year (e.g., `September 2026`).
  * Right chevron: Increments month (`homeViewModel.nextMonth()`).

### B. Upstream State & Pipeline Reaction
* **State Property:** `uiState.selectedMonth: YearMonth` (defaults to `YearMonth.now()`).
* **Reactive Pipeline:** Whenever `_selectedMonth` changes in `HomeViewModel`, the second stage of the reactive `combine` operator immediately re-executes [`OverviewAnalyticsCalculator.calculate()`](file:///home/frank/AndroidStudioProjects/smartmoney/app/src/main/java/com/example/smartmoney/ui/home/analytics/calculator/OverviewAnalyticsCalculator.kt#L41) on `Dispatchers.Default`, recalculating all analytics cards for that specific month in under 5 milliseconds.

---

## 7. Multi-Timeframe Cash Flow Columns (Day, Week, Month)

### A. Visual Representation on Screen
* **Widget:** `CashFlowColumnsCard` in [`CashFlowColumnsCard.kt`](file:///home/frank/AndroidStudioProjects/smartmoney/app/src/main/java/com/example/smartmoney/ui/home/analytics/components/CashFlowColumnsCard.kt).
* **Timeframe Selector:** Segmented pill toggle allowing switching between `Day`, `Week`, and `Month`.
* **Visual Data:** Side-by-side vertical columns for Money In vs Money Out, paired with a net cash flow summary banner (`+KES xx,xxx Net Flow`).

### B. Computation Across Timeframes
Computed in `OverviewAnalyticsCalculator.kt`:
1. **Day View (Intraday 24h Timeline):**
   * Bins 24 hours into six 4-hour slots: `12 AM (00:00-04:00)`, `4 AM (04:00-08:00)`, `8 AM (08:00-12:00)`, `12 PM (12:00-16:00)`, `4 PM (16:00-20:00)`, `8 PM (20:00-24:00)`.
   * Sums debits and credits occurring in each hour slot.
2. **Week View (7-Day Mon–Sun Timeline):**
   * Evaluates the 7 days of the target week.
   * Aggregates money in and money out for each calendar day.
3. **Month View (Weeks 1 to 5):**
   * Breaks the selected month into 5 discrete weekly brackets:
     * Week 1: Days 1 to 7
     * Week 2: Days 8 to 14
     * Week 3: Days 15 to 21
     * Week 4: Days 22 to 28
     * Week 5: Days 29 to end of month

---

## 8. Spending by Category Donut Chart

### A. Visual Representation on Screen
* **Widget:** `SpendingDonutCard` in [`SpendingDonutCard.kt`](file:///home/frank/AndroidStudioProjects/smartmoney/app/src/main/java/com/example/smartmoney/ui/home/analytics/components/SpendingDonutCard.kt).
* **Donut Ring:** Proportional colored arcs rendered on Canvas with a hollow center displaying `Total Spending`.
* **Legend Pills:** Interactive category cards with category color, spending sum, percentage of total expenditure, and transaction count.

### B. Upstream State & Calculation Engine
* **State Property:** `uiState.analytics.categories: List<SpendingCategory>`
* **Classification Algorithm in `OverviewAnalyticsCalculator.kt` (Lines 213–246 & 390–420):**
  * Filters all transactions where `type` is `DEBIT` or `EXPENSE` and timestamp falls within `selectedMonth`.
  * Runs heuristic keyword detection against `transaction.description`:
    * *"Rent"*, *"Landlord"*, *"Apartment"* ➔ **Rent** (`#657166`)
    * *"Naivas"*, *"Carrefour"*, *"Quickmart"*, *"Groceries"* ➔ **Groceries** (`#22C55E`)
    * *"Uber"*, *"Bolt"*, *"Shell"*, *"Total"*, *"Matatu"* ➔ **Transport** (`#38BDF8`)
    * *"KPLC"*, *"Token"*, *"Water"*, *"Internet"*, *"Zuku"* ➔ **Utilities** (`#F59E0B`)
    * *"KFC"*, *"Java"*, *"Artcaffe"*, *"Restaurant"*, *"Cafe"* ➔ **Dining** (`#FB923C`)
    * Fallback ➔ **Other** (`#94A3B8`)
  * Computes percentage share:
    $$\text{Percentage} = \left(\frac{\text{Category Amount}}{\text{Total Monthly Spending}}\right) \times 100$$

---

## 9. Daily Spending Calendar Heatmap

### A. Visual Representation on Screen
* **Widget:** `DailySpendingHeatmapCard` in [`DailySpendingHeatmapCard.kt`](file:///home/frank/AndroidStudioProjects/smartmoney/app/src/main/java/com/example/smartmoney/ui/home/analytics/components/DailySpendingHeatmapCard.kt).
* **Grid Layout:** 7-column calendar matrix matching the exact day-of-week alignment of the selected month.
* **Highest Day Badge:** Highlights the calendar date with the largest aggregate expenditure.

### B. Intensity Shading Formula
In `OverviewAnalyticsCalculator.kt` (Lines 248–291), each day of the month is assigned an intensity level from 0 to 3:
* **Level 0 (Zero Spending):** Transparent / surface background.
* **Level 1 (Low Spending, < KES 1,000):** Light mint/slate tint (alpha 0.35).
* **Level 2 (Medium Spending, KES 1,000 – KES 2,499):** Medium solid green/amber tint (alpha 0.65).
* **Level 3 (High Spending, ≥ KES 2,500):** High-intensity vibrant slate green / coral red (alpha 1.0).

---

## 10. Budget Pacing Cumulative Velocity Graph

### A. Visual Representation on Screen
* **Widget:** `BudgetPacingGraphCard` in [`BudgetPacingGraphCard.kt`](file:///home/frank/AndroidStudioProjects/smartmoney/app/src/main/java/com/example/smartmoney/ui/home/analytics/components/BudgetPacingGraphCard.kt).
* **Canvas Chart:**
  * Dashed/Grey reference line: Planned linear daily budget accumulation.
  * Solid Teal curve: Actual cumulative spending up to the current day.
* **Status Badges:** `On pace`, `Ahead of budget`, `Behind budget`, or `No budget`.
* **Budget Metrics:** Monthly budget ceiling, total amount spent to date, and amount remaining.

### B. Upstream State & Computation
* **State Properties:**
  * `uiState.analytics.pacingPoints: List<BudgetPacingPoint>`
  * `uiState.analytics.pacingStatus: PacingStatus`
  * `uiState.analytics.budgetRemaining: BigDecimal`
* **Data Sources:** Combined from `budgetRepository.getBudgets()` and `transactionRepository.getTransactionsFlow()`.
* **Mathematical Formula in `OverviewAnalyticsCalculator.kt` (Lines 293–345):**
  1. Computes total budget limit $B = \sum \text{allocatedMajor}$.
  2. Computes linear daily pace:
     $$\text{Daily Pace} = \frac{B}{\text{Days in Month}}$$
  3. Planned cumulative spending for day $d$:
     $$\text{Planned}(d) = \text{Daily Pace} \times d$$
  4. Accumulates actual daily spending up to today: $\text{Actual}(d) = \sum_{i=1}^d \text{DailySpend}(i)$.
  5. Determines pacing status:
     * If $\text{Actual} \le \text{Planned} \times 0.95$ ➔ **`Ahead of budget`** (spending less than planned).
     * If $\text{Actual} \le \text{Planned} \times 1.05$ ➔ **`On pace`** (within $\pm 5\%$ of ideal pace).
     * If $\text{Actual} > \text{Planned} \times 1.05$ ➔ **`Behind budget`** (spending faster than planned).

---

## 11. Onboarding & First-Time Setup State

### A. Visual Representation on Screen
* **Widgets:** `OnboardingGetStartedCard` and `AccountOnboardingBottomSheet`.
* **Behavior:** When a newly registered user logs in and has zero connected accounts or transactions, the dashboard hides complex graphs and presents an engaging setup card prompting them to link their first bank account or M-Pesa wallet.

### B. State Derivation
* **State Properties:**
  * `uiState.isOnboardingActive: Boolean`
  * `uiState.shouldShowWelcomeSheet: Boolean`
* **Derivation in `HomeViewModel.kt` (Lines 108–109):**
  ```kotlin
  val isOnboarding = accounts.isEmpty() && bankAccounts.isEmpty()
  val showSheet = isOnboarding && !dismissedWelcome
  ```
  Automatically switches off the moment any bank account is linked or ingested from the remote backend.

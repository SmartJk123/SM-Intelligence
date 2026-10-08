# Comprehensive Class & File Directory Map

This document provides an exhaustive, directory-by-directory index of all Kotlin source files across the SmartMoney mobile application, mapping their architectural layer, primary responsibilities, and direct collaborators.

---

## 1. Root & Core Infrastructure (`com.example.smartmoney`)

| File Path | Layer | Primary Class / Component | Responsibilities | Collaborators |
| :--- | :--- | :--- | :--- | :--- |
| `SmartMoneyApplication.kt` | Application Root | `SmartMoneyApplication` | Application subclass. Instantiates `DefaultAppContainer`, initializes notification channels, and bootstraps user profile sessions. | `AppContainer`, `UserProfileManager`, `NotificationHelper` |
| `MainActivity.kt` | Presentation Root | `MainActivity` | Single activity host. Sets Compose content, manages system bars, renders root `MainResponsiveShell` and navigation graph. | `MainViewModel`, `MainResponsiveShell`, `Screen` |
| `MainViewModel.kt` | Presentation | `MainViewModel` | Root activity view model. Observes global auth state, active screen route, and global app notifications. | `AuthRepository`, `UserPreferencesRepository` |
| `core/di/AppContainer.kt` | Core / DI | `AppContainer`, `DefaultAppContainer` | Pure Kotlin dependency injection container holding application-scoped singletons lazily. | `AppDatabase`, all Repositories, `DispatcherProvider` |
| `core/coroutine/DispatcherProvider.kt` | Core / Concurrency | `DispatcherProvider`, `DefaultDispatcherProvider`, `TestDispatcherProvider` | Abstraction over CoroutineDispatchers (Main, IO, Default, Unconfined) enabling deterministic unit testing. | Kotlin Coroutines `Dispatchers` |
| `core/notification/NotificationHelper.kt` | Core / OS | `NotificationHelper` | Manages Android OS NotificationChannels and posts native system notifications for bank transactions. | Android `NotificationManagerCompat` |
| `core/util/CurrencyUtils.kt` | Core / Formatting | `CurrencyUtils` | Static helpers for formatting `BigDecimal` into localized KES currency strings with thousand separators. | `BigDecimal`, `Locale` |

---

## 2. Presentation Layer (`com.example.smartmoney.ui`)

### A. Home & Analytics (`ui/home/`)
| File Path | Component | Responsibilities | Collaborators |
| :--- | :--- | :--- | :--- |
| `HomeScreen.kt` | Screen Composable | Main landing screen. Displays balance cards, analytics charts, linked accounts strip, and recent activity. | `HomeViewModel`, `OverviewAnalyticsSection` |
| `HomeViewModel.kt` | ViewModel | Combines transactions, bank accounts, and budgets into `HomeUiState`. Coordinates month switching. | `TransactionRepository`, `BankAccountRepository`, `BudgetRepository` |
| `HomeUiState.kt` | State Model | Immutable data class capturing the entire visual state of the Home dashboard. | `Transaction`, `BankAccount`, `OverviewAnalyticsData` |
| `analytics/calculator/OverviewAnalyticsCalculator.kt` | Domain Calculator | High-performance calculations for Cash Flow groupings, Donut slices, Heatmap intensities, and Budget pacing. | `Transaction`, `Budget`, `YearMonth` |
| `analytics/model/OverviewAnalyticsModels.kt` | UI Models | Data models for charts: `WeeklyCashFlow`, `SpendingCategory`, `DailySpending`, `BudgetPacingPoint`. | `LocalDate`, `OverviewAnalyticsData` |
| `analytics/components/OverviewAnalyticsSection.kt` | Component | Parent container hosting the four financial visualizations with month navigation controls. | Child chart cards, `HomeViewModel` |
| `analytics/components/CashFlowColumnsCard.kt` | Chart Composable | Native Compose Canvas grouped column chart displaying weekly Money In vs Money Out. | `WeeklyCashFlow`, `OverviewAnalyticsData` |
| `analytics/components/SpendingDonutCard.kt` | Chart Composable | Native Compose Canvas donut chart showing category distribution with center total. | `SpendingCategory`, `Canvas` |
| `analytics/components/DailySpendingHeatmapCard.kt` | Chart Composable | GitHub-style calendar intensity heatmap with interactive day inspection popups. | `DailySpending`, Compose Grid/Canvas |
| `analytics/components/BudgetPacingGraphCard.kt` | Chart Composable | Cumulative actual spending line vs uniform planned pace line with touch scrubber. | `BudgetPacingPoint`, Canvas `Path` |
| `analytics/components/AnalyticsMonthHeader.kt` | Component | Month selector header with previous/next chevron buttons and month picker sheet. | `YearMonth` |

### B. Accounts & Banking (`ui/accounts/`)
| File Path | Component | Responsibilities | Collaborators |
| :--- | :--- | :--- | :--- |
| `AccountScreen.kt` | Screen Composable | Displays linked bank cards (KCB, NCBA, Stanbic, Equity), account linking dialog, and simulation triggers. | `AccountViewModel`, `BankLogo`, `LinkBankAccountDialog` |
| `AccountViewModel.kt` | ViewModel | Manages linked bank accounts stream, account addition, deletion, freshness guards, and KCB/NCBA simulations. | `AccountRepository`, `BankAccountRepository`, `BankIntegrationApi` |

### C. Raha AI Assistant (`ui/raha/`)
| File Path | Component | Responsibilities | Collaborators |
| :--- | :--- | :--- | :--- |
| `RahaFloatingButton.kt` | Floating Component | Collapsible circular floating button with ambient blooming pulse and unread alert glow. | Compose `rememberInfiniteTransition` |
| `animation/AiChatTransition.kt` | Animation | Apple Genie fluid morphing window transition blooming outward from the FAB center. | `graphicsLayer`, `TransformOrigin` |
| `RahaBottomSheet.kt` | Modal Bottom Sheet | Interactive conversational surface with message bubble history, typing indicators, and quick action pills. | `RahaViewModel`, `RahaActionCard` |
| `RahaViewModel.kt` | ViewModel | Manages message list, optimistic user bubble updates, and asynchronous AI dispatch. | `RahaRepository`, `RahaUiState` |
| `RahaUiState.kt` | State Model | Immutable state containing conversation history, typing status, and sheet visibility. | `RahaMessage`, `RahaSender` |
| `components/RahaActionCard.kt` | Component | Interactive action card embedded within assistant messages enabling one-tap navigation. | `RahaAction` |

### D. Authentication, Budgets, Investments, Invoices & Navigation
| File Path | Component | Responsibilities | Collaborators |
| :--- | :--- | :--- | :--- |
| `auth/LoginScreen.kt` | Screen Composable | User sign-in interface with email, password, and biometric triggers. | `AuthViewModel` |
| `auth/SignUpScreen.kt` | Screen Composable | New user registration form. | `AuthViewModel` |
| `auth/AuthViewModel.kt` | ViewModel | Coordinates login, signup, token persistence, and session bootstrap. | `AuthRepository`, `AuthState` |
| `budget/BudgetScreen.kt` | Screen Composable | Budget category cards, monthly limits, and progress meters. | `BudgetViewModel`, `BudgetCard` |
| `budget/BudgetViewModel.kt` | ViewModel | Budget CRUD operations, spending threshold alerts. | `BudgetRepository`, `TransactionRepository` |
| `investment/InvestmentScreen.kt` | Screen Composable | Investment asset breakdown (Money Market Funds, Treasury Bills, Stocks). | `InvestmentViewModel` |
| `investment/InvestmentViewModel.kt` | ViewModel | Asset portfolio valuation and performance tracking. | `InvestmentRepository` |
| `invoice/InvoiceScreen.kt` | Screen Composable | Commercial invoice generation and payment tracking. | `InvoiceViewModel` |
| `invoice/InvoiceViewModel.kt` | ViewModel | Invoice creation, PDF export, status updates. | `InvoiceApi` |
| `transactions/TransactionScreen.kt` | Screen Composable | Full searchable, filterable transaction ledger. | `TransactionViewModel` |
| `transactions/TransactionViewModel.kt` | ViewModel | Search queries with anti-thrashing debounce, category filtering. | `TransactionRepository` |
| `warmup/WarmUpSyncScreen.kt` | Screen Composable | Post-login loading screen synchronizing Room DB caches before dashboard entry. | `WarmUpSyncViewModel` |
| `warmup/WarmUpSyncViewModel.kt` | ViewModel | Executes parallel prewarming of accounts, transactions, and categories. | All Repositories |
| `navigation/Screen.kt` | Sealed Hierarchy | Type-safe destination routes, icons, and titles. | Jetpack Navigation |
| `components/MainResponsiveShell.kt` | Adaptive Shell | Adapts layout: Bottom navigation bar on phones vs Sidebar on tablets. | `AppBottomNavigationBar`, `AppSidebar` |
| `components/BankLogo.kt` | Component | Resolves and renders vector logos for KCB, NCBA, Stanbic, Equity. | Drawable resources |
| `theme/Color.kt`, `Theme.kt`, `Type.kt` | Material Theme | Color schemes (Dark/Light), typography scales, shapes. | Material 3 `ColorScheme` |

---

## 3. Domain Layer (`com.example.smartmoney.domain`)

| Package / File | Category | Responsibilities | Collaborators |
| :--- | :--- | :--- | :--- |
| `model/Transaction.kt` | Domain Model | Pure model for financial movements (amount, type, category, date, pending). | `BigDecimal`, `Date` |
| `model/BankAccount.kt` | Domain Model | Pure model for linked bank accounts (institution, accountNumber, balance, cardType). | `BigDecimal` |
| `model/Budget.kt` | Domain Model | Pure model for spending limits per category. | `BigDecimal` |
| `model/Investment.kt` | Domain Model | Pure model for investment assets and yields. | `BigDecimal` |
| `model/Invoice.kt` | Domain Model | Pure model for commercial invoices. | `BigDecimal` |
| `model/Notification.kt` | Domain Model | In-app notification alerts. | `Instant` |
| `model/RahaMessage.kt` | Domain Model | Conversational message model (sender, text, quick replies, actions). | `RahaSender`, `RahaAction` |
| `repository/TransactionRepository.kt` | Interface | Contract for observing and synchronizing transactions. | Returns `Flow<List<Transaction>>` |
| `repository/BankAccountRepository.kt` | Interface | Contract for observing and managing bank accounts. | Returns `Flow<List<BankAccount>>` |
| `repository/RahaRepository.kt` | Interface | Contract for sending prompts to Raha with financial context. | Returns `Result<RahaMessage>` |
| `repository/AuthRepository.kt` | Interface | Contract for user authentication and session ID queries. | Returns `currentUserId()` |
| `util/TransactionTrendCalculator.kt` | Domain Util | Calculates daily/weekly spending velocity and income trends. | `Transaction` |

---

## 4. Data Layer (`com.example.smartmoney.data`)

| Package / File | Category | Responsibilities | Collaborators |
| :--- | :--- | :--- | :--- |
| `local/database/AppDatabase.kt` | Local DB | Room Database declaration (v4), migration configuration, DAO provider. | SQLite, AndroidX Room |
| `local/database/Converters.kt` | Local DB | Type converters for `BigDecimal`, `Instant`, and `Date`. | Room Database |
| `local/dao/TransactionDao.kt` | DAO | SQL queries for transactions table (`getTransactionsByUserId`). | `TransactionEntity`, SQLite |
| `local/dao/AccountDao.kt` | DAO | SQL queries for accounts table (`getAccountsByUserId`). | `AccountEntity`, SQLite |
| `local/entity/TransactionEntity.kt` | Room Entity | SQLite table schema for transactions. | Room `@Entity` |
| `local/entity/AccountEntity.kt` | Room Entity | SQLite table schema for bank accounts. | Room `@Entity` |
| `local/UserProfileManager.kt` | Local State | Manages active user identity, session purging, and preferences. | `SharedPreferences` |
| `remote/RetrofitClient.kt` | Remote Network | OkHttpClient and Retrofit builder configured for microservice ports (`:8090`, `:8091`). | Retrofit, OkHttp |
| `remote/api/BankIntegrationApi.kt` | Remote API | REST interface for bank linking, IPN health, and demo movements. | Retrofit, `BankTransactionResponse` |
| `remote/api/RahaApi.kt` | Remote API | REST interface for Raha AI chat prompts and context injection. | Retrofit, `RahaDto` |
| `remote/supabase/SupabaseClientProvider.kt` | Remote Cloud | Singleton provider for Supabase Kotlin SDK (Auth, Postgrest). | Supabase SDK |
| `repository/TransactionRepositoryImpl.kt` | Repository Impl | Implements `TransactionRepository`. Single source of truth over Room + Supabase. | `TransactionDao`, `BankIntegrationApi` |
| `repository/BankAccountRepositoryImpl.kt` | Repository Impl | Implements `BankAccountRepository`. Single source of truth over Room + :8090. | `AccountDao`, `BankIntegrationApi` |
| `repository/RahaRepositoryImpl.kt` | Repository Impl | Implements `RahaRepository`. Aggregates local context, calls :8091, fallback engine. | `RahaApi`, `AccountDao`, `TransactionDao` |

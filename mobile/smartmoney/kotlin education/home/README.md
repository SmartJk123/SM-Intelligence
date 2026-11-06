# 🏠 Home & Overview System Documentation

Welcome to the comprehensive architecture, data flow, and request pipeline documentation for the **Home / Overview Screen** in the SmartMoney mobile application.

This documentation explains how financial data travels from remote backend microservices, bank webhook gateways, and local Room databases into the reactive state pipeline, and how every single financial metric and visual chart on the Home screen is requested, computed, and rendered.

---

## 📑 Table of Contents

1. **[01. Architecture & End-to-End Data Flow](file:///home/frank/AndroidStudioProjects/smartmoney/kotlin%20education/home/01_architecture_and_data_flow.md)**
   - High-level Clean Architecture & Unidirectional Data Flow (UDF)
   - The two-stage reactive aggregation pipeline (`rawDataFlow` ➔ `uiState`)
   - The autonomous 6-second background synchronization engine
   - End-to-end data ingestion and recomposition sequence diagrams

2. **[02. Screen Values & Network Request Pipeline](file:///home/frank/AndroidStudioProjects/smartmoney/kotlin%20education/home/02_screen_values_and_request_pipeline.md)**
   - **Exhaustive value-by-value guide**:
     1. Total Account Balance & Total Investment Portfolio
     2. Total Cash In & Total Cash Out metric badges
     3. Ambient Inflow/Outflow Neon Card Glow event
     4. Connected Accounts carousel & linked bank accounts list
     5. 7-Day Cash Flow Trend Graph (native Canvas)
     6. Interactive Month Navigation header
     7. Multi-Timeframe Cash Flow Columns (Day, Week, Month)
     8. Category Spending Donut Chart & Distribution Legend
     9. 30-Day Daily Spending Calendar Heatmap
     10. Budget Pacing Cumulative Velocity Graph & Health Status
     11. Onboarding & First-Time Setup Welcome Sheet
   - For every value: Exact UI widget, ViewModel derivation, mathematical calculation, Room SQLite query, and HTTP microservice request (endpoints, ports, DTOs, headers).

3. **[03. Layer-by-Layer Code Deep Dive](file:///home/frank/AndroidStudioProjects/smartmoney/kotlin%20education/home/03_layer_by_layer_code_deep_dive.md)**
   - Line-by-line exploration across architectural layers:
     - **UI Layer**: `HomeScreen.kt`, `HomeViewModel.kt`, `HomeUiState.kt`, `DashboardScreen.kt`
     - **Domain Layer**: `AccountRepository.kt`, `BankAccountRepository.kt`, `TransactionRepository.kt`, `BudgetRepository.kt`, `TransactionTrendCalculator.kt`
     - **Data Layer**: `AccountRepositoryImpl.kt`, `TransactionRepositoryImpl.kt`, `BankAccountRepositoryImpl.kt`, `BudgetRepositoryImpl.kt`, Room DAOs (`AccountDao`, `TransactionDao`), and `RetrofitClient`
     - **Core Layer**: `AppContainer.kt`, `DefaultAppContainer.kt`, `MainActivity.kt`

4. **[04. Analytics Engine & Native Canvas Visualizations](file:///home/frank/AndroidStudioProjects/smartmoney/kotlin%20education/home/04_analytics_engine_and_visualizations.md)**
   - High-performance `OverviewAnalyticsCalculator` mathematics (O(N) single-pass aggregation, category classification heuristics, linear budget pacing algorithms)
   - Compose native Canvas implementations: Smooth cubic-Bezier splines, gradient area clipping, donut geometry, and multi-layer neon bloom shaders.

---

## 🏛️ Quick Map of Home Screen Classes

```
smartmoney/
├── MainActivity.kt                                       ◄── [UI] Instantiates HomeViewModel with scoped Factory
│
├── core/
│   ├── di/AppContainer.kt                                ◄── [Core] Provides singletons (Room DB & Repositories)
│   └── coroutine/DispatcherProvider.kt                   ◄── [Core] Manages Main, Default, and IO dispatchers
│
├── domain/
│   ├── model/
│   │   ├── Account.kt, BankAccount.kt                    ◄── [Domain] Account data contracts
│   │   ├── Transaction.kt, TrendPoint.kt                 ◄── [Domain] Transaction data contracts
│   │   └── Budget.kt                                     ◄── [Domain] Budget allocations contract
│   ├── repository/
│   │   ├── AccountRepository.kt                          ◄── [Domain] Account data streaming contract
│   │   ├── BankAccountRepository.kt                      ◄── [Domain] Bank card linking & storage contract
│   │   ├── TransactionRepository.kt                      ◄── [Domain] Transaction streaming & sync contract
│   │   └── BudgetRepository.kt                           ◄── [Domain] Budget limit streaming contract
│   └── util/
│       └── TransactionTrendCalculator.kt                 ◄── [Domain] 7-day cash flow aggregation engine
│
├── data/
│   ├── local/
│   │   ├── dao/AccountDao.kt                             ◄── [Data] Room queries for user accounts
│   │   ├── dao/TransactionDao.kt                         ◄── [Data] Room queries for user transactions
│   │   └── entity/AccountEntity.kt, TransactionEntity.kt ◄── [Data] Room database table entities
│   ├── remote/
│   │   ├── RetrofitClient.kt                             ◄── [Data] Microservices HTTP clients & interceptors
│   │   ├── api/AccountApi.kt                             ◄── [Data] accounts-service client (:8082)
│   │   ├── api/TransactionApi.kt                         ◄── [Data] transactions-service client (:8083)
│   │   ├── api/BankIntegrationApi.kt                     ◄── [Data] bank-integration-service client (:8090)
│   │   └── api/ActivityFeedApi.kt                        ◄── [Data] User activity stream endpoint
│   └── repository/
│       ├── AccountRepositoryImpl.kt                      ◄── [Data] Offline-first account cache & remote sync
│       ├── TransactionRepositoryImpl.kt                  ◄── [Data] Multi-source sync (Activity + Bank + Transactions)
│       ├── BankAccountRepositoryImpl.kt                  ◄── [Data] Bank card integration & linking
│       └── BudgetRepositoryImpl.kt                       ◄── [Data] Monthly budget allocations
│
└── ui/
    ├── dashboard/DashboardScreen.kt                      ◄── [UI] Top-level shell hosting Pager (Page 0 = HomeScreen)
    └── home/
        ├── HomeUiState.kt                                ◄── [UI] Immutable state representing all screen values
        ├── HomeViewModel.kt                              ◄── [UI] Two-stage reactive pipeline & background poller
        ├── HomeScreen.kt                                 ◄── [UI] Jetpack Compose layout, cards, and canvas charts
        └── analytics/
            ├── model/OverviewAnalyticsModels.kt          ◄── [UI] Models for donut, heatmap, cash flow & budget pacing
            ├── calculator/OverviewAnalyticsCalculator.kt ◄── [UI] Pure Kotlin multi-dimension calculation engine
            └── components/
                ├── OverviewAnalyticsSection.kt           ◄── [UI] Container for analytics cards
                ├── CashFlowColumnsCard.kt                ◄── [UI] Day/Week/Month cash flow bar charts
                ├── SpendingDonutCard.kt                  ◄── [UI] Category breakdown donut chart
                ├── DailySpendingHeatmapCard.kt           ◄── [UI] Monthly calendar spending intensity matrix
                └── BudgetPacingGraphCard.kt              ◄── [UI] Cumulative linear budget pacing graph
```

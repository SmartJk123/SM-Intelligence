# 💸 Transaction System Documentation

Welcome to the comprehensive architecture and design documentation for **Transactions & Financial Activity** in the SmartMoney mobile application.

This documentation walks through the design, classes, request pipelines, and exact code implementations across all architectural layers: **UI Layer**, **Domain Layer**, **Data Layer (Remote & Local)**, and **Core Infrastructure**.

---

## 📑 Table of Contents

1. **[01. Architecture & End-to-End Data Flow](file:///home/frank/AndroidStudioProjects/smartmoney/kotlin%20education/transaction/01_architecture_and_data_flow.md)**
   - High-level architectural diagram
   - Multi-source transaction ingestion pipeline (Backend Activity Feed, Bank Integration Service, and Supabase)
   - Visual sequence diagrams for:
     - Observing transactions (Offline-First Flow)
     - Syncing transactions & deduplication
     - Recording a manual transaction
     - Real-time system notifications for financial movements
2. **[02. Layer-by-Layer Code Deep Dive](file:///home/frank/AndroidStudioProjects/smartmoney/kotlin%20education/transaction/02_layer_by_layer_code_deep_dive.md)**
   - Line-by-line breakdown of every transaction file and class:
     - **UI Layer**: `TransactionScreen.kt`, `TransactionViewModel.kt`, `TransactionUiState.kt`
     - **Domain Layer**: `Transaction.kt`, `TransactionRepository.kt`, `TransactionTrendCalculator.kt`
     - **Data Layer (Remote)**: `ActivityApi.kt`, `TransactionDto.kt`, `TransactionRemoteDataSource.kt`, `BankIntegrationApi.kt`
     - **Data Layer (Local)**: `TransactionEntity.kt`, `TransactionDao.kt`, `AppDatabase.kt`
     - **Data Layer (Repository)**: `TransactionRepositoryImpl.kt`, `BankTransactionRepository.kt`

---

## 🏛️ Quick Map of Transaction Classes

```
smartmoney/
├── domain/
│   ├── model/Transaction.kt                         ◄── [Domain] Pure business model representing a financial movement
│   ├── repository/TransactionRepository.kt          ◄── [Domain] Pure Kotlin contract for transaction operations
│   └── util/TransactionTrendCalculator.kt           ◄── [Domain] Math engine computing financial trends & cash flow
│
├── data/
│   ├── remote/
│   │   ├── api/ActivityApi.kt                       ◄── [Data] Retrofit endpoint for backend transactions-service (:8083)
│   │   ├── api/BankIntegrationApi.kt                ◄── [Data] Retrofit endpoint for bank movements (:8090)
│   │   ├── dto/TransactionDto.kt                    ◄── [Data] Network DTO with BigDecimal & kotlinx.serialization
│   │   └── datasource/TransactionRemoteDataSource.kt ◄── [Data] Supabase PostgREST data source
│   ├── local/
│   │   ├── entity/TransactionEntity.kt              ◄── [Data] Room entity with multi-column composite indices
│   │   ├── dao/TransactionDao.kt                    ◄── [Data] Room DAO returning reactive Kotlin Flows
│   │   └── database/AppDatabase.kt                  ◄── [Data] SQLite Room database definition
│   └── repository/
│       ├── TransactionRepositoryImpl.kt             ◄── [Data] Multi-source orchestrator, sync engine & notification trigger
│       └── BankTransactionRepository.kt             ◄── [Data] KCB demo movement cache & poller
│
└── ui/
    └── transactions/
        ├── TransactionUiState.kt                    ◄── [UI] Sealed UI states (Loading, Success, Error)
        ├── TransactionViewModel.kt                  ◄── [UI] 60 FPS list filtering, trend calculation & Flow combining
        └── TransactionScreen.kt                     ◄── [UI] Jetpack Compose list, filters, and transaction cards
```

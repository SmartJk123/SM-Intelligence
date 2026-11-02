# 🏦 Accounts & Banking Integration Documentation

Welcome to the comprehensive architecture and design documentation for **Accounts, Bank Integration, and Open Banking Connections** in the SmartMoney mobile application.

This documentation walks through the design, classes, request pipelines, and exact code implementations across all architectural layers: **UI Layer**, **Domain Layer**, **Data Layer (Remote & Local)**, and **Repository Orchestrators**.

---

## 📑 Table of Contents

1. **[01. Architecture & End-to-End Data Flow](file:///home/frank/AndroidStudioProjects/smartmoney/kotlin%20education/accounts/01_architecture_and_data_flow.md)**
   - High-level architectural diagram
   - Multi-tier banking architecture (Bank Integration Service for KCB/Equity/NCBA/Stanbic vs Accounts Service for manual balances)
   - Visual sequence diagrams for:
     - Observing linked accounts (Reactive Room Observation)
     - Linking a bank account (Bank API Handshake & Fallback)
     - Unlinking and cascade deletion
     - Real-time balance adjustment & reconciliation
2. **[02. Layer-by-Layer Code Deep Dive](file:///home/frank/AndroidStudioProjects/smartmoney/kotlin%20education/accounts/02_layer_by_layer_code_deep_dive.md)**
   - Line-by-line breakdown of every account file and class:
     - **UI Layer**: `AccountScreen.kt`, `AccountViewModel.kt`, `BankIntegrationInfo`, `BankLogo.kt`
     - **Domain Layer**: `Account.kt`, `BankAccount.kt`, `AccountConnection.kt`, `AccountRepository.kt`, `BankAccountRepository.kt`
     - **Data Layer (Remote)**: `AccountApi.kt`, `BankIntegrationApi.kt`, `AccountRemoteDataSource.kt`, `AccountDto.kt`, `BankAccountDto.kt`
     - **Data Layer (Local)**: `AccountEntity.kt`, `AccountDao.kt`, `AccountConnectionDao.kt`
     - **Data Layer (Repository)**: `BankAccountRepositoryImpl.kt`, `AccountRepositoryImpl.kt`

---

## 🏛️ Quick Map of Account Classes

```
smartmoney/
├── domain/
│   ├── model/Account.kt                              ◄── [Domain] General account model (deposit, credit, energy)
│   ├── model/BankAccount.kt                          ◄── [Domain] Specific bank card/account entity
│   ├── model/AccountConnection.kt                    ◄── [Domain] Provider aggregation connection status
│   ├── repository/AccountRepository.kt                ◄── [Domain] Pure Kotlin contract for accounts-service
│   └── repository/BankAccountRepository.kt           ◄── [Domain] Contract for linking, unlinking & balance tweaks
│
├── data/
│   ├── remote/
│   │   ├── api/AccountApi.kt                         ◄── [Data] Retrofit endpoints for accounts-service (:8082)
│   │   ├── api/BankIntegrationApi.kt                 ◄── [Data] Retrofit endpoints for bank-integration (:8090 / Render)
│   │   ├── dto/AccountDto.kt                         ◄── [Data] Network DTO with BigDecimal serialization
│   │   ├── dto/BankAccountDto.kt                     ◄── [Data] Supabase table schema DTO
│   │   └── datasource/AccountRemoteDataSource.kt     ◄── [Data] Remote data source wrapping AccountApi
│   ├── local/
│   │   ├── entity/AccountEntity.kt                   ◄── [Data] Room cache table with user indices
│   │   ├── dao/AccountDao.kt                         ◄── [Data] DAO for balance updates & user account queries
│   │   └── dao/AccountConnectionDao.kt               ◄── [Data] DAO for tracking sync health & errors
│   └── repository/
│       ├── BankAccountRepositoryImpl.kt              ◄── [Data] Hybrid orchestrator (Bank API with Manual Fallback)
│       └── AccountRepositoryImpl.kt                  ◄── [Data] Repository implementation for accounts-service (:8082)
│
└── ui/
    └── accounts/
        ├── AccountViewModel.kt                       ◄── [UI] StateFlow consolidator, cooldown guard & link trigger
        ├── AccountScreen.kt                          ◄── [UI] Jetpack Compose bank cards, balance pills & add dialog
        └── components/BankLogo.kt                    ◄── [UI] Dynamic vector branding (KCB, Equity, Stanbic, NCBA)
```

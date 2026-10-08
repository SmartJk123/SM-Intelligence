# Architecture Dependency Graphs: Visualizing Component Couplings

This document maps the exact, file-to-file relationships and data transformations across the SmartMoney mobile application using visual Mermaid diagrams.

---

## 1. Feature 1: Overview & Financial Visualizations Pipeline

This pipeline demonstrates how transactions flow from the local database, through calculation models, into the four custom Compose Canvas charts on the Overview page.

```mermaid
graph TD
    subgraph Data_Layer ["Data Layer (Persistence)"]
        RoomDB[(Room AppDatabase)]
        TxDao[TransactionDao.kt]
        TxEntity[TransactionEntity.kt]
        RoomDB --> TxDao
        TxDao --> TxEntity
    end

    subgraph Repository_Layer ["Repository Layer (Domain Inversion)"]
        TxRepoImpl[TransactionRepositoryImpl.kt]
        TxRepoInterface[TransactionRepository.kt]
        TxRepoImpl -.->|Implements| TxRepoInterface
        TxDao -->|Flow of Entities| TxRepoImpl
        TxRepoImpl -->|Maps to Domain| TxDomain[Transaction.kt]
    end

    subgraph ViewModel_Layer ["ViewModel Layer (State Synthesis)"]
        HomeVM[HomeViewModel.kt]
        Calc[OverviewAnalyticsCalculator.kt]
        HomeState[HomeUiState.kt]

        TxRepoInterface -->|Flow of Domain Transactions| HomeVM
        HomeVM -->|Passes Transactions on Dispatchers.Default| Calc
        Calc -->|Produces Pre-computed Models| AnalyticsModels[OverviewAnalyticsModels.kt\n- WeeklyCashFlow\n- SpendingCategory\n- DailySpending\n- BudgetPacingPoint]
        AnalyticsModels --> HomeState
        HomeVM -->|Emits StateFlow| HomeState
    end

    subgraph UI_Layer ["Presentation Layer (Jetpack Compose Canvas)"]
        HomeScreen[HomeScreen.kt]
        OverviewSection[OverviewAnalyticsSection.kt]
        CashFlowCard[CashFlowColumnsCard.kt\nGrouped Bar Canvas]
        DonutCard[SpendingDonutCard.kt\nArc Sweep Canvas]
        HeatmapCard[DailySpendingHeatmapCard.kt\nCalendar Grid Canvas]
        PacingCard[BudgetPacingGraphCard.kt\nBezier Path Canvas]

        HomeState -->|collectAsStateWithLifecycle| HomeScreen
        HomeScreen --> OverviewSection
        OverviewSection --> CashFlowCard
        OverviewSection --> DonutCard
        OverviewSection --> HeatmapCard
        OverviewSection --> PacingCard
    end
```

---

## 2. Feature 2: Bank Integration & Account Linking Pipeline

This pipeline maps how external bank accounts (KCB, NCBA, Stanbic, Equity) are linked and how Instant Payment Notifications (IPN) reach the mobile app.

```mermaid
sequenceDiagram
    autonumber
    actor User as User / Mobile Client
    participant UI as AccountScreen.kt
    participant VM as AccountViewModel.kt
    participant Repo as BankAccountRepositoryImpl.kt
    participant API as BankIntegrationApi.kt (:8090)
    participant DAO as AccountDao.kt
    participant DB as AppDatabase (Room)

    User->>UI: Selects Bank (e.g. NCBA/KCB) & inputs Account Number
    UI->>VM: linkBankAccount(bank, number, type)
    VM->>Repo: linkAccount(bank, number, type)
    Repo->>API: POST /api/v1/admin/account-links (LinkBankRequest)
    API-->>Repo: Returns BankLinkResponse (DTO)
    Repo->>DAO: Upserts AccountEntity into Room
    DAO->>DB: SQLite INSERT/REPLACE INTO accounts
    DB-->>DAO: Invalidation Tracker triggers query re-run
    DAO-->>VM: Emits Flow<List<AccountEntity>> mapped to BankAccount
    VM-->>UI: StateFlow<AccountUiState> updates atomically!
    UI-->>User: Card appears instantly with brand glow and balance!
```

---

## 3. Feature 3: Raha AI Assistant & Financial Grounding Pipeline

This pipeline illustrates how Raha queries local financial state to ground user queries without leaking personal identifying information over the wire.

```mermaid
graph TD
    subgraph UI_Surface ["Conversational UI Surface"]
        FAB[RahaFloatingButton.kt] -->|Tap Trigger| Transition[AiChatTransition.kt\nApple Genie Morph]
        Transition --> Sheet[RahaBottomSheet.kt]
        Sheet -->|User Prompt| RahaVM[RahaViewModel.kt]
    end

    subgraph Context_Assembly ["Local Privacy Grounding Engine"]
        RahaVM -->|Query with History| RahaRepo[RahaRepositoryImpl.kt]
        AccountDao[AccountDao.kt] -->|Read Live Balances| RahaRepo
        TxDao[TransactionDao.kt] -->|Read Recent 30d Spending| RahaRepo
        BudgetRepo[BudgetRepository] -->|Read Category Limits| RahaRepo

        RahaRepo --> Masker[PII Masker & Anonymizer]
        Masker --> ContextDto[FinancialContextDto\n- Liquid Balance: KES 125,000\n- Masked Accounts: KCB ****164, NCBA ****902\n- Monthly Spend: KES 48,350]
    end

    subgraph Microservice ["Raha Intelligence Microservice (:8091)"]
        RahaApi[RahaApi.kt]
        PythonServer[FastAPI / LangChain Reasoning Engine]
        ContextDto --> RahaApi
        RahaApi -->|POST /api/v1/chat| PythonServer
    end

    subgraph Fallback_System ["Resilience & Fallback Engine"]
        PythonServer -.->|If Offline or Timeout| LocalHeuristics[Local Heuristic Engine\nGenerates Accurate Offline Balance Summary]
        LocalHeuristics --> RahaMsg[RahaMessage.kt]
        PythonServer -->|200 OK Response| RahaMsg
        RahaMsg --> RahaVM
        RahaVM -->|Appends to Message List| Sheet
    end
```

---

## 4. Feature 4: Post-Login Warm-Up Synchronization Pipeline

When a user signs in, the application performs an orchestrated pre-warming synchronization in [`WarmUpSyncViewModel.kt`](file:///home/frank/AndroidStudioProjects/smartmoney/app/src/main/java/com/example/smartmoney/ui/warmup/WarmUpSyncViewModel.kt) to ensure the local database cache is fully primed before revealing the dashboard.

```mermaid
flowchart TD
    LoginSuccess[User Signs In via LoginScreen.kt] --> WarmUpNav[Navigate to WarmUpSyncScreen.kt]
    WarmUpNav --> WarmUpVM[WarmUpSyncViewModel.kt]

    subgraph Parallel_Sync ["Structured Concurrency: CoroutineScope(IO).launch"]
        Job1["Sync Bank Accounts\n(BankAccountRepository.syncBankAccounts())"]
        Job2["Sync Transactions\n(TransactionRepository.syncTransactions())"]
        Job3["Sync In-App Notifications\n(NotificationRepository.syncNotifications())"]
        Job4["Warm User Preferences\n(UserPreferencesRepository.loadPreferences())"]
    end

    WarmUpVM --> Job1
    WarmUpVM --> Job2
    WarmUpVM --> Job3
    WarmUpVM --> Job4

    Job1 --> RoomCache[(Room SQLite Local Cache Primed)]
    Job2 --> RoomCache
    Job3 --> RoomCache
    Job4 --> RoomCache

    RoomCache --> AllReady{All Sync Jobs Complete?}
    AllReady -->|Yes| NavHome[Navigate to Screen.Home (Instant 0ms Load!)]
    AllReady -->|Network Failure| NavHomeFallback[Log Warning & Navigate to Screen.Home with Local Cache]
```

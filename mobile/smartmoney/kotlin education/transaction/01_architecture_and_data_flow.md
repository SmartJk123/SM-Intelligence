# 01. Transaction Architecture & End-to-End Data Flow

This document details the architectural layout, data pipelines, and request journeys of the **Transaction System** in SmartMoney.

---

## 1. High-Level Architectural Flow

SmartMoney implements an **Offline-First Multi-Source Transaction Pipeline**. Transactions are ingested from three possible backends, normalized into Room database entities, and reactively pushed to the UI via Kotlin `Flow`.

```mermaid
flowchart TD
    subgraph UI Layer
        TS["TransactionScreen (Compose)"]
        TVM["TransactionViewModel"]
    end

    subgraph Domain Layer
        TR["TransactionRepository (Interface)"]
        TM["Transaction (Domain Model)"]
    end

    subgraph Data Layer - Repository
        TRI["TransactionRepositoryImpl (Orchestrator)"]
        NH["NotificationHelper (System Alerts)"]
    end

    subgraph Data Layer - Local Persistence
        TDAO["TransactionDao (Room)"]
        TDB[("SQLite AppDatabase")]
    end

    subgraph Data Layer - Remote Sources
        ACT["ActivityApi (:8083)\nGET /api/transactions/activity"]
        BANK["BankIntegrationApi (:8090 / Render)\nGET /api/v1/admin/demo/transactions"]
        SUPA["TransactionRemoteDataSource\nSupabase PostgREST transactions table"]
    end

    TS -->|"Observes filteredTransactions"| TVM
    TVM -->|"uiState combines getTransactionsFlow()"| TR
    TR -.->|"Implemented by"| TRI

    %% Local Read Path (Instant)
    TRI -->|"Reads Flow<List<TransactionEntity>>"| TDAO
    TDAO -->|"Queries indexed table"| TDB
    TDAO -.->|"Emits fresh data"| TRI
    TRI -.->|"toDomain() -> List<Transaction>"| TVM

    %% Remote Ingestion Path (Background)
    TRI -->|"1. syncActivityFeed()"| ACT
    TRI -->|"2. getKcbTransactions()"| BANK
    TRI -->|"3. fetchTransactions()"| SUPA

    ACT -.->|"ActivityItem list"| TRI
    BANK -.->|"BankTransactionResponse list"| TRI
    SUPA -.->|"TransactionDto list"| TRI

    TRI -->|"Upserts validated entities"| TDAO
    TRI -->|"Triggers notifications on new inflow/outflow"| NH
```

---

## 2. Core Request Journeys

### Journey 1: Viewing Transactions (Offline-First Flow)

SmartMoney prioritizes instant startup and zero-latency rendering. The UI **never** waits for the network before rendering transactions.

```mermaid
sequenceDiagram
    autonumber
    actor User
    participant UI as TransactionScreen
    participant VM as TransactionViewModel
    participant Repo as TransactionRepositoryImpl
    participant DAO as TransactionDao (Room)
    participant DB as SQLite Cache

    User->>UI: Opens Transactions Tab
    UI->>VM: Observes uiState & filteredTransactions
    VM->>Repo: getTransactionsFlow(userId)
    Repo->>DAO: getTransactionsForUser(userId)
    DAO->>DB: SELECT * FROM transactions WHERE userId = :userId ORDER BY timestamp DESC
    DB-->>DAO: Cached rows
    DAO-->>Repo: Emits Flow<List<TransactionEntity>>
    Repo->>Repo: Converts to List<Transaction> (toDomain())
    Repo-->>VM: Emits List<Transaction>
    VM->>VM: Computes filters (ALL, CREDIT, DEBIT) on Dispatchers.Default
    VM-->>UI: Emits TransactionUiState.Success
    UI-->>User: Screen renders immediately (0ms network delay)
```

1. **Instant Render**: Even if the device is in Airplane Mode, Room emits the cached transactions immediately.
2. **Background Thread Computation**: Filtering (`CREDIT` vs `DEBIT`) runs on `Dispatchers.Default` inside the ViewModel, preventing UI lag or jank during scrolling.

---

### Journey 2: Syncing Remote Transactions & Deduplication

When the screen mounts or a pull-to-refresh is triggered, [`TransactionRepositoryImpl.syncTransactions()`](file:///home/frank/AndroidStudioProjects/smartmoney/app/src/main/java/com/example/smartmoney/data/repository/TransactionRepositoryImpl.kt#L59) synchronizes external transactions from all active backends.

```mermaid
sequenceDiagram
    autonumber
    participant VM as TransactionViewModel
    participant Repo as TransactionRepositoryImpl
    participant ActApi as ActivityApi (:8083)
    participant BankApi as BankIntegrationApi (:8090)
    participant DAO as TransactionDao (Room)
    participant Notif as NotificationHelper

    VM->>Repo: syncTransactions(userId)
    
    rect rgb(240, 248, 255)
        Note over Repo, ActApi: Source 1: Backend Transactions Service (:8083)
        Repo->>ActApi: getActivity(limit = 200)
        ActApi-->>Repo: List<ActivityItem>
        Repo->>Repo: Map ActivityItem -> TransactionEntity
        Repo->>DAO: upsertTransactions(entities)
        Repo->>Notif: Post push notification if new transaction detected
    end

    rect rgb(255, 250, 240)
        Note over Repo, BankApi: Source 2: Bank Integration Movements (:8090)
        Repo->>BankApi: getKcbTransactions(userId, accountNumber)
        BankApi-->>Repo: List<BankTransactionResponse>
        Repo->>Repo: Validate movement matches user's verified accounts
        Repo->>DAO: upsertTransactions(entities)
    end

    DAO-->>VM: Room auto-emits new dataset to getTransactionsFlow()
    VM-->>VM: UI updates automatically!
```

#### Key Safeguards in Syncing:
1. **User Account Verification**: In [`TransactionRepositoryImpl.kt`](file:///home/frank/AndroidStudioProjects/smartmoney/app/src/main/java/com/example/smartmoney/data/repository/TransactionRepositoryImpl.kt#L80-L105), incoming bank movements are strictly validated against accounts owned by the signed-in `userId`. Unrelated bank movements are dropped immediately to prevent cross-user data leakage.
2. **Notification Deduplication**: An in-memory synchronized set (`alertedTxIds`) tracks posted notification IDs so users never receive duplicate push alerts for the same movement.

---

### Journey 3: Recording a Transaction (Creating / Adding)

When a manual transaction or payment is recorded:

```mermaid
sequenceDiagram
    autonumber
    actor User
    participant UI as AddTransactionDialog
    participant VM as TransactionViewModel
    participant Repo as TransactionRepositoryImpl
    participant Supa as TransactionRemoteDataSource
    participant DAO as TransactionDao (Room)

    User->>UI: Enter amount, account, description -> "Save"
    UI->>Repo: recordTransaction(transaction, userId)
    Repo->>Repo: TransactionDto.fromDomain(transaction)
    
    alt Remote Backend Available
        Repo->>Supa: recordTransaction(dto)
        Supa-->>Repo: TransactionDto (Server confirmed with timestamp)
        Repo->>Repo: dto.toDomain()
    else Offline Fallback
        Repo->>Repo: Keep local timestamp and UUID
    end

    Repo->>DAO: upsertTransaction(TransactionEntity.fromDomain(domainTx))
    DAO-->>VM: Room emits updated list via Flow
    Repo-->>UI: Result.success(transaction)
    UI-->>User: Dialog closes, transaction appears in list
```

---

### Journey 4: The Push Notification Trigger

In [`TransactionRepositoryImpl.kt`](file:///home/frank/AndroidStudioProjects/smartmoney/app/src/main/java/com/example/smartmoney/data/repository/TransactionRepositoryImpl.kt#L390-L402):
Whenever new movements are fetched from [`ActivityApi`](file:///home/frank/AndroidStudioProjects/smartmoney/app/src/main/java/com/example/smartmoney/data/remote/api/ActivityApi.kt), the repository checks if it has previously alerted the user:

```kotlin
if (!alertedTxIds.add("activity_${item.id}")) continue

val isCredit = !item.direction.equals("DEBIT", ignoreCase = true)
val bankUpper = (item.bank?.takeIf { it.isNotBlank() } ?: "Bank").uppercase(Locale.ROOT)
val formatted = DecimalFormat("#,##0.00").format(item.amount)
val currency = item.currency ?: "KES"

NotificationHelper.postSystemNotification(
    context,
    if (isCredit) "$bankUpper Inflow Received" else "$bankUpper Outflow Paid",
    if (isCredit) "+$currency $formatted credited • $note" else "-$currency $formatted debited • $note"
)
```
This guarantees real-time feedback on user deposits and withdrawals without requiring external FCM services.

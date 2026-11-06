# 01. Accounts Architecture & End-to-End Data Flow

This document details the architectural layout, data pipelines, and request journeys of the **Account & Banking Integration System** in SmartMoney.

---

## 1. High-Level Architectural Flow

SmartMoney uses a **Hybrid Multi-Tier Banking Pipeline**. Supported Tier-1 financial institutions (KCB, Equity, NCBA, Stanbic) route through the dedicated **Bank Integration Service** (`:8090` or Render Cloud), while generic accounts fall back to the **Accounts Service** (`:8082`) and local Room persistence.

```mermaid
flowchart TD
    subgraph UI Layer
        AS["AccountScreen (Compose)"]
        AVM["AccountViewModel"]
    end

    subgraph Domain Layer
        BAR["BankAccountRepository (Interface)"]
        AR["AccountRepository (Interface)"]
    end

    subgraph Data Layer - Repository
        BARI["BankAccountRepositoryImpl (Hybrid Router)"]
        ARI["AccountRepositoryImpl"]
    end

    subgraph Data Layer - Local Persistence
        ADAO["AccountDao (Room)"]
        TDAO["TransactionDao (Cascade Clean)"]
        ADB[("SQLite AppDatabase")]
    end

    subgraph Data Layer - Remote APIs
        BIA["BankIntegrationApi (:8090 / Render)\nPOST /api/v1/admin/account-links"]
        AA["AccountApi (:8082)\nPOST /api/accounts"]
    end

    AS -->|"Observes uiState & bankAccounts"| AVM
    AVM -->|"bankAccountRepository.getBankAccounts()"| BAR
    BAR -.->|"Implemented by"| BARI

    %% Local Read Path
    BARI -->|"getAccountsForUser(userId)"| ADAO
    ADAO -->|"Queries indexed table"| ADB
    ADAO -.->|"Emits Flow<List<AccountEntity>>"| BARI
    BARI -.->|"toBankAccount() -> List<BankAccount>"| AVM

    %% Remote Linking Path
    BARI -->|"Supported Bank (kcb, stanbic, ncba, equity)"| BIA
    BARI -->|"Fallback / Manual Account"| AA

    BIA -.->|"BankLinkResponse (id, accountId, maskedIdentifier)"| BARI
    AA -.->|"AccountResponse"| BARI

    BARI -->|"Upserts into Room cache"| ADAO
    BARI -->|"Cascade delete on unlinking"| TDAO
```

---

## 2. Core Request Journeys

### Journey 1: Viewing Linked Accounts & Freshness Guard

```mermaid
sequenceDiagram
    autonumber
    actor User
    participant UI as AccountScreen
    participant VM as AccountViewModel
    participant Repo as BankAccountRepositoryImpl
    participant DAO as AccountDao (Room)
    participant DB as SQLite Cache

    User->>UI: Opens Accounts Tab
    UI->>VM: Observes uiState
    VM->>Repo: getBankAccounts()
    Repo->>DAO: getAccountsForUser(userId)
    DAO->>DB: SELECT * FROM accounts WHERE userId = :userId
    DB-->>DAO: Cached rows
    DAO-->>Repo: Emits Flow<List<AccountEntity>>
    Repo->>Repo: Map to List<BankAccount> (toBankAccount())
    Repo-->>VM: Emits List<BankAccount>
    VM-->>UI: Emits AccountUiState(bankAccounts = [...])
    UI-->>User: Renders cards immediately (0ms delay)
```

#### The 30-Second Freshness Guard
In [`AccountViewModel.kt`](file:///home/frank/AndroidStudioProjects/smartmoney/app/src/main/java/com/example/smartmoney/ui/accounts/AccountViewModel.kt#L64-L66):
```kotlin
private var lastSyncTimestamp: Long = 0L
private val SYNC_COOLDOWN_MS = 30_000L
```
When navigating between screens or app warmup, sync requests within 30 seconds of an existing sync are ignored. This prevents duplicate network roundtrips and avoids rate-limiting backend services.

---

### Journey 2: Linking a Real Bank Account (With Resilient Fallback)

When the user links an account (e.g. KCB or Equity), [`BankAccountRepositoryImpl.addBankAccount()`](file:///home/frank/AndroidStudioProjects/smartmoney/app/src/main/java/com/example/smartmoney/data/repository/BankAccountRepositoryImpl.kt#L77-L162) executes an intelligent two-tier handshake:

```mermaid
sequenceDiagram
    autonumber
    actor User
    participant UI as AddAccountDialog
    participant VM as AccountViewModel
    participant Repo as BankAccountRepositoryImpl
    participant BankApi as BankIntegrationApi (:8090)
    participant AccApi as AccountApi (:8082)
    participant DAO as AccountDao (Room)

    User->>UI: Select Bank: "KCB", Acc: "1234567890", Type: "Credit"
    UI->>VM: addBankAccount(...)
    VM->>Repo: addBankAccount(bankName, accountNumber, cardType)

    rect rgb(240, 255, 240)
        Note over Repo, BankApi: Step 1: Attempt Live Bank Integration
        Repo->>BankApi: linkAccount(LinkBankRequest(bankId="kcb", accountNumber=..., userId=...))
        alt Bank Integration Service Responds (200 OK)
            BankApi-->>Repo: BankLinkResponse(accountId, accountName, ...)
            Repo->>DAO: upsertAccount(AccountEntity(dataSource="BANK_API"))
            Repo-->>VM: Result.success(Unit)
        else Bank Integration Fails / Service Unreachable
            Note over Repo, AccApi: Step 2: Resilient Fallback to accounts-service (:8082)
            Repo->>AccApi: createAccount(CreateAccountRequest(dataSource="MANUAL"))
            AccApi-->>Repo: AccountResponse
            Repo->>DAO: upsertAccount(AccountEntity(dataSource="MANUAL"))
            Repo-->>VM: Result.success(Unit)
        end
    end

    DAO-->>VM: Room auto-emits updated account list!
    VM-->>UI: Dialog closes, new card animated onto screen
```

---

### Journey 3: Unlinking and Cascade Deletion

When an account is unlinked or removed:

```mermaid
sequenceDiagram
    autonumber
    actor User
    participant UI as AccountScreen
    participant VM as AccountViewModel
    participant Repo as BankAccountRepositoryImpl
    participant BankApi as BankIntegrationApi
    participant AccDao as AccountDao (Room)
    participant TxDao as TransactionDao (Room)

    User->>UI: Tap "Unlink Account"
    UI->>VM: removeBankAccount(id)
    VM->>Repo: removeBankAccount(id)

    rect rgb(255, 245, 245)
        Note over Repo, BankApi: Remote Unlinking (Best Effort)
        Repo->>BankApi: unlinkAccount(id)
        BankApi-->>Repo: 200 OK / Unit
    end

    rect rgb(245, 245, 255)
        Note over Repo, TxDao: Local Persistence & Cascade Clean
        Repo->>AccDao: deleteAccountById(id)
        Repo->>TxDao: deleteTransactionsForAccount(userId, id)
    end

    AccDao-->>VM: Room emits updated bank account list (without deleted account)
    TxDao-->>UI: Transaction history automatically purges movements from deleted card!
    Repo-->>VM: Result.success(Unit)
```

* **Cascade Cleaning**: In [`BankAccountRepositoryImpl.kt`](file:///home/frank/AndroidStudioProjects/smartmoney/app/src/main/java/com/example/smartmoney/data/repository/BankAccountRepositoryImpl.kt#L251-L253), deleting an account triggers `transactionDao.deleteTransactionsForAccount(userId, id)`. This ensures that ghost transactions from deleted accounts never remain in the user's dashboard.

---

### Journey 4: Real-Time Balance Adjustment & Reconciliation

When simulated movements or debit transactions happen, the system updates account balances dynamically:

```kotlin
// BankAccountRepositoryImpl.kt (Line 265)
override suspend fun adjustBankBalance(
    bankName: String,
    delta: BigDecimal,
    accountId: String?,
    userId: String?
) = withContext(dispatchers.io) {
    val effectiveUserId = userId ?: userIdProvider()
    if (localDao != null) {
        if (!accountId.isNullOrBlank()) {
            localDao.adjustAccountBalance(effectiveUserId, accountId, delta)
        } else {
            localDao.adjustBankBalanceForUser(effectiveUserId, bankName, delta)
        }
    }
}
```
Room instantly computes the new balance and triggers recomposition on the account card!

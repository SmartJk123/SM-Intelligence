# User Data Isolation & Distributed Cross-Database Relationships

## 1. Overview & Problem Statement

### 1.1 Observed Issues
During multi-user authentication testing on the SmartMoney mobile client, two critical data isolation vulnerabilities were identified:
1. **Cross-User Transaction Leakage**: When a user registered a new account or signed in on a device previously used by another user, transactions belonging to the previous user were displayed on the Overview, Accounts, and Transactions screens.
2. **Profile Picture Bleed**: A newly registered account displayed the custom profile picture uploaded by the previous user rather than defaulting to the new user's initials.

### 1.2 Underlying Root Causes
1. **Un-scoped Offline Room Cache**:
   - [`TransactionEntity`](file:///home/frank/AndroidStudioProjects/smartmoney/app/src/main/java/com/example/smartmoney/data/local/entity/TransactionEntity.kt) and [`NotificationEntity`](file:///home/frank/AndroidStudioProjects/smartmoney/app/src/main/java/com/example/smartmoney/data/local/entity/NotificationEntity.kt) lacked a `userId` column in the SQLite schema.
   - DAOs ([`TransactionDao`](file:///home/frank/AndroidStudioProjects/smartmoney/app/src/main/java/com/example/smartmoney/data/local/dao/TransactionDao.kt) and [`NotificationDao`](file:///home/frank/AndroidStudioProjects/smartmoney/app/src/main/java/com/example/smartmoney/data/local/dao/NotificationDao.kt)) performed global `SELECT *` queries across all persisted rows regardless of the active user.
   - On logout, [`MainActivity.kt`](file:///home/frank/AndroidStudioProjects/smartmoney/app/src/main/java/com/example/smartmoney/MainActivity.kt) called `authViewModel.signOut()` without clearing the local SQLite tables (`energy_cache_database`). Consequently, previous session data persisted on disk.
2. **Static Profile Avatar Storage**:
   - [`UserProfileManager`](file:///home/frank/AndroidStudioProjects/smartmoney/app/src/main/java/com/example/smartmoney/data/local/UserProfileManager.kt) stored avatars using a single global filename (`user_profile_avatar.jpg`) in internal storage with no user ID scoping.
   - An in-memory static `_profileBitmap` StateFlow was retained across sessions and never wiped on sign out.
   - [`SmartMoneyApplication`](file:///home/frank/AndroidStudioProjects/smartmoney/app/src/main/java/com/example/smartmoney/SmartMoneyApplication.kt) loaded `user_profile_avatar.jpg` at app launch before any user authenticated.
3. **Hardcoded User Reference in Bank Ingestion**:
   - In [`TransactionRepositoryImpl`](file:///home/frank/AndroidStudioProjects/smartmoney/app/src/main/java/com/example/smartmoney/data/repository/TransactionRepositoryImpl.kt), bank transactions from `bank-integration-service` were ingested using a hardcoded `userId = "default-user"`.

---

## 2. Distributed Architecture: Cross-Database Data Relationships

A central architectural challenge in microservices is maintaining data integrity and relationships when individual domains reside in completely separate databases.

In the SmartMoney intelligence backend ([`docker-compose.yml`](file:///home/frank/SmartMoney_intelligence/SM-Intelligence/backend/docker-compose.yml)), the system employs the **Database-per-Service** pattern:
- `identity-service` &rarr; `smi_identity` (PostgreSQL on port 55432)
- `accounts-service` &rarr; `smi_accounts` (PostgreSQL on port 5433)
- `transactions-service` &rarr; `smi_transactions` (PostgreSQL on port 5434)
- `bank-integration-service` &rarr; Port 8090
- `categories-service`, `budgets-service`, `investments-service`, `notifications-service` &rarr; Ports 5435–5440
- `smi-kafka` &rarr; Apache Kafka event broker on port 9092

Traditional relational database Foreign Key (FK) constraints cannot cross network or database engine boundaries. To establish clean relationships across these isolated databases, the system implements four fundamental patterns:

```
+-----------------------------------------------------------------------------------+
|                                 IDENTITY SERVICE                                  |
|                         Database: smi_identity (:55432)                           |
|  Table: users                                                                     |
|  - id: UUID (Canonical Root Entity: e.g., 9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d)   |
|  - name, email, password_hash                                                     |
+------------------------------------------+----------------------------------------+
                                           | Mints JWT (sub = user_id)
                                           v
+-----------------------------------------------------------------------------------+
|                                   API GATEWAY                                     |
|  - Validates JWT signature & expiration                                           |
|  - Injects trusted downstream HTTP Header: X-User-Id: <user_id>                   |
+---------------------+--------------------+--------------------+-------------------+
                      |                    |                    |
                      v                    v                    v
+-----------------------------+ +---------------------+ +--------------------------+
|      ACCOUNTS SERVICE       | | BANK-INTEGRATION    | |   TRANSACTIONS SERVICE   |
| Database: smi_accounts(:5433| | Database: (Port 8090| | Database: smi_tx (:5434)  |
| Table: accounts             | | Table: bank_link    | | Table: transactions      |
| - id: UUID (PK)             | | - id: UUID (PK)     | | - id: UUID (PK)          |
| - user_id: UUID (Soft FK)   | | - user_id: UUID(SFK)| | - account_id: UUID (SFK) |
| - provider_account_id       | | - account_number    | | - amount, type, status   |
| - ledger_balance, currency  | | - bank_id: 'kcb'    | | - provider_reference     |
+-----------------------------+ +---------------------+ +--------------------------+
              ^                                                     |
              |                                                     |
              +=================== Logical Join ====================+
                               (via account_id)
```

### 2.1 The Canonical Root Identifier (`user_id` as Soft Foreign Key)
- The [`identity-service`](file:///home/frank/SmartMoney_intelligence/SM-Intelligence/backend/identity-service/src/main/java/com/smi/identity_service/domain/User.java) acts as the sole authority for user identity, generating an immutable, cryptographically random `UUID v4` upon registration.
- Downstream microservices store this UUID as an indexed column (**Soft Foreign Key / Logical Reference**):
  - In `accounts-service`: `accounts.user_id: UUID NOT NULL` (indexed).
  - In `bank-integration-service`: `bank_link.user_id: VARCHAR(40) NOT NULL` (indexed).
  - In `budgets-service`: `budgets.user_id: UUID NOT NULL` (indexed).
  - In `notifications-service`: `notifications.user_id: UUID NOT NULL` (indexed).

### 2.2 Hierarchical Relationship Mapping
Entities relate to users either directly or hierarchically:
- **Direct Domain Mapping**: High-cohesion user entities (Accounts, Bank Links, Budgets, Notifications) store `user_id` directly.
- **Hierarchical Domain Mapping**: Financial transactions belong directly to an Account (`transactions.account_id: UUID NOT NULL`). Because an Account belongs to a User, the relationship forms a strict hierarchy:
  $$\text{User } (\text{Identity}) \xrightarrow{1:N} \text{Account } (\text{Accounts}) \xrightarrow{1:N} \text{Transaction } (\text{Transactions})$$
  Transactions are resolved by querying the user's verified `accountIds` or via denormalized `user_id` indexing.

### 2.3 Cryptographic Token Propagation & Gateway Enforcement
- Upon successful authentication, `identity-service` mints a signed JWT containing `sub = user_id`.
- The mobile client transmits this token in HTTP requests via `Authorization: Bearer <token>`.
- The `api-gateway` validates the cryptographic signature and expiration, extracting the `sub` claim and injecting a trusted downstream header: `X-User-Id: <user_id>`.
- Microservices extract this identifier and enforce query-level tenancy:
  ```sql
  -- Scoped in accounts-service
  SELECT * FROM accounts WHERE user_id = :userId;

  -- Scoped in bank-integration-service
  SELECT * FROM bank_link WHERE user_id = :userId;
  ```

### 2.4 Asynchronous Event Choreography (Kafka)
- Cross-service lifecycle events (e.g. user deletion or account closure) are handled asynchronously via Kafka (`smi-kafka:9092`).
- When a user requests account deletion in `identity-service`, it publishes a `UserDeletedEvent(userId)` topic message.
- Downstream services consume this event and cascade-delete or archive corresponding rows in their respective databases, ensuring GDPR right-to-erasure and eventual consistency without point-to-point HTTP coupling.

### 2.5 API Composition & Mobile Aggregation
- Without cross-database SQL `JOIN` capabilities, the mobile client executes **API Composition**:
  - Screen ViewModels ([`HomeViewModel`](file:///home/frank/AndroidStudioProjects/smartmoney/app/src/main/java/com/example/smartmoney/ui/home/HomeViewModel.kt)) collect reactive Kotlin Flows from independent repositories (`accountRepository.getAccountsFlow(userId)` + `transactionRepository.getTransactionsFlow(userId)`).
  - Data streams are combined concurrently off the Main Thread on `Dispatchers.Default`.

---

## 3. Mobile Client Implementation Details

### 3.1 User-Scoped Avatar Isolation ([`UserProfileManager.kt`](file:///home/frank/AndroidStudioProjects/smartmoney/app/src/main/java/com/example/smartmoney/data/local/UserProfileManager.kt))
- **Dynamic File Generation**: Replaced the global `"user_profile_avatar.jpg"` with a user-partitioned helper:
  ```kotlin
  fun getAvatarFile(context: Context, userId: String): File {
      return File(context.filesDir, "user_profile_${userId}_avatar.jpg")
  }
  ```
- **Active Session Switching**: Added `switchUser(context, userId)`. If `userId` is null or the user has no saved avatar, `_profileBitmap.value` emits `null` (displaying the user's colored initials). When an avatar exists for that user, it is decoded on `Dispatchers.IO`.
- **Session Teardown**: Added `clearSession()` to reset `currentUserId` and emit `null` to `_profileBitmap`.
- **Screen Binding**: Updated [`MoreScreen.kt`](file:///home/frank/AndroidStudioProjects/smartmoney/app/src/main/java/com/example/smartmoney/ui/more/MoreScreen.kt) and [`DashboardScreen.kt`](file:///home/frank/AndroidStudioProjects/smartmoney/app/src/main/java/com/example/smartmoney/ui/dashboard/DashboardScreen.kt) to accept `userId` and scope avatar updates and removals to that user.
- **Application Startup**: Updated [`SmartMoneyApplication.kt`](file:///home/frank/AndroidStudioProjects/smartmoney/app/src/main/java/com/example/smartmoney/SmartMoneyApplication.kt) to inspect `authRepository.currentUserId()` and only switch to a valid session, preventing premature un-scoped avatar decoding.

### 3.2 Room Schema Migration (Version 4)
- **`TransactionEntity`**:
  - Added `val userId: String` column.
  - Added composite database indices: `["userId"]`, `["accountId"]`, `["timestamp"]`, `["userId", "timestamp"]`, `["userId", "accountId", "timestamp"]`.
  - Updated `fromDomain(tx: Transaction, userId: String)` helper.
- **`NotificationEntity`**:
  - Added `val userId: String = ""` column.
  - Added composite indices: `["userId"]`, `["timestamp"]`, `["isRead"]`, `["userId", "timestamp"]`, `["userId", "isRead"]`.
  - Updated `fromDomain(notification: Notification, userId: String)` helper.
- **`AppDatabase`**:
  - Bumped schema `version = 4`. Because `fallbackToDestructiveMigration(dropAllTables = true)` is configured, local cache tables cleanly rebuild without migration exceptions.

### 3.3 User-Scoped DAOs
- **`TransactionDao`**:
  - Added `@Query("SELECT * FROM transactions WHERE userId = :userId ORDER BY timestamp DESC") fun getTransactionsForUser(userId: String): Flow<List<TransactionEntity>>`.
  - Added `@Query("SELECT * FROM transactions WHERE userId = :userId AND accountId = :accountId ORDER BY timestamp DESC") fun getTransactionsForAccount(userId: String, accountId: String): Flow<List<TransactionEntity>>`.
  - Added `@Query("DELETE FROM transactions WHERE userId = :userId") suspend fun clearTransactionsForUser(userId: String)`.
  - Added `@Query("DELETE FROM transactions") suspend fun clearAll()`.
- **`NotificationDao`**:
  - Added `getNotificationsForUser(userId: String): Flow<List<NotificationEntity>>`.
  - Added `getUnreadCount(userId: String): Flow<Int>`.
  - Added `markAsReadForUser`, `markAllAsReadForUser`, `deleteNotificationForUser`, and `clearForUser(userId)`.

### 3.4 Repository Layer Enhancements
- **`TransactionRepository` & `TransactionRepositoryImpl`**:
  - Injected `userIdProvider: (() -> String?)?` in constructor.
  - Updated `getTransactionsFlow(userId: String?, accountId: String?)`: defaults to `userIdProvider?.invoke()`, routing to `localDao.getTransactionsForUser(targetUserId)`.
  - Updated `syncTransactions(userId: String?, accountId: String?)`: passes `targetUserId` to `bankIntegrationApi.getKcbTransactions(userId = targetUserId)`, removing the hardcoded `"default-user"`.
  - Ingested KCB transactions, remote transactions, and notifications are now tagged with `userId = targetUserId`.
- **`NotificationRepository` & `NotificationRepositoryImpl`**:
  - Injected `userIdProvider: (() -> String?)?`.
  - Scoped all queries, unread counts, and mutations to `targetUserId`.
- **`AppContainer`**:
  - Wired `userIdProvider = { authRepository.currentUserId() }` into both `TransactionRepositoryImpl` and `NotificationRepositoryImpl`.

### 3.5 Screen & ViewModel Tenancy Wiring
- **`TransactionViewModel`**: Added `userId: String?` parameter to constructor and Factory. Scoped `getTransactionsFlow(userId = userId)`, `syncTransactions(userId = userId)`, and KCB simulation.
- **`NotificationViewModel`**: Added `userId: String?` parameter. Scoped in-app banner listener, unread count, notification stream, and read/delete actions.
- **`HomeViewModel`**: Passed `userId` to `transactionRepository.getTransactionsFlow(userId = userId)` and `transactionRepository.syncTransactions(userId = userId)`.
- **`BudgetViewModel`**: Added `userId: String?` parameter and passed `userId` to `transactionRepository.getTransactionsFlow(userId = userId)`.
- **`MainActivity.kt`**:
  - Added `LaunchedEffect(currentUserId)` to trigger `UserProfileManager.switchUser(context, currentUserId)` as soon as authentication resolves.
  - Injected `currentUserId` into all ViewModel Factories (`AccountViewModel`, `TransactionViewModel`, `BudgetViewModel`, `InvestmentViewModel`, `NotificationViewModel`, `HomeViewModel`).
  - **Complete Logout Teardown**: Updated `onLogout` to clear in-memory state and purge local Room SQLite tables on `Dispatchers.IO`:
    ```kotlin
    onLogout = {
        val db = appContainer.database
        CoroutineScope(Dispatchers.IO).launch {
            try {
                db.clearAllTables()
            } catch (e: Exception) {
                e.printStackTrace()
            }
            UserProfileManager.clearSession()
        }
        authViewModel.signOut()
        showLogin = true 
    }
    ```

---

## 4. Summary of Modified Files

| File | Changes Made |
| :--- | :--- |
| [`UserProfileManager.kt`](file:///home/frank/AndroidStudioProjects/smartmoney/app/src/main/java/com/example/smartmoney/data/local/UserProfileManager.kt) | User-partitioned filenames (`user_profile_${userId}_avatar.jpg`), `switchUser`, `clearSession`, and user-scoped mutation methods. |
| [`SmartMoneyApplication.kt`](file:///home/frank/AndroidStudioProjects/smartmoney/app/src/main/java/com/example/smartmoney/SmartMoneyApplication.kt) | Guarded initialization to only switch when an active user session exists. |
| [`MoreScreen.kt`](file:///home/frank/AndroidStudioProjects/smartmoney/app/src/main/java/com/example/smartmoney/ui/more/MoreScreen.kt) | Added `userId` parameter and scoped photo picker and delete actions to `userId`. |
| [`DashboardScreen.kt`](file:///home/frank/AndroidStudioProjects/smartmoney/app/src/main/java/com/example/smartmoney/ui/dashboard/DashboardScreen.kt) | Added `userId` parameter and passed to `MoreScreen`. |
| [`TransactionEntity.kt`](file:///home/frank/AndroidStudioProjects/smartmoney/app/src/main/java/com/example/smartmoney/data/local/entity/TransactionEntity.kt) | Added `userId` column, user-scoped composite indices, and updated `fromDomain`. |
| [`NotificationEntity.kt`](file:///home/frank/AndroidStudioProjects/smartmoney/app/src/main/java/com/example/smartmoney/data/local/entity/NotificationEntity.kt) | Added `userId` column, user-scoped composite indices, and updated `fromDomain`. |
| [`TransactionDao.kt`](file:///home/frank/AndroidStudioProjects/smartmoney/app/src/main/java/com/example/smartmoney/data/local/dao/TransactionDao.kt) | Added `getTransactionsForUser`, user-scoped account query, `clearTransactionsForUser`, and `clearAll`. |
| [`NotificationDao.kt`](file:///home/frank/AndroidStudioProjects/smartmoney/app/src/main/java/com/example/smartmoney/data/local/dao/NotificationDao.kt) | Added `getNotificationsForUser`, `getUnreadCount`, and user-scoped read/delete queries. |
| [`AppDatabase.kt`](file:///home/frank/AndroidStudioProjects/smartmoney/app/src/main/java/com/example/smartmoney/data/local/database/AppDatabase.kt) | Bumped schema version to 4 for destructive migration. |
| [`TransactionRepository.kt`](file:///home/frank/AndroidStudioProjects/smartmoney/app/src/main/java/com/example/smartmoney/domain/repository/TransactionRepository.kt) | Added optional `userId` parameters to interface methods. |
| [`TransactionRepositoryImpl.kt`](file:///home/frank/AndroidStudioProjects/smartmoney/app/src/main/java/com/example/smartmoney/data/repository/TransactionRepositoryImpl.kt) | Injected `userIdProvider`, scoped Room queries by user, removed hardcoded `"default-user"` from bank ingestion. |
| [`NotificationRepository.kt`](file:///home/frank/AndroidStudioProjects/smartmoney/app/src/main/java/com/example/smartmoney/domain/repository/NotificationRepository.kt) | Added optional `userId` parameters to interface methods. |
| [`NotificationRepositoryImpl.kt`](file:///home/frank/AndroidStudioProjects/smartmoney/app/src/main/java/com/example/smartmoney/data/repository/NotificationRepositoryImpl.kt) | Injected `userIdProvider` and scoped all queries and mutations by user ID. |
| [`AppContainer.kt`](file:///home/frank/AndroidStudioProjects/smartmoney/app/src/main/java/com/example/smartmoney/core/di/AppContainer.kt) | Injected `userIdProvider = { authRepository.currentUserId() }` into transaction and notification repositories. |
| [`TransactionViewModel.kt`](file:///home/frank/AndroidStudioProjects/smartmoney/app/src/main/java/com/example/smartmoney/ui/transactions/TransactionViewModel.kt) | Added `userId` to constructor, Factory, query streams, sync, and bank simulation. |
| [`NotificationViewModel.kt`](file:///home/frank/AndroidStudioProjects/smartmoney/app/src/main/java/com/example/smartmoney/ui/notifications/NotificationViewModel.kt) | Added `userId` to constructor, Factory, banner listener, and mutations. |
| [`HomeViewModel.kt`](file:///home/frank/AndroidStudioProjects/smartmoney/app/src/main/java/com/example/smartmoney/ui/home/HomeViewModel.kt) | Passed `userId` to `getTransactionsFlow` and `syncTransactions`. |
| [`BudgetViewModel.kt`](file:///home/frank/AndroidStudioProjects/smartmoney/app/src/main/java/com/example/smartmoney/ui/budget/BudgetViewModel.kt) | Added `userId` to constructor and Factory, passed `userId` to `getTransactionsFlow`. |
| [`MainActivity.kt`](file:///home/frank/AndroidStudioProjects/smartmoney/app/src/main/java/com/example/smartmoney/MainActivity.kt) | Added `LaunchedEffect` for avatar switching, wired `userId` into ViewModels and `DashboardScreen`, and added full session teardown on logout. |

---

## 5. Verification & Testing

### 5.1 Compilation Verification
- The full Android build was executed and verified:
  ```bash
  ./gradlew assembleDebug
  ```
  **Result**: `BUILD SUCCESSFUL in 1m 31s` (0 compilation errors, 0 lint failures, KSP generated Room schemas for Version 4 successfully).

### 5.2 Multi-User Lifecycle Test Procedure
To verify complete multi-user isolation on an emulator or physical device:
1. **User A Sign Up & Setup**:
   - Register User A (`usera@example.com`).
   - Navigate to **More / Settings** &rarr; Upload a profile picture.
   - Verify avatar displays in TitleBar, Navigation Rail/Drawer, and Settings.
   - Perform or simulate transactions &rarr; Verify transactions appear on Overview and Transactions tabs.
2. **User A Logout**:
   - Tap **Sign Out**.
   - Verify `database.clearAllTables()` runs and `UserProfileManager.clearSession()` executes.
3. **User B Sign Up**:
   - Register User B (`userb@example.com`).
   - **Verification 1**: Avatar displays User B's initials. User A's photo is absent.
   - **Verification 2**: Transactions list and Overview show 0 transactions (or only User B's own transactions). User A's transactions are completely absent.
   - **Verification 3**: Notifications screen shows 0 notifications from User A.
4. **User B Setup & User A Return**:
   - Upload a distinct profile photo for User B.
   - Sign out of User B and sign back in as User A.
   - **Verification 4**: User A's photo and User A's transactions reappear cleanly and isolated from User B.

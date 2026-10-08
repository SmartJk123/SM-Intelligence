# Multi-Tenant User Isolation: Safeguarding Financial Privacy

In a banking and financial intelligence application, **user data isolation is a critical security and compliance requirement**. If User A logs out and User B logs in on the same mobile device, User B must never see cached balances, linked bank accounts, or transaction histories belonging to User A.

SmartMoney implements multi-tenancy and session boundary isolation across three defense layers.

---

## 1. The Three Layers of Data Isolation

```mermaid
flowchart TD
    subgraph Layer1 ["Layer 1: Session Management (UserProfileManager)"]
        LoginEvent[User Logs In / Out] --> SwitchUser[UserProfileManager.switchUser(context, newUserId)]
        SwitchUser --> ClearPrefs[Clear Active Encrypted SharedPreferences]
    end

    subgraph Layer2 ["Layer 2: Database Partitioning & Purging"]
        SwitchUser --> Purge[Purge Unlinked Data or Re-initialize Session]
        Purge --> TableIsolation[Every Room Entity contains strict foreign key: userId]
    end

    subgraph Layer3 ["Layer 3: DAO Query Scoping (userIdProvider)"]
        Repo[All Repositories inject userIdProvider lambda]
        Repo --> ScopedQuery["SELECT * FROM accounts WHERE userId = :currentUserId"]
        ScopedQuery --> ZeroLeak[Guarantees Zero Cross-User Data Leaks]
    end
```

---

## 2. Dynamic `userIdProvider` Pattern

Rather than hardcoding a static `userId` string when repositories are instantiated at application startup, repositories inject a dynamic **`userIdProvider: () -> String?`** lambda from `AuthRepository`:

```kotlin
// In DefaultAppContainer.kt
override val bankAccountRepository: BankAccountRepository by lazy {
    BankAccountRepositoryImpl(
        remoteDataSource = accountRemoteDataSource,
        localDao = database.accountDao(),
        userIdProvider = { authRepository.currentUserId() ?: "default-user" },
        dispatchers = dispatchers
    )
}

override val transactionRepository: TransactionRepository by lazy {
    TransactionRepositoryImpl(
        remoteDataSource = TransactionRemoteDataSource(SupabaseClientProvider.getClient()),
        localDao = database.transactionDao(),
        accountDao = database.accountDao(),
        notificationDao = database.notificationDao(),
        userIdProvider = { authRepository.currentUserId() },
        context = context,
        dispatchers = dispatchers
    )
}
```

### Why a Lambda?
1. **Dynamic Resolution**: If the active user changes upon login/logout, the repository immediately resolves the new user ID on its very next call without having to destroy and reconstruct the entire dependency graph.
2. **Fail-Closed Security**: If `authRepository.currentUserId()` returns `null` (e.g. user is logged out), repositories refuse to query or insert records, preventing unauthenticated data writes.

---

## 3. Strict DAO Query Scoping

Every single database query in Room DAOs requires `userId` as a mandatory query parameter:

```kotlin
@Dao
interface AccountDao {
    @Query("SELECT * FROM accounts WHERE userId = :userId ORDER BY createdAt ASC")
    fun getAccountsByUserId(userId: String): Flow<List<AccountEntity>>

    @Query("DELETE FROM accounts WHERE userId = :userId")
    suspend fun clearAccountsForUser(userId: String)
}

@Dao
interface TransactionDao {
    @Query("SELECT * FROM transactions WHERE userId = :userId ORDER BY date DESC")
    fun getTransactionsByUserId(userId: String): Flow<List<TransactionEntity>>

    @Query("DELETE FROM transactions WHERE userId = :userId")
    suspend fun clearTransactionsForUser(userId: String)
}
```

Even if a malicious or malformed network response contains accounts belonging to another tenant, the local database query filters strictly by the authenticated session's `userId`.

---

## 4. Session Teardown & Switching: `UserProfileManager`

When a user explicitly logs out or switches accounts, [`UserProfileManager.kt`](file:///home/frank/AndroidStudioProjects/smartmoney/app/src/main/java/com/example/smartmoney/data/local/UserProfileManager.kt) coordinates the teardown:

```kotlin
object UserProfileManager {

    suspend fun switchUser(context: Context, newUserId: String) {
        val app = context.applicationContext as SmartMoneyApplication
        val db = app.container.database

        // 1. Clear in-memory caches and active preferences
        clearSession()

        // 2. Load preferences for new user
        app.container.userPreferencesRepository.loadPreferencesForUser(newUserId)
    }

    suspend fun clearSession() {
        // Clear active session tokens and volatile UI states
        _activeUserSession.value = null
    }
}
```

During login in [`SmartMoneyApplication.kt`](file:///home/frank/AndroidStudioProjects/smartmoney/app/src/main/java/com/example/smartmoney/SmartMoneyApplication.kt):
```kotlin
val initialUserId = container.authRepository.currentUserId()
CoroutineScope(Dispatchers.IO).launch {
    if (!initialUserId.isNullOrBlank()) {
        UserProfileManager.switchUser(this@SmartMoneyApplication, initialUserId)
    } else {
        UserProfileManager.clearSession()
    }
}
```
This guarantees that upon every cold start, the session is verified and scoped before the first Compose screen is rendered.

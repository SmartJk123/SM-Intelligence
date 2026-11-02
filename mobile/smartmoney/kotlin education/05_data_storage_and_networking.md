# Module 05: Offline-First Data Storage & Networking

Welcome to Module 05! A production-grade mobile app must function reliably under poor network conditions, provide instant startup times, and communicate seamlessly with backend APIs.

In this module, we explore how SmartMoney implements an **Offline-First Architecture** combining **Room Database (Local SQLite Cache)**, **Retrofit & OkHttp (REST APIs)**, and **Supabase (Backend-as-a-Service)**.

---

## 1. The Offline-First Strategy

In an offline-first app:
1. **The UI always reads from the local database (Room)**. This guarantees instant rendering with zero network latency, even in Airplane Mode.
2. **Background coroutines fetch data from the remote server**.
3. **New remote data is saved into Room**.
4. **Room automatically emits the new data to the UI** via Kotlin `Flow`.

```
┌─────────────────┐       Observes Flow       ┌────────────────┐
│   Compose UI    │ ◄─────────────────────────│  Room Database │
└─────────────────┘                           └───────▲────────┘
                                                      │ Saves
                                              ┌───────┴────────┐
                                              │   Repository   │
                                              └───────▲────────┘
                                                      │ Syncs
                                              ┌───────┴────────┐
                                              │ Remote Server  │
                                              │(Retrofit/Supa) │
                                              └────────────────┘
```

---

## 2. Local Persistence: Room Database & KSP

Android's **Room** persistence library provides an object-mapping abstraction over SQLite.

### 1. Database Entities
Entities represent SQLite tables:
```kotlin
@Entity(tableName = "transactions")
data class TransactionEntity(
    @PrimaryKey val id: String,
    val accountId: String,
    val amount: String, // Or BigDecimal with TypeConverter
    val type: String,
    val timestamp: String,
    val description: String?
)
```

### 2. Data Access Objects (DAOs)
DAOs define the SQL queries. Notice how Room natively returns a reactive Kotlin `Flow`:
```kotlin
@Dao
interface TransactionDao {
    @Query("SELECT * FROM transactions WHERE accountId = :accountId ORDER BY timestamp DESC")
    fun getTransactionsFlow(accountId: String): Flow<List<TransactionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(transactions: List<TransactionEntity>)

    @Query("DELETE FROM transactions")
    suspend fun clearAll()
}
```
> [!TIP]
> Because `getTransactionsFlow` returns a `Flow<List<TransactionEntity>>`, Room will automatically re-query the SQLite table and emit a fresh list whenever an `INSERT`, `UPDATE`, or `DELETE` occurs!

### 3. The Thread-Safe Singleton Pattern
In [`AppDatabase.kt`](file:///home/frank/AndroidStudioProjects/smartmoney/app/src/main/java/com/example/smartmoney/data/local/database/AppDatabase.kt#L43-L60):
```kotlin
companion object {
    @Volatile
    private var INSTANCE: AppDatabase? = null

    fun getDatabase(context: Context): AppDatabase {
        return INSTANCE ?: synchronized(this) {
            val instance = Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "energy_cache_database"
            ).build()
            INSTANCE = instance
            instance
        }
    }
}
```
* `@Volatile`: Guarantees changes made to `INSTANCE` by one thread are immediately visible to all other threads.
* `synchronized(this)`: Double-checked locking ensures only **one** instance of the database is created, preventing database locking and memory leaks.

---

## 3. Remote Networking: Retrofit & OkHttp

SmartMoney communicates with backend microservices via **Retrofit** and **OkHttp** in [`RetrofitClient.kt`](file:///home/frank/AndroidStudioProjects/smartmoney/app/src/main/java/com/example/smartmoney/data/remote/RetrofitClient.kt).

### 1. Powerful OkHttp Interceptors
Interceptors act like middleware that sit between your application and the network:

* **Authorization Interceptor**:
  Automatically attaches the user's JWT token to the `Authorization` header of every outgoing request:
  ```kotlin
  val authInterceptor = Interceptor { chain ->
      val original = chain.request()
      val token = tokenProvider?.invoke()
      val request = if (!token.isNullOrBlank()) {
          original.newBuilder()
              .header("Authorization", "Bearer $token")
              .build()
      } else original
      chain.proceed(request)
  }
  ```

* **Dynamic Host Fallback Interceptor**:
  In [`RetrofitClient.kt`](file:///home/frank/AndroidStudioProjects/smartmoney/app/src/main/java/com/example/smartmoney/data/remote/RetrofitClient.kt#L86-L106):
  If the app fails to connect to `127.0.0.1` (e.g. testing on a physical phone instead of the emulator), the interceptor automatically catches the `ConnectException` and redirects the request to the local network IP `192.168.68.121` without crashing!

### 2. Modern JSON Serialization
Instead of legacy reflection libraries (like Gson), SmartMoney uses **`kotlinx.serialization`**:
```kotlin
private val json = Json {
    ignoreUnknownKeys = true // Won't crash if the backend adds new fields!
    isLenient = true
    coerceInputValues = true
}
```
It compiles parsing logic ahead of time using compiler plugins, resulting in faster parsing and smaller app size.

---

## 4. Backend as a Service (BaaS): Supabase

SmartMoney also utilizes **Supabase** (PostgreSQL + GoTrue Auth) for cloud synchronization and authentication:
- `SupabaseClientProvider`: Initializes the Supabase client using environment config (`SUPABASE_URL` and `SUPABASE_ANON_KEY` configured in `build.gradle.kts`).
- `AuthRemoteDataSource`: Handles authentication (login, signup, session restoration).
- `TransactionRemoteDataSource`: Performs Postgrest queries directly against the PostgreSQL tables.

---

## 🏋️ Practice Exercises

1. **Room Migration Analysis**:
   Notice that `version = 4` in [`AppDatabase.kt`](file:///home/frank/AndroidStudioProjects/smartmoney/app/src/main/java/com/example/smartmoney/data/local/database/AppDatabase.kt#L31). Why is `.fallbackToDestructiveMigration()` convenient during prototyping, and what would you use for production apps with real user data? (Hint: `Migration(from, to)`).
2. **Trace a Sync Operation**:
   Open [`TransactionRepositoryImpl.kt`](file:///home/frank/AndroidStudioProjects/smartmoney/app/src/main/java/com/example/smartmoney/data/repository/TransactionRepositoryImpl.kt). Find the `syncTransactions()` method. How does it fetch from remote and save to `localDao`?
3. **Interceptor Experiment**:
   How would you add a custom header like `"X-App-Version: 1.0"` to every outgoing HTTP call in `RetrofitClient`?

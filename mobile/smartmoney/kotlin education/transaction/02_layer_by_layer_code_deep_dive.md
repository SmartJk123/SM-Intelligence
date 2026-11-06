# 02. Transaction Layer-by-Layer Code Deep Dive

This document breaks down every file, class, method, and schema within the **Transaction System** of SmartMoney across all 4 architectural layers.

---

## 1. UI Layer (`com.example.smartmoney.ui.transactions`)

### A. [`TransactionUiState.kt`](file:///home/frank/AndroidStudioProjects/smartmoney/app/src/main/java/com/example/smartmoney/ui/transactions/TransactionUiState.kt)
* **Purpose**: Sealed interface modeling the UI render state for the transactions screen.
* **Code Implementation**:
  ```kotlin
  sealed interface TransactionUiState {
      object Loading : TransactionUiState
      data class Success(val transactions: List<Transaction>) : TransactionUiState
      data class Error(val message: String) : TransactionUiState
  }
  ```
* **Why it matters**: Ensures the Jetpack Compose screen has exhaustive state handling (`when (state) { is Loading -> ..., is Success -> ..., is Error -> ... }`) preventing illegal or blank UI states.

---

### B. [`TransactionViewModel.kt`](file:///home/frank/AndroidStudioProjects/smartmoney/app/src/main/java/com/example/smartmoney/ui/transactions/TransactionViewModel.kt)
* **Purpose**: Orchestrates state streams, filters transactions off the Main Thread, and calculates cash-flow trends.
* **Key Mechanics**:
  1. **Flow Combination & Background Processing**:
     ```kotlin
     val filteredTransactions: StateFlow<List<Transaction>> = combine(
         uiState,
         _selectedFilter,
         _pendingInvoices
     ) { state, filter, pending ->
         withContext(dispatchers.default) {
             val transactions = when (state) {
                 is TransactionUiState.Success -> pending + state.transactions
                 else -> pending
             }
             when (filter) {
                 "CREDIT" -> transactions.filter { it.type.equals("CREDIT", ignoreCase = true) }
                 "DEBIT" -> transactions.filter { it.type.equals("DEBIT", ignoreCase = true) }
                 else -> transactions
             }
         }
     }.stateIn(scope, SharingStarted.WhileSubscribed(5_000), emptyList())
     ```
     Filtering operations run on `Dispatchers.Default` (CPU pool), ensuring the Android Main thread remains completely dedicated to 60 FPS Compose rendering.
  2. **`WhileSubscribed(5_000)` Lifecycle Guard**:
     When the user navigates away from the Transactions screen, Room stops querying the database after 5 seconds, preserving CPU and battery.

---

## 2. Domain Layer (`com.example.smartmoney.domain`)

### A. [`Transaction.kt`](file:///home/frank/AndroidStudioProjects/smartmoney/app/src/main/java/com/example/smartmoney/domain/model/Transaction.kt)
* **Purpose**: The pure, platform-independent business model of a transaction.
* **Code**:
  ```kotlin
  @Immutable
  data class Transaction(
      val id: String,
      val accountId: String,
      val amount: BigDecimal,
      val type: String, // "CREDIT", "DEBIT", "TRANSFER"
      val timestamp: String,
      val description: String? = null,
      val providerTransactionId: String? = null,
      val createdAt: String? = null,
      val updatedAt: String? = null,
      val status: String = "COMPLETED",
      val source: String = "BANK"
  )
  ```
* **Why `BigDecimal`?**: Avoids binary floating-point rounding errors common with `Double` or `Float` in financial calculations (e.g. `0.1 + 0.2 != 0.3`).
* **Why `@Immutable`?**: Tells the Jetpack Compose compiler that once created, instances cannot mutate, enabling aggressive recomposition skipping optimizations.

---

### B. [`TransactionRepository.kt`](file:///home/frank/AndroidStudioProjects/smartmoney/app/src/main/java/com/example/smartmoney/domain/repository/TransactionRepository.kt)
* **Purpose**: Clean interface decoupling presentation from persistence frameworks:
  ```kotlin
  interface TransactionRepository {
      fun getTransactionsFlow(userId: String?, accountId: String? = null): Flow<List<Transaction>>
      suspend fun syncTransactions(userId: String?, accountId: String? = null): Result<Unit>
      suspend fun recordTransaction(transaction: Transaction, userId: String? = null): Result<Transaction>
      suspend fun deleteTransaction(id: String): Result<Unit>
  }
  ```

---

### C. [`TransactionTrendCalculator.kt`](file:///home/frank/AndroidStudioProjects/smartmoney/app/src/main/java/com/example/smartmoney/domain/util/TransactionTrendCalculator.kt)
* **Purpose**: Domain utility that analyzes raw transaction lists to compute 7-day spending trends, daily moving averages, and cumulative burn rates for interactive charts.

---

## 3. Data Layer - Remote (`com.example.smartmoney.data.remote`)

### A. [`ActivityApi.kt`](file:///home/frank/AndroidStudioProjects/smartmoney/app/src/main/java/com/example/smartmoney/data/remote/api/ActivityApi.kt)
* **Purpose**: Retrofit endpoint targeting `transactions-service` (:8083).
* **Endpoint**:
  ```kotlin
  @GET("api/transactions/activity")
  suspend fun getActivity(
      @Query("limit") limit: Int = 200,
      @Query("since") since: String? = null
  ): Response<List<ActivityItem>>
  ```
* Receives all movements recorded by the Spring Boot backend matching the web portal.

---

### B. [`TransactionDto.kt`](file:///home/frank/AndroidStudioProjects/smartmoney/app/src/main/java/com/example/smartmoney/data/remote/dto/TransactionDto.kt)
* **Purpose**: Serializable payload with `snake_case` PostgREST and JSON mappings.
* **Mappings**:
  ```kotlin
  @Serializable
  data class TransactionDto(
      @SerialName("id") val id: String,
      @SerialName("account_id") val accountId: String,
      @Serializable(with = BigDecimalSerializer::class)
      @SerialName("amount") val amount: BigDecimal,
      @SerialName("transaction_type") val transactionType: String,
      @SerialName("timestamp") val timestamp: String,
      ...
  ) {
      fun toDomain(): Transaction = ...
      companion object {
          fun fromDomain(tx: Transaction): TransactionDto = ...
      }
  }
  ```

---

## 4. Data Layer - Local Persistence (`com.example.smartmoney.data.local`)

### A. [`TransactionEntity.kt`](file:///home/frank/AndroidStudioProjects/smartmoney/app/src/main/java/com/example/smartmoney/data/local/entity/TransactionEntity.kt)
* **Purpose**: Room SQLite table schema with optimized compound indices:
  ```kotlin
  @Entity(
      tableName = "transactions",
      indices = [
          Index(value = ["userId"]),
          Index(value = ["accountId"]),
          Index(value = ["timestamp"]),
          Index(value = ["userId", "timestamp"]),
          Index(value = ["userId", "accountId", "timestamp"])
      ]
  )
  data class TransactionEntity(
      @PrimaryKey val id: String,
      val userId: String,
      val accountId: String,
      val amount: BigDecimal,
      val transactionType: String,
      val timestamp: String,
      val description: String? = null,
      val providerTransactionId: String? = null,
      ...
  )
  ```
* **Performance Benefit**: The composite index `["userId", "accountId", "timestamp"]` enables SQLite to satisfy ordered history queries in sub-millisecond time even with tens of thousands of records.

---

### B. [`TransactionDao.kt`](file:///home/frank/AndroidStudioProjects/smartmoney/app/src/main/java/com/example/smartmoney/data/local/dao/TransactionDao.kt)
* **Purpose**: Executes reactive Room queries:
  ```kotlin
  @Query("SELECT * FROM transactions WHERE userId = :userId ORDER BY timestamp DESC")
  fun getTransactionsForUser(userId: String): Flow<List<TransactionEntity>>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsertTransactions(transactions: List<TransactionEntity>)

  @Query("DELETE FROM transactions WHERE userId = :userId AND accountId = :accountId")
  suspend fun deleteTransactionsForAccount(userId: String, accountId: String)
  ```

---

## 5. Data Layer - Repository Implementation

### [`TransactionRepositoryImpl.kt`](file:///home/frank/AndroidStudioProjects/smartmoney/app/src/main/java/com/example/smartmoney/data/repository/TransactionRepositoryImpl.kt)
* **The Coordinator**:
  - Connects [`TransactionDao`](file:///home/frank/AndroidStudioProjects/smartmoney/app/src/main/java/com/example/smartmoney/data/local/dao/TransactionDao.kt) for local reading/writing.
  - Connects [`ActivityApi`](file:///home/frank/AndroidStudioProjects/smartmoney/app/src/main/java/com/example/smartmoney/data/remote/api/ActivityApi.kt) for backend movements.
  - Connects [`BankIntegrationApi`](file:///home/frank/AndroidStudioProjects/smartmoney/app/src/main/java/com/example/smartmoney/data/remote/api/BankIntegrationApi.kt) for live KCB/Equity sync.
  - Connects [`NotificationHelper`](file:///home/frank/AndroidStudioProjects/smartmoney/app/src/main/java/com/example/smartmoney/core/notification/NotificationHelper.kt) to dispatch system alerts when funds arrive.

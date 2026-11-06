# 02. Accounts Layer-by-Layer Code Deep Dive

This document breaks down every file, class, method, and schema within the **Account & Banking Integration System** of SmartMoney across all 4 architectural layers.

---

## 1. UI Layer (`com.example.smartmoney.ui.accounts`)

### A. [`AccountViewModel.kt`](file:///home/frank/AndroidStudioProjects/smartmoney/app/src/main/java/com/example/smartmoney/ui/accounts/AccountViewModel.kt)
* **Purpose**: Holds and coordinates account state, bank health checks, simulation triggers, and link handshakes.
* **Consolidated UI State**:
  ```kotlin
  data class BankIntegrationInfo(
      val environment: String = "SANDBOX",
      val isLive: Boolean = false,
      val webhookActive: Boolean = false
  )

  data class AccountUiState(
      val bankAccounts: List<BankAccount> = emptyList(),
      val isLoading: Boolean = false,
      val isAddingBankAccount: Boolean = false,
      val errorMessage: String? = null,
      val bankEnvironments: Map<String, BankIntegrationInfo> = emptyMap()
  )
  ```
  By merging `bankAccounts`, `isLoading`, `isAddingBankAccount`, and `bankEnvironments` into a single `AccountUiState` StateFlow, the ViewModel eliminates multiple independent recomposition passes in Compose.
* **Reactive Stream**:
  ```kotlin
  val bankAccounts: StateFlow<List<BankAccount>> = bankAccountRepository.getBankAccounts()
      .distinctUntilChanged()
      .stateIn(scope = viewModelScope, started = SharingStarted.WhileSubscribed(5_000), initialValue = emptyList())
  ```

---

### B. [`BankLogo.kt`](file:///home/frank/AndroidStudioProjects/smartmoney/app/src/main/java/com/example/smartmoney/ui/components/BankLogo.kt)
* **Purpose**: Dynamic vector brand rendering. Given an institution name (`"KCB"`, `"EQUITY"`, `"STANBIC"`, `"NCBA"`), it displays official banking logos with glassmorphism badges on the debit/credit cards.

---

## 2. Domain Layer (`com.example.smartmoney.domain`)

### A. [`BankAccount.kt`](file:///home/frank/AndroidStudioProjects/smartmoney/app/src/main/java/com/example/smartmoney/domain/model/BankAccount.kt)
* **Purpose**: Simplified domain model representing an individual card or linked bank account:
  ```kotlin
  data class BankAccount(
      val id: String,
      val bankName: String,
      val accountNumber: String,
      val cardType: String, // "Credit" or "Debit"
      val balance: BigDecimal = BigDecimal.ZERO
  )
  ```

---

### B. [`Account.kt`](file:///home/frank/AndroidStudioProjects/smartmoney/app/src/main/java/com/example/smartmoney/domain/model/Account.kt)
* **Purpose**: Complete enterprise model representing financial and energy ledger accounts:
  ```kotlin
  data class Account(
      val id: String,
      val userId: String,
      val accountId: String,
      val accountName: String,
      val institution: String,
      val accountType: String, // "DEPOSIT", "CREDIT"
      val maskedIdentifier: String? = null,
      val currency: String = "KES",
      val ledgerBalance: BigDecimal = BigDecimal.ZERO,
      val availableBalance: BigDecimal = BigDecimal.ZERO,
      val creditOutstanding: BigDecimal = BigDecimal.ZERO,
      val creditLimit: BigDecimal? = null,
      val availableCredit: BigDecimal? = null,
      val accountStatus: String = "ACTIVE",
      val connectionStatus: String = "CONNECTED",
      val dataSource: String = "BANK_API", // "BANK_API" or "MANUAL"
      ...
  )
  ```

---

### C. Repository Contracts
* **[`BankAccountRepository.kt`](file:///home/frank/AndroidStudioProjects/smartmoney/app/src/main/java/com/example/smartmoney/domain/repository/BankAccountRepository.kt)**:
  ```kotlin
  interface BankAccountRepository {
      fun getBankAccounts(): Flow<List<BankAccount>>
      suspend fun addBankAccount(bankName: String, accountNumber: String, cardType: String): Result<Unit>
      suspend fun removeBankAccount(id: String): Result<Unit>
      suspend fun adjustBankBalance(bankName: String, delta: BigDecimal, accountId: String? = null, userId: String? = null)
      suspend fun adjustKcbBalance(delta: BigDecimal)
  }
  ```
* **[`AccountRepository.kt`](file:///home/frank/AndroidStudioProjects/smartmoney/app/src/main/java/com/example/smartmoney/domain/repository/AccountRepository.kt)**:
  Contract for accounts-service synchronization (`getAccountsFlow`, `syncAccounts`, `createAccount`, `deleteAccount`).

---

## 3. Data Layer - Remote (`com.example.smartmoney.data.remote`)

### A. [`BankIntegrationApi.kt`](file:///home/frank/AndroidStudioProjects/smartmoney/app/src/main/java/com/example/smartmoney/data/remote/api/BankIntegrationApi.kt)
* **Purpose**: Retrofit contract communicating with `bank-integration-service` (:8090 / Render Cloud):
  ```kotlin
  @POST("api/v1/admin/account-links")
  suspend fun linkAccount(@Body request: LinkBankRequest): Response<BankLinkResponse>

  @GET("api/v1/admin/account-links")
  suspend fun getAccountLinks(@Query("userId") userId: String? = null): Response<List<BankLinkResponse>>

  @DELETE("api/v1/admin/account-links/{id}")
  suspend fun unlinkAccount(@Path("id") id: String): Response<Unit>

  @GET("api/v1/admin/bank-integrations/{bankId}")
  suspend fun getBankHealth(@Path("bankId") bankId: String): Response<IntegrationHealthResponse>
  ```

---

### B. [`AccountApi.kt`](file:///home/frank/AndroidStudioProjects/smartmoney/app/src/main/java/com/example/smartmoney/data/remote/api/AccountApi.kt)
* **Purpose**: Retrofit contract communicating with `accounts-service` (:8082):
  ```kotlin
  @GET("api/accounts")
  suspend fun getAccounts(@Query("userId") userId: String, @Query("status") status: String? = null): Response<List<AccountResponse>>

  @POST("api/accounts")
  suspend fun createAccount(@Body request: CreateAccountRequest): Response<AccountResponse>

  @PUT("api/accounts/{id}/balance")
  suspend fun updateBalance(@Path("id") id: String, @Body request: UpdateBalanceRequest): Response<AccountResponse>
  ```

---

### C. [`AccountRemoteDataSource.kt`](file:///home/frank/AndroidStudioProjects/smartmoney/app/src/main/java/com/example/smartmoney/data/remote/datasource/AccountRemoteDataSource.kt)
* **Purpose**: Wraps [`AccountApi`](file:///home/frank/AndroidStudioProjects/smartmoney/app/src/main/java/com/example/smartmoney/data/remote/api/AccountApi.kt), unwrapping HTTP responses, converting `AccountResponse` DTOs into pure domain models (`Account`), and parsing Spring Boot error payloads into descriptive exceptions.

---

## 4. Data Layer - Local Persistence (`com.example.smartmoney.data.local`)

### A. [`AccountEntity.kt`](file:///home/frank/AndroidStudioProjects/smartmoney/app/src/main/java/com/example/smartmoney/data/local/entity/AccountEntity.kt)
* **Purpose**: Room SQLite cache table:
  ```kotlin
  @Entity(
      tableName = "accounts",
      indices = [
          Index(value = ["userId"]),
          Index(value = ["accountId"]),
          Index(value = ["userId", "accountId"])
      ]
  )
  data class AccountEntity(
      @PrimaryKey val id: String,
      val userId: String,
      val accountId: String,
      val accountName: String,
      val institution: String,
      val accountType: String,
      val maskedIdentifier: String? = null,
      val currency: String = "KES",
      val ledgerBalance: BigDecimal = BigDecimal.ZERO,
      val availableBalance: BigDecimal = BigDecimal.ZERO,
      val creditOutstanding: BigDecimal = BigDecimal.ZERO,
      val creditLimit: BigDecimal? = null,
      val accountStatus: String = "ACTIVE",
      val connectionStatus: String = "CONNECTED",
      val dataSource: String = "MANUAL",
      ...
  )
  ```

---

### B. [`AccountDao.kt`](file:///home/frank/AndroidStudioProjects/smartmoney/app/src/main/java/com/example/smartmoney/data/local/dao/AccountDao.kt)
* **Purpose**: Room DAO queries:
  ```kotlin
  @Query("SELECT * FROM accounts WHERE userId = :userId")
  fun getAccountsForUser(userId: String): Flow<List<AccountEntity>>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsertAccount(account: AccountEntity)

  @Query("DELETE FROM accounts WHERE id = :id")
  suspend fun deleteAccountById(id: String)

  @Query("UPDATE accounts SET availableBalance = availableBalance + :delta WHERE userId = :userId AND institution = :bankName")
  suspend fun adjustBankBalanceForUser(userId: String, bankName: String, delta: BigDecimal)
  ```

---

## 5. Data Layer - Repository Implementation

### [`BankAccountRepositoryImpl.kt`](file:///home/frank/AndroidStudioProjects/smartmoney/app/src/main/java/com/example/smartmoney/data/repository/BankAccountRepositoryImpl.kt)
* **The Hybrid Router**:
  - Checks if the selected bank is supported (`"kcb"`, `"stanbic"`, `"ncba"`, `"equity"`).
  - If supported, attempts real-time linking through `RetrofitClient.bankIntegrationApi.linkAccount(linkReq)`.
  - If unreachable or unsupported, falls back smoothly to `AccountRemoteDataSource.createAccount(...)` with `dataSource = "MANUAL"`.
  - Updates local Room database [`AccountDao`](file:///home/frank/AndroidStudioProjects/smartmoney/app/src/main/java/com/example/smartmoney/data/local/dao/AccountDao.kt).
  - Handles cascade deletion through [`TransactionDao`](file:///home/frank/AndroidStudioProjects/smartmoney/app/src/main/java/com/example/smartmoney/data/local/dao/TransactionDao.kt) so removed cards don't leave orphaned transactions behind.

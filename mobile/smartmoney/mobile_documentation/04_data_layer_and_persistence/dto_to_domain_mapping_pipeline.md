# The DTO to Domain Mapping Pipeline: Safe Data Transformation

In modern mobile engineering, raw data coming over the network or retrieved from an SQLite table rarely matches the ideal representation needed by the user interface.

If raw network models (DTOs) or database entities are passed directly into UI composables, any API schema change breaks the UI, and UI formatting logic pollutes the data layer.

SmartMoney prevents this by enforcing an explicit, unidirectional **Data Transformation Pipeline**.

---

## 1. The Multi-Stage Transformation Pipeline

```mermaid
flowchart LR
    Network[Raw JSON / XML Network Response] -->|Retrofit Deserialization| DTO[Data Transfer Object e.g. BankTransactionResponse]
    DTO -->|Extension Function .toEntity()| Entity[Room Database Entity e.g. TransactionEntity]
    Entity -->|Stored in SQLite Table| Room[(Room Database)]
    Room -->|DAO Flow<List<Entity>>| DAO[TransactionDao]
    DAO -->|Extension Function .toDomainModel()| Domain[Clean Domain Model e.g. Transaction]
    Domain -->|Calculator / ViewModel Synthesis| UiState[Immutable UiState e.g. HomeUiState]
    UiState -->|Compose collectAsState| Screen[HomeScreen UI Components]
```

Each stage has a specific role:

| Representation | Scope | Key Characteristics |
| :--- | :--- | :--- |
| **DTO (Data Transfer Object)** | Network boundary | Models the raw HTTP JSON/XML payload. Handles nullable fields, raw strings, backward compatibility aliases. |
| **Entity** | Database boundary | Models SQLite tables and column types (`@Entity`, `@PrimaryKey`, `@ColumnInfo`). Optimized for SQL indexes and queries. |
| **Domain Model** | Business logic | Clean Kotlin data classes (`BigDecimal`, strongly-typed enums). Free of annotations. |
| **UiState** | Screen boundary | Presentation-ready, formatted text (currency symbols, relative dates, colors, chart models). |

---

## 2. Real-World Transformation Walkthrough: Transactions

Let's trace how a bank credit transaction moves from an API response to screen pixels:

### Step 1: Remote DTO ([`BankIntegrationApi.kt`](file:///home/frank/AndroidStudioProjects/smartmoney/app/src/main/java/com/example/smartmoney/data/remote/api/BankIntegrationApi.kt#L165-L179))
```kotlin
@Serializable
data class BankTransactionResponse(
    @SerialName("id") val id: Long? = null,
    @SerialName("reference") val reference: String = "",
    @SerialName("bankId") val bankId: String = "kcb",
    @Serializable(with = FlexibleStringSerializer::class)
    @SerialName("amount") val amount: String = "0.00",
    @SerialName("currency") val currency: String = "KES",
    @SerialName("direction") val direction: String = "Credit",
    @SerialName("narration") val narration: String? = null,
    @SerialName("accountNumber") val accountNumber: String? = null,
    @SerialName("bookingDate") val bookingDate: String? = null
)
```

---

### Step 2: Mapping DTO $\rightarrow$ Room Entity ([`TransactionRepositoryImpl.kt`](file:///home/frank/AndroidStudioProjects/smartmoney/app/src/main/java/com/example/smartmoney/data/repository/TransactionRepositoryImpl.kt))
```kotlin
fun BankTransactionResponse.toEntity(userId: String): TransactionEntity {
    val parsedAmount = try {
        BigDecimal(this.amount.replace(",", "").trim())
    } catch (e: Exception) {
        BigDecimal.ZERO
    }

    val parsedDate = bookingDate?.let { dateStr ->
        try { Instant.parse(dateStr).toEpochMilli() } catch (e: Exception) { System.currentTimeMillis() }
    } ?: System.currentTimeMillis()

    return TransactionEntity(
        id = this.reference.ifBlank { UUID.randomUUID().toString() },
        userId = userId,
        accountId = this.accountNumber ?: "unknown-account",
        amount = parsedAmount,
        type = if (direction.equals("Credit", ignoreCase = true)) "INCOME" else "EXPENSE",
        category = categorizeNarration(this.narration),
        date = parsedDate,
        description = this.narration ?: "Bank Transaction",
        isPending = false
    )
}
```
* **Resilience**: Strips commas, handles number parse failures gracefully without crashing.
* **Auto-Categorization**: Heuristically infers categories (Rent, Groceries, Utilities) from the transaction narration.

---

### Step 3: Mapping Room Entity $\rightarrow$ Domain Model
```kotlin
fun TransactionEntity.toDomainModel(): Transaction {
    return Transaction(
        id = this.id,
        userId = this.userId,
        accountId = this.accountId,
        amount = this.amount,
        type = TransactionType.valueOf(this.type.uppercase()),
        category = this.category,
        date = Date(this.date),
        description = this.description,
        isPending = this.isPending
    )
}
```

---

### Step 4: Domain Model $\rightarrow$ UI State Synthesis ([`HomeViewModel.kt`](file:///home/frank/AndroidStudioProjects/smartmoney/app/src/main/java/com/example/smartmoney/ui/home/HomeViewModel.kt))
```kotlin
val homeUiState = combine(transactionsFlow, accountsFlow) { transactions, accounts ->
    val income = transactions
        .filter { it.type == TransactionType.INCOME }
        .sumOf { it.amount }
    val expense = transactions
        .filter { it.type == TransactionType.EXPENSE }
        .sumOf { it.amount }

    HomeUiState(
        totalBalance = accounts.sumOf { it.balance },
        monthlyIncome = income,
        monthlyExpense = expense,
        recentTransactions = transactions.take(5)
    )
}
```

---

## 3. Why This Separation Protects the App

1. **Backwards Compatibility**: If KCB renames `bookingDate` to `transactionDate` or switches `amount` from string to number, **only Step 1 (the DTO) changes**. The Room database, domain business rules, and Compose screens remain 100% untouched.
2. **Defensive Parsing**: Corrupted or malicious network payloads are sanitized at Step 2 before entering the local database.
3. **Purity of Domain Logic**: `TransactionTrendCalculator` and `OverviewAnalyticsCalculator` can test complex financial algorithms against clean `Transaction` instances without worrying about JSON serialization or database column names.

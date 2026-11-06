# Module 01: Kotlin Essentials through SmartMoney

Welcome to Module 01! In this module, we will explore the core pillars of the **Kotlin programming language** by analyzing how they are used inside the SmartMoney application.

---

## 1. Immutability: `val` vs `var`

In modern Kotlin and Android development, **immutability by default** is a golden rule. It prevents accidental bugs, race conditions in multi-threaded code, and unnecessary UI recompositions.

* `val` (Value): **Read-only reference**. Once assigned, it cannot be reassigned (similar to `final` in Java or `const` in JS).
* `var` (Variable): **Mutable reference**. Can be reassigned.

### 📌 In SmartMoney:
Look at [`Transaction.kt`](file:///home/frank/AndroidStudioProjects/smartmoney/app/src/main/java/com/example/smartmoney/domain/model/Transaction.kt#L11-L23):
```kotlin
@Immutable
data class Transaction(
    val id: String,
    val accountId: String,
    val amount: BigDecimal,
    val type: String,
    val timestamp: String,
    val description: String? = null,
    val status: String = "COMPLETED"
)
```
Notice that **every single property** in the domain model uses `val`. You will almost never see `var` in modern domain models or UI state.

---

## 2. Null Safety: The Billion-Dollar Mistake Fixed

In languages like Java, a null pointer exception (`NullPointerException` / NPE) is one of the most common causes of app crashes. Kotlin makes nullability an explicit part of the type system.

### The Syntax:
* `String` &rarr; Can **never** be null. The compiler will prevent you from assigning `null`.
* `String?` &rarr; Can hold either a valid `String` or `null`.

### Handling Nullable Types:
1. **Safe Call Operator (`?.`)**: Executes the expression only if the variable is not null.
2. **Elvis Operator (`?:`)**: Provides a fallback default value if the left side evaluates to `null`.
3. **Safe Scoping (`let`)**: Executes a block only if the target is not null.

### 📌 In SmartMoney:
Check [`SmartMoneyApplication.kt`](file:///home/frank/AndroidStudioProjects/smartmoney/app/src/main/java/com/example/smartmoney/SmartMoneyApplication.kt#L26-L33):
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
And in [`AppContainer.kt`](file:///home/frank/AndroidStudioProjects/smartmoney/app/src/main/java/com/example/smartmoney/core/di/AppContainer.kt#L88):
```kotlin
// Using the Elvis operator to supply a fallback string if currentUserId is null
userIdProvider = { authRepository.currentUserId() ?: "default-user" }
```

---

## 3. Data Classes & The `@Immutable` Annotation

In Java, creating a simple model with getters, `equals()`, `hashCode()`, `toString()`, and copy methods would require 50+ lines of boilerplate. In Kotlin, you use a **`data class`**.

### What Kotlin generates automatically:
* `equals()` and `hashCode()` based on all properties.
* `toString()` formatted as `Transaction(id=1, amount=50.00, ...)`.
* `copy()` which lets you clone an object while altering specific fields:
  ```kotlin
  val updatedTransaction = transaction.copy(status = "PENDING")
  ```

### Why `@Immutable`?
In Jetpack Compose, the compiler tracks when data changes. Annotating a class with `@Immutable` tells Compose:
> *"The properties of this object will never change after creation. If the instance reference is the same, do not waste battery and CPU re-drawing the UI."*

See [`Transaction.kt`](file:///home/frank/AndroidStudioProjects/smartmoney/app/src/main/java/com/example/smartmoney/domain/model/Transaction.kt#L10).

---

## 4. Sealed Interfaces & Pattern Matching

How do you represent a UI screen that could be **Loading**, **Success** (with data), or **Error** (with a message)?

Instead of using boolean flags like `isLoading = true`, Kotlin provides **`sealed interface`** / **`sealed class`**.

### 📌 In SmartMoney:
Look at [`TransactionUiState.kt`](file:///home/frank/AndroidStudioProjects/smartmoney/app/src/main/java/com/example/smartmoney/ui/transactions/TransactionUiState.kt#L5-L9):
```kotlin
sealed interface TransactionUiState {
    object Loading : TransactionUiState
    data class Success(val transactions: List<Transaction>) : TransactionUiState
    data class Error(val message: String) : TransactionUiState
}
```

### Exhaustive `when` Expressions:
When you evaluate a sealed type with a `when` expression, the compiler forces you to handle **all possible cases**. No `else` branch is required:

```kotlin
when (uiState) {
    is TransactionUiState.Loading -> ShowLoadingSpinner()
    is TransactionUiState.Success -> TransactionList(uiState.transactions)
    is TransactionUiState.Error -> ErrorMessage(uiState.message)
}
```
If you later add a new state like `TransactionUiState.Empty`, the compiler immediately highlights everywhere in the app that needs to handle it!

---

## 5. Extension Functions & Lambdas

Kotlin lets you extend any class with new functionality without modifying its source code or inheriting from it.

### Extension Functions
Example from [`CurrencyUtils.kt`](file:///home/frank/AndroidStudioProjects/smartmoney/app/src/main/java/com/example/smartmoney/core/util/CurrencyUtils.kt):
Instead of writing `CurrencyUtils.format(amount)`, you can define an extension function on `BigDecimal`:
```kotlin
fun BigDecimal.formatToKes(): String {
    return "KES " + NumberFormat.getNumberInstance().format(this)
}

// Usage:
val price = BigDecimal("1500.50")
println(price.formatToKes()) // "KES 1,500.50"
```

### Lambdas & Higher-Order Functions
A **higher-order function** is a function that takes another function as a parameter or returns one.

In [`TransactionViewModel.kt`](file:///home/frank/AndroidStudioProjects/smartmoney/app/src/main/java/com/example/smartmoney/ui/transactions/TransactionViewModel.kt#L95):
```kotlin
transactions.filter { it.type.equals("CREDIT", ignoreCase = true) }
```
- `{ it.type == "CREDIT" }` is a **lambda expression**.
- `it` is the implicit name of the single parameter passed into the lambda.

---

## 🏋️ Practice Exercises

1. **Null Safety Challenge**:
   Write a Kotlin snippet that takes a nullable user email `val email: String?` and prints it in uppercase if it exists, or prints `"Guest"` if it is null or empty. (Hint: use `?.let` and `?:`).

2. **Data Class Experiment**:
   Take a look at [`Budget.kt`](file:///home/frank/AndroidStudioProjects/smartmoney/app/src/main/java/com/example/smartmoney/domain/model/Budget.kt). Use the `.copy()` function to create a new budget with the `spentAmount` increased by 100.

3. **Sealed Hierarchy Expansion**:
   If SmartMoney added an `OfflineMode` state to [`TransactionUiState`](file:///home/frank/AndroidStudioProjects/smartmoney/app/src/main/java/com/example/smartmoney/ui/transactions/TransactionUiState.kt), how would you declare it?

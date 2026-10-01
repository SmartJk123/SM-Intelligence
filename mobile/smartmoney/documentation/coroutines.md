# Kotlin Coroutines & Structured Concurrency Documentation

This document outlines the Kotlin Coroutines and Structured Concurrency architecture implemented across the **SmartMoney (SM-Intelligence)** Android mobile application.

---

## 1. Architectural Overview

Mobile applications require continuous responsiveness on the **Main (UI) Thread**. Any long-running or blocking work—such as disk I/O, database access, image processing, or network communication—will stall the UI thread and trigger Android **jank (frame drops)** or **ANR (Application Not Responding)** dialogs.

Kotlin Coroutines provide lightweight, non-blocking asynchronous execution built upon the principle of **Structured Concurrency**.

```
+-------------------------------------------------------------------------+
|                               UI LAYER                                  |
|  - Jetpack Compose Screens (HomeScreen, MoreScreen, DashboardScreen)    |
|  - rememberCoroutineScope() for user-triggered events (clicks, pickers) |
|  - Observes StateFlow<T> reactively                                     |
+------------------------------------+------------------------------------+
                                     |
                                     v
+------------------------------------+------------------------------------+
|                         VIEWMODEL / SCOPE LAYER                         |
|  - viewModelScope & lifecycleScope (Lifecycle-bound jobs)              |
|  - Launches coroutines with automatic cancellation on teardown          |
|  - Injects DispatcherProvider for deterministic testability             |
+------------------------------------+------------------------------------+
                                     |
                                     v
+------------------------------------+------------------------------------+
|                       DATA & REPOSITORY LAYER                           |
|  - UserProfileManager, BankAccountRepository, TransactionRepository     |
|  - Main-Safe Suspend Functions                                          |
|  - Offloads disk I/O & network parsing to withContext(Dispatchers.IO)   |
|  - Emits updates via StateFlow / Flow                                   |
+------------------------------------+------------------------------------+
                                     |
                                     v
+------------------------------------+------------------------------------+
|                         I/O / WORKER THREADS                            |
|  - Disk Cache (Internal Storage, Room DB)                               |
|  - Network Services (Spring Boot REST API, Retrofit, OkHttp)            |
+-------------------------------------------------------------------------+
```

---

## 2. Core Coroutine Principles

### 2.1 The `suspend` Modifier
A function marked with `suspend` can pause its execution without blocking the underlying thread. While suspended, the thread is free to execute other work (such as rendering UI animations). Once the asynchronous task completes, the coroutine resumes at the point of suspension.

```kotlin
// Blocks the calling thread until disk I/O completes (Avoid)
fun loadData(): Data { ... }

// Non-blocking: Suspends the coroutine while I/O runs in background
suspend fun loadData(): Data { ... }
```

### 2.2 Dispatchers and Thread Pools
Dispatchers dictate what thread pool executes a coroutine block:

| Dispatcher | Thread Pool | Intended Use Case |
|---|---|---|
| **`Dispatchers.Main`** | Android Main (UI) Thread | UI rendering, user interaction, lightweight state updates |
| **`Dispatchers.IO`** | On-demand elastic pool (up to 64 threads) | Disk I/O (files, Room DB), network requests, image decoding |
| **`Dispatchers.Default`** | CPU-core count shared pool | Heavy computation, complex JSON parsing, sorting large datasets |
| **`Dispatchers.Unconfined`** | Unconfined (caller thread until suspension) | Specialized testing scenarios |

### 2.3 The `DispatcherProvider` Abstraction
Hardcoding `Dispatchers.IO` or `Dispatchers.Default` directly into ViewModels and Repositories violates clean architecture and makes unit tests brittle. 

SmartMoney defines a centralized contract:
```kotlin
package com.example.smartmoney.core.coroutine

interface DispatcherProvider {
    val main: CoroutineDispatcher
    val io: CoroutineDispatcher
    val default: CoroutineDispatcher
    val unconfined: CoroutineDispatcher
}
```

- In production, `DefaultDispatcherProvider` routes to standard `Dispatchers`.
- In test suites, a test implementation can supply `StandardTestDispatcher` for instant virtual time manipulation.

---

## 3. Case Study: Main-Safe `UserProfileManager`

`UserProfileManager` is an internal caching singleton managing the user's profile avatar. Previously, it performed file existence checks, byte copying, and JPEG decoding **synchronously on the Main Thread** upon app startup.

### 3.1 The "Before" Problem (UI Thread Stutter)
```kotlin
// ❌ ANTI-PATTERN: Runs synchronously on MainActivity.onCreate() UI thread
fun initialize(context: Context) {
    val file = File(context.filesDir, PROFILE_IMAGE_FILE_NAME)
    if (file.exists() && file.length() > 0) {
        val bitmap = BitmapFactory.decodeFile(file.absolutePath)?.asImageBitmap() // Blocking disk I/O!
        _profileBitmap.value = bitmap
    }
}
```

### 3.2 The "After" Solution (Main-Safe Coroutine)
```kotlin
// ✅ COROUTINE BEST PRACTICE: Main-safe, offloaded to Dispatchers.IO
suspend fun initialize(
    context: Context,
    dispatcher: CoroutineDispatcher = Dispatchers.IO
) = withContext(dispatcher) {
    val file = File(context.filesDir, PROFILE_IMAGE_FILE_NAME)
    if (file.exists() && file.length() > 0) {
        try {
            val bitmap = BitmapFactory.decodeFile(file.absolutePath)?.asImageBitmap()
            _profileBitmap.value = bitmap
        } catch (_: Exception) {
            _profileBitmap.value = null
        }
    } else {
        _profileBitmap.value = null
    }
}
```

### 3.3 Non-Blocking Execution in `MainActivity`
```kotlin
// In MainActivity.kt
lifecycleScope.launch {
    UserProfileManager.initialize(applicationContext)
}
```

### 3.4 User-Triggered Actions in Compose UI
In `MoreScreen.kt`, photo picking and photo removal are executed using Compose's lifecycle-aware `rememberCoroutineScope()`:
```kotlin
val coroutineScope = rememberCoroutineScope()

// Visual photo picker result
coroutineScope.launch {
    UserProfileManager.updateProfilePicture(context, uri)
}

// Remove photo button
coroutineScope.launch {
    UserProfileManager.removeProfilePicture(context)
}
```

---

## 4. Structured Concurrency & Scopes

A coroutine must always be launched within a bounded **`CoroutineScope`**:
- **`lifecycleScope`**: Bound to the Activity/Fragment lifecycle. Cancelled automatically when the Activity is destroyed.
- **`viewModelScope`**: Bound to the ViewModel lifecycle. Cancelled automatically when the ViewModel is cleared.
- **`rememberCoroutineScope()`**: Bound to the Compose composition. Cancelled when the composable leaves the composition.

### Why Structured Concurrency Matters:
1. **Zero Memory Leaks**: If a user navigates away from a screen while an image is saving or a network request is loading, the coroutine is cancelled automatically.
2. **Error Propagation**: If a child coroutine fails, the parent scope handles the failure without leaving dangling background threads.

---

## 5. Developer Best Practices Checklist

When writing new features or modifying existing code in SmartMoney:

- [x] **Make Suspend Functions Main-Safe**: Any function performing disk, network, or heavy computation must use `withContext(dispatchers.io)` or `withContext(dispatchers.default)` so callers on the UI thread never freeze.
- [x] **Never Use `GlobalScope`**: Always launch coroutines from `viewModelScope`, `lifecycleScope`, or `rememberCoroutineScope()`.
- [x] **Expose State via `StateFlow`**: Keep mutable state private (`_uiState = MutableStateFlow(...)`) and expose read-only state (`val uiState: StateFlow<T> = _uiState.asStateFlow()`).
- [x] **Inject Dispatchers**: Use `DispatcherProvider` instead of hardcoding `Dispatchers.IO` or `Dispatchers.Default` inside ViewModels and Repositories.
- [x] **Handle Cancellation Gracefully**: Never catch `CancellationException` and swallow it; allow cancellation to propagate up the coroutine tree.

---

## 6. Phase 2: Parallel Decomposition & Coroutine Builders

While `suspend` functions and `withContext` solve thread-blocking issues sequentially, complex screens (like the Dashboard or Accounts view) often require data from multiple disparate sources simultaneously (e.g., local database sync + remote bank integration sync).

Running these sequentially wastes time. We utilize **Coroutine Builders** to achieve parallel decomposition.

### 6.1 The "Before" Problem (Sequential Execution)
```kotlin
// ❌ ANTI-PATTERN: Sequential execution blocks the user
fun refreshAccounts() {
    viewModelScope.launch {
        _isLoading.value = true
        // Task A takes 2 seconds
        repository.syncAccounts(userId) 
        // Task B waits for Task A to finish, then takes 3 seconds
        bankAccountRepository.refreshBankAccounts() 
        // Total wait time: 5 seconds
        _isLoading.value = false
    }
}
```

### 6.2 The "After" Solution (Parallel Execution with `async` / `awaitAll`)
```kotlin
// ✅ BEST PRACTICE: Concurrent execution cuts wait time
fun refreshAccounts() {
    viewModelScope.launch(dispatchers.main) {
        _isLoading.value = true
        try {
            // coroutineScope creates a structured concurrency boundary
            coroutineScope { 
                // Launch both tasks in parallel on the IO dispatcher
                val accountsDeferred = async(dispatchers.io) {
                    repository.syncAccounts(userId)
                }
                val bankAccountsDeferred = async(dispatchers.io) {
                    bankAccountRepository.refreshBankAccounts()
                }
                // Suspend until both are complete. 
                // Total wait time is now Max(Task A, Task B) -> 3 seconds
                awaitAll(accountsDeferred, bankAccountsDeferred) 
            }
        } finally {
            _isLoading.value = false
        }
    }
}
```

### 6.3 Coroutine Builder Summary
- **`launch { ... }`**: "Fire and forget." Used at the top level of UI/ViewModels. Does not return a result.
- **`async { ... }`**: "Fire and remember." Returns a `Deferred<T>`. Used when you need to compute a value concurrently and await its result later.
- **`awaitAll(...)`**: Suspends execution until all provided `Deferred` tasks finish successfully. If any child task fails, the entire `coroutineScope` fails gracefully.

---

## 7. Deterministic Unit Testing

Asynchronous code is historically difficult to test because assertions often evaluate before the background thread finishes execution. By injecting `DispatcherProvider` into our architecture, we can swap out the production multi-threaded environment for a synchronous, unconfined test environment.

### 7.1 The Test Dispatcher Setup
We created a `TestDispatcherProvider` implementation specifically for our JUnit test suite:

```kotlin
class TestDispatcherProvider(
    override val main: CoroutineDispatcher = Dispatchers.Unconfined,
    override val io: CoroutineDispatcher = Dispatchers.Unconfined,
    override val default: CoroutineDispatcher = Dispatchers.Unconfined,
    override val unconfined: CoroutineDispatcher = Dispatchers.Unconfined
) : DispatcherProvider
```
By defaulting all threads to `Dispatchers.Unconfined`, the coroutine executes instantly on the current test thread without queuing or delays.

### 7.2 Writing a Deterministic Test (`AccountViewModelCoroutineTest.kt`)
```kotlin
@Test
fun testRefreshAccounts_executesDeterministically_withTestDispatchers() = runTest {
    // 1. Arrange: Use our TestDispatcherProvider
    val testDispatchers = TestDispatcherProvider()
    val fakeRepo = FakeAccountRepository()

    // 2. Act: Instantiate view model (which calls refreshAccounts internally)
    val viewModel = AccountViewModel(
        repository = fakeRepo,
        dispatchers = testDispatchers // <-- INJECTED
    )

    // 3. Assert: Verify the view model completed its work synchronously!
    // No need for Thread.sleep() or advanceUntilIdle()
    assertTrue(fakeRepo.syncCalled)
    assertFalse(viewModel.isLoading.value)
}
```
*Note: `runTest` from `kotlinx-coroutines-test` automatically skips delays (`delay(1000)` becomes instant).*

---

## 8. Exception Handling in Coroutines

Coroutines handle exceptions differently depending on the builder used (`launch` vs `async`).

### 8.1 Safe try-catch in `launch`
When using `launch`, exceptions must be caught *inside* the coroutine block:
```kotlin
viewModelScope.launch(dispatchers.main) {
    try {
        repository.syncData() // Suspending call
    } catch (e: Exception) {
        _errorState.value = "Sync failed: ${e.message}"
    }
}
```

### 8.2 SupervisorJob vs Job
In a standard `coroutineScope`, if one child coroutine fails, the entire scope is cancelled. If you want children to fail independently without bringing down the parent, use `supervisorScope`:

```kotlin
suspend fun fetchAllIndependentFeeds() = supervisorScope {
    val news = async { fetchNews() }
    val weather = async { fetchWeather() } // If this fails, 'news' still completes
    
    try {
        val weatherResult = weather.await()
    } catch (e: Exception) {
        // Handle weather failure silently
    }
}
```

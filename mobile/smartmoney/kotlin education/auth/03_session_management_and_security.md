# 03. Session Management, Security & Error Resilience

This document explores how SmartMoney handles session lifecycles, cross-microservice security, user data isolation, and backend error resilience.

---

## 1. 🔑 Session State Architecture

In SmartMoney, the active user session is managed through a hybrid in-memory and persistent design:

```
┌────────────────────────────────────────────────────────┐
│ In-Memory Session State (AuthRemoteDataSource)         │
│  - authToken (JWT)                                     │
│  - currentUserId                                       │
│  - currentUserName                                     │
│  - currentUserEmail                                    │
│  - _isLoggedInFlow (MutableStateFlow<Boolean>)         │
└───────────────────────────┬────────────────────────────┘
                            │ Provides token
                            ▼
┌────────────────────────────────────────────────────────┐
│ OkHttp Auth Interceptor (RetrofitClient)               │
│  - Adds "Authorization: Bearer <token>" to all APIs   │
└────────────────────────────────────────────────────────┘
```

### Why this design?
1. **Zero Token Leakage:** The token lives in memory and is automatically wiped whenever `signOut()` is called or the app process is terminated.
2. **Centralized Provider:** `RetrofitClient.tokenProvider` is a lambda `{ authToken }`. Any microservice call made through Retrofit automatically resolves the active token at execution time without needing token parameters passed through repository methods.

---

## 2. 🛡️ Multi-User Data Isolation

When multiple users share the same device (or when testing different user accounts), preventing cross-account data leaks is critical.

SmartMoney implements **three layers of user isolation**:

### A. ViewModel Instance Isolation (Scoped Keys)
In [`MainActivity.kt`](file:///home/frank/AndroidStudioProjects/smartmoney/app/src/main/java/com/example/smartmoney/MainActivity.kt#L121-L185):
```kotlin
val transactionViewModel: TransactionViewModel = viewModel(
    key = "transaction_$currentUserId", // 🌟 UNIQUE PER USER
    factory = TransactionViewModel.Factory(...)
)
```
* **How it works:** Android's `ViewModelStore` caches ViewModels by key. By appending `$currentUserId` to the key, switching from User A to User B creates a **fresh ViewModel instance**, ensuring User B never sees User A's cached transactions in memory.

### B. Avatar & File System Isolation
In [`UserProfileManager.kt`](file:///home/frank/AndroidStudioProjects/smartmoney/app/src/main/java/com/example/smartmoney/data/local/UserProfileManager.kt#L32-L34):
```kotlin
fun getAvatarFile(context: Context, userId: String): File {
    return File(context.filesDir, "user_profile_${userId}_avatar.jpg")
}
```
* Profile pictures are saved as `user_profile_<userId>_avatar.jpg`. When User A logs out and User B logs in, User B only sees their own avatar (or initials if none is uploaded).

### C. Complete Cache Purge on Sign-Out
In [`MainActivity.kt`](file:///home/frank/AndroidStudioProjects/smartmoney/app/src/main/java/com/example/smartmoney/MainActivity.kt#L215-L225):
```kotlin
onLogout = {
    val db = appContainer.database
    CoroutineScope(Dispatchers.IO).launch {
        try {
            db.clearAllTables() // 🌟 Wipes all Room SQLite tables
        } catch (e: Exception) {
            e.printStackTrace()
        }
        UserProfileManager.clearSession() // 🌟 Resets avatar bitmap
    }
    authViewModel.signOut()
}
```

---

## 3. 🌐 Cross-Microservice Routing & Gateway Support

SmartMoney communicates with a Spring Boot microservice architecture:

| Service | Port | Description |
| :--- | :--- | :--- |
| **Identity Service** | `:8081` | Registration, Login, JWT issuing |
| **Accounts Service** | `:8082` | Bank accounts & linked balances |
| **Transactions Service** | `:8083` | Transaction logs & movements |
| **Bank Integration Service** | `:8090` / Render | Bank simulator & Open Banking APIs |
| **Assistant Service (Raha)** | `:8091` | AI conversational assistant |
| **API Gateway (Optional)** | `:8080` | Unified gateway routing all endpoints |

### Dynamic Gateway & Cloud Deployment:
In [`RetrofitClient.kt`](file:///home/frank/AndroidStudioProjects/smartmoney/app/src/main/java/com/example/smartmoney/data/remote/RetrofitClient.kt#L127-L137):
```kotlin
private val deployedGateway: String = BuildConfig.API_BASE_URL.trim().trimEnd('/')

private fun buildRetrofit(servicePort: Int): Retrofit {
    val port = gatewayPort ?: servicePort
    val baseUrl = if (deployedGateway.isNotEmpty()) "$deployedGateway/" else "http://$host:$port/"
    return Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(okHttpClient)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()
}
```
* If `API_BASE_URL` is configured in `local.properties` (e.g. cloud deployment), all API calls automatically route through the API Gateway.
* If empty, Retrofit falls back to direct per-port local communication (`127.0.0.1:8081`, `:8082`, etc.).

---

## 4. ⚡ Resilience & Error Handling

Backend servers can fail, lose connectivity, or spin down when idle. SmartMoney implements layered defenses against these failure modes:

### A. The 30-Second Cloud Cold-Start Trap
Free cloud hosting providers (such as Render) put backend containers to sleep after 15 minutes of inactivity. When a user opens the app and submits their credentials, the server can take 20–30 seconds to wake up, causing standard HTTP clients to crash with a timeout.

In [`AuthRepositoryImpl.kt`](file:///home/frank/AndroidStudioProjects/smartmoney/app/src/main/java/com/example/smartmoney/data/repository/AuthRepositoryImpl.kt#L51-L52):
```kotlin
try {
    // ...
} catch (e: SocketTimeoutException) {
    Result.failure(Exception("The server is waking up. Please try again in 30 seconds."))
}
```
Instead of an unhelpful "SocketTimeoutException" crash, the user receives an informative message explaining exactly what is happening.

### B. Intelligent Spring Boot JSON Error Extraction
When Spring Boot rejects a request (e.g. HTTP 400 or 409), it often returns a JSON payload with detailed error information:

```json
{
  "timestamp": "2026-10-12T14:30:00Z",
  "status": 409,
  "error": "Conflict",
  "message": "An account with email frank@example.com already exists",
  "details": {
    "email": "Email address already in use"
  }
}
```

In [`AuthRepositoryImpl.parseHttpException()`](file:///home/frank/AndroidStudioProjects/smartmoney/app/src/main/java/com/example/smartmoney/data/repository/AuthRepositoryImpl.kt#L111-L156):
```kotlin
if (jsonElement is JsonObject) {
    val message = (jsonElement["message"] as? JsonPrimitive)?.content
    if (!message.isNullOrBlank()) return message

    val details = jsonElement["details"] as? JsonObject
    if (details != null && details.isNotEmpty()) {
        val detailMsgs = details.values
            .mapNotNull { (it as? JsonPrimitive)?.content }
            .filter { it.isNotBlank() }
        if (detailMsgs.isNotEmpty()) return detailMsgs.joinToString(", ")
    }

    val error = (jsonElement["error"] as? JsonPrimitive)?.content
    if (!error.isNullOrBlank()) return error
}
```
* The parser attempts to extract the specific `message`.
* If empty, it inspects field-level validation errors inside `details`.
* If JSON parsing fails entirely, it provides clean fallback messages mapped to standard HTTP status codes (`401 -> Invalid email or password`, `409 -> An account with this email already exists`).

---

## 5. 🔒 Client-Side Form Security
* **Password Masking:** [`LoginScreen.kt`](file:///home/frank/AndroidStudioProjects/smartmoney/app/src/main/java/com/example/smartmoney/ui/auth/LoginScreen.kt) uses `PasswordVisualTransformation()` with a toggle icon allowing the user to view or hide their password.
* **Defensive Schemas:** Both `email` and `emailAddress` are serialized in requests, accommodating variations in Spring Boot model binding.
* **Input Trimming:** Emails are automatically trimmed before validation and transmission to prevent invisible whitespace from causing failed logins.

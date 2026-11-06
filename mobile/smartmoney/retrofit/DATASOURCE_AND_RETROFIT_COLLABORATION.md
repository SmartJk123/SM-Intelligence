# DataSource & RetrofitClient Collaboration Guide

This document explains how **DataSources** and the **[`RetrofitClient`](file:///home/frank/AndroidStudioProjects/smartmoney/app/src/main/java/com/example/smartmoney/data/remote/RetrofitClient.kt)** work together in **SmartMoney**, with a special focus on **JWT (JSON Web Token) authentication**, session lifecycle management, and transparent header injection.

---

## 1. Division of Responsibilities

In Clean Architecture, network communication is divided between two distinct layers:

| Responsibility | [`RetrofitClient.kt`](file:///home/frank/AndroidStudioProjects/smartmoney/app/src/main/java/com/example/smartmoney/data/remote/RetrofitClient.kt) | DataSources (e.g. [`AuthRemoteDataSource`](file:///home/frank/AndroidStudioProjects/smartmoney/app/src/main/java/com/example/smartmoney/data/remote/datasource/AuthRemoteDataSource.kt), [`AccountRemoteDataSource`](file:///home/frank/AndroidStudioProjects/smartmoney/app/src/main/java/com/example/smartmoney/data/remote/datasource/AccountRemoteDataSource.kt)) |
| :--- | :--- | :--- |
| **Role** | Low-level HTTP Transport Engine | High-level Remote Data Coordinator |
| **Scope** | App-wide Singleton | Service/Feature specific |
| **Concerns** | Socket timeouts, connection pooling, IP fallbacks, JSON serialization, HTTP headers | Endpoint execution, domain mapping, error translation, session state |
| **Authentication** | Injects `Authorization` header via interceptors | Obtains, stores, and clears JWT tokens |
| **Knows about** | OkHttp, Serialization, HTTP ports, Base URLs | Business models, Repository requirements, App state |

---

## 2. The Architectural Challenge: The Circular Dependency Trap

A naive implementation often creates a circular dependency:
* `AuthRemoteDataSource` needs `RetrofitClient.authApi` to send login requests.
* `RetrofitClient` needs `AuthRemoteDataSource` to get the JWT token to authenticate requests.

```
┌────────────────────────┐                    ┌────────────────────────┐
│      RetrofitClient    │ ◄── Needs Token ── │  AuthRemoteDataSource  │
│ (builds OkHttpClient)  │ ── Needs Api ────► │ (calls authApi.login)  │
└────────────────────────┘                    └────────────────────────┘
                    ⚠️ Circular Initialization Deadlock ⚠️
```

### How SmartMoney Solves This: The Lambda Bridge Pattern
Instead of holding a direct reference to `AuthRemoteDataSource`, `RetrofitClient` exposes a lightweight, nullable lambda provider:

```kotlin
// In RetrofitClient.kt (Line 62)
var tokenProvider: (() -> String?)? = null
```

During initialization, [`AuthRemoteDataSource`](file:///home/frank/AndroidStudioProjects/smartmoney/app/src/main/java/com/example/smartmoney/data/remote/datasource/AuthRemoteDataSource.kt#L33-L35) registers a lambda pointing directly to its private `authToken` variable:

```kotlin
// In AuthRemoteDataSource.kt (Lines 31-35)
private var authToken: String? = null

init {
    RetrofitClient.tokenProvider = { authToken }
}
```

* Zero circular dependencies.
* `RetrofitClient` never needs an instance of `AuthRemoteDataSource`.
* Whenever `authToken` updates in `AuthRemoteDataSource`, `RetrofitClient` immediately reads the latest value on its very next HTTP call.

---

## 3. End-to-End JWT Lifecycle Example

Here is how the collaboration works in practice across 4 stages.

### Stage 1: Token Acquisition (Login / Registration)
When the user submits credentials in [`LoginScreen`](file:///home/frank/AndroidStudioProjects/smartmoney/app/src/main/java/com/example/smartmoney/ui/auth/LoginScreen.kt):

1. [`AuthViewModel`](file:///home/frank/AndroidStudioProjects/smartmoney/app/src/main/java/com/example/smartmoney/ui/auth/AuthViewModel.kt) calls `authRepository.signIn(email, password)`.
2. [`AuthRepositoryImpl`](file:///home/frank/AndroidStudioProjects/smartmoney/app/src/main/java/com/example/smartmoney/data/repository/AuthRepositoryImpl.kt#L72) calls `authApi.login(LoginRequest(email, password))`.
3. The backend returns HTTP `200 OK` with JSON containing the JWT:
   ```json
   {
     "userId": "usr_9981",
     "name": "Frank",
     "email": "frank@example.com",
     "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJ1c3JfOTk4MSIsImV4cCI6MTgwMDAw..."
   }
   ```
4. [`AuthRemoteDataSource.updateSession(...)`](file:///home/frank/AndroidStudioProjects/smartmoney/app/src/main/java/com/example/smartmoney/data/remote/datasource/AuthRemoteDataSource.kt#L127-L138) saves the token into memory:
   ```kotlin
   fun updateSession(body: AuthResponse, email: String, fallbackName: String?) {
       currentUserId = body.userId ?: body.id
       currentUserName = body.name ?: fallbackName
       currentUserEmail = body.email ?: email
       authToken = body.token ?: body.accessToken // <-- JWT stored here!
       _isLoggedInFlow.value = true
   }
   ```

---

### Stage 2: Transparent JWT Injection on Subsequent Calls
Now suppose the user navigates to the **Accounts** screen. The app needs to fetch the user's financial accounts.

1. [`AccountRemoteDataSource`](file:///home/frank/AndroidStudioProjects/smartmoney/app/src/main/java/com/example/smartmoney/data/remote/datasource/AccountRemoteDataSource.kt#L28-L30) calls `accountApi.getAccounts(userId)`:
   ```kotlin
   class AccountRemoteDataSource(
       private val accountApi: AccountApi = RetrofitClient.accountApi
   ) {
       suspend fun fetchAccounts(userId: String): List<Account> {
           // Notice: AccountRemoteDataSource does NOT manually pass tokens or headers!
           val response = accountApi.getAccounts(userId = userId)
           ...
       }
   }
   ```

2. Notice that `AccountRemoteDataSource` **does not care about tokens, headers, or authentication**. Its code remains pure and simple.

---

### Stage 3: OkHttp Interceptor Executes the Handshake
Behind the scenes, before the HTTP request leaves the device, `RetrofitClient`'s [`authInterceptor`](file:///home/frank/AndroidStudioProjects/smartmoney/app/src/main/java/com/example/smartmoney/data/remote/RetrofitClient.kt#L71-L82) executes:

```kotlin
private val authInterceptor = okhttp3.Interceptor { chain ->
    val original = chain.request()
    
    // 1. Asks the tokenProvider lambda for the token
    val token = tokenProvider?.invoke() // Invokes { authToken } in AuthRemoteDataSource!

    // 2. If token exists and header isn't already set, inject "Authorization: Bearer <token>"
    val request = if (!token.isNullOrBlank() && original.header("Authorization") == null) {
        original.newBuilder()
            .header("Authorization", "Bearer $token")
            .build()
    } else {
        original
    }
    
    // 3. Send request over the network to the backend
    chain.proceed(request)
}
```

The resulting HTTP packet transmitted to the microservice:
```http
GET /api/accounts?userId=usr_9981 HTTP/1.1
Host: 127.0.0.1:8082
Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
Content-Type: application/json
```

---

### Stage 4: Session Invalidation (Sign Out)
When the user taps **Sign Out**:

1. [`AuthViewModel.signOut()`](file:///home/frank/AndroidStudioProjects/smartmoney/app/src/main/java/com/example/smartmoney/ui/auth/AuthViewModel.kt#L119) calls `authRepository.signOut()`.
2. [`AuthRemoteDataSource.signOut()`](file:///home/frank/AndroidStudioProjects/smartmoney/app/src/main/java/com/example/smartmoney/data/remote/datasource/AuthRemoteDataSource.kt#L100-L108) resets all in-memory credentials:
   ```kotlin
   suspend fun signOut(): Result<Unit> {
       currentUserId = null
       currentUserName = null
       currentUserEmail = null
       authToken = null          // <-- Token cleared!
       _isLoggedInFlow.value = false
       return Result.success(Unit)
   }
   ```
3. On the next HTTP request, `tokenProvider?.invoke()` returns `null`.
4. `authInterceptor` sees no token and sends the request without the `Authorization` header.

---

## 4. Sequence Diagram: Collaboration in Action

```mermaid
sequenceDiagram
    autonumber
    actor User
    participant Screen as Login / Account Screen
    participant VM as ViewModel
    participant Repo as RepositoryImpl
    participant DS as DataSource (Auth / Account)
    participant RC as RetrofitClient (authInterceptor)
    participant Server as Spring Boot Microservices

    Note over User, Server: Phase 1: Authentication & Token Storage
    User->>Screen: Tap "Sign In"
    Screen->>VM: signIn(email, password)
    VM->>Repo: signIn(email, password)
    Repo->>DS: login(email, password)
    DS->>Server: POST /api/auth/login
    Server-->>DS: 200 OK + JWT { "token": "ey..." }
    DS->>DS: updateSession(): authToken = "ey..."
    Note over DS, RC: RetrofitClient.tokenProvider now evaluates to "ey..."

    Note over User, Server: Phase 2: Authenticated Data Request
    User->>Screen: View Accounts
    Screen->>VM: loadAccounts()
    VM->>Repo: getAccounts()
    Repo->>DS: fetchAccounts() (AccountRemoteDataSource)
    DS->>RC: accountApi.getAccounts()
    RC->>RC: authInterceptor executes
    RC->>DS: tokenProvider?.invoke()
    DS-->>RC: returns "ey..."
    RC->>RC: Adds header "Authorization: Bearer ey..."
    RC->>Server: GET /api/accounts (with Bearer Token)
    Server-->>RC: 200 OK + Account List JSON
    RC-->>DS: Response<List<AccountResponse>>
    DS-->>Repo: List<Account> (Domain Models)
    Repo-->>VM: Flow / Result
    VM-->>Screen: Display UI State
```

---

## 5. Key Benefits of This Architecture

1. **Separation of Concerns**:
   Feature data sources ([`AccountRemoteDataSource`](file:///home/frank/AndroidStudioProjects/smartmoney/app/src/main/java/com/example/smartmoney/data/remote/datasource/AccountRemoteDataSource.kt), [`TransactionRemoteDataSource`](file:///home/frank/AndroidStudioProjects/smartmoney/app/src/main/java/com/example/smartmoney/data/remote/datasource/TransactionRemoteDataSource.kt)) don't write authorization logic. They stay 100% focused on their own domain queries.

2. **Single Source of Truth for Session**:
   The JWT token is held exclusively in [`AuthRemoteDataSource`](file:///home/frank/AndroidStudioProjects/smartmoney/app/src/main/java/com/example/smartmoney/data/remote/datasource/AuthRemoteDataSource.kt), avoiding scattered token copies that could get out of sync.

3. **Fault-Tolerant & Reactive**:
   If the token changes, refreshes, or is destroyed on logout, every single network request across the entire app reflects the change instantly without requiring Retrofit or OkHttpClient to be re-instantiated.

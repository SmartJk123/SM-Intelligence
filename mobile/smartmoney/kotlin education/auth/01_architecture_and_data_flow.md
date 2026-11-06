# 01. Authentication Architecture & End-to-End Data Flow

This document details how a user authentication request travels across the SmartMoney mobile application — from a user tapping a button on the screen down to the network socket, and back up to the UI.

---

## 🏗️ High-Level Architectural Flow

SmartMoney follows **Clean Architecture** with a unidirectional data flow (UDF):

```mermaid
flowchart TD
    subgraph UI_Layer ["1. UI Layer (Presentation)"]
        UI["LoginScreen / SignUpScreen"]
        VM["AuthViewModel"]
        State["AuthState (StateFlow)"]
    end

    subgraph Domain_Layer ["2. Domain Layer (Contract)"]
        RepoContract["AuthRepository (Interface)"]
    end

    subgraph Data_Layer ["3. Data Layer (Implementation)"]
        RepoImpl["AuthRepositoryImpl"]
        RemoteDS["AuthRemoteDataSource"]
        Retrofit["RetrofitClient & AuthApi"]
        Interceptor["OkHttp Auth Interceptor (Bearer Token)"]
        ProfileMgr["UserProfileManager (Disk Storage)"]
    end

    subgraph Backend ["4. Remote Backend"]
        SpringBoot["Spring Boot Identity Service (:8081)"]
    end

    UI -->|User taps 'Sign In'| VM
    VM -->|Emits AuthState.Loading| State
    State -->|Triggers Spinner| UI
    VM -->|Calls suspend signIn()| RepoContract
    RepoContract -.Implemented by.-> RepoImpl
    RepoImpl -->|withContext(Dispatchers.IO)| RemoteDS
    RemoteDS -->|Calls POST /api/auth/login| Retrofit
    Retrofit -->|HTTP POST JSON| SpringBoot
    SpringBoot -->|Returns JWT + User Details| Retrofit
    Retrofit --> RemoteDS
    RemoteDS -->|Stores authToken & updates tokenProvider| Interceptor
    RemoteDS -->|Emits isLoggedIn = true| RepoImpl
    RepoImpl -->|Returns Result.success| VM
    VM -->|Emits AuthState.Success| State
    RemoteDS -.Observes isLoggedIn.-> VM
    VM -.Observes isLoggedIn.-> UI
    UI -.Navigates to Dashboard.-> ProfileMgr
```

---

## 🚀 The Complete Journey of a Sign-In Request

Let's trace a user logging in step-by-step:

### Step 1: User Input & Form Validation (UI Layer)
* **File:** [`ui/auth/LoginScreen.kt`](file:///home/frank/AndroidStudioProjects/smartmoney/app/src/main/java/com/example/smartmoney/ui/auth/LoginScreen.kt)
* **Action:** The user enters `frank@example.com` and their password, then taps the **Sign In** button.
* **Validation:** Before any network call is initiated, client-side validation runs locally:
  ```kotlin
  if (email.isBlank()) {
      emailError = "Email cannot be blank"
  } else if (!EMAIL_REGEX.matches(email.trim())) {
      emailError = "Please enter a valid email address"
  }
  ```
* If validation passes, the screen invokes:
  ```kotlin
  authViewModel.signIn(email.trim(), password)
  ```

---

### Step 2: Coroutine Launch & Loading State (UI Layer)
* **File:** [`ui/auth/AuthViewModel.kt`](file:///home/frank/AndroidStudioProjects/smartmoney/app/src/main/java/com/example/smartmoney/ui/auth/AuthViewModel.kt#L80-L100)
* **Action:** `AuthViewModel` launches a coroutine scoped to `viewModelScope`:
  ```kotlin
  fun signIn(email: String, password: String) {
      viewModelScope.launch {
          _authState.value = AuthState.Loading // 1. Notify UI to show loading spinner
          val result = authRepository.signIn(email, password) // 2. Call Domain
          result.fold(
              onSuccess = { _authState.value = AuthState.Success("Sign in successful!") },
              onFailure = { error -> _authState.value = AuthState.Error(error.localizedMessage) }
          )
      }
  }
  ```
* Because `_authState` is a `StateFlow<AuthState>`, the `LoginScreen` immediately receives `AuthState.Loading` and disables the login button while rendering a `CircularProgressIndicator`.

---

### Step 3: Domain Inversion of Control (Domain Layer)
* **File:** [`domain/repository/AuthRepository.kt`](file:///home/frank/AndroidStudioProjects/smartmoney/app/src/main/java/com/example/smartmoney/domain/repository/AuthRepository.kt#L11)
* **Action:** The call reaches the domain interface:
  ```kotlin
  suspend fun signIn(email: String, password: String): Result<Unit>
  ```
* Notice that the Domain layer **does not know** whether the request goes to Spring Boot, Supabase, Firebase, or an in-memory test database. It only specifies that `signIn` returns a Kotlin `Result<Unit>`.

---

### Step 4: Thread Switching & DTO Preparation (Data Layer)
* **File:** [`data/repository/AuthRepositoryImpl.kt`](file:///home/frank/AndroidStudioProjects/smartmoney/app/src/main/java/com/example/smartmoney/data/repository/AuthRepositoryImpl.kt#L63-L90)
* **Action:** `AuthRepositoryImpl` switches execution to a worker thread:
  ```kotlin
  override suspend fun signIn(email: String, password: String): Result<Unit> = withContext(dispatchers.io) {
      try {
          val request = LoginRequest(email = email, password = password)
          val response = authApi.login(request)
          if (!response.isSuccessful) {
              throw HttpException(response)
          }
          val body = response.body() ?: AuthResponse()
          remoteDataSource.updateSession(body = body, email = email, fallbackName = null)
          Result.success(Unit)
      } catch (e: SocketTimeoutException) {
          Result.failure(Exception("The server is waking up. Please try again in 30 seconds."))
      } catch (e: HttpException) {
          Result.failure(Exception(parseHttpException(e)))
      }
  }
  ```
* **Thread Safety:** `withContext(dispatchers.io)` guarantees that serialization and HTTP network sockets never touch the Main Thread.
* **Cold-Start Resilience:** Notice the explicit catch for `SocketTimeoutException`. When the free-tier Render backend spins down, the app gracefully catches the timeout and tells the user: *"The server is waking up. Please try again in 30 seconds."*

---

### Step 5: Network Serialization & Wire Transmission (Data Layer)
* **Files:** [`data/remote/api/AuthApi.kt`](file:///home/frank/AndroidStudioProjects/smartmoney/app/src/main/java/com/example/smartmoney/data/remote/api/AuthApi.kt) and [`data/remote/RetrofitClient.kt`](file:///home/frank/AndroidStudioProjects/smartmoney/app/src/main/java/com/example/smartmoney/data/remote/RetrofitClient.kt)
* **Action:**
  1. `kotlinx.serialization` serializes `LoginRequest` into JSON:
     ```json
     {
       "email": "frank@example.com",
       "emailAddress": "frank@example.com",
       "password": "secretpassword"
     }
     ```
  2. Retrofit maps this to `@POST("api/auth/login")`.
  3. OkHttp opens an HTTP connection to the backend port (`:8081` on local or the configured `apiBaseUrl`).

---

### Step 6: Backend Response & Session Storage (Data Layer)
* **File:** [`data/remote/datasource/AuthRemoteDataSource.kt`](file:///home/frank/AndroidStudioProjects/smartmoney/app/src/main/java/com/example/smartmoney/data/remote/datasource/AuthRemoteDataSource.kt#L127-L138)
* **Action:** The Spring Boot backend returns HTTP 200 with JSON:
  ```json
  {
    "userId": "usr_981247",
    "name": "Frank",
    "email": "frank@example.com",
    "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
  }
  ```
* `AuthRemoteDataSource.updateSession()` processes the payload:
  ```kotlin
  fun updateSession(body: AuthResponse, email: String, fallbackName: String?) {
      currentUserId = body.userId ?: body.id ?: UUID.randomUUID().toString()
      currentUserName = body.name ?: fallbackName ?: email.substringBefore('@')
      currentUserEmail = body.email ?: body.emailAddress ?: email
      authToken = body.token ?: body.accessToken
      _isLoggedInFlow.value = true // 🌟 EMITS TRUE TO ENTIRE APP
  }
  ```

---

### Step 7: Automatic Bearer Token Injection (OkHttp Interceptor)
* **File:** [`data/remote/RetrofitClient.kt`](file:///home/frank/AndroidStudioProjects/smartmoney/app/src/main/java/com/example/smartmoney/data/remote/RetrofitClient.kt#L71-L82)
* **Action:**
  In `AuthRemoteDataSource`:
  ```kotlin
  init {
      RetrofitClient.tokenProvider = { authToken }
  }
  ```
  And inside `RetrofitClient`:
  ```kotlin
  private val authInterceptor = okhttp3.Interceptor { chain ->
      val original = chain.request()
      val token = tokenProvider?.invoke()
      val request = if (!token.isNullOrBlank() && original.header("Authorization") == null) {
          original.newBuilder()
              .header("Authorization", "Bearer $token")
              .build()
      } else original
      chain.proceed(request)
  }
  ```
* **Result:** The moment `authToken` is saved in `AuthRemoteDataSource`, **every subsequent HTTP call** made across the app (Accounts, Transactions, Budgets, Raha Assistant) automatically has `Authorization: Bearer <token>` injected!

---

### Step 8: UI Navigation & User Profile Isolation (UI Layer)
* **File:** [`MainActivity.kt`](file:///home/frank/AndroidStudioProjects/smartmoney/app/src/main/java/com/example/smartmoney/MainActivity.kt#L91-L120)
* **Action:**
  1. `MainActivity` observes `isLoggedIn` via `authViewModel.isLoggedIn.collectAsState()`.
  2. When it becomes `true`, Compose replaces the `LoginScreen` with the main app flow:
     ```kotlin
     if (isLoggedIn) {
         val currentUserId = authViewModel.currentUserId() ?: "default-user"

         // Switch user profile picture cache to the newly logged-in user
         LaunchedEffect(currentUserId) {
             UserProfileManager.switchUser(this@MainActivity, currentUserId)
         }

         // Request Android 13+ Notification permission
         // Launch Warm-Up Sync Screen, then DashboardScreen
     }
     ```
  3. `UserProfileManager` loads the user's specific avatar (`user_profile_<userId>_avatar.jpg`) from internal storage on `Dispatchers.IO`.

---

## 🔄 The Complete Journey of Sign-Out

When the user taps **Log Out** in [`MoreScreen.kt`](file:///home/frank/AndroidStudioProjects/smartmoney/app/src/main/java/com/example/smartmoney/ui/more/MoreScreen.kt):

1. **`MainActivity.kt` executes the cleanup lambda:**
   ```kotlin
   onLogout = {
       val db = appContainer.database
       CoroutineScope(Dispatchers.IO).launch {
           try {
               db.clearAllTables() // Purges cached accounts and transactions
           } catch (_: Exception) {}
           UserProfileManager.clearSession() // Resets avatar in memory
       }
       authViewModel.signOut()
       isWarmUpCompleted = false
       showLogin = true
   }
   ```
2. **`AuthViewModel.signOut()` calls `authRepository.signOut()`:**
   - Clears `authToken = null`
   - Sets `_isLoggedInFlow.value = false`
3. **Reactive Recomposition:**
   - `MainActivity` sees `isLoggedIn == false`.
   - The UI immediately leaves `DashboardScreen` and displays `LoginScreen`.

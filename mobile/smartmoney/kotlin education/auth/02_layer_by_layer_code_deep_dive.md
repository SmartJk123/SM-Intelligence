# 02. Layer-by-Layer Code Deep Dive

This document breaks down every file and class involved in user authentication across all four layers of the application: **Core Layer**, **Domain Layer**, **Data Layer**, and **UI Layer**.

---

## 1. ⚙️ Core Layer

The Core layer houses cross-cutting infrastructure: dependency injection, application startup, and coroutine dispatchers.

---

### A. [`AppContainer.kt`](file:///home/frank/AndroidStudioProjects/smartmoney/app/src/main/java/com/example/smartmoney/core/di/AppContainer.kt) & `DefaultAppContainer`
* **Role:** Manual Dependency Injection (Service Locator). Provides singletons so classes never use the `new` / constructor keyword directly for long-lived components.
* **Code Excerpt:**
  ```kotlin
  interface AppContainer {
      val authRepository: AuthRepository
      val userPreferencesRepository: UserPreferencesRepository
      // ...
  }

  class DefaultAppContainer(
      private val context: Context,
      private val dispatchers: DispatcherProvider = DefaultDispatcherProvider()
  ) : AppContainer {

      private val authRemoteDataSource: AuthRemoteDataSource by lazy {
          AuthRemoteDataSource()
      }

      override val authRepository: AuthRepository by lazy {
          AuthRepositoryImpl(authRemoteDataSource)
      }
  }
  ```
* **Line-by-Line Explanation:**
  * **Line 33:** Declares `val authRepository: AuthRepository` on the interface. ViewModels and Activities only know about the `AuthRepository` interface, never `AuthRepositoryImpl`.
  * **Line 61–63:** `authRemoteDataSource` is declared `by lazy`. It will only be instantiated into memory the very first time it is accessed.
  * **Line 65–67:** Instantiates `AuthRepositoryImpl`, injecting `authRemoteDataSource`. This ensures that there is only **one instance** of the remote data source holding the in-memory auth token.

---

### B. [`SmartMoneyApplication.kt`](file:///home/frank/AndroidStudioProjects/smartmoney/app/src/main/java/com/example/smartmoney/SmartMoneyApplication.kt)
* **Role:** The Android `Application` class. It is the very first code executed when the Android OS launches the process.
* **Code Excerpt:**
  ```kotlin
  class SmartMoneyApplication : Application() {
      lateinit var container: AppContainer
          private set

      override fun onCreate() {
          super.onCreate()
          container = DefaultAppContainer(this)
          NotificationHelper.createNotificationChannel(this)

          val initialUserId = container.authRepository.currentUserId()
          CoroutineScope(Dispatchers.IO).launch {
              if (!initialUserId.isNullOrBlank()) {
                  UserProfileManager.switchUser(this@SmartMoneyApplication, initialUserId)
              } else {
                  UserProfileManager.clearSession()
              }
          }
      }
  }
  ```
* **Line-by-Line Explanation:**
  * **Line 18–19:** `lateinit var container: AppContainer private set`. Creates a globally accessible dependency container while preventing external modification.
  * **Line 23:** `container = DefaultAppContainer(this)`. Initializes the DI graph before any Activities or Services are created.
  * **Line 26–33:** Reads `initialUserId`. If a user is already active, it immediately offloads avatar loading (`UserProfileManager.switchUser`) to `Dispatchers.IO` so the avatar is ready before the first UI frame draws.

---

### C. [`DispatcherProvider.kt`](file:///home/frank/AndroidStudioProjects/smartmoney/app/src/main/java/com/example/smartmoney/core/coroutine/DispatcherProvider.kt)
* **Role:** Abstraction over Kotlin Coroutines dispatchers.
* **Code Excerpt:**
  ```kotlin
  interface DispatcherProvider {
      val main: CoroutineDispatcher
      val io: CoroutineDispatcher
      val default: CoroutineDispatcher
  }
  ```
* **Why it matters for Auth:** Prevents hardcoding `Dispatchers.IO` inside `AuthRepositoryImpl`. In unit tests, we inject `TestDispatcherProvider` (`Dispatchers.Unconfined`), allowing instant, deterministic unit testing without async delays.

---

## 2. 🏛️ Domain Layer

The Domain layer contains pure business contracts with zero Android framework dependencies.

---

### [`AuthRepository.kt`](file:///home/frank/AndroidStudioProjects/smartmoney/app/src/main/java/com/example/smartmoney/domain/repository/AuthRepository.kt)
* **Role:** Defines the behavioral contract for authentication.
* **Full Code:**
  ```kotlin
  package com.example.smartmoney.domain.repository

  import kotlinx.coroutines.flow.Flow

  interface AuthRepository {
      suspend fun signUp(name: String, email: String, password: String, phoneNumber: String? = null): Result<Unit>
      suspend fun signIn(email: String, password: String): Result<Unit>
      suspend fun signOut(): Result<Unit>
      fun currentUserId(): String?
      fun currentUserName(): String?
      fun currentUserEmail(): String?
      fun currentUserPhoneNumber(): String?
      fun isUserLoggedIn(): Boolean
      fun observeAuthState(): Flow<Boolean>
  }
  ```
* **Line-by-Line Explanation:**
  * **Lines 10–12:** `suspend fun signUp(...)` and `signIn(...)` return Kotlin's standard `Result<Unit>`. This models either a successful operation or a captured `Throwable` error without requiring `try/catch` boilerplate in the presentation layer.
  * **Lines 13–16:** Synchronous getter methods returning cached user attributes for fast UI title rendering.
  * **Line 17:** `isUserLoggedIn(): Boolean` provides an immediate boolean check.
  * **Line 18:** `observeAuthState(): Flow<Boolean>` returns a cold or hot stream that emits `true` on login and `false` on logout. The UI observes this stream to trigger automatic navigation.

---

## 3. 💾 Data Layer

The Data layer implements the repository contracts, serializes network payloads, and manages local storage.

---

### A. [`AuthApi.kt`](file:///home/frank/AndroidStudioProjects/smartmoney/app/src/main/java/com/example/smartmoney/data/remote/api/AuthApi.kt)
* **Role:** Retrofit API interface and DTO definitions.
* **Code Excerpt:**
  ```kotlin
  @Serializable
  data class RegisterRequest(
      @SerialName("name") val name: String,
      @SerialName("email") val email: String,
      @SerialName("emailAddress") val emailAddress: String = email,
      @SerialName("phoneNumber") val phoneNumber: String? = null,
      @SerialName("password") val password: String
  )

  @Serializable
  data class LoginRequest(
      @SerialName("email") val email: String,
      @SerialName("emailAddress") val emailAddress: String = email,
      @SerialName("password") val password: String
  )

  @Serializable
  data class AuthResponse(
      @SerialName("id") val id: String? = null,
      @SerialName("userId") val userId: String? = null,
      @SerialName("name") val name: String? = null,
      @SerialName("email") val email: String? = null,
      @SerialName("token") val token: String? = null,
      @SerialName("accessToken") val accessToken: String? = null
  )

  interface AuthApi {
      @POST("api/auth/register")
      suspend fun register(@Body request: RegisterRequest): Response<AuthResponse>

      @POST("api/auth/login")
      suspend fun login(@Body request: LoginRequest): Response<AuthResponse>
  }
  ```
* **Line-by-Line Explanation:**
  * **Dual Fields (`email` & `emailAddress`):** Notice lines 23–26 and 41–44 populate both `email` and `emailAddress`. This defensive design ensures compatibility whether the backend deserializes via `email` or `emailAddress`.
  * **Optional Fields in `AuthResponse`:** All response properties are nullable (`String? = null`). If the backend returns either `token` or `accessToken`, or `userId` or `id`, the app safely consumes either without failing deserialization.
  * **Retrofit `@POST`:** Defines endpoints matching the Spring Boot identity service routes.

---

### B. [`AuthRemoteDataSource.kt`](file:///home/frank/AndroidStudioProjects/smartmoney/app/src/main/java/com/example/smartmoney/data/remote/datasource/AuthRemoteDataSource.kt)
* **Role:** Holds in-memory session tokens and connects to `RetrofitClient`.
* **Key Code Excerpt:**
  ```kotlin
  class AuthRemoteDataSource(
      val authApi: AuthApi = RetrofitClient.authApi
  ) {
      private val _isLoggedInFlow = MutableStateFlow(false)
      val isLoggedInFlow: Flow<Boolean> = _isLoggedInFlow.asStateFlow()

      private var currentUserId: String? = null
      private var currentUserName: String? = null
      private var currentUserEmail: String? = null
      private var authToken: String? = null

      init {
          RetrofitClient.tokenProvider = { authToken }
      }

      fun updateSession(body: AuthResponse, email: String, fallbackName: String?) {
          currentUserId = body.userId ?: body.id ?: UUID.randomUUID().toString()
          currentUserName = body.name ?: fallbackName ?: email.substringBefore('@')
          currentUserEmail = body.email ?: body.emailAddress ?: email
          authToken = body.token ?: body.accessToken
          _isLoggedInFlow.value = true
      }

      suspend fun signOut(): Result<Unit> {
          currentUserId = null
          currentUserName = null
          currentUserEmail = null
          authToken = null
          _isLoggedInFlow.value = false
          return Result.success(Unit)
      }
  }
  ```
* **Line-by-Line Explanation:**
  * **Line 33–35 (`init` block):** `RetrofitClient.tokenProvider = { authToken }`. This wires the datasource directly into OkHttp's request chain. Whenever any API call is made, OkHttp queries this lambda to obtain the latest bearer token.
  * **Line 127–138 (`updateSession`):**
    * Extracts `userId` (with a UUID fallback).
    * Extracts `currentUserName` (falling back to user portion of email if null).
    * Stores `authToken`.
    * Sets `_isLoggedInFlow.value = true`, triggering UI recomposition across the entire app.
  * **Line 100–108 (`signOut`):** Completely clears all in-memory variables and sets `_isLoggedInFlow.value = false`.

---

### C. [`AuthRepositoryImpl.kt`](file:///home/frank/AndroidStudioProjects/smartmoney/app/src/main/java/com/example/smartmoney/data/repository/AuthRepositoryImpl.kt)
* **Role:** Implements `AuthRepository`. Coordinates network execution, thread dispatching, and HTTP exception parsing.
* **Key Code Excerpt:**
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
          val mappedMessage = parseHttpException(e)
          Result.failure(Exception(mappedMessage))
      } catch (e: IOException) {
          Result.failure(Exception("Network error occurred. Please check your internet connection and try again."))
      }
  }
  ```
* **Line-by-Line Explanation:**
  * **Line 66:** `withContext(dispatchers.io)`: Ensures execution occurs on IO thread pool.
  * **Line 72–75:** Verifies `response.isSuccessful` (HTTP 200–299). If false, it throws an `HttpException` to jump into the error mapper.
  * **Line 79–81:** Catches `SocketTimeoutException`. This specifically targets free-tier cloud environments (like Render) that take up to 30 seconds to wake up from an idle spin-down.
  * **Line 111–156 (`parseHttpException`):** Inspects the raw error body JSON. If Spring Boot returns `{"message": "Invalid password"}` or `{"details": {"email": "Format invalid"}}`, this function extracts the human-readable text and presents it cleanly to the user.

---

### D. [`RetrofitClient.kt`](file:///home/frank/AndroidStudioProjects/smartmoney/app/src/main/java/com/example/smartmoney/data/remote/RetrofitClient.kt#L71-L82)
* **Role:** OkHttp Client with automatic Bearer token injection.
* **Code Excerpt:**
  ```kotlin
  private val authInterceptor = okhttp3.Interceptor { chain ->
      val original = chain.request()
      val token = tokenProvider?.invoke()
      val request = if (!token.isNullOrBlank() && original.header("Authorization") == null) {
          original.newBuilder()
              .header("Authorization", "Bearer $token")
              .build()
      } else {
          original
      }
      chain.proceed(request)
  }
  ```
* **Line-by-Line Explanation:**
  * **Line 73:** `val token = tokenProvider?.invoke()`. Evaluates the lambda provided by `AuthRemoteDataSource`.
  * **Line 74–77:** If `token` is present and the request does not already possess an `Authorization` header, it injects `"Authorization: Bearer $token"`.
  * **Result:** No other repository (Transactions, Accounts, Budgets) needs to manually manage or pass auth tokens!

---

### E. [`UserProfileManager.kt`](file:///home/frank/AndroidStudioProjects/smartmoney/app/src/main/java/com/example/smartmoney/data/local/UserProfileManager.kt)
* **Role:** Manages per-user profile pictures in internal app storage.
* **Code Excerpt:**
  ```kotlin
  fun getAvatarFile(context: Context, userId: String): File {
      return File(context.filesDir, "user_profile_${userId}_avatar.jpg")
  }

  suspend fun switchUser(
      context: Context,
      userId: String?,
      dispatcher: CoroutineDispatcher = Dispatchers.IO
  ) = withContext(dispatcher) {
      currentUserId = userId
      if (userId.isNullOrBlank()) {
          _profileBitmap.value = null
          return@withContext
      }
      val file = getAvatarFile(context, userId)
      if (file.exists() && file.length() > 0) {
          val bitmap = BitmapFactory.decodeFile(file.absolutePath)?.asImageBitmap()
          _profileBitmap.value = bitmap
      } else {
          _profileBitmap.value = null
      }
  }
  ```
* **Line-by-Line Explanation:**
  * **Line 32–34:** Scopes the filename to the user ID: `"user_profile_${userId}_avatar.jpg"`. This ensures multiple users logging into the same phone never see each other's avatars.
  * **Line 41–62:** Main-safe avatar loading on `Dispatchers.IO`. Decodes bitmap and publishes to `StateFlow<ImageBitmap?>`.

---

## 4. 🎨 UI Layer

The UI layer renders Compose screens, validates input forms, and binds to ViewModel StateFlows.

---

### A. [`AuthState.kt`](file:///home/frank/AndroidStudioProjects/smartmoney/app/src/main/java/com/example/smartmoney/ui/auth/AuthState.kt)
* **Role:** Sealed interface representing the state of the auth form.
* **Code:**
  ```kotlin
  sealed interface AuthState {
      data object Idle : AuthState
      data object Loading : AuthState
      data class Success(val message: String) : AuthState
      data class Error(val message: String) : AuthState
  }
  ```
* **Explanation:** By using a `sealed interface`, the UI can use a `when` expression to cleanly render either normal inputs (`Idle`), a spinner (`Loading`), a checkmark (`Success`), or a warning banner (`Error`).

---

### B. [`AuthViewModel.kt`](file:///home/frank/AndroidStudioProjects/smartmoney/app/src/main/java/com/example/smartmoney/ui/auth/AuthViewModel.kt)
* **Role:** Bridges Compose UI screens with the `AuthRepository`.
* **Key Code Excerpt:**
  ```kotlin
  class AuthViewModel(
      private val authRepository: AuthRepository,
      private val dispatchers: DispatcherProvider = DefaultDispatcherProvider()
  ) : ViewModel() {

      private val _authState = MutableStateFlow<AuthState>(AuthState.Idle)
      val authState: StateFlow<AuthState> = _authState.asStateFlow()

      val isLoggedIn: StateFlow<Boolean> = authRepository.observeAuthState()
          .stateIn(
              scope = viewModelScope,
              started = SharingStarted.WhileSubscribed(5000),
              initialValue = authRepository.isUserLoggedIn()
          )

      fun signIn(email: String, password: String) {
          viewModelScope.launch {
              _authState.value = AuthState.Loading
              val result = authRepository.signIn(email, password)
              result.fold(
                  onSuccess = { _authState.value = AuthState.Success("Sign in successful!") },
                  onFailure = { error -> _authState.value = AuthState.Error(error.localizedMessage) }
              )
          }
      }
  }
  ```
* **Line-by-Line Explanation:**
  * **Line 25–26:** `_authState` is private mutable, exposed as public read-only `StateFlow`.
  * **Line 31–36:** Converts `observeAuthState()` into a `StateFlow` scoped to `viewModelScope` with `WhileSubscribed(5000)`.
  * **Line 84–98:** Launches coroutine on `viewModelScope`. Handles both `onSuccess` and `onFailure` seamlessly with Kotlin's `result.fold()`.

---

### C. [`MainViewModel.kt`](file:///home/frank/AndroidStudioProjects/smartmoney/app/src/main/java/com/example/smartmoney/MainViewModel.kt)
* **Role:** Decides which screen to display when the user opens the app.
* **Key Code Excerpt:**
  ```kotlin
  val startDestination: StateFlow<String?> = combine(
      userPreferencesRepository.hasSeenOnboarding,
      authRepository.observeAuthState()
  ) { hasSeenOnboarding, isLoggedIn ->
      val destination = when {
          isLoggedIn -> "dashboard"
          !hasSeenOnboarding -> "onboarding"
          else -> "login"
      }
      delay(300)
      _isLoading.value = false
      destination
  }.stateIn(scope = viewModelScope, started = SharingStarted.WhileSubscribed(5_000), initialValue = null)
  ```
* **Line-by-Line Explanation:**
  * **Line 29–32:** Combines the onboarding SharedPreferences boolean and the authentication stream.
  * **Line 33–37:** If logged in &rarr; `"dashboard"`. If never seen onboarding &rarr; `"onboarding"`. Otherwise &rarr; `"login"`.

---

### D. [`MainActivity.kt`](file:///home/frank/AndroidStudioProjects/smartmoney/app/src/main/java/com/example/smartmoney/MainActivity.kt#L90-L135)
* **Role:** Top-level Android orchestrator.
* **Code Excerpt:**
  ```kotlin
  val isLoggedIn by authViewModel.isLoggedIn.collectAsState()

  if (isLoggedIn) {
      val currentUserId = authViewModel.currentUserId() ?: "default-user"
      val userName = authViewModel.currentUserName() ?: "User"

      LaunchedEffect(currentUserId) {
          if (currentUserId.isNotBlank()) {
              UserProfileManager.switchUser(this@MainActivity, currentUserId)
          }
      }

      // Instantiate user-scoped ViewModels (AccountViewModel, TransactionViewModel, BudgetViewModel)
      DashboardScreen(...)
  } else if (showLogin) {
      LoginScreen(...)
  } else {
      SignUpScreen(...)
  }
  ```
* **Line-by-Line Explanation:**
  * **Line 91:** Recomposes whenever `isLoggedIn` changes.
  * **Line 115–119:** `LaunchedEffect(currentUserId)` runs every time a new user logs in, ensuring their unique avatar and session are immediately loaded.
  * **Line 121–185:** Re-instantiates all sub-ViewModels with key `"$currentUserId"`. This guarantees that user B never accidentally sees cached ViewModel memory belonging to user A!

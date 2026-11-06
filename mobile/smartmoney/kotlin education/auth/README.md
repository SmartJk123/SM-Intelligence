# 🔐 Authentication System Documentation

Welcome to the comprehensive architecture and design documentation for **User Authentication** in the SmartMoney mobile application.

This documentation walks through the design, classes, request journeys, and exact code implementations across all architectural layers: **Core Layer**, **Data Layer**, **Domain Layer**, and **UI Layer**.

---

## 📑 Table of Contents

1. **[01. Architecture & End-to-End Data Flow](file:///home/frank/AndroidStudioProjects/smartmoney/kotlin%20education/auth/01_architecture_and_data_flow.md)**
   - High-level architectural diagram
   - Visual sequence diagrams for Sign In, Sign Up, App Launch, and Sign Out
   - The token propagation pipeline (how JWT tokens reach backend microservices)
2. **[02. Layer-by-Layer Code Deep Dive](file:///home/frank/AndroidStudioProjects/smartmoney/kotlin%20education/auth/02_layer_by_layer_code_deep_dive.md)**
   - Line-by-line breakdown of every authentication file and class:
     - **UI Layer**: `LoginScreen.kt`, `SignUpScreen.kt`, `AuthState.kt`, `AuthViewModel.kt`, `MainViewModel.kt`, `MainActivity.kt`
     - **Domain Layer**: `AuthRepository.kt`
     - **Data Layer**: `AuthApi.kt`, `AuthRemoteDataSource.kt`, `AuthRepositoryImpl.kt`, `RetrofitClient.kt`, `UserProfileManager.kt`, `UserPreferencesRepository.kt`
     - **Core Layer**: `AppContainer.kt`, `DefaultAppContainer.kt`, `SmartMoneyApplication.kt`, `DispatcherProvider.kt`
3. **[03. Session Management & Multi-User Security](file:///home/frank/AndroidStudioProjects/smartmoney/kotlin%20education/auth/03_session_management_and_security.md)**
   - In-memory vs persistent state
   - Per-user Room database & avatar file isolation
   - Handling Render cold starts (30-second server sleep wakeups)
   - HTTP error message parsing from Spring Boot

---

## 🏛️ Quick Map of Authentication Classes

```
smartmoney/
├── SmartMoneyApplication.kt                      ◄── [Core] Application entry & container initialization
├── MainActivity.kt                               ◄── [UI] Root orchestrator of auth state & screens
├── MainViewModel.kt                              ◄── [UI] Start destination decider (onboarding vs login vs home)
│
├── core/
│   ├── di/AppContainer.kt                        ◄── [Core] Manual Dependency Injection container
│   └── coroutine/DispatcherProvider.kt           ◄── [Core] Coroutine threading abstraction
│
├── domain/
│   └── repository/AuthRepository.kt              ◄── [Domain] Pure Kotlin contract for authentication
│
├── data/
│   ├── remote/
│   │   ├── api/AuthApi.kt                        ◄── [Data] Retrofit endpoints & DTO request/response schemas
│   │   ├── datasource/AuthRemoteDataSource.kt    ◄── [Data] In-memory session state & token provider
│   │   └── RetrofitClient.kt                     ◄── [Data] OkHttp Bearer token interceptor
│   ├── repository/AuthRepositoryImpl.kt          ◄── [Data] Repository implementation & HTTP error mapper
│   └── local/
│       ├── UserProfileManager.kt                 ◄── [Data] Per-user avatar cache & profile switcher
│       └── UserPreferencesRepository.kt          ◄── [Data] SharedPreferences for onboarding flag
│
└── ui/
    └── auth/
        ├── AuthState.kt                          ◄── [UI] Sealed UI state (Idle, Loading, Success, Error)
        ├── AuthViewModel.kt                      ◄── [UI] Exposes state flows & launches coroutines
        ├── LoginScreen.kt                        ◄── [UI] Jetpack Compose Login form & validation
        └── SignUpScreen.kt                       ◄── [UI] Jetpack Compose Registration form
```

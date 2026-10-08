# SmartMoney Android: Architecture & Engineering Educational Manual

Welcome to the **SmartMoney Android Engineering Manual**. This documentation is designed not only as an architectural specification of the codebase, but as an **in-depth educational guide** explaining the software engineering principles, patterns, and optimization strategies employed throughout the mobile application.

Whether you are onboarding as a new mobile engineer, exploring modern Android architecture with Jetpack Compose, or studying production-grade Kotlin Coroutines and reactive database pipelines, this manual provides complete transparency into how every layer functions and collaborates.

---

## 📚 Curriculum & Table of Contents

The documentation is organized into six specialized modules:

### [Module 1: Architecture & Software Design Patterns](01_architecture_and_patterns/)
* [**Clean Architecture Overview**](01_architecture_and_patterns/clean_architecture_overview.md) — The 3-tier presentation, domain, and data layer separation, dependency inversion, and business logic isolation.
* [**MVVM & Unidirectional Data Flow (UDF)**](01_architecture_and_patterns/mvvm_and_udf_design.md) — Immutable UiState contracts, single source of state, atomic UI rendering, and user event dispatching.
* [**Manual Dependency Injection**](01_architecture_and_patterns/dependency_injection_container.md) — Why manual DI (`AppContainer` & `DefaultAppContainer`) was chosen over Hilt/Dagger, dependency trees, and instant unit test swapping.

### [Module 2: Concurrency & Coroutines Optimization](02_concurrency_and_coroutines/)
* [**Coroutine Fundamentals in Android**](02_concurrency_and_coroutines/coroutine_fundamentals_in_android.md) — Structured concurrency, `viewModelScope` lifecycles, and the `DispatcherProvider` pattern (decoupling `Dispatchers.IO` for unit tests).
* [**Reactive Streams with Kotlin Flow**](02_concurrency_and_coroutines/reactive_streams_with_flow.md) — Cold database streams vs hot `StateFlow`, stream merging via `combine`, and the mechanics of `SharingStarted.WhileSubscribed(5000)`.
* [**Performance & Caching Guards**](02_concurrency_and_coroutines/performance_and_caching_guards.md) — Eliminating redundant network thrashing using `distinctUntilChanged`, freshness timestamps, and anti-thrashing cooldowns.

### [Module 3: Jetpack Compose Presentation & Performance](03_presentation_and_compose/)
* [**Compose Lifecycle & Recomposition Optimization**](03_presentation_and_compose/compose_lifecycle_and_recomposition.md) — Compose compiler runtime, skipping recompositions, `@Immutable` / `@Stable` data classes, and proper usage of `remember` and `derivedStateOf`.
* [**Custom Charts & Native Canvas Drawing**](03_presentation_and_compose/custom_charts_and_canvas_drawing.md) — Building zero-dependency financial visualizers: Cash Flow column chart, Spending Donut arc drawing, Daily Spending Heatmap grid, and the Budget Pacing bezier line graph with touch scrubber.
* [**Animations & Transitions**](03_presentation_and_compose/animations_and_transitions.md) — Physics-based springs, the Apple Genie window morph transition for Raha AI chat, blooming ripple effects, and shimmer card glow shaders.
* [**Navigation & Responsive Layout**](03_presentation_and_compose/navigation_and_responsive_layout.md) — Sealed class navigation routes, `MainResponsiveShell`, adaptive bottom bars vs sidebars for tablets and foldables.

### [Module 4: Data Layer, Persistence & Multi-Tenancy](04_data_layer_and_persistence/)
* [**Room Database & Offline-First SSOT**](04_data_layer_and_persistence/room_database_and_offline_first.md) — Single Source of Truth architecture: SQLite tables, reactive Room DAOs, entity definitions, and database type converters.
* [**Networking with Retrofit & Supabase**](04_data_layer_and_persistence/networking_retrofit_and_supabase.md) — Multi-backend networking (Spring Boot microservices on `:8080`, `:8090`, Python service on `:8091`, and Supabase Cloud), interceptors, and error handling.
* [**DTO to Domain Mapping Pipeline**](04_data_layer_and_persistence/dto_to_domain_mapping_pipeline.md) — Safe transformations from raw JSON/XML DTOs into domain models, Room entities, and UI state models.
* [**Multi-Tenant User Isolation**](04_data_layer_and_persistence/multi_tenant_user_isolation.md) — Safeguarding personal financial data: `UserProfileManager`, `userIdProvider` scoping, and database clearing during user switching or logout.

### [Module 5: Raha AI Financial Assistant](05_raha_ai_assistant/)
* [**Raha Architecture & UI Integration**](05_raha_ai_assistant/raha_architecture_and_ui.md) — The expandable floating action button, Genie sheet animation, quick actions, and chat messaging state.
* [**Privacy-Preserving Financial Context Aggregation**](05_raha_ai_assistant/ai_financial_context_aggregation.md) — Aggregating user balances, account lists, and budgets on-device to ground LLM reasoning without leaking sensitive PII.
* [**Streaming & Error Handling**](05_raha_ai_assistant/streaming_and_error_handling.md) — Handling intelligence service latency, timeouts, and intelligent heuristic fallbacks during offline operation.

### [Module 6: Component & File Relationship Map](06_component_and_file_relationships/)
* [**Comprehensive Class & File Directory Map**](06_component_and_file_relationships/comprehensive_class_and_file_map.md) — An exhaustive directory-by-directory inventory of every single Kotlin source file, its layer, duties, and collaborators.
* [**Architecture Dependency Graphs**](06_component_and_file_relationships/architecture_dependency_graph.md) — Complete visual Mermaid diagrams showing exact file-to-file couplings across presentation, domain, data, and remote layers.

---

## 🛠️ Technology Stack Summary

| Layer | Technologies & Libraries |
| :--- | :--- |
| **Language** | Kotlin 1.9+ |
| **UI Framework** | Jetpack Compose (BOM 2024.02.00), Material 3 |
| **Architecture** | Clean Architecture, MVVM, Unidirectional Data Flow (UDF) |
| **Asynchrony** | Kotlin Coroutines, Kotlin Flow, StateFlow, SharedFlow |
| **Local Database** | AndroidX Room 2.6.1 (SQLite), Encrypted / SharedPreferences |
| **Networking** | Retrofit 2.9.0, OkHttp 4.12.0, Kotlinx Serialization, Gson |
| **Cloud Backend** | Supabase Kotlin SDK (Auth, PostgREST, Storage) |
| **Bank Integration** | REST/Webhook connectors for KCB BUNI, Stanbic, NCBA, and Equity |
| **AI Intelligence** | Raha Assistant Client connecting to Local Microservice (:8091) |
| **Testing** | JUnit 4, Kotlinx Coroutines Test, MockK, AndroidX Test Core |

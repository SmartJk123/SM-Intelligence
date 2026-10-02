# Energy Android App

Energy is a modern Android application prototype built with **Kotlin** and **Jetpack Compose** for managing energy utility accounts, balances, and financial transactions (in KES).

The project follows clean architecture principles with a unidirectional data flow and an **offline-first repository pattern**:
* **Supabase / PostgreSQL** is the remote single source of truth.
* **Supabase Auth (`auth.users`)** manages all user authentication credentials. No passwords or tokens are stored in application tables or local storage.
* **Room (SQLite)** acts exclusively as a local read-through cache for offline availability.
* **BigDecimal** is used across all layers for monetary amounts (`NUMERIC(19,4)` in PostgreSQL) to prevent floating-point precision loss.

---

## 🏗 Architecture & Data Flow

```text
       UI Layer (Jetpack Compose)
                  ↓
          ViewModel Layer (StateFlow)
                  ↓
       Repository Layer (Interface)
                  ↓
 ┌──────────────────────────────────────────┐
 │ Repository Implementation (Cache-First)  │
 └────────────────────┬─────────────────────┘
         ┌────────────┴────────────┐
         ↓                         ↓
Remote Data Source         Local Cache (Room)
 (Supabase Postgrest)      (Offline Persistence)
         ↓                         ↓
  PostgreSQL / RLS               SQLite
```

### Relational Schema (PostgreSQL / Supabase)
```text
auth.users (Supabase Auth)
    │ (1:1 via trigger)
    ▼
public.users
    │ (1:N)
    ▼
public.accounts
    ├── (1:N) ──► public.account_connections
    └── (1:N) ──► public.transactions
```

---

## 📂 Project Directory Structure

```text
energy/
├── app/
│   ├── build.gradle.kts
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml
│       │   └── java/com/example/energy/
│       │       ├── MainActivity.kt
│       │       ├── data/
│       │       │   ├── local/
│       │       │   │   ├── dao/
│       │       │   │   │   ├── AccountConnectionDao.kt
│       │       │   │   │   ├── AccountDao.kt
│       │       │   │   │   ├── TransactionDao.kt
│       │       │   │   │   └── UserDao.kt
│       │       │   │   ├── database/
│       │       │   │   │   ├── AppDatabase.kt
│       │       │   │   │   └── Converters.kt
│       │       │   │   └── entity/
│       │       │   │       ├── AccountConnectionEntity.kt
│       │       │   │       ├── AccountEntity.kt
│       │       │   │       ├── TransactionEntity.kt
│       │       │   │       └── UserEntity.kt
│       │       │   ├── remote/
│       │       │   │   ├── datasource/
│       │       │   │   │   ├── AccountConnectionRemoteDataSource.kt
│       │       │   │   │   ├── AccountRemoteDataSource.kt
│       │       │   │   │   ├── AuthRemoteDataSource.kt
│       │       │   │   │   ├── TransactionRemoteDataSource.kt
│       │       │   │   │   └── UserRemoteDataSource.kt
│       │       │   │   ├── dto/
│       │       │   │   │   ├── AccountConnectionDto.kt
│       │       │   │   │   ├── AccountDto.kt
│       │       │   │   │   ├── BigDecimalSerializer.kt
│       │       │   │   │   ├── TransactionDto.kt
│       │       │   │   │   └── UserDto.kt
│       │       │   │   └── supabase/
│       │       │   │       ├── SupabaseClientProvider.kt
│       │       │   │       └── SupabaseConfig.kt
│       │       │   └── repository/
│       │       │       ├── AccountConnectionRepositoryImpl.kt
│       │       │       ├── AccountRepositoryImpl.kt
│       │       │       ├── AuthRepositoryImpl.kt
│       │       │       ├── TransactionRepositoryImpl.kt
│       │       │       └── UserRepositoryImpl.kt
│       │       ├── domain/
│       │       │   ├── model/
│       │       │   │   ├── Account.kt
│       │       │   │   ├── AccountConnection.kt
│       │       │   │   ├── Transaction.kt
│       │       │   │   └── User.kt
│       │       │   └── repository/
│       │       │       ├── AccountConnectionRepository.kt
│       │       │       ├── AccountRepository.kt
│       │       │       ├── AuthRepository.kt
│       │       │       ├── TransactionRepository.kt
│       │       │       └── UserRepository.kt
│       │       └── ui/
│       │           ├── accounts/
│       │           │   ├── AccountScreen.kt
│       │           │   └── AccountViewModel.kt
│       │           ├── auth/
│       │           │   ├── AuthState.kt
│       │           │   ├── AuthViewModel.kt
│       │           │   ├── LoginScreen.kt
│       │           │   └── SignUpScreen.kt
│       │           ├── dashboard/
│       │           │   ├── BottomMenu.kt
│       │           │   └── DashboardScreen.kt
│       │           ├── home/
│       │           │   └── HomeScreen.kt
│       │           ├── more/
│       │           │   └── MoreScreen.kt
│       │           ├── theme/
│       │           │   ├── Color.kt
│       │           │   ├── Theme.kt
│       │           │   └── Type.kt
│       │           └── transactions/
│       │               ├── TransactionScreen.kt
│       │               ├── TransactionUiState.kt
│       │               └── TransactionViewModel.kt
│       └── test/java/com/example/energy/
│           ├── ConvertersTest.kt
│           ├── ExampleUnitTest.kt
│           ├── ModelMappingTest.kt
│           └── SerializationTest.kt
├── gradle/
│   └── libs.versions.toml
├── supabase/
│   ├── migrations/
│   │   ├── 001_create_users.sql
│   │   ├── 002_create_accounts.sql
│   │   ├── 003_create_account_connections.sql
│   │   ├── 004_create_transactions.sql
│   │   └── 005_enable_rls.sql
│   └── seed/
│       ├── 001_users.sql
│       ├── 002_accounts.sql
│       └── 003_transactions.sql
├── build.gradle.kts
├── settings.gradle.kts
└── README.md
```

---

## 📄 Detailed File Descriptions & Functions

### 1. Application Entry Point
* **`MainActivity.kt`**: Main Android entry point. Sets up AndroidX Splash Screen, enables edge-to-edge UI, initializes Room `AppDatabase` and Supabase clients, instantiates repositories, and conditionally renders the authentication flow (`SignUpScreen` / `LoginScreen`) or `DashboardScreen` based on real-time auth state.

---

### 2. Presentation Layer (`ui/`)

#### Authentication (`ui/auth/`)
* **`AuthState.kt`**: Sealed interface defining authentication UI states (`Idle`, `Loading`, `Success`, `Error`).
* **`AuthViewModel.kt`**: ViewModel that drives signup, signin, and signout flows via `AuthRepository`. Emits reactive `isLoggedIn` and `uiState` flows.
* **`SignUpScreen.kt`**: Jetpack Compose screen for user registration (Name, Email, Password). Validates user input before delegating to `AuthViewModel`.
* **`LoginScreen.kt`**: Jetpack Compose screen for user login. Connects to `AuthViewModel` and navigates to the dashboard upon successful Supabase session verification.

#### Dashboard & Shell (`ui/dashboard/`)
* **`DashboardScreen.kt`**: Main container screen using Compose `HorizontalPager` to host the Home, Accounts, Transactions, and More tabs with swipe and tab-based navigation.
* **`BottomMenu.kt`**: Material 3 bottom navigation bar (`NavigationBar`) synchronized with the pager state.

#### Home Tab (`ui/home/`)
* **`HomeScreen.kt`**: Dashboard landing page. Greets the user and displays their aggregate account balance calculated from all active accounts.

#### Accounts Tab (`ui/accounts/`)
* **`AccountScreen.kt`**: Displays a list of energy and utility accounts (e.g., Prepaid Meter, Postpaid Utility) with provider details and balances.
* **`AccountViewModel.kt`**: Collects `Account` lists from `AccountRepository` as a `StateFlow` and calculates the real-time aggregate balance.

#### Transactions Tab (`ui/transactions/`)
* **`TransactionScreen.kt`**: Displays transaction history in a scrollable `LazyColumn`, rendering amount, type (credit/debit), description, and timestamp with loading and error states.
* **`TransactionUiState.kt`**: Sealed interface representing transaction UI state (`Loading`, `Success`, `Error`).
* **`TransactionViewModel.kt`**: Exposes transaction flow from `TransactionRepository` and triggers remote synchronization on launch.

#### Settings Tab (`ui/more/`)
* **`MoreScreen.kt`**: Settings and profile overview showing the current authenticated user's email address and a logout action.

#### Theming (`ui/theme/`)
* **`Color.kt`**: Brand color palette definitions.
* **`Theme.kt`**: Material Design 3 light/dark theme implementation with dynamic color support for Android 12+.
* **`Type.kt`**: Material typography configurations.

---

### 3. Domain Layer (`domain/`)

*All domain models and repository interfaces are pure Kotlin without Android, Room, or serialization annotations.*

#### Models (`domain/model/`)
* **`User.kt`**: Pure business entity representing a user profile (`id`, `name`, `emailAddress`, `phoneNumber`). Does not contain passwords.
* **`Account.kt`**: Represents an energy or financial account with `BigDecimal` monetary values (`ledgerBalance`, `availableBalance`, `creditLimit`).
* **`AccountConnection.kt`**: Represents the sync relationship between an energy account and an external provider or smart meter.
* **`Transaction.kt`**: Represents a financial/energy transaction with `BigDecimal` amount and transaction type.

#### Interfaces (`domain/repository/`)
* **`AuthRepository.kt`**: Contract for authentication operations (signup, signin, signout, session observation).
* **`UserRepository.kt`**: Contract for fetching and updating user profiles.
* **`AccountRepository.kt`**: Contract for retrieving accounts flow, remote sync, and creating accounts.
* **`AccountConnectionRepository.kt`**: Contract for external provider connections.
* **`TransactionRepository.kt`**: Contract for observing transactions flow and syncing with the remote backend.

---

### 4. Data Layer — Local Cache (`data/local/`)

*Room SQLite database used exclusively as an offline-first read-through cache.*

#### Database & Converters (`data/local/database/`)
* **`AppDatabase.kt`**: Abstract Room database definition registering `UserEntity`, `AccountEntity`, `AccountConnectionEntity`, and `TransactionEntity` with schema export enabled.
* **`Converters.kt`**: Room `@TypeConverter` methods converting `BigDecimal` to/from `String` to preserve arbitrary-precision decimals without binary rounding errors.

#### Entities (`data/local/entity/`)
* **`UserEntity.kt`**: SQLite table `users` caching profile information with UUID primary key.
* **`AccountEntity.kt`**: SQLite table `accounts` caching account balances with indexes on `userId` and `accountStatus`.
* **`AccountConnectionEntity.kt`**: SQLite table `account_connections` with index on `accountId`.
* **`TransactionEntity.kt`**: SQLite table `transactions` with indexes on `accountId` and `timestamp`.

#### DAOs (`data/local/dao/`)
* **`UserDao.kt`**: Queries for user profile retrieval and upsert.
* **`AccountDao.kt`**: Reactive queries returning `Flow<List<AccountEntity>>` by user ID and upsert operations.
* **`AccountConnectionDao.kt`**: Reactive queries for provider connection states.
* **`TransactionDao.kt`**: Reactive queries returning `Flow<List<TransactionEntity>>` sorted by timestamp descending.

---

### 5. Data Layer — Remote Supabase (`data/remote/`)

#### Configuration & Client (`data/remote/supabase/`)
* **`SupabaseConfig.kt`**: Reads Supabase URL and anon key safely from generated `BuildConfig` fields (sourced from `local.properties`).
* **`SupabaseClientProvider.kt`**: Thread-safe singleton providing a configured `SupabaseClient` with `Postgrest` and `Auth` plugins using the Ktor Android engine.

#### Data Transfer Objects (`data/remote/dto/`)
* **`BigDecimalSerializer.kt`**: Custom Kotlinx Serialization serializer parsing both numeric JSON literals (`1500.0000`) and JSON strings (`"1500.0000"`) into `BigDecimal`.
* **`UserDto.kt`**: DTO mapping PostgreSQL `public.users` to `User` domain model.
* **`AccountDto.kt`**: DTO mapping PostgreSQL `public.accounts` to `Account` domain model.
* **`AccountConnectionDto.kt`**: DTO mapping PostgreSQL `public.account_connections` to `AccountConnection` domain model.
* **`TransactionDto.kt`**: DTO mapping PostgreSQL `public.transactions` to `Transaction` domain model.

#### Data Sources (`data/remote/datasource/`)
* **`AuthRemoteDataSource.kt`**: Encapsulates Supabase GoTrue Auth calls (`signUpWith`, `signInWith`, `signOut`, `sessionStatus`).
* **`UserRemoteDataSource.kt`**: Executes Postgrest queries against `public.users`.
* **`AccountRemoteDataSource.kt`**: Executes Postgrest queries against `public.accounts`.
* **`AccountConnectionRemoteDataSource.kt`**: Executes Postgrest queries against `public.account_connections`.
* **`TransactionRemoteDataSource.kt`**: Executes Postgrest queries against `public.transactions`.

---

### 6. Data Layer — Repositories (`data/repository/`)

*Implements domain repository interfaces, coordinating remote fetches into local Room cache and exposing reactive Flows.*

* **`AuthRepositoryImpl.kt`**: Delegates authentication to `AuthRemoteDataSource` and exposes reactive session state.
* **`UserRepositoryImpl.kt`**: Reads user profile from `UserDao` as a `Flow` and synchronizes with `UserRemoteDataSource`.
* **`AccountRepositoryImpl.kt`**: Emits cached accounts from `AccountDao` as a `Flow`, syncs from Supabase, and updates Room cache.
* **`AccountConnectionRepositoryImpl.kt`**: Manages connection synchronization and Room caching.
* **`TransactionRepositoryImpl.kt`**: Emits cached transactions from `TransactionDao` as a `Flow`, syncs latest transactions from Supabase, and stores them in Room.

---

### 7. Database Migrations & Security (`supabase/`)

#### Migrations (`supabase/migrations/`)
* **`001_create_users.sql`**: Creates `public.users` with UUID PK referencing `auth.users(id) ON DELETE CASCADE`. Installs `on_auth_user_created` trigger function to automatically mirror new Supabase Auth registrations into `public.users` with metadata.
* **`002_create_accounts.sql`**: Creates `public.accounts` with `NUMERIC(19,4)` balances, uniqueness constraint on `(user_id, institution, account_id)`, and performance indexes.
* **`003_create_account_connections.sql`**: Creates `public.account_connections` referencing `public.accounts(id)`.
* **`004_create_transactions.sql`**: Creates `public.transactions` with `NUMERIC(19,4)` amounts, check constraints on transaction type, and indexes on `(account_id, timestamp DESC)`.
* **`005_enable_rls.sql`**: Enables Row Level Security (RLS) across all tables with strict tenant isolation policies (`auth.uid() = user_id` or join checks). Ensures users can only access their own records.

#### Seeds (`supabase/seed/`)
* **`001_users.sql`**: Seed templates for user accounts in local environments.
* **`002_accounts.sql`**: Sample energy meter accounts.
* **`003_transactions.sql`**: Sample token purchase transactions.

---

### 8. Unit Tests (`app/src/test/java/com/example/energy/`)

* **`ModelMappingTest.kt`**: Verifies lossless transformations between DTOs, Domain models, and Room entities. Ensures passwords are never present and `BigDecimal` precision is preserved.
* **`SerializationTest.kt`**: Tests JSON serialization and deserialization of DTOs using `BigDecimalSerializer` with both numeric and string values.
* **`ConvertersTest.kt`**: Verifies Room `Converters` round-trip serialization for `BigDecimal` values.
* **`ExampleUnitTest.kt`**: Baseline verification test.

---

## ⚙️ Configuration & Secrets Setup

Credentials must **never** be hardcoded in Kotlin source code or tracked in version control.

Add your project credentials to `local.properties`:

```properties
sdk.dir=/path/to/android/sdk
SUPABASE_URL=https://your-project.supabase.co
SUPABASE_ANON_KEY=your-anon-publishable-key
```

During build, Gradle injects these values into `BuildConfig.SUPABASE_URL` and `BuildConfig.SUPABASE_ANON_KEY`, which are consumed safely by `SupabaseConfig.kt`.

---

## 🚀 Building and Running

### 1. Run Unit Tests
```bash
./gradlew test
```

### 2. Assemble Debug APK
```bash
./gradlew assembleDebug
```

### 3. Applying Migrations to Supabase (Remote or Local CLI)
To deploy the database schema and RLS policies to your Supabase project:
```bash
# If using Supabase CLI locally:
supabase db reset

# Or push directly to linked remote project:
supabase db push
```
Alternatively, copy the SQL scripts from `supabase/migrations/001_create_users.sql` through `005_enable_rls.sql` in numerical order and run them in the **Supabase Dashboard SQL Editor**.

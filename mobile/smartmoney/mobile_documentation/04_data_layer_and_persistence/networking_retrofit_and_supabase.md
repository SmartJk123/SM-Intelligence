# Remote Networking: Retrofit Microservices & Supabase Cloud

SmartMoney connects to a hybrid backend architecture consisting of **Spring Boot microservices**, a **Python AI intelligence service**, and **Supabase Cloud**.

This document outlines the networking architecture, client configurations, interceptors, and error mitigation strategies.

---

## 1. Multi-Backend Architecture Overview

The mobile app communicates with four distinct backend interfaces:

```mermaid
graph TD
    App[SmartMoney Android App]
    Retrofit[RetrofitClient.kt]
    Supabase[SupabaseClientProvider.kt]

    App --> Retrofit
    App --> Supabase

    Retrofit -->|Core Accounts & Banking (:8080 / :8082)| BackendAccounts[Accounts Service]
    Retrofit -->|Bank Integration Gateway (:8090)| BankIntegration[Bank Integration Service\n(KCB, Stanbic, NCBA, Equity)]
    Retrofit -->|Raha AI Intelligence (:8091)| RahaIntelligence[Raha AI Python Service]
    Supabase -->|Cloud Auth & Remote Database| SupabaseCloud[Supabase Cloud Backend]
```

---

## 2. Dynamic Port Architecture: `RetrofitClient.kt`

In local development and staging environments, microservices run on separate ports on the development machine (`10.0.2.2` for Android Emulator, or the host machine's LAN IP).

[`RetrofitClient.kt`](file:///home/frank/AndroidStudioProjects/smartmoney/app/src/main/java/com/example/smartmoney/data/remote/RetrofitClient.kt) manages isolated Retrofit instances scoped to each port:

```kotlin
object RetrofitClient {
    private const val DEFAULT_HOST = "10.0.2.2" // Emulator loopback
    const val BANK_INTEGRATION_PORT = 8090
    const val RAHA_AI_PORT = 8091
    const val ACCOUNTS_PORT = 8082

    private fun buildOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY
            })
            .build()
    }

    private fun buildRetrofit(port: Int): Retrofit {
        return Retrofit.Builder()
            .baseUrl("http://$DEFAULT_HOST:$port/")
            .client(buildOkHttpClient())
            .addConverterFactory(Json {
                ignoreUnknownKeys = true
                isLenient = true
            }.asConverterFactory("application/json".toMediaType()))
            .build()
    }

    val bankIntegrationApi: BankIntegrationApi by lazy {
        buildRetrofit(BANK_INTEGRATION_PORT).create(BankIntegrationApi::class.java)
    }

    val rahaApi: RahaApi by lazy {
        buildRetrofit(RAHA_AI_PORT).create(RahaApi::class.java)
    }
}
```

### Key Networking Decisions:
* **`ignoreUnknownKeys = true`**: When the backend adds new fields to response payloads, the mobile app does not crash with deserialization errors.
* **Timeout Configuration**: 15s connection / 20s read timeout balances responsiveness against temporary mobile carrier latency.

---

## 3. Bank Integration API: `BankIntegrationApi.kt`

[`BankIntegrationApi.kt`](file:///home/frank/AndroidStudioProjects/smartmoney/app/src/main/java/com/example/smartmoney/data/remote/api/BankIntegrationApi.kt) communicates with `bank-integration-service` (:8090).

It exposes:
* **Account Linking**: `POST /api/v1/admin/account-links` with `LinkBankRequest(bankId, accountNumber, userId)`.
* **Account Querying**: `GET /api/v1/admin/account-links?userId={userId}`.
* **Bank Health**: `GET /api/v1/admin/bank-integrations/{bankId}`.
* **Simulation Triggers**: `POST /api/v1/admin/demo/transactions` to trigger real-time simulated bank deposits/withdrawals.

### Flexible Deserialization (`FlexibleStringSerializer`)
Different bank APIs and microservice endpoints return amounts inconsistently (some as JSON numbers e.g. `1000.00`, others as formatted strings e.g. `"1,000.00"`).

We handle this using a custom serializer:
```kotlin
object FlexibleStringSerializer : KSerializer<String> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("FlexibleString", PrimitiveKind.STRING)
    override fun deserialize(decoder: Decoder): String {
        return if (decoder is JsonDecoder) {
            val element = decoder.decodeJsonElement()
            if (element is JsonPrimitive) element.content else element.toString()
        } else {
            decoder.decodeString()
        }
    }
    override fun serialize(encoder: Encoder, value: String) = encoder.encodeString(value)
}
```

---

## 4. Supabase Cloud Integration

For authentication, remote transaction backups, and storage, SmartMoney integrates Supabase using the official Supabase Kotlin SDK:

```kotlin
// In SupabaseClientProvider.kt
object SupabaseClientProvider {
    private var client: SupabaseClient? = null

    fun getClient(): SupabaseClient {
        return client ?: createSupabaseClient(
            supabaseUrl = SupabaseConfig.SUPABASE_URL,
            supabaseKey = SupabaseConfig.SUPABASE_ANON_KEY
        ) {
            install(Auth)
            install(Postgrest)
            install(Storage)
        }.also { client = it }
    }
}
```

* **Remote Data Source**: `TransactionRemoteDataSource.kt` interacts with Supabase PostgREST tables to pull remote transactions when a user signs in on a new device.

package com.example.smartmoney.data.remote

import com.example.smartmoney.BuildConfig
import com.example.smartmoney.data.remote.api.AccountApi
import com.example.smartmoney.data.remote.api.ActivityApi
import com.example.smartmoney.data.remote.api.AuthApi
import com.example.smartmoney.data.remote.api.BankIntegrationApi
import com.example.smartmoney.data.remote.api.BudgetApi
import com.example.smartmoney.data.remote.api.InvoiceApi
import com.example.smartmoney.data.remote.api.RahaApi
import com.example.smartmoney.data.remote.api.TransactionApi
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Singleton Retrofit client routing 100% of mobile application network requests
 * through the unified Production oracle VM gateway running the microservices using kubernetes
 * Microservice routes via API Gateway:
 * - /api/auth/...         -> identity-service
 * - /api/accounts/...     -> accounts-service
 * - /api/transactions/... -> transactions-service
 * - /api/invoices/...     -> transactions-service (invoices)
 * - /api/budgets/...      -> budgets-service
 * - /api/assistant/...    -> assistant-service (Raha)
 * - /api/v1/admin/...     -> bank-integration-service
 */
object RetrofitClient {

    /**
     * Default oracle VM Gateway URL used across production environments.
     */
    const val DEFAULT_GATEWAY_URL: String = "http://145.241.250.59/"

    var host: String = "127.0.0.1"

    /**
     * Resolves the active base URL from BuildConfig (injected from local.properties),
     * defaulting to the live Render Gateway.
     */
    val resolvedGatewayUrl: String = try {
        val field = BuildConfig::class.java.getField("API_BASE_URL")
        (field.get(null) as? String)?.takeIf { it.isNotBlank() } ?: DEFAULT_GATEWAY_URL
    } catch (_: Throwable) {
        DEFAULT_GATEWAY_URL
    }

    /**
     * Dynamic base URL for the unified Gateway. Can be updated at runtime.
     */
    var apiBaseUrl: String = if (resolvedGatewayUrl.endsWith("/")) resolvedGatewayUrl else "$resolvedGatewayUrl/"
        set(value) {
            field = if (value.endsWith("/")) value else "$value/"
            rebuildRetrofit()
        }

    /**
     * Provider supplying the active JWT authentication token.
     */
    var tokenProvider: (() -> String?)? = null

    /**
     * Callback invoked when an authenticated request returns 401/403 or when the active
     * token is determined to have expired client-side. Triggers reactive auto-logout.
     */
    var onSessionExpiredListener: (() -> Unit)? = null

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
        coerceInputValues = true
    }

    private val authInterceptor = okhttp3.Interceptor { chain ->
        val original = chain.request()
        val path = original.url.encodedPath
        val isAuthEndpoint = path.contains("api/auth/login") || path.contains("api/auth/register")
        val token = tokenProvider?.invoke()

        val cleanToken = token?.removePrefix("Bearer ")?.removePrefix("bearer ")?.trim()

        val request = if (!cleanToken.isNullOrBlank() && original.header("Authorization") == null) {
            original.newBuilder()
                .header("Authorization", "Bearer $cleanToken")
                .build()
        } else {
            original
        }

        val response = chain.proceed(request)

        // Reactive 401 Unauthorized Interceptor (Gap 2):
        // Triggers auto-logout only when an authenticated core service explicitly rejects the session token
        if (!isAuthEndpoint && response.code == 401) {
            onSessionExpiredListener?.invoke()
        }

        response
    }

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(authInterceptor)
        .addInterceptor(loggingInterceptor)
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private var _retrofit: Retrofit = buildRetrofitInstance(apiBaseUrl)

    private fun buildRetrofitInstance(url: String): Retrofit {
        return Retrofit.Builder()
            .baseUrl(url)
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
    }

    private fun rebuildRetrofit() {
        _retrofit = buildRetrofitInstance(apiBaseUrl)
        _authApi = null
        _accountApi = null
        _transactionApi = null
        _activityApi = null
        _budgetApi = null
        _invoiceApi = null
        _rahaApi = null
        _bankIntegrationApi = null
    }

    private var _authApi: AuthApi? = null
    private var _accountApi: AccountApi? = null
    private var _transactionApi: TransactionApi? = null
    private var _activityApi: ActivityApi? = null
    private var _budgetApi: BudgetApi? = null
    private var _invoiceApi: InvoiceApi? = null
    private var _rahaApi: RahaApi? = null
    private var _bankIntegrationApi: BankIntegrationApi? = null

    /**
     * Identity Service: /api/auth/...
     */
    val authApi: AuthApi
        get() = _authApi ?: _retrofit.create(AuthApi::class.java).also { _authApi = it }

    /**
     * Accounts Service: /api/accounts/...
     */
    val accountApi: AccountApi
        get() = _accountApi ?: _retrofit.create(AccountApi::class.java).also { _accountApi = it }

    /**
     * Transactions Service: /api/transactions/...
     */
    val transactionApi: TransactionApi
        get() = _transactionApi ?: _retrofit.create(TransactionApi::class.java).also { _transactionApi = it }

    /**
     * Activity Feed (Transactions Service): /api/transactions/activity
     */
    val activityApi: ActivityApi
        get() = _activityApi ?: _retrofit.create(ActivityApi::class.java).also { _activityApi = it }

    /**
     * Budgets Service: /api/budgets/...
     */
    val budgetApi: BudgetApi
        get() = _budgetApi ?: _retrofit.create(BudgetApi::class.java).also { _budgetApi = it }

    /**
     * Invoices (Transactions Service): /api/invoices/...
     */
    val invoiceApi: InvoiceApi
        get() = _invoiceApi ?: _retrofit.create(InvoiceApi::class.java).also { _invoiceApi = it }

    /**
     * Assistant Service (Raha AI): /api/assistant/...
     */
    val rahaApi: RahaApi
        get() = _rahaApi ?: _retrofit.create(RahaApi::class.java).also { _rahaApi = it }

    /**
     * Bank Integration Service: /api/v1/admin/... and webhooks
     */
    val bankIntegrationApi: BankIntegrationApi
        get() = _bankIntegrationApi ?: _retrofit.create(BankIntegrationApi::class.java).also { _bankIntegrationApi = it }
}

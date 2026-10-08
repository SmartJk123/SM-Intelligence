package com.example.smartmoney.core.di

import android.content.Context
import com.example.smartmoney.core.coroutine.DefaultDispatcherProvider
import com.example.smartmoney.core.coroutine.DispatcherProvider
import com.example.smartmoney.data.local.UserPreferencesRepository
import com.example.smartmoney.data.local.database.AppDatabase
import com.example.smartmoney.data.remote.datasource.AccountRemoteDataSource
import com.example.smartmoney.data.remote.datasource.AuthRemoteDataSource
import com.example.smartmoney.data.remote.datasource.TransactionRemoteDataSource
import com.example.smartmoney.data.remote.supabase.SupabaseClientProvider
import com.example.smartmoney.data.repository.AccountRepositoryImpl
import com.example.smartmoney.data.repository.AuthRepositoryImpl
import com.example.smartmoney.data.repository.BankAccountRepositoryImpl
import com.example.smartmoney.data.repository.BudgetRepositoryImpl
import com.example.smartmoney.data.repository.InvestmentRepositoryImpl
import com.example.smartmoney.data.repository.NotificationRepositoryImpl
import com.example.smartmoney.data.repository.TransactionRepositoryImpl
import com.example.smartmoney.domain.repository.AccountRepository
import com.example.smartmoney.domain.repository.AuthRepository
import com.example.smartmoney.domain.repository.BankAccountRepository
import com.example.smartmoney.domain.repository.BudgetRepository
import com.example.smartmoney.domain.repository.InvestmentRepository
import com.example.smartmoney.domain.repository.NotificationRepository
import com.example.smartmoney.domain.repository.TransactionRepository

/**
 * Dependency container providing application-level singletons and repository instances.
 * Establishes explicit dependency ownership outside of the Compose UI hierarchy.
 */
interface AppContainer {
    val database: AppDatabase
    val authRepository: AuthRepository
    val accountRepository: AccountRepository
    val bankAccountRepository: BankAccountRepository
    val transactionRepository: TransactionRepository
    val notificationRepository: NotificationRepository
    val budgetRepository: BudgetRepository
    val investmentRepository: InvestmentRepository
    val userPreferencesRepository: UserPreferencesRepository
    val rahaRepository: com.example.smartmoney.domain.repository.RahaRepository

    /**
     * Creates or provides a [BankAccountRepository] scoped to a specific user ID.
     */
    fun getBankAccountRepository(userId: String): BankAccountRepository
}

/**
 * Default implementation of [AppContainer] managing long-lived application dependencies.
 */
class DefaultAppContainer(
    private val context: Context,
    private val dispatchers: DispatcherProvider = DefaultDispatcherProvider()
) : AppContainer {

    override val database: AppDatabase by lazy {
        AppDatabase.getDatabase(context)
    }

    private val authRemoteDataSource: AuthRemoteDataSource by lazy {
        AuthRemoteDataSource()
    }

    override val authRepository: AuthRepository by lazy {
        AuthRepositoryImpl(authRemoteDataSource)
    }

    private val accountRemoteDataSource: AccountRemoteDataSource by lazy {
        AccountRemoteDataSource()
    }

    override val accountRepository: AccountRepository by lazy {
        AccountRepositoryImpl(
            remoteDataSource = accountRemoteDataSource,
            localDao = database.accountDao(),
            dispatchers = dispatchers
        )
    }

    override val bankAccountRepository: BankAccountRepository by lazy {
        BankAccountRepositoryImpl(
            remoteDataSource = accountRemoteDataSource,
            localDao = database.accountDao(),
            userIdProvider = { authRepository.currentUserId() ?: "default-user" },
            dispatchers = dispatchers
        )
    }

    override fun getBankAccountRepository(userId: String): BankAccountRepository {
        return BankAccountRepositoryImpl(
            remoteDataSource = accountRemoteDataSource,
            localDao = database.accountDao(),
            userId = userId,
            dispatchers = dispatchers
        )
    }

    override val notificationRepository: NotificationRepository by lazy {
        NotificationRepositoryImpl(
            notificationDao = database.notificationDao(),
            userIdProvider = { authRepository.currentUserId() },
            dispatchers = dispatchers
        )
    }

    override val transactionRepository: TransactionRepository by lazy {
        TransactionRepositoryImpl(
            remoteDataSource = TransactionRemoteDataSource(SupabaseClientProvider.getClient()),
            localDao = database.transactionDao(),
            accountDao = database.accountDao(),
            notificationDao = database.notificationDao(),
            userIdProvider = { authRepository.currentUserId() },
            context = context,
            dispatchers = dispatchers
        )
    }

    override val budgetRepository: BudgetRepository by lazy {
        BudgetRepositoryImpl()
    }

    override val investmentRepository: InvestmentRepository by lazy {
        InvestmentRepositoryImpl()
    }

    override val userPreferencesRepository: UserPreferencesRepository by lazy {
        UserPreferencesRepository(context)
    }

    override val rahaRepository: com.example.smartmoney.domain.repository.RahaRepository by lazy {
        com.example.smartmoney.data.repository.RahaRepositoryImpl(
            rahaApi = com.example.smartmoney.data.remote.RetrofitClient.rahaApi,
            accountDao = database.accountDao(),
            userIdProvider = { authRepository.currentUserId() },
            investmentRepository = investmentRepository,
            dispatchers = dispatchers
        )
    }
}

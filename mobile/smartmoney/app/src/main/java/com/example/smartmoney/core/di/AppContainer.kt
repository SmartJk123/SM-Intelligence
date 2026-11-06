package com.example.smartmoney.core.di

import android.content.Context
import com.example.smartmoney.core.coroutine.DefaultDispatcherProvider
import com.example.smartmoney.core.coroutine.DispatcherProvider
import com.example.smartmoney.data.local.UserPreferencesRepository
import com.example.smartmoney.data.local.database.AppDatabase
import com.example.smartmoney.data.remote.RetrofitClient
import com.example.smartmoney.data.remote.datasource.AccountRemoteDataSource
import com.example.smartmoney.data.remote.datasource.AuthRemoteDataSource
import com.example.smartmoney.data.remote.datasource.TransactionRemoteDataSource
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
    val mpesaTransactionIngestionService: com.example.smartmoney.core.sms.ingestion.MpesaTransactionIngestionService

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

    override val userPreferencesRepository: UserPreferencesRepository by lazy {
        UserPreferencesRepository(context)
    }

    private val authRemoteDataSource: AuthRemoteDataSource by lazy {
        AuthRemoteDataSource(
            authApi = RetrofitClient.authApi,
            userPreferencesRepository = userPreferencesRepository
        )
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
            transactionDao = database.transactionDao(),
            userIdProvider = { authRepository.currentUserId() },
            dispatchers = dispatchers
        )
    }

    override val bankAccountRepository: BankAccountRepository by lazy {
        BankAccountRepositoryImpl(
            remoteDataSource = accountRemoteDataSource,
            localDao = database.accountDao(),
            transactionDao = database.transactionDao(),
            userIdProvider = { authRepository.currentUserId() ?: "default-user" },
            dispatchers = dispatchers
        )
    }

    override fun getBankAccountRepository(userId: String): BankAccountRepository {
        return BankAccountRepositoryImpl(
            remoteDataSource = accountRemoteDataSource,
            localDao = database.accountDao(),
            transactionDao = database.transactionDao(),
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
            remoteDataSource = TransactionRemoteDataSource(RetrofitClient.transactionApi),
            localDao = database.transactionDao(),
            accountDao = database.accountDao(),
            notificationDao = database.notificationDao(),
            userIdProvider = { authRepository.currentUserId() },
            context = context,
            dispatchers = dispatchers
        )
    }

    override val budgetRepository: BudgetRepository by lazy {
        BudgetRepositoryImpl(
            context = context,
            budgetApi = RetrofitClient.budgetApi,
            userIdProvider = { authRepository.currentUserId() },
            dispatchers = dispatchers
        )
    }

    override val investmentRepository: InvestmentRepository by lazy {
        InvestmentRepositoryImpl(
            context = context,
            userIdProvider = { authRepository.currentUserId() }
        )
    }

    override val rahaRepository: com.example.smartmoney.domain.repository.RahaRepository by lazy {
        com.example.smartmoney.data.repository.RahaRepositoryImpl(
            rahaApi = RetrofitClient.rahaApi,
            accountDao = database.accountDao(),
            userIdProvider = { authRepository.currentUserId() },
            investmentRepository = investmentRepository,
            dispatchers = dispatchers
        )
    }

    override val mpesaTransactionIngestionService: com.example.smartmoney.core.sms.ingestion.MpesaTransactionIngestionService by lazy {
        com.example.smartmoney.core.sms.ingestion.MpesaTransactionIngestionService(
            transactionDao = database.transactionDao(),
            accountDao = database.accountDao(),
            userIdProvider = { authRepository.currentUserId() },
            dispatchers = dispatchers
        )
    }
}

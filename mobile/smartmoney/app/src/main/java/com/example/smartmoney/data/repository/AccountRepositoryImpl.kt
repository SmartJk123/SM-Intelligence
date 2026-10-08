package com.example.smartmoney.data.repository

import com.example.smartmoney.core.coroutine.DefaultDispatcherProvider
import com.example.smartmoney.core.coroutine.DispatcherProvider
import com.example.smartmoney.data.local.dao.AccountDao
import com.example.smartmoney.data.local.entity.AccountEntity
import com.example.smartmoney.data.remote.api.CreateAccountRequest
import com.example.smartmoney.data.remote.datasource.AccountRemoteDataSource
import com.example.smartmoney.domain.model.Account
import com.example.smartmoney.domain.repository.AccountRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.math.BigDecimal

class AccountRepositoryImpl(
    private val remoteDataSource: AccountRemoteDataSource,
    private val localDao: AccountDao,
    private val dispatchers: DispatcherProvider = DefaultDispatcherProvider()
) : AccountRepository {

    override fun getAccountsFlow(userId: String): Flow<List<Account>> {
        return localDao.getAccountsForUser(userId)
            .map { list -> list.map { it.toDomain() } }
            .flowOn(dispatchers.default)
    }

    override suspend fun syncAccounts(userId: String): Result<Unit> = withContext(dispatchers.io) {
        try {
            val domainAccounts = remoteDataSource.fetchAccounts(userId)
            val entities = domainAccounts.map { AccountEntity.fromDomain(it) }
            localDao.upsertAccounts(entities)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun createAccount(account: Account): Result<Account> = withContext(dispatchers.io) {
        try {
            val request = CreateAccountRequest(
                userId = account.userId,
                providerAccountId = account.accountId,
                accountName = account.accountName,
                institution = account.institution,
                accountType = if (account.accountType.equals("CREDIT", ignoreCase = true)) "CREDIT" else "DEPOSIT",
                maskedIdentifier = account.maskedIdentifier ?: "**** ${account.accountId.takeLast(4)}",
                currency = account.currency,
                initialBalance = account.availableBalance,
                creditLimit = account.creditLimit ?: BigDecimal.ZERO,
                dataSource = account.dataSource
            )
            val domainAccount = remoteDataSource.createAccount(request)
            localDao.upsertAccount(AccountEntity.fromDomain(domainAccount))
            Result.success(domainAccount)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun deleteAccount(id: String): Result<Unit> = withContext(dispatchers.io) {
        try {
            remoteDataSource.deleteAccount(id)
            localDao.deleteAccountById(id)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

package com.example.smartmoney.data.repository

import com.example.smartmoney.core.coroutine.DefaultDispatcherProvider
import com.example.smartmoney.core.coroutine.DispatcherProvider
import com.example.smartmoney.data.local.dao.AccountDao
import com.example.smartmoney.data.local.entity.AccountEntity
import com.example.smartmoney.data.remote.RetrofitClient
import com.example.smartmoney.data.remote.api.CreateAccountRequest
import com.example.smartmoney.data.remote.api.LinkBankRequest
import com.example.smartmoney.data.remote.datasource.AccountRemoteDataSource
import com.example.smartmoney.domain.model.Account
import com.example.smartmoney.domain.model.BankAccount
import com.example.smartmoney.domain.repository.BankAccountRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.withContext
import java.math.BigDecimal
import java.util.UUID

/**
 * Concrete implementation of [BankAccountRepository] utilizing the Spring Boot accounts-service
 * and optional local Room cache [AccountDao].
 */
class BankAccountRepositoryImpl(
    private val remoteDataSource: AccountRemoteDataSource = AccountRemoteDataSource(),
    private val localDao: AccountDao? = null,
    private val userIdProvider: () -> String,
    private val dispatchers: DispatcherProvider = DefaultDispatcherProvider()
) : BankAccountRepository {

    constructor(
        remoteDataSource: AccountRemoteDataSource = AccountRemoteDataSource(),
        localDao: AccountDao? = null,
        userId: String,
        dispatchers: DispatcherProvider = DefaultDispatcherProvider()
    ) : this(
        remoteDataSource = remoteDataSource,
        localDao = localDao,
        userIdProvider = { userId },
        dispatchers = dispatchers
    )

    private val _bankAccountsFlow = MutableStateFlow<List<BankAccount>>(emptyList())

    override fun getBankAccounts(): Flow<List<BankAccount>> {
        val currentUserId = userIdProvider()
        return if (localDao != null) {
            localDao.getAccountsForUser(currentUserId).map { list ->
                list.map { it.toBankAccount() }
            }.flowOn(dispatchers.default)
        } else {
            _bankAccountsFlow.asStateFlow()
        }
    }

    override suspend fun addBankAccount(
        bankName: String,
        accountNumber: String,
        cardType: String
    ): Result<Unit> = withContext(dispatchers.io) {
        try {
            val currentUserId = userIdProvider()
            val effectiveUserId = try {
                UUID.fromString(currentUserId).toString()
            } catch (_: Exception) {
                UUID.nameUUIDFromBytes(currentUserId.toByteArray()).toString()
            }

            val trimmedNumber = accountNumber.trim()
            val masked = if (trimmedNumber.length > 4) {
                "**** " + trimmedNumber.takeLast(4)
            } else {
                "**** $trimmedNumber"
            }

            val accountType = if (cardType.trim().equals("Credit", ignoreCase = true)) {
                "CREDIT"
            } else {
                "DEPOSIT"
            }

            // 1. If a supported bank (KCB, Stanbic, NCBA, Equity) is selected, attempt linking via bank-integration-service (:8090)
            val bankKey = bankName.trim().lowercase()
            val isSupportedBank = bankKey in listOf("kcb", "stanbic", "ncba", "equity")

            if (isSupportedBank) {
                try {
                    val generatedAccountId = UUID.randomUUID().toString()
                    val accountTitle = "${bankName.trim()} ${cardType.trim()} Account"
                    val linkReq = LinkBankRequest(
                        bankId = bankKey,
                        accountNumber = trimmedNumber,
                        userId = effectiveUserId,
                        accountName = accountTitle,
                        accountId = generatedAccountId,
                        cardType = cardType.trim()
                    )
                    val response = RetrofitClient.bankIntegrationApi.linkAccount(linkReq)
                    if (response.isSuccessful && response.body() != null) {
                        val body = response.body()!!
                        val accountId = body.accountId?.ifBlank { null } ?: generatedAccountId
                        val initialBalance = BigDecimal.ZERO
                        val linkedAccount = BankAccount(
                            id = accountId,
                            bankName = bankName.trim(),
                            accountNumber = body.accountNumber.ifBlank { trimmedNumber },
                            cardType = cardType.trim(),
                            balance = initialBalance
                        )
                        if (localDao != null) {
                            val entity = AccountEntity(
                                id = accountId,
                                userId = effectiveUserId,
                                accountId = body.accountNumber.ifBlank { trimmedNumber },
                                accountName = body.accountName ?: accountTitle,
                                institution = bankName.trim(),
                                accountType = accountType,
                                maskedIdentifier = masked,
                                currency = "KES",
                                ledgerBalance = initialBalance,
                                availableBalance = initialBalance,
                                creditOutstanding = BigDecimal.ZERO,
                                creditLimit = BigDecimal.ZERO,
                                availableCredit = BigDecimal.ZERO,
                                accountStatus = "ACTIVE",
                                connectionStatus = "CONNECTED",
                                lastUpdated = null,
                                dataSource = "BANK_API"
                            )
                            localDao.upsertAccount(entity)
                        }
                        _bankAccountsFlow.value = _bankAccountsFlow.value + linkedAccount
                        return@withContext Result.success(Unit)
                    }
                } catch (_: Exception) {
                    // Fall back to direct accounts-service creation below if bank-integration-service is unreachable
                }
            }

            // 2. Direct accounts-service fallback (MANUAL)
            val request = CreateAccountRequest(
                userId = effectiveUserId,
                providerAccountId = trimmedNumber,
                accountName = "${bankName.trim()} ${cardType.trim()} Account",
                institution = bankName.trim(),
                accountType = accountType,
                maskedIdentifier = masked,
                currency = "KES",
                initialBalance = BigDecimal.ZERO,
                creditLimit = BigDecimal.ZERO,
                dataSource = "MANUAL"
            )

            val created = try {
                remoteDataSource.createAccount(request)
            } catch (_: Exception) {
                // Offline fallback if accounts-service is unreachable or offline
                Account(
                    id = UUID.randomUUID().toString(),
                    userId = effectiveUserId,
                    accountId = trimmedNumber,
                    accountName = "${bankName.trim()} ${cardType.trim()} Account",
                    institution = bankName.trim(),
                    accountType = accountType,
                    maskedIdentifier = masked,
                    currency = "KES",
                    ledgerBalance = BigDecimal.ZERO,
                    availableBalance = BigDecimal.ZERO,
                    creditOutstanding = BigDecimal.ZERO,
                    creditLimit = BigDecimal.ZERO,
                    availableCredit = BigDecimal.ZERO,
                    accountStatus = "ACTIVE",
                    connectionStatus = "CONNECTED",
                    dataSource = "MANUAL"
                )
            }

            if (localDao != null) {
                localDao.upsertAccount(AccountEntity.fromDomain(created))
            }

            val createdBankAccount = created.toBankAccount()
            _bankAccountsFlow.value = _bankAccountsFlow.value + createdBankAccount

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun removeBankAccount(id: String): Result<Unit> = withContext(dispatchers.io) {
        try {
            // 1. Try unlinking via bank-integration-service (:8090)
            try {
                val longId = id.toLongOrNull()
                if (longId != null) {
                    RetrofitClient.bankIntegrationApi.unlinkAccount(longId.toString())
                } else {
                    val linksResponse = RetrofitClient.bankIntegrationApi.getAccountLinks()
                    if (linksResponse.isSuccessful) {
                        val matchingLink = linksResponse.body().orEmpty().find { it.accountId == id || it.accountNumber == id }
                        matchingLink?.id?.let { linkId ->
                            RetrofitClient.bankIntegrationApi.unlinkAccount(linkId.toString())
                        }
                    }
                }
            } catch (_: Exception) {
                // Ignore bank integration unlinking errors
            }

            // Fallback attempt to delete from accounts-service (:8082) if manual account
            try {
                remoteDataSource.deleteAccount(id)
            } catch (_: Exception) {
                // Best effort remote deletion
            }

            // 2. Always remove locally from Room cache and StateFlow
            if (localDao != null) {
                localDao.deleteAccountById(id)
            }
            _bankAccountsFlow.value = _bankAccountsFlow.value.filterNot { it.id == id || it.accountNumber == id }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun adjustKcbBalance(delta: BigDecimal) = withContext(dispatchers.io) {
        localDao?.adjustKcbBalance(delta)
        Unit
    }

    private fun Account.toBankAccount(): BankAccount = BankAccount(
        id = this.id,
        bankName = this.institution,
        accountNumber = this.accountId,
        cardType = if (this.accountType.equals("CREDIT", ignoreCase = true)) "Credit" else "Debit",
        balance = this.availableBalance
    )

    private fun AccountEntity.toBankAccount(): BankAccount = BankAccount(
        id = this.id,
        bankName = this.institution,
        accountNumber = this.accountId,
        cardType = if (this.accountType.equals("CREDIT", ignoreCase = true)) "Credit" else "Debit",
        balance = this.availableBalance
    )
}

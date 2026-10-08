package com.example.smartmoney.data.repository

import com.example.smartmoney.data.local.dao.AccountConnectionDao
import com.example.smartmoney.data.local.entity.AccountConnectionEntity
import com.example.smartmoney.data.remote.datasource.AccountConnectionRemoteDataSource
import com.example.smartmoney.domain.model.AccountConnection
import com.example.smartmoney.domain.repository.AccountConnectionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class AccountConnectionRepositoryImpl(
    private val remoteDataSource: AccountConnectionRemoteDataSource,
    private val localDao: AccountConnectionDao
) : AccountConnectionRepository {

    override fun getConnectionsFlow(accountId: String): Flow<List<AccountConnection>> {
        return localDao.getConnectionsForAccount(accountId).map { list ->
            list.map { it.toDomain() }
        }
    }

    override suspend fun syncConnections(accountId: String): Result<Unit> {
        return try {
            val remoteDtos = remoteDataSource.fetchConnections(accountId)
            val domainModels = remoteDtos.map { it.toDomain() }
            val entities = domainModels.map { AccountConnectionEntity.fromDomain(it) }
            localDao.upsertConnections(entities)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

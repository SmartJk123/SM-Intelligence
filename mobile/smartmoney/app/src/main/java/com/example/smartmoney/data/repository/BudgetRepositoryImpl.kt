package com.example.smartmoney.data.repository

import android.content.Context
import com.example.smartmoney.core.coroutine.DefaultDispatcherProvider
import com.example.smartmoney.core.coroutine.DispatcherProvider
import com.example.smartmoney.data.remote.RetrofitClient
import com.example.smartmoney.data.remote.api.BudgetApi
import com.example.smartmoney.data.remote.dto.BudgetBackendResponse
import com.example.smartmoney.data.remote.dto.CreateBudgetBackendRequest
import com.example.smartmoney.domain.model.Budget
import com.example.smartmoney.domain.repository.BudgetRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.math.BigDecimal
import java.util.UUID

/**
 * Concrete implementation of [BudgetRepository] providing user-scoped budget persistence
 * and remote synchronization via the Render Gateway.
 *
 * Ensures each logged-in user only sees and tracks their own budget limits and allocations.
 */
class BudgetRepositoryImpl(
    private val context: Context? = null,
    private val budgetApi: BudgetApi = RetrofitClient.budgetApi,
    private val userIdProvider: (() -> String?)? = null,
    private val dispatchers: DispatcherProvider = DefaultDispatcherProvider()
) : BudgetRepository {

    private val prefs by lazy {
        context?.getSharedPreferences("smartmoney_budgets_prefs", Context.MODE_PRIVATE)
    }

    // Initialized to empty list: zero hardcoded / demo budgets
    private val _budgetsFlow = MutableStateFlow<List<Budget>>(emptyList())

    override fun getBudgets(): Flow<List<Budget>> = _budgetsFlow.asStateFlow()

    override fun getBudgets(userId: String?): Flow<List<Budget>> {
        val target = userId ?: userIdProvider?.invoke()
        if (!target.isNullOrBlank()) {
            _budgetsFlow.value = loadFromStorage(target)
        }
        return _budgetsFlow.asStateFlow()
    }

    override suspend fun syncBudgets(userId: String?): Result<Unit> = withContext(dispatchers.io) {
        val targetUserId = userId ?: userIdProvider?.invoke()
        if (targetUserId.isNullOrBlank()) {
            _budgetsFlow.value = emptyList()
            return@withContext Result.success(Unit)
        }

        try {
            val response = budgetApi.getBudgets(userId = targetUserId)
            if (response.isSuccessful && response.body() != null) {
                val remoteList = response.body()!!.map { it.toDomain() }
                saveToStorage(targetUserId, remoteList)
                _budgetsFlow.value = remoteList
            } else {
                _budgetsFlow.value = loadFromStorage(targetUserId)
            }
            Result.success(Unit)
        } catch (e: Exception) {
            // Offline fallback: load cached budgets for this specific user
            _budgetsFlow.value = loadFromStorage(targetUserId)
            Result.failure(e)
        }
    }

    override suspend fun saveBudget(budget: Budget): Result<Unit> = withContext(dispatchers.io) {
        val targetUserId = userIdProvider?.invoke() ?: ""

        val currentList = loadFromStorage(targetUserId).toMutableList()
        val index = currentList.indexOfFirst { it.id == budget.id }
        val effectiveId = if (budget.id.isBlank()) UUID.randomUUID().toString() else budget.id
        val updatedBudget = budget.copy(id = effectiveId)

        if (index >= 0) {
            currentList[index] = updatedBudget
        } else {
            currentList.add(0, updatedBudget)
        }

        saveToStorage(targetUserId, currentList)
        _budgetsFlow.value = currentList

        // Remote persistence via Render Gateway
        if (targetUserId.isNotBlank()) {
            try {
                val req = CreateBudgetBackendRequest(
                    userId = targetUserId,
                    category = updatedBudget.category,
                    monthlyLimit = BigDecimal(updatedBudget.allocatedMinor).divide(BigDecimal(100))
                )
                val response = budgetApi.createBudget(req)
                if (response.isSuccessful && response.body() != null) {
                    val saved = response.body()!!.toDomain()
                    val remoteUpdated = _budgetsFlow.value.toMutableList()
                    val idx = remoteUpdated.indexOfFirst { it.id == effectiveId || it.category.equals(saved.category, ignoreCase = true) }
                    if (idx >= 0) {
                        remoteUpdated[idx] = saved
                    } else {
                        remoteUpdated.add(0, saved)
                    }
                    saveToStorage(targetUserId, remoteUpdated)
                    _budgetsFlow.value = remoteUpdated
                }
            } catch (_: Exception) {
                // Keep local optimistic update on network timeout
            }
        }

        Result.success(Unit)
    }

    override suspend fun deleteBudget(id: String): Result<Unit> = withContext(dispatchers.io) {
        val targetUserId = userIdProvider?.invoke() ?: ""
        val updated = loadFromStorage(targetUserId).filter { it.id != id }

        saveToStorage(targetUserId, updated)
        _budgetsFlow.value = updated

        try {
            budgetApi.deleteBudget(id)
        } catch (_: Exception) {
            // Tolerate network failure
        }
        Result.success(Unit)
    }

    private fun loadFromStorage(userId: String): List<Budget> {
        if (prefs == null || userId.isBlank()) return emptyList()
        val raw = prefs?.getString("budgets_$userId", null) ?: return emptyList()

        return try {
            val array = JSONArray(raw)
            val list = mutableListOf<Budget>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(Budget.fromJson(obj))
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun saveToStorage(userId: String, budgets: List<Budget>) {
        if (prefs == null || userId.isBlank()) return
        try {
            val array = JSONArray()
            for (b in budgets) {
                array.put(b.toJson())
            }
            prefs?.edit()?.putString("budgets_$userId", array.toString())?.apply()
        } catch (_: Exception) {
            // Tolerate write failures
        }
    }
}

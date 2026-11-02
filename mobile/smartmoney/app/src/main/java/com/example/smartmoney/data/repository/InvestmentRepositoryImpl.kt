package com.example.smartmoney.data.repository

import android.content.Context
import com.example.smartmoney.domain.model.Investment
import com.example.smartmoney.domain.repository.InvestmentRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import java.util.UUID

/**
 * Concrete implementation of [InvestmentRepository] providing user-scoped,
 * offline persistent storage for investment records.
 *
 * Ensures each logged-in user only sees and edits their own investment assets.
 */
class InvestmentRepositoryImpl(
    private val context: Context? = null,
    private val userIdProvider: (() -> String?)? = null
) : InvestmentRepository {

    private val prefs by lazy {
        context?.getSharedPreferences("smartmoney_investments_prefs", Context.MODE_PRIVATE)
    }

    // Initialized to empty list: zero hardcoded / demo investments
    private val _investmentsFlow = MutableStateFlow<List<Investment>>(emptyList())

    override fun getInvestments(): Flow<List<Investment>> = _investmentsFlow.asStateFlow()

    override fun getInvestments(userId: String?): Flow<List<Investment>> {
        val target = userId ?: userIdProvider?.invoke()
        if (!target.isNullOrBlank()) {
            _investmentsFlow.value = loadFromStorage(target)
        }
        return _investmentsFlow.asStateFlow()
    }

    override suspend fun loadInvestmentsForUser(userId: String?) {
        val target = userId ?: userIdProvider?.invoke()
        _investmentsFlow.value = if (!target.isNullOrBlank()) {
            loadFromStorage(target)
        } else {
            emptyList()
        }
    }

    override suspend fun saveInvestment(investment: Investment): Result<Unit> {
        val target = investment.userId.ifBlank { userIdProvider?.invoke() ?: "" }
        return saveInvestment(investment, target)
    }

    override suspend fun saveInvestment(investment: Investment, userId: String?): Result<Unit> {
        val targetUserId = userId ?: investment.userId.ifBlank { userIdProvider?.invoke() ?: "" }
        val currentList = loadFromStorage(targetUserId).toMutableList()

        val index = currentList.indexOfFirst { it.id == investment.id }
        val effectiveId = if (investment.id.isBlank()) UUID.randomUUID().toString() else investment.id
        val updated = investment.copy(id = effectiveId, userId = targetUserId)

        if (index >= 0) {
            currentList[index] = updated
        } else {
            currentList.add(0, updated)
        }

        saveToStorage(targetUserId, currentList)
        _investmentsFlow.value = currentList
        return Result.success(Unit)
    }

    override suspend fun deleteInvestment(id: String): Result<Unit> {
        val target = userIdProvider?.invoke() ?: ""
        return deleteInvestment(id, target)
    }

    override suspend fun deleteInvestment(id: String, userId: String?): Result<Unit> {
        val targetUserId = userId ?: userIdProvider?.invoke() ?: ""
        val currentList = loadFromStorage(targetUserId).filter { it.id != id }

        saveToStorage(targetUserId, currentList)
        _investmentsFlow.value = currentList
        return Result.success(Unit)
    }

    private fun loadFromStorage(userId: String): List<Investment> {
        if (prefs == null || userId.isBlank()) return emptyList()
        val raw = prefs?.getString("investments_$userId", null) ?: return emptyList()

        return try {
            val array = JSONArray(raw)
            val list = mutableListOf<Investment>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(Investment.fromJson(obj))
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun saveToStorage(userId: String, investments: List<Investment>) {
        if (prefs == null || userId.isBlank()) return
        try {
            val array = JSONArray()
            for (inv in investments) {
                array.put(inv.toJson())
            }
            prefs?.edit()?.putString("investments_$userId", array.toString())?.apply()
        } catch (_: Exception) {
            // Tolerate write failures
        }
    }
}

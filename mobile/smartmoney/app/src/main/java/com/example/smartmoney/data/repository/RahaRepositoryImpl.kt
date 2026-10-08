package com.example.smartmoney.data.repository

import com.example.smartmoney.core.coroutine.DefaultDispatcherProvider
import com.example.smartmoney.core.coroutine.DispatcherProvider
import com.example.smartmoney.core.util.CurrencyUtils
import com.example.smartmoney.data.local.dao.AccountDao
import com.example.smartmoney.data.remote.api.RahaApi
import com.example.smartmoney.data.remote.dto.RahaActionDto
import com.example.smartmoney.data.remote.dto.RahaChatRequest
import com.example.smartmoney.data.remote.dto.RahaInvestmentDto
import com.example.smartmoney.domain.model.InvestmentType
import com.example.smartmoney.domain.model.RahaAction
import com.example.smartmoney.domain.model.RahaMessage
import com.example.smartmoney.domain.model.RahaSender
import com.example.smartmoney.domain.repository.InvestmentRepository
import com.example.smartmoney.domain.repository.RahaRepository
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import java.math.BigDecimal

class RahaRepositoryImpl(
    private val rahaApi: RahaApi,
    private val accountDao: AccountDao,
    private val userIdProvider: () -> String?,
    private val investmentRepository: InvestmentRepository? = null,
    private val dispatchers: DispatcherProvider = DefaultDispatcherProvider()
) : RahaRepository {

    override suspend fun sendMessage(
        message: String,
        conversationId: String?
    ): Result<RahaMessage> = withContext(dispatchers.io) {
        // Collect local investments to provide hybrid portfolio context
        val clientInvestments = try {
            val localInvs = investmentRepository?.getInvestments()?.firstOrNull() ?: emptyList()
            localInvs.map { inv ->
                RahaInvestmentDto(
                    name = inv.name,
                    productType = inv.type.name,
                    institution = when (inv.type) {
                        InvestmentType.MONEY_MARKET -> "Sanlam Investments"
                        InvestmentType.TREASURY_BILL -> "Central Bank of Kenya"
                        InvestmentType.FIXED_DEPOSIT -> "Stanbic Bank"
                        else -> "SmartMoney Wealth"
                    },
                    amount = inv.currentValueMajor ?: inv.principalMajor,
                    returnRate = when (inv.type) {
                        InvestmentType.MONEY_MARKET -> 14.50
                        InvestmentType.TREASURY_BILL -> 15.80
                        InvestmentType.FIXED_DEPOSIT -> 12.00
                        else -> 10.00
                    },
                    maturityDate = inv.maturityDate?.toString()
                )
            }
        } catch (_: Exception) {
            emptyList()
        }

        try {
            val response = rahaApi.chat(
                RahaChatRequest(
                    message = message,
                    conversationId = conversationId,
                    clientInvestments = clientInvestments
                )
            )

            if (response.isSuccessful && response.body() != null) {
                val body = response.body()!!
                val action = body.action?.let { mapAction(it) }
                val replyMessage = RahaMessage(
                    sender = RahaSender.RAHA,
                    text = body.replyMessage,
                    action = action,
                    quickReplies = body.quickReplies,
                    conversationId = body.conversationId
                )
                return@withContext Result.success(replyMessage)
            }
        } catch (_: Exception) {
            // Network failure or backend offline -> fall through to local fallback
        }

        // Local Offline Intelligence Fallback
        val fallback = generateOfflineResponse(message)
        Result.success(fallback)
    }

    override suspend fun clearConversation(conversationId: String): Result<Unit> = withContext(dispatchers.io) {
        try {
            rahaApi.clearConversation(conversationId)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun mapAction(dto: RahaActionDto): RahaAction {
        return RahaAction(
            type = dto.type,
            targetRoute = dto.targetRoute,
            title = dto.title,
            description = dto.description,
            params = dto.params
        )
    }

    private suspend fun generateOfflineResponse(userPrompt: String): RahaMessage {
        val lower = userPrompt.trim().lowercase()
        val userId = userIdProvider() ?: "default-user"

        return when {
            lower.contains("balance") || lower.contains("money") || lower.contains("how much") -> {
                val accounts = accountDao.getAccountsForUser(userId).firstOrNull() ?: emptyList()
                val total = accounts.fold(BigDecimal.ZERO) { acc, item -> acc + item.availableBalance }
                val formattedTotal = CurrencyUtils.formatKes(total)

                val breakdown = if (accounts.isNotEmpty()) {
                    accounts.joinToString("\n") { "• ${it.accountName}: ${CurrencyUtils.formatKes(it.availableBalance)}" }
                } else {
                    "No linked bank accounts found."
                }

                RahaMessage(
                    sender = RahaSender.RAHA,
                    text = "You currently have a total of $formattedTotal across your accounts:\n\n$breakdown",
                    action = RahaAction(
                        type = "NAVIGATE",
                        targetRoute = "accounts",
                        title = "Manage Accounts",
                        description = "View your connected bank accounts"
                    ),
                    quickReplies = listOf("Check Budgets", "Recent Transactions", "Add Invoice")
                )
            }

            lower.contains("budget") || lower.contains("limit") -> {
                RahaMessage(
                    sender = RahaSender.RAHA,
                    text = "I can help you monitor your spending limits and create new budgets for groceries, dining, or bills.",
                    action = RahaAction(
                        type = "NAVIGATE",
                        targetRoute = "budgets",
                        title = "Open Budgets",
                        description = "Review your active budget limits"
                    ),
                    quickReplies = listOf("Total Balance", "Recent Transactions", "Upload Invoice")
                )
            }

            lower.contains("invoice") || lower.contains("bill") || lower.contains("receipt") -> {
                RahaMessage(
                    sender = RahaSender.RAHA,
                    text = "You can upload or scan supplier invoices to track pending company expenditures.",
                    action = RahaAction(
                        type = "NAVIGATE",
                        targetRoute = "invoice",
                        title = "Add Invoice",
                        description = "Scan or upload a new invoice document"
                    ),
                    quickReplies = listOf("Total Balance", "Check Budgets")
                )
            }

            lower.contains("transaction") || lower.contains("spent") || lower.contains("history") -> {
                RahaMessage(
                    sender = RahaSender.RAHA,
                    text = "Let's review your recent inflows and outflows across your linked banks.",
                    action = RahaAction(
                        type = "NAVIGATE",
                        targetRoute = "transactions",
                        title = "View Transactions",
                        description = "Explore categorized transaction activity"
                    ),
                    quickReplies = listOf("Total Balance", "Check Budgets")
                )
            }

            lower.contains("invest") || lower.contains("stock") || lower.contains("mmf") || lower.contains("treasury") || lower.contains("yield") || lower.contains("tbill") || lower.contains("portfolio") -> {
                val investments = try {
                    investmentRepository?.getInvestments()?.firstOrNull() ?: emptyList()
                } catch (_: Exception) {
                    emptyList()
                }

                val totalInvested = investments.fold(BigDecimal.ZERO) { acc, inv ->
                    acc + BigDecimal.valueOf(inv.currentValueMajor ?: inv.principalMajor)
                }
                val formattedTotal = CurrencyUtils.formatKes(totalInvested)
                val breakdown = if (investments.isNotEmpty()) {
                    investments.joinToString("\n") { "• ${it.name}: ${CurrencyUtils.formatKes(BigDecimal.valueOf(it.currentValueMajor ?: it.principalMajor))}" }
                } else {
                    "No active investments recorded."
                }

                RahaMessage(
                    sender = RahaSender.RAHA,
                    text = "You currently have $formattedTotal in investments:\n\n$breakdown\n\nTap below to explore your portfolio or add new funds.",
                    action = RahaAction(
                        type = "NAVIGATE",
                        targetRoute = "investments",
                        title = "View Investments",
                        description = "Track portfolio valuation and returns"
                    ),
                    quickReplies = listOf("Total Balance", "Recent Transactions", "Check Budgets")
                )
            }

            else -> {
                RahaMessage(
                    sender = RahaSender.RAHA,
                    text = "Habari! I am Raha, your SmartMoney financial assistant. I can show your balances, check budget health, or guide you directly to any feature in the app.",
                    quickReplies = listOf(
                        "What is my total balance?",
                        "Check my Budgets",
                        "View Transactions",
                        "Add an Invoice"
                    )
                )
            }
        }
    }
}

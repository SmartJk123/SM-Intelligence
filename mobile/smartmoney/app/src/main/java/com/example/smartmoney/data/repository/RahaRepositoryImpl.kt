package com.example.smartmoney.data.repository

import com.example.smartmoney.core.coroutine.DefaultDispatcherProvider
import com.example.smartmoney.core.coroutine.DispatcherProvider
import com.example.smartmoney.core.util.CurrencyUtils
import com.example.smartmoney.data.local.dao.AccountDao
import com.example.smartmoney.data.remote.api.RahaApi
import com.example.smartmoney.data.remote.dto.RahaActionDto
import com.example.smartmoney.data.remote.dto.RahaChatRequest
import com.example.smartmoney.data.remote.dto.RahaInvestmentDto
import com.example.smartmoney.data.remote.dto.RahaPageBriefingRequest
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

    private suspend fun getClientInvestments(): List<RahaInvestmentDto> {
        return try {
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
    }

    override suspend fun sendMessage(
        message: String,
        conversationId: String?,
        screenRoute: String?,
        screenTitle: String?,
        pageData: Map<String, String>
    ): Result<RahaMessage> = withContext(dispatchers.io) {
        val clientInvestments = getClientInvestments()

        try {
            val response = rahaApi.chat(
                RahaChatRequest(
                    message = message,
                    conversationId = conversationId,
                    clientInvestments = clientInvestments,
                    screenRoute = screenRoute,
                    screenTitle = screenTitle,
                    pageData = pageData
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
        val fallback = generateOfflineResponse(message, screenRoute, screenTitle, pageData)
        Result.success(fallback)
    }

    override suspend fun getPageBriefing(
        screenRoute: String,
        screenTitle: String,
        conversationId: String?,
        pageData: Map<String, String>
    ): Result<RahaMessage> = withContext(dispatchers.io) {
        val clientInvestments = getClientInvestments()

        try {
            val response = rahaApi.getPageBriefing(
                RahaPageBriefingRequest(
                    screenRoute = screenRoute,
                    screenTitle = screenTitle,
                    conversationId = conversationId,
                    pageData = pageData,
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
            // Network failure or backend offline -> fall through to local offline briefing
        }

        val offlineBriefing = generateOfflineBriefing(screenRoute, screenTitle, pageData)
        Result.success(offlineBriefing)
    }

    override suspend fun getPromptNudge(screenRoute: String): String = withContext(dispatchers.io) {
        try {
            val response = rahaApi.getNudge(screenRoute)
            if (response.isSuccessful && response.body() != null) {
                val nudge = response.body()!!["nudge"]
                if (!nudge.isNullOrBlank()) {
                    return@withContext nudge
                }
            }
        } catch (_: Exception) {
            // Backend offline or unreachable -> use local smart templates
        }

        when (screenRoute.lowercase().trim()) {
            "dashboard", "overview" -> "Ask Raha: What is my total net worth today? ✨"
            "accounts" -> "Ask Raha: Break down my bank & liquid cash ✨"
            "transactions" -> "Ask Raha: Where did most of my money go? ✨"
            "budgets" -> "Ask Raha: Am I on track with my spending limits? ✨"
            "investments" -> "Ask Raha: How are my MMF & M-Shwari yields? ✨"
            "cashflow" -> "Ask Raha: How is my monthly savings flow trending? ✨"
            "analysis", "analytics" -> "Ask Raha: How does my spending match 50/30/20? ✨"
            "reports" -> "Ask Raha: Generate my monthly financial statement ✨"
            "notifications" -> "Ask Raha: Review my recent SMS transaction alerts ✨"
            "settings" -> "Ask Raha: Is my biometric & PIN security active? ✨"
            "invoice" -> "Ask Raha: Help me scan a receipt with camera OCR ✨"
            "menu" -> "Ask Raha: Guide me to any feature in SmartMoney ✨"
            else -> "Ask Raha: Need financial insights or balance check? ✨"
        }
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

    private suspend fun generateOfflineBriefing(
        screenRoute: String,
        screenTitle: String,
        pageData: Map<String, String>
    ): RahaMessage {
        val userId = userIdProvider() ?: "default-user"
        val accounts = accountDao.getAccountsForUser(userId).firstOrNull() ?: emptyList()
        val totalLiquid = accounts.fold(BigDecimal.ZERO) { acc, item -> acc + item.availableBalance }
        val formattedLiquid = CurrencyUtils.formatKes(totalLiquid)

        val investments = try {
            investmentRepository?.getInvestments()?.firstOrNull() ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
        val totalInvested = investments.fold(BigDecimal.ZERO) { acc, inv ->
            acc + BigDecimal.valueOf(inv.currentValueMajor ?: inv.principalMajor)
        }
        val formattedInvested = CurrencyUtils.formatKes(totalInvested)

        return when (screenRoute.lowercase().trim()) {
            "dashboard", "overview" -> {
                val netWorth = CurrencyUtils.formatKes(totalLiquid + totalInvested)
                RahaMessage(
                    sender = RahaSender.RAHA,
                    text = "Habari! Here on your **Overview Dashboard**, your total portfolio stands at $netWorth ($formattedLiquid liquid cash across ${accounts.size} accounts and $formattedInvested in investments). How can I assist you today?",
                    quickReplies = listOf("What is my total balance?", "Check my Budgets", "Recent Transactions", "Review Investments")
                )
            }
            "accounts" -> {
                val accBreakdown = if (accounts.isNotEmpty()) {
                    accounts.joinToString(", ") { "${it.accountName} (${CurrencyUtils.formatKes(it.availableBalance)})" }
                } else {
                    "No linked bank accounts"
                }
                RahaMessage(
                    sender = RahaSender.RAHA,
                    text = "You are viewing your **Bank & Cash Accounts**. You have ${accounts.size} connected accounts with a combined liquid balance of $formattedLiquid:\n$accBreakdown.\n\nTap below to link another bank account or review cash inflows.",
                    action = RahaAction(
                        type = "NAVIGATE",
                        targetRoute = "accounts",
                        title = "Manage Accounts"
                    ),
                    quickReplies = listOf("Link a new account", "Check my Balance", "Recent Transactions")
                )
            }
            "transactions" -> {
                RahaMessage(
                    sender = RahaSender.RAHA,
                    text = "You are on your **Transactions Feed**. Review all categorized income, debits, and M-PESA movements. You can search transactions, filter by category, or record a receipt invoice.",
                    action = RahaAction(
                        type = "NAVIGATE",
                        targetRoute = "transactions",
                        title = "View Transactions"
                    ),
                    quickReplies = listOf("Show highest expense", "Check Budgets", "Add an Invoice")
                )
            }
            "budgets" -> {
                RahaMessage(
                    sender = RahaSender.RAHA,
                    text = "Here on your **Budgets** page, you can set and monitor spending limits for groceries, dining, utilities, and shopping to meet your monthly savings targets.",
                    action = RahaAction(
                        type = "NAVIGATE",
                        targetRoute = "budgets",
                        title = "Manage Budgets"
                    ),
                    quickReplies = listOf("Which budget is closest to limit?", "Tips to save on groceries", "Check my Balance")
                )
            }
            "investments" -> {
                val invDetails = if (investments.isNotEmpty()) {
                    investments.joinToString(", ") { "${it.name} (${CurrencyUtils.formatKes(BigDecimal.valueOf(it.currentValueMajor ?: it.principalMajor))})" }
                } else {
                    "No active holdings yet"
                }
                RahaMessage(
                    sender = RahaSender.RAHA,
                    text = "You are in your **Investments Portfolio**. Your active investments total $formattedInvested ($invDetails), earning returns up to 14.50% annual yield. Tap below to review your holdings or compare funds.",
                    action = RahaAction(
                        type = "NAVIGATE",
                        targetRoute = "investments",
                        title = "View Investments"
                    ),
                    quickReplies = listOf("Compare MMF vs T-Bills", "Check interest yields", "How do I invest more?")
                )
            }
            "cashflow" -> {
                val income = pageData["monthlyIncome"] ?: "0.00"
                val expenses = pageData["monthlyExpenses"] ?: "0.00"
                val netSavings = pageData["netSavings"] ?: "0.00"
                RahaMessage(
                    sender = RahaSender.RAHA,
                    text = "Welcome to your **Cash Flow** breakdown. This month, your recorded income is KES $income against expenses of KES $expenses, resulting in net savings of KES $netSavings. Keep your cash flow positive to accelerate your financial goals!",
                    action = RahaAction(
                        type = "NAVIGATE",
                        targetRoute = "cashflow",
                        title = "View Cash Flow"
                    ),
                    quickReplies = listOf("How can I increase cash flow?", "View Budgets", "Explore Investments")
                )
            }
            "analysis", "analytics" -> {
                RahaMessage(
                    sender = RahaSender.RAHA,
                    text = "You are viewing **Financial Analytics**. Here you can inspect your category spending distribution, identify lifestyle spending leaks, and track month-over-month trends against the 50/30/20 rule.",
                    action = RahaAction(
                        type = "NAVIGATE",
                        targetRoute = "analysis",
                        title = "View Analytics"
                    ),
                    quickReplies = listOf("Where am I spending most?", "50/30/20 rule review", "Check Budgets")
                )
            }
            "reports" -> {
                RahaMessage(
                    sender = RahaSender.RAHA,
                    text = "You are in the **Financial Reports** section. Generate comprehensive monthly and annual financial statements, tax reports, and downloadable transaction summaries.",
                    action = RahaAction(
                        type = "NAVIGATE",
                        targetRoute = "reports",
                        title = "View Reports"
                    ),
                    quickReplies = listOf("Generate monthly statement", "Download tax summary", "Check Cash Flow")
                )
            }
            "notifications" -> {
                val unread = pageData["unreadCount"] ?: "0"
                RahaMessage(
                    sender = RahaSender.RAHA,
                    text = "You are on the **Notifications** screen ($unread unread alerts). This feed captures automatic M-PESA SMS transaction parsing, bank sync alerts, and budget notifications in real time.",
                    action = RahaAction(
                        type = "NAVIGATE",
                        targetRoute = "notifications",
                        title = "View Notifications"
                    ),
                    quickReplies = listOf("Explain recent SMS alert", "Manage SMS tracking", "Check Transactions")
                )
            }
            "settings" -> {
                RahaMessage(
                    sender = RahaSender.RAHA,
                    text = "You are on the **Settings & Security** screen. Here you can configure App Lock PIN, toggle Biometric authentication, adjust session timeout, and manage automated M-PESA SMS tracking.",
                    action = RahaAction(
                        type = "NAVIGATE",
                        targetRoute = "settings",
                        title = "Security Settings"
                    ),
                    quickReplies = listOf("Change security PIN", "Adjust lock timeout", "Check M-PESA tracking")
                )
            }
            "invoice" -> {
                RahaMessage(
                    sender = RahaSender.RAHA,
                    text = "You are on the **Add Invoice** screen. You can issue client invoices with tax calculations or use your camera to scan a paper receipt with OCR auto-fill.",
                    action = RahaAction(
                        type = "NAVIGATE",
                        targetRoute = "invoice",
                        title = "Add Invoice"
                    ),
                    quickReplies = listOf("How does OCR receipt scan work?", "What VAT rate applies?", "Recent Transactions")
                )
            }
            "menu" -> {
                RahaMessage(
                    sender = RahaSender.RAHA,
                    text = "Welcome to the **Menu Hub**. From here, you have quick shortcuts to Accounts, Budgets, Investments, Cash Flow, Financial Reports, Analytics, and Security Settings.",
                    quickReplies = listOf("Open Accounts", "Check Budgets", "View Investments")
                )
            }
            else -> {
                RahaMessage(
                    sender = RahaSender.RAHA,
                    text = "You are on the **$screenTitle** screen. I can answer questions about your accounts, analyze your spending, or help guide you to any feature.",
                    quickReplies = listOf("What is my total balance?", "Check my Budgets", "Recent Transactions")
                )
            }
        }
    }

    private suspend fun generateOfflineResponse(
        userPrompt: String,
        screenRoute: String?,
        screenTitle: String?,
        pageData: Map<String, String>
    ): RahaMessage {
        val lower = userPrompt.trim().lowercase()
        val userId = userIdProvider() ?: "default-user"

        if (lower.contains("briefing") || lower.contains("explain this page") || lower.contains("what is this")) {
            return generateOfflineBriefing(screenRoute ?: "dashboard", screenTitle ?: "Overview", pageData)
        }

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

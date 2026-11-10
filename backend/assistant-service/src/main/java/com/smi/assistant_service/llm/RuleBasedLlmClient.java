package com.smi.assistant_service.llm;

import com.smi.assistant_service.dto.RahaActionDto;
import com.smi.assistant_service.dto.RahaChatResponse;
import com.smi.assistant_service.model.AccountSummary;
import com.smi.assistant_service.model.BudgetSummary;
import com.smi.assistant_service.model.FinancialContext;
import com.smi.assistant_service.model.InvestmentSummary;
import com.smi.assistant_service.model.ScreenContext;
import com.smi.assistant_service.model.TransactionSummary;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Component
public class RuleBasedLlmClient implements LlmClient {

    private final NumberFormat currencyFormat = NumberFormat.getNumberInstance(Locale.US);

    public RuleBasedLlmClient() {
        currencyFormat.setMinimumFractionDigits(0);
        currencyFormat.setMaximumFractionDigits(2);
    }

    @Override
    public boolean isConfigured() {
        return true;
    }

    @Override
    public RahaChatResponse generatePageBriefing(String conversationId, FinancialContext context) {
        String convId = conversationId != null && !conversationId.isBlank()
                ? conversationId
                : UUID.randomUUID().toString();

        ScreenContext screen = context.getScreenContext();
        String route = screen != null && screen.getRoute() != null ? screen.getRoute().toLowerCase().trim() : "dashboard";
        String name = context.getUserName() != null ? context.getUserName() : "there";
        Map<String, Object> data = screen != null && screen.getData() != null ? screen.getData() : Map.of();

        switch (route) {
            case "dashboard":
            case "overview": {
                BigDecimal liquid = context.getTotalBalance() != null ? context.getTotalBalance() : BigDecimal.ZERO;
                BigDecimal invested = context.getTotalInvestments() != null ? context.getTotalInvestments() : BigDecimal.ZERO;
                BigDecimal netWorth = liquid.add(invested);

                StringBuilder sb = new StringBuilder();
                sb.append("Habari ").append(name).append("! Here on your **Overview Dashboard**, your total portfolio stands at KES ")
                        .append(currencyFormat.format(netWorth))
                        .append(" (KES ").append(currencyFormat.format(liquid)).append(" liquid cash");
                if (invested.compareTo(BigDecimal.ZERO) > 0) {
                    sb.append(" and KES ").append(currencyFormat.format(invested)).append(" in investments");
                }
                sb.append("). ");

                if (context.getBudgets() != null && !context.getBudgets().isEmpty()) {
                    sb.append("You have ").append(context.getBudgets().size()).append(" active category budgets tracking your spending. ");
                }
                sb.append("How can I assist you with your finances today?");

                return new RahaChatResponse(
                        convId,
                        sb.toString(),
                        null,
                        List.of("Check my Balance", "My Budgets", "Recent Transactions", "Review Investments")
                );
            }

            case "accounts": {
                List<AccountSummary> accounts = context.getAccounts();
                BigDecimal total = context.getTotalBalance() != null ? context.getTotalBalance() : BigDecimal.ZERO;

                StringBuilder sb = new StringBuilder();
                sb.append("You are viewing your **Bank & Cash Accounts**. ");
                if (accounts != null && !accounts.isEmpty()) {
                    sb.append("You have ").append(accounts.size()).append(" linked ").append(accounts.size() == 1 ? "account" : "accounts")
                            .append(" with a combined liquid balance of KES ").append(currencyFormat.format(total)).append(": ");
                    List<String> accList = new ArrayList<>();
                    for (AccountSummary acc : accounts) {
                        String mask = acc.getMaskedAccountNumber() != null ? " (" + acc.getMaskedAccountNumber() + ")" : "";
                        accList.add(acc.getInstitution() + " " + acc.getAccountType() + mask + " [KES " + currencyFormat.format(acc.getBalance()) + "]");
                    }
                    sb.append(String.join(", ", accList)).append(". ");
                } else {
                    sb.append("No bank accounts are currently connected. Connect your KCB, NCBA, or mobile money accounts to automatically track balances. ");
                }
                sb.append("Would you like to link another account or check transaction history?");

                return new RahaChatResponse(
                        convId,
                        sb.toString(),
                        RahaActionDto.navigate("accounts"),
                        List.of("Link a new account", "Check my Balance", "Recent Transactions")
                );
            }

            case "transactions": {
                List<TransactionSummary> txs = context.getRecentTransactions();
                StringBuilder sb = new StringBuilder();
                sb.append("You are on your **Transactions Feed**. ");
                if (txs != null && !txs.isEmpty()) {
                    sb.append("Showing your recent account movements. Latest entries include: ");
                    List<String> recent = new ArrayList<>();
                    for (int i = 0; i < Math.min(3, txs.size()); i++) {
                        TransactionSummary tx = txs.get(i);
                        recent.add(tx.getDescription() + " (KES " + currencyFormat.format(tx.getAmount()) + ")");
                    }
                    sb.append(String.join(", ", recent)).append(". ");
                    sb.append("You can search transactions, filter by category, or record a receipt invoice.");
                } else {
                    sb.append("No recent transactions have been recorded yet. Automatic M-PESA and bank SMS sync will log transactions here in real time.");
                }

                return new RahaChatResponse(
                        convId,
                        sb.toString(),
                        RahaActionDto.navigate("transactions"),
                        List.of("Show highest expense", "Check Budgets", "Add an Invoice")
                );
            }

            case "cashflow": {
                StringBuilder sb = new StringBuilder();
                sb.append("Welcome to your **Cash Flow** breakdown. ");

                Object incomeObj = data.get("monthlyIncome");
                Object expenseObj = data.get("monthlyExpenses");
                Object netObj = data.get("netSavings");

                if (incomeObj != null && expenseObj != null) {
                    sb.append("This month, your recorded income is KES ").append(incomeObj)
                            .append(" against expenses of KES ").append(expenseObj);
                    if (netObj != null) {
                        sb.append(", giving a net savings flow of KES ").append(netObj).append(". ");
                    } else {
                        sb.append(". ");
                    }
                } else {
                    sb.append("This screen visualizes your monthly money in versus money out to help you optimize your savings rate. ");
                }
                sb.append("Maintaining a positive cash flow allows you to direct surplus funds into high-yielding Money Market Funds.");

                return new RahaChatResponse(
                        convId,
                        sb.toString(),
                        RahaActionDto.navigate("cashflow"),
                        List.of("How can I increase cash flow?", "View Budgets", "Explore Investments")
                );
            }

            case "budgets": {
                List<BudgetSummary> budgets = context.getBudgets();
                StringBuilder sb = new StringBuilder();
                sb.append("Here on your **Budgets** page, ");

                if (budgets != null && !budgets.isEmpty()) {
                    BigDecimal totalLimit = BigDecimal.ZERO;
                    BigDecimal totalSpent = BigDecimal.ZERO;
                    BudgetSummary highestSpentBudget = null;

                    for (BudgetSummary b : budgets) {
                        totalLimit = totalLimit.add(b.getLimitAmount() != null ? b.getLimitAmount() : BigDecimal.ZERO);
                        totalSpent = totalSpent.add(b.getSpentAmount() != null ? b.getSpentAmount() : BigDecimal.ZERO);
                        if (highestSpentBudget == null || (b.getSpentAmount() != null && b.getSpentAmount().compareTo(highestSpentBudget.getSpentAmount()) > 0)) {
                            highestSpentBudget = b;
                        }
                    }

                    BigDecimal remaining = totalLimit.subtract(totalSpent);
                    sb.append("you have ").append(budgets.size()).append(" active category budgets. ")
                            .append("Total allocated limit is KES ").append(currencyFormat.format(totalLimit))
                            .append(" with KES ").append(currencyFormat.format(remaining)).append(" remaining this month. ");

                    if (highestSpentBudget != null && highestSpentBudget.getLimitAmount().compareTo(BigDecimal.ZERO) > 0) {
                        sb.append("Your highest expenditure is in ").append(highestSpentBudget.getCategory())
                                .append(" (KES ").append(currencyFormat.format(highestSpentBudget.getSpentAmount())).append(" spent). ");
                    }
                    sb.append("Would you like tips on staying within limits or creating a new allocation?");
                } else {
                    sb.append("you haven't created any category budgets yet. Creating budgets for groceries, dining, and utility bills prevents overspending.");
                }

                return new RahaChatResponse(
                        convId,
                        sb.toString(),
                        RahaActionDto.navigate("budgets"),
                        List.of("Which budget is closest to limit?", "Tips to save on groceries", "Check my Balance")
                );
            }

            case "investments": {
                List<InvestmentSummary> invs = context.getInvestments();
                BigDecimal totalInv = context.getTotalInvestments() != null ? context.getTotalInvestments() : BigDecimal.ZERO;

                StringBuilder sb = new StringBuilder();
                sb.append("You are in the **Investments** portfolio. ");
                if (invs != null && !invs.isEmpty()) {
                    sb.append("You currently hold KES ").append(currencyFormat.format(totalInv))
                            .append(" across ").append(invs.size()).append(" active wealth holdings: ");
                    List<String> details = new ArrayList<>();
                    for (InvestmentSummary inv : invs) {
                        details.add(inv.getName() + " (" + inv.getProductType() + ", KES " + currencyFormat.format(inv.getAmount()) + ", " + inv.getReturnRate() + "% yield)");
                    }
                    sb.append(String.join("; ", details)).append(". ");
                    sb.append("Your M-Shwari Savings and Money Market Funds are earning steady interest. What investment questions can I answer?");
                } else {
                    sb.append("SmartMoney lets you monitor high-yield Money Market Funds (MMFs), Treasury Bills (T-Bills), and M-Shwari Savings yielding up to 14.5% annual return. Explore available products to grow your wealth.");
                }

                return new RahaChatResponse(
                        convId,
                        sb.toString(),
                        RahaActionDto.navigate("investments"),
                        List.of("Compare MMF vs T-Bills", "Check interest yields", "How do I invest more?")
                );
            }

            case "analysis":
            case "analytics": {
                return new RahaChatResponse(
                        convId,
                        "You are viewing your **Financial Analytics**. This screen highlights your expense distribution, category trends, and monthly comparisons. Tracking your top expenditure categories ensures your lifestyle conforms to the 50/30/20 financial rule.",
                        RahaActionDto.navigate("analysis"),
                        List.of("Where am I spending most?", "50/30/20 rule review", "Check Budgets")
                );
            }

            case "reports": {
                return new RahaChatResponse(
                        convId,
                        "You are in the **Financial Reports** section. Here you can generate monthly and annual account statements, tax summaries, and export income vs expense breakdowns for personal records or accounting.",
                        RahaActionDto.navigate("reports"),
                        List.of("Generate monthly statement", "Download tax summary", "Check Cash Flow")
                );
            }

            case "notifications": {
                Object unread = data.get("unreadCount");
                String unreadStr = unread != null ? unread.toString() : "0";
                return new RahaChatResponse(
                        convId,
                        "You are on the **Notifications** screen (" + unreadStr + " unread alerts). This feed captures automatic M-PESA SMS ingestion, bank transaction alerts, and budget limit notifications in real time.",
                        RahaActionDto.navigate("notifications"),
                        List.of("Explain recent SMS alert", "Manage SMS tracking", "Check Transactions")
                );
            }

            case "settings": {
                return new RahaChatResponse(
                        convId,
                        "You are on the **Settings & Security** screen. Here you can manage your App Lock PIN, toggle Biometric authentication, adjust session inactivity timeout, and configure automated M-PESA SMS transaction tracking.",
                        RahaActionDto.navigate("settings"),
                        List.of("Change security PIN", "Adjust lock timeout", "Check M-PESA tracking")
                );
            }

            case "invoice": {
                return new RahaChatResponse(
                        convId,
                        "You are on the **Add Invoice** screen. You can issue client invoices with custom items and tax calculations, or tap the Camera button to scan a paper receipt using OCR auto-fill.",
                        RahaActionDto.navigate("invoice"),
                        List.of("How does OCR receipt scan work?", "What VAT rate applies?", "Recent Transactions")
                );
            }

            case "menu": {
                return new RahaChatResponse(
                        convId,
                        "Welcome to the **Menu Hub**. From here, you have quick shortcuts to Accounts, Budgets, Investments, Cash Flow, Financial Reports, Analytics, and Security Settings.",
                        null,
                        List.of("Open Accounts", "Check Budgets", "View Investments")
                );
            }

            default: {
                String title = screen != null && screen.getTitle() != null ? screen.getTitle() : "SmartMoney";
                return new RahaChatResponse(
                        convId,
                        "You are currently on the **" + title + "** screen. I can answer questions about your accounts, analyze your spending, or help you navigate any feature.",
                        null,
                        List.of("Check my Balance", "My Budgets", "Recent Transactions")
                );
            }
        }
    }

    @Override
    public RahaChatResponse processChat(String userMessage, String conversationId, FinancialContext context) {
        String convId = conversationId != null && !conversationId.isBlank()
                ? conversationId
                : UUID.randomUUID().toString();

        String lower = userMessage != null ? userMessage.toLowerCase().trim() : "";
        String name = context.getUserName() != null ? context.getUserName() : "there";

        // Check if user is asking for page briefing or help on the active screen
        if (containsAny(lower, "page briefing", "screen info", "explain this screen", "what is this page", "summarize page", "help on this page", "what am i looking at")) {
            return generatePageBriefing(convId, context);
        }

        // 1. Balance / Liquid cash intent
        if (containsAny(lower, "balance", "how much", "liquid", "pesa", "cash", "account balance", "total")) {
            return handleBalanceQuery(convId, context);
        }

        // 2. Budget intent
        if (containsAny(lower, "budget", "spending limit", "limits", "overspend", "allocate")) {
            return handleBudgetQuery(convId, context);
        }

        // 3. Transactions / Recent spending intent
        if (containsAny(lower, "transaction", "transactions", "spend", "spent", "spending", "expense", "expenses", "statement", "bought", "cost", "paid", "history")) {
            return handleTransactionQuery(convId, context);
        }

        // 4. Investments intent
        if (containsAny(lower, "invest", "investment", "investments", "treasury", "tbill", "portfolio", "shares", "stock", "mmf", "yield")) {
            return handleInvestmentQuery(convId, context);
        }

        // 5. Invoices intent
        if (containsAny(lower, "invoice", "invoices", "due date", "unpaid", "receivable", "billing", "ocr", "receipt")) {
            return new RahaChatResponse(
                    convId,
                    "You can issue and track client invoices and scan receipts via camera OCR directly in the Invoices dashboard.",
                    RahaActionDto.navigate("invoice"),
                    List.of("View Invoices", "Check Balance", "Budgets")
            );
        }

        // 6. Transfer / Send money intent
        if (containsAny(lower, "send", "transfer", "pay", "move money")) {
            return new RahaChatResponse(
                    convId,
                    "To transfer money or link other bank accounts, open the Accounts tab.",
                    RahaActionDto.navigate("accounts"),
                    List.of("Open Accounts", "Check Balance", "Recent Transactions")
            );
        }

        // 7. Greeting intent
        if (containsAny(lower, "hi", "hello", "hey", "jambo", "habari", "mambo", "sasa", "good morning", "good evening")) {
            String balStr = currencyFormat.format(context.getTotalBalance());
            return new RahaChatResponse(
                    convId,
                    "Jambo " + name + "! I'm Raha, your SmartMoney financial assistant. Your total liquid balance is KES " + balStr + ". How can I help you today?",
                    null,
                    List.of("Check my Balance", "My Budgets", "Recent Spending")
            );
        }

        // 8. General Financial Advice / Fallback
        return new RahaChatResponse(
                convId,
                "A smart rule of thumb is the 50/30/20 rule: 50% for needs, 30% for wants, and 20% for savings and investments. Would you like me to check your accounts, review your active budgets, or inspect your investments?",
                null,
                List.of("Check my Balance", "Review Budgets", "View Investments")
        );
    }

    private RahaChatResponse handleBalanceQuery(String convId, FinancialContext context) {
        BigDecimal total = context.getTotalBalance() != null ? context.getTotalBalance() : BigDecimal.ZERO;
        String formattedTotal = currencyFormat.format(total);

        StringBuilder sb = new StringBuilder();
        sb.append("Your total liquid balance is KES ").append(formattedTotal);

        List<AccountSummary> accounts = context.getAccounts();
        if (accounts != null && !accounts.isEmpty()) {
            sb.append(" across ").append(accounts.size()).append(accounts.size() == 1 ? " account: " : " accounts: ");
            List<String> accDetails = new ArrayList<>();
            for (AccountSummary acc : accounts) {
                String mask = acc.getMaskedAccountNumber() != null ? " (" + acc.getMaskedAccountNumber() + ")" : "";
                accDetails.add(acc.getInstitution() + " " + acc.getAccountType() + mask + ": KES " + currencyFormat.format(acc.getBalance()));
            }
            sb.append(String.join(", ", accDetails)).append(".");
        } else {
            sb.append(". Connect your bank accounts to automatically track balances in real time.");
        }

        return new RahaChatResponse(
                convId,
                sb.toString(),
                RahaActionDto.navigate("accounts"),
                List.of("View Accounts", "Check Budgets", "Recent Transactions")
        );
    }

    private RahaChatResponse handleBudgetQuery(String convId, FinancialContext context) {
        List<BudgetSummary> budgets = context.getBudgets();
        if (budgets != null && !budgets.isEmpty()) {
            StringBuilder sb = new StringBuilder("You have ").append(budgets.size()).append(" active budgets: ");
            List<String> bDetails = new ArrayList<>();
            for (BudgetSummary b : budgets) {
                bDetails.add(b.getCategory() + " (Limit: KES " + currencyFormat.format(b.getLimitAmount()) + ", Remaining: KES " + currencyFormat.format(b.getRemainingAmount()) + ")");
            }
            sb.append(String.join(", ", bDetails)).append(". Stay within your allocations to meet your savings goals!");
            return new RahaChatResponse(
                    convId,
                    sb.toString(),
                    RahaActionDto.navigate("budgets"),
                    List.of("Manage Budgets", "Check Balance", "Recent Spending")
            );
        } else {
            return new RahaChatResponse(
                    convId,
                    "You haven't set up any category budgets yet. Creating budgets for groceries, utilities, and dining out helps prevent overspending.",
                    RahaActionDto.navigate("budgets"),
                    List.of("Create a Budget", "Check Balance", "Recent Expenses")
            );
        }
    }

    private RahaChatResponse handleTransactionQuery(String convId, FinancialContext context) {
        List<TransactionSummary> txs = context.getRecentTransactions();
        if (txs != null && !txs.isEmpty()) {
            StringBuilder sb = new StringBuilder("Here are your latest transactions: ");
            List<String> tDetails = new ArrayList<>();
            for (TransactionSummary tx : txs) {
                tDetails.add(tx.getDescription() + " (KES " + currencyFormat.format(tx.getAmount()) + ")");
            }
            sb.append(String.join(", ", tDetails)).append(".");
            return new RahaChatResponse(
                    convId,
                    sb.toString(),
                    RahaActionDto.navigate("transactions"),
                    List.of("View All Transactions", "Check Balance", "Budgets")
            );
        } else {
            return new RahaChatResponse(
                    convId,
                    "No recent transactions were found for this account. Tap below to see your full transaction history.",
                    RahaActionDto.navigate("transactions"),
                    List.of("Open Transactions", "Check Balance", "Budgets")
            );
        }
    }

    private RahaChatResponse handleInvestmentQuery(String convId, FinancialContext context) {
        List<InvestmentSummary> invs = context.getInvestments();
        if (invs != null && !invs.isEmpty()) {
            StringBuilder sb = new StringBuilder("You currently hold KES ")
                    .append(currencyFormat.format(context.getTotalInvestments()))
                    .append(" in investments: ");
            List<String> details = new ArrayList<>();
            for (InvestmentSummary inv : invs) {
                details.add(inv.getName() + " (KES " + currencyFormat.format(inv.getAmount()) + ", " + inv.getReturnRate() + "% yield)");
            }
            sb.append(String.join(", ", details)).append(". Tap below to review your portfolio.");
            return new RahaChatResponse(
                    convId,
                    sb.toString(),
                    RahaActionDto.navigate("investments"),
                    List.of("View Investments", "Check Balance", "Budgets")
            );
        } else {
            return new RahaChatResponse(
                    convId,
                    "SmartMoney lets you explore high-yield Money Market Funds (MMFs) and Treasury Bills with ease.",
                    RahaActionDto.navigate("investments"),
                    List.of("View Investments", "Check Balance", "Savings Tips")
            );
        }
    }

    private boolean containsAny(String input, String... keywords) {
        for (String kw : keywords) {
            if (input.contains(kw)) {
                return true;
            }
        }
        return false;
    }
}

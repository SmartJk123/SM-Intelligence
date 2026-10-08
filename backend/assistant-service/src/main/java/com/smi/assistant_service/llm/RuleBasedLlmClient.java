package com.smi.assistant_service.llm;

import com.smi.assistant_service.dto.RahaActionDto;
import com.smi.assistant_service.dto.RahaChatResponse;
import com.smi.assistant_service.model.AccountSummary;
import com.smi.assistant_service.model.BudgetSummary;
import com.smi.assistant_service.model.FinancialContext;
import com.smi.assistant_service.model.InvestmentSummary;
import com.smi.assistant_service.model.TransactionSummary;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
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
    public RahaChatResponse processChat(String userMessage, String conversationId, FinancialContext context) {
        String convId = conversationId != null && !conversationId.isBlank()
                ? conversationId
                : UUID.randomUUID().toString();

        String lower = userMessage != null ? userMessage.toLowerCase().trim() : "";
        String name = context.getUserName() != null ? context.getUserName() : "there";

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
        if (containsAny(lower, "invoice", "invoices", "due date", "unpaid", "receivable", "billing")) {
            return new RahaChatResponse(
                    convId,
                    "You can issue and track client invoices and incoming payments directly in the Invoices dashboard.",
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
        BigDecimal total = context.getTotalBalance();
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

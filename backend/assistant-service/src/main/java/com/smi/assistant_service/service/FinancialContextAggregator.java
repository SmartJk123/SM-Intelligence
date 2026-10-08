package com.smi.assistant_service.service;

import com.smi.assistant_service.dto.RahaInvestmentDto;
import com.smi.assistant_service.model.AccountSummary;
import com.smi.assistant_service.model.BudgetSummary;
import com.smi.assistant_service.model.FinancialContext;
import com.smi.assistant_service.model.InvestmentSummary;
import com.smi.assistant_service.model.TransactionSummary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class FinancialContextAggregator {

    private static final Logger log = LoggerFactory.getLogger(FinancialContextAggregator.class);

    private final RestClient restClient;
    private final String identityUrl;
    private final String accountsUrl;
    private final String transactionsUrl;
    private final String budgetsUrl;

    public FinancialContextAggregator(
            @Value("${services.identity-url:http://localhost:8081}") String identityUrl,
            @Value("${services.accounts-url:http://localhost:8082}") String accountsUrl,
            @Value("${services.transactions-url:http://localhost:8083}") String transactionsUrl,
            @Value("${services.budgets-url:http://localhost:8085}") String budgetsUrl) {
        this.identityUrl = identityUrl;
        this.accountsUrl = accountsUrl;
        this.transactionsUrl = transactionsUrl;
        this.budgetsUrl = budgetsUrl;

        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build()
        );
        factory.setReadTimeout(Duration.ofSeconds(4));
        this.restClient = RestClient.builder().requestFactory(factory).build();
    }

    public FinancialContext aggregateContext(String authorization) {
        return aggregateContext(authorization, Collections.emptyList());
    }

    public FinancialContext aggregateContext(String authorization, List<RahaInvestmentDto> clientInvestments) {
        FinancialContext context = new FinancialContext();

        String userId = null;
        String userName = "SmartMoney User";

        // 1. Identity Service: Get current user profile
        if (authorization != null && authorization.startsWith("Bearer ")) {
            try {
                Map<String, Object> profile = restClient.get()
                        .uri(identityUrl + "/api/auth/me")
                        .header("Authorization", authorization)
                        .retrieve()
                        .body(new ParameterizedTypeReference<Map<String, Object>>() {});

                if (profile != null) {
                    if (profile.get("id") != null) {
                        userId = profile.get("id").toString();
                    }
                    if (profile.get("name") != null) {
                        userName = profile.get("name").toString();
                        // Extract first name if multi-word
                        String[] parts = userName.split("\\s+");
                        if (parts.length > 0) {
                            userName = parts[0];
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("Unable to fetch user profile from identity service: {}", e.getMessage());
            }
        }
        context.setUserName(userName);

        // 2. Accounts Service: Get connected bank accounts
        List<AccountSummary> accountSummaries = new ArrayList<>();
        BigDecimal totalBalance = BigDecimal.ZERO;
        List<String> accountIds = new ArrayList<>();

        if (authorization != null && !authorization.isBlank()) {
            try {
                List<Map<String, Object>> accounts = restClient.get()
                        .uri(accountsUrl + "/api/accounts")
                        .header("Authorization", authorization)
                        .retrieve()
                        .body(new ParameterizedTypeReference<List<Map<String, Object>>>() {});

                if (accounts != null) {
                    for (Map<String, Object> acc : accounts) {
                        String institution = (String) acc.getOrDefault("institution", "Bank Account");
                        String accountType = (String) acc.getOrDefault("accountType", "CHECKING");
                        String currency = (String) acc.getOrDefault("currency", "KES");

                        BigDecimal bal = BigDecimal.ZERO;
                        Object availBal = acc.get("availableBalance");
                        Object ledgerBal = acc.get("ledgerBalance");
                        if (availBal != null) {
                            bal = new BigDecimal(availBal.toString());
                        } else if (ledgerBal != null) {
                            bal = new BigDecimal(ledgerBal.toString());
                        }

                        // Mask account number
                        String maskedId = (String) acc.get("maskedIdentifier");
                        if (maskedId == null || maskedId.isBlank()) {
                            Object rawNum = acc.get("providerAccountId");
                            if (rawNum != null && rawNum.toString().length() >= 4) {
                                String s = rawNum.toString();
                                maskedId = "•••• " + s.substring(s.length() - 4);
                            } else {
                                int hash = Math.abs(acc.getOrDefault("id", "0000").hashCode() % 9000 + 1000);
                                maskedId = "•••• " + hash;
                            }
                        }

                        accountSummaries.add(new AccountSummary(institution, accountType, bal, currency, maskedId));
                        totalBalance = totalBalance.add(bal);

                        if (acc.get("id") != null) {
                            accountIds.add(acc.get("id").toString());
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("Unable to fetch accounts from accounts service: {}", e.getMessage());
            }
        }

        context.setAccounts(accountSummaries);
        context.setTotalBalance(totalBalance);

        // 3. Transactions Service: Get recent transactions across accounts
        List<TransactionSummary> txSummaries = new ArrayList<>();
        Map<String, BigDecimal> categorySpentMap = new HashMap<>();

        for (String accId : accountIds) {
            if (txSummaries.size() >= 10) break;
            try {
                List<Map<String, Object>> txs = restClient.get()
                        .uri(transactionsUrl + "/api/transactions?accountId=" + accId)
                        .retrieve()
                        .body(new ParameterizedTypeReference<List<Map<String, Object>>>() {});

                if (txs != null) {
                    for (Map<String, Object> tx : txs) {
                        if (txSummaries.size() >= 10) break;
                        String desc = (String) tx.getOrDefault("description", "Transaction");
                        String currency = (String) tx.getOrDefault("currency", "KES");
                        String txDate = tx.get("transactionDate") != null ? tx.get("transactionDate").toString() : "";
                        BigDecimal amt = BigDecimal.ZERO;
                        if (tx.get("amount") != null) {
                            amt = new BigDecimal(tx.get("amount").toString());
                        }
                        String txType = (String) tx.getOrDefault("transactionType", "DEBIT");

                        String cat = "General";
                        if (desc.toLowerCase().contains("grocer") || desc.toLowerCase().contains("supermarket") || desc.toLowerCase().contains("naivas")) {
                            cat = "Groceries";
                        } else if (desc.toLowerCase().contains("power") || desc.toLowerCase().contains("kplc") || desc.toLowerCase().contains("water") || desc.toLowerCase().contains("bill")) {
                            cat = "Utilities";
                        } else if (desc.toLowerCase().contains("uber") || desc.toLowerCase().contains("bolt") || desc.toLowerCase().contains("fuel") || desc.toLowerCase().contains("transport")) {
                            cat = "Transport";
                        } else if (desc.toLowerCase().contains("food") || desc.toLowerCase().contains("restaurant") || desc.toLowerCase().contains("cafe") || desc.toLowerCase().contains("java")) {
                            cat = "Dining";
                        }

                        // Track spending for budget correlation (outflows / debits)
                        if (!"CREDIT".equalsIgnoreCase(txType)) {
                            categorySpentMap.merge(cat.toLowerCase(), amt, BigDecimal::add);
                        }

                        txSummaries.add(new TransactionSummary(cat, amt, currency, desc, txDate));
                    }
                }
            } catch (Exception e) {
                log.warn("Unable to fetch transactions for account {}: {}", accId, e.getMessage());
            }
        }
        context.setRecentTransactions(txSummaries);

        // 4. Budgets Service: Get user's budgets with spent calculations
        if (userId != null) {
            try {
                List<Map<String, Object>> budgets = restClient.get()
                        .uri(budgetsUrl + "/api/budgets?userId=" + userId)
                        .retrieve()
                        .body(new ParameterizedTypeReference<List<Map<String, Object>>>() {});

                if (budgets != null) {
                    List<BudgetSummary> budgetSummaries = new ArrayList<>();
                    for (Map<String, Object> b : budgets) {
                        String category = (String) b.getOrDefault("category", "General");
                        String currency = (String) b.getOrDefault("currency", "KES");
                        BigDecimal limit = BigDecimal.ZERO;
                        if (b.get("monthlyLimit") != null) {
                            limit = new BigDecimal(b.get("monthlyLimit").toString());
                        }

                        // Check spent against calculated category spent or fallback
                        BigDecimal spent = categorySpentMap.getOrDefault(category.toLowerCase(), BigDecimal.ZERO);
                        BigDecimal remaining = limit.subtract(spent);

                        budgetSummaries.add(new BudgetSummary(category, limit, spent, remaining, currency));
                    }
                    context.setBudgets(budgetSummaries);
                }
            } catch (Exception e) {
                log.warn("Unable to fetch budgets from budgets service: {}", e.getMessage());
            }
        }

        // 5. Investments: Ingest client-supplied and backend investments
        List<InvestmentSummary> investmentSummaries = new ArrayList<>();
        BigDecimal totalInvestments = BigDecimal.ZERO;

        if (clientInvestments != null && !clientInvestments.isEmpty()) {
            for (RahaInvestmentDto invDto : clientInvestments) {
                InvestmentSummary summary = new InvestmentSummary(
                        invDto.getName(),
                        invDto.getProductType(),
                        invDto.getInstitution(),
                        invDto.getAmount(),
                        invDto.getReturnRate(),
                        invDto.getMaturityDate(),
                        "KES"
                );
                investmentSummaries.add(summary);
                if (invDto.getAmount() != null) {
                    totalInvestments = totalInvestments.add(invDto.getAmount());
                }
            }
        }

        context.setInvestments(investmentSummaries);
        context.setTotalInvestments(totalInvestments);

        return context;
    }
}

package com.smi.assistant_service.model;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class FinancialContext {

    private String userName = "Customer";
    private BigDecimal totalBalance = BigDecimal.ZERO;
    private BigDecimal totalInvestments = BigDecimal.ZERO;
    private String currency = "KES";
    private List<AccountSummary> accounts = new ArrayList<>();
    private List<BudgetSummary> budgets = new ArrayList<>();
    private List<TransactionSummary> recentTransactions = new ArrayList<>();
    private List<InvestmentSummary> investments = new ArrayList<>();

    public FinancialContext() {
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public BigDecimal getTotalBalance() {
        return totalBalance;
    }

    public void setTotalBalance(BigDecimal totalBalance) {
        this.totalBalance = totalBalance != null ? totalBalance : BigDecimal.ZERO;
    }

    public BigDecimal getTotalInvestments() {
        return totalInvestments;
    }

    public void setTotalInvestments(BigDecimal totalInvestments) {
        this.totalInvestments = totalInvestments != null ? totalInvestments : BigDecimal.ZERO;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public List<AccountSummary> getAccounts() {
        return accounts;
    }

    public void setAccounts(List<AccountSummary> accounts) {
        this.accounts = accounts != null ? accounts : new ArrayList<>();
    }

    public List<BudgetSummary> getBudgets() {
        return budgets;
    }

    public void setBudgets(List<BudgetSummary> budgets) {
        this.budgets = budgets != null ? budgets : new ArrayList<>();
    }

    public List<TransactionSummary> getRecentTransactions() {
        return recentTransactions;
    }

    public void setRecentTransactions(List<TransactionSummary> recentTransactions) {
        this.recentTransactions = recentTransactions != null ? recentTransactions : new ArrayList<>();
    }

    public List<InvestmentSummary> getInvestments() {
        return investments;
    }

    public void setInvestments(List<InvestmentSummary> investments) {
        this.investments = investments != null ? investments : new ArrayList<>();
    }
}

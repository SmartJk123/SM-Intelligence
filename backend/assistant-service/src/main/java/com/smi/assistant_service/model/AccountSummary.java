package com.smi.assistant_service.model;

import java.math.BigDecimal;

public class AccountSummary {

    private String institution;
    private String accountType;
    private BigDecimal balance;
    private String currency;
    private String maskedAccountNumber;

    public AccountSummary() {
    }

    public AccountSummary(String institution, String accountType, BigDecimal balance, String currency) {
        this(institution, accountType, balance, currency, null);
    }

    public AccountSummary(String institution, String accountType, BigDecimal balance, String currency, String maskedAccountNumber) {
        this.institution = institution;
        this.accountType = accountType;
        this.balance = balance != null ? balance : BigDecimal.ZERO;
        this.currency = currency != null ? currency : "KES";
        this.maskedAccountNumber = maskedAccountNumber;
    }

    public String getInstitution() {
        return institution;
    }

    public void setInstitution(String institution) {
        this.institution = institution;
    }

    public String getAccountType() {
        return accountType;
    }

    public void setAccountType(String accountType) {
        this.accountType = accountType;
    }

    public BigDecimal getBalance() {
        return balance;
    }

    public void setBalance(BigDecimal balance) {
        this.balance = balance;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public String getMaskedAccountNumber() {
        return maskedAccountNumber;
    }

    public void setMaskedAccountNumber(String maskedAccountNumber) {
        this.maskedAccountNumber = maskedAccountNumber;
    }
}

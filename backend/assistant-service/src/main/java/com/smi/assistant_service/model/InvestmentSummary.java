package com.smi.assistant_service.model;

import java.math.BigDecimal;

public class InvestmentSummary {

    private String name;
    private String productType; // MONEY_MARKET, TREASURY_BILL, FIXED_DEPOSIT, OTHER
    private String institution;
    private BigDecimal amount;
    private BigDecimal returnRate; // Annual percentage yield e.g. 14.50
    private String maturityDate;
    private String currency;

    public InvestmentSummary() {
        this.currency = "KES";
        this.amount = BigDecimal.ZERO;
        this.returnRate = BigDecimal.ZERO;
    }

    public InvestmentSummary(String name, String productType, String institution,
                             BigDecimal amount, BigDecimal returnRate,
                             String maturityDate, String currency) {
        this.name = name;
        this.productType = productType;
        this.institution = institution;
        this.amount = amount != null ? amount : BigDecimal.ZERO;
        this.returnRate = returnRate != null ? returnRate : BigDecimal.ZERO;
        this.maturityDate = maturityDate;
        this.currency = currency != null ? currency : "KES";
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getProductType() {
        return productType;
    }

    public void setProductType(String productType) {
        this.productType = productType;
    }

    public String getInstitution() {
        return institution;
    }

    public void setInstitution(String institution) {
        this.institution = institution;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public BigDecimal getReturnRate() {
        return returnRate;
    }

    public void setReturnRate(BigDecimal returnRate) {
        this.returnRate = returnRate;
    }

    public String getMaturityDate() {
        return maturityDate;
    }

    public void setMaturityDate(String maturityDate) {
        this.maturityDate = maturityDate;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }
}

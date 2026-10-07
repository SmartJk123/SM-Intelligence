package com.smi.assistant_service.dto;

import java.math.BigDecimal;

public class RahaInvestmentDto {

    private String name;
    private String productType;
    private String institution;
    private BigDecimal amount;
    private BigDecimal returnRate;
    private String maturityDate;

    public RahaInvestmentDto() {
    }

    public RahaInvestmentDto(String name, String productType, String institution,
                             BigDecimal amount, BigDecimal returnRate, String maturityDate) {
        this.name = name;
        this.productType = productType;
        this.institution = institution;
        this.amount = amount;
        this.returnRate = returnRate;
        this.maturityDate = maturityDate;
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
}

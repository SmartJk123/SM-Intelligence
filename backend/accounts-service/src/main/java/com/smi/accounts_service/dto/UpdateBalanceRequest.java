package com.smi.accounts_service.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public class UpdateBalanceRequest {

    @NotNull(message = "availableBalance is required")
    @JsonAlias({"available_balance", "balance"})
    private BigDecimal availableBalance;

    @JsonAlias({"ledger_balance"})
    private BigDecimal ledgerBalance;

    @JsonAlias({"credit_outstanding"})
    private BigDecimal creditOutstanding;

    public UpdateBalanceRequest() {
    }

    public UpdateBalanceRequest(BigDecimal availableBalance, BigDecimal ledgerBalance, BigDecimal creditOutstanding) {
        this.availableBalance = availableBalance;
        this.ledgerBalance = ledgerBalance != null ? ledgerBalance : availableBalance;
        this.creditOutstanding = creditOutstanding != null ? creditOutstanding : BigDecimal.ZERO;
    }

    public BigDecimal getAvailableBalance() {
        return availableBalance;
    }

    public void setAvailableBalance(BigDecimal availableBalance) {
        this.availableBalance = availableBalance;
    }

    public BigDecimal getLedgerBalance() {
        return ledgerBalance;
    }

    public void setLedgerBalance(BigDecimal ledgerBalance) {
        this.ledgerBalance = ledgerBalance;
    }

    public BigDecimal getCreditOutstanding() {
        return creditOutstanding;
    }

    public void setCreditOutstanding(BigDecimal creditOutstanding) {
        this.creditOutstanding = creditOutstanding;
    }
}

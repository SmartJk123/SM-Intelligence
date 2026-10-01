package com.smi.accounts_service.dto;

import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public class AdjustBalanceRequest {

    @NotNull(message = "delta is required")
    private BigDecimal delta;

    public AdjustBalanceRequest() {
    }

    public AdjustBalanceRequest(BigDecimal delta) {
        this.delta = delta;
    }

    public BigDecimal getDelta() {
        return delta;
    }

    public void setDelta(BigDecimal delta) {
        this.delta = delta;
    }
}

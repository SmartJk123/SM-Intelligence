package io.smartmoney.api.bankintegration;

import com.fasterxml.jackson.annotation.JsonValue;

public enum TokenStatus {
    VALID,
    EXPIRING,
    EXPIRED,
    UNKNOWN;

    @JsonValue
    public String value() {
        return name();
    }
}

package io.smartmoney.api.bankintegration;

import com.fasterxml.jackson.annotation.JsonValue;

public enum IntegrationStatus {
    CONNECTED,
    HEALTHY,
    WARNING,
    ERROR,
    PENDING,
    UNKNOWN;

    @JsonValue
    public String value() {
        return name();
    }
}

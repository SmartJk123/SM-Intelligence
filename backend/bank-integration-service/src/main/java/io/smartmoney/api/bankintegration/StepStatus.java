package io.smartmoney.api.bankintegration;

import com.fasterxml.jackson.annotation.JsonValue;

public enum StepStatus {
    OK("ok"),
    WARN("warn"),
    FAIL("fail");

    private final String value;

    StepStatus(String value) {
        this.value = value;
    }

    @JsonValue
    public String value() {
        return value;
    }
}

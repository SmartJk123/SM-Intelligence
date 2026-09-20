package io.smartmoney.api.bankintegration;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/** Matches the values the Angular interface sends and expects. */
public enum BankEnvironment {
    SANDBOX("Sandbox"),
    PRODUCTION("Production");

    private final String value;

    BankEnvironment(String value) {
        this.value = value;
    }

    @JsonValue
    public String value() {
        return value;
    }

    @JsonCreator
    public static BankEnvironment from(String raw) {
        if (raw == null) {
            return SANDBOX;
        }
        for (BankEnvironment candidate : values()) {
            if (candidate.value.equalsIgnoreCase(raw) || candidate.name().equalsIgnoreCase(raw)) {
                return candidate;
            }
        }
        return SANDBOX;
    }
}

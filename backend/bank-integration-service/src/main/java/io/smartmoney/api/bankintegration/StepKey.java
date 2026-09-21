package io.smartmoney.api.bankintegration;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Stable step identifiers. The Angular interface reads these keys to derive API
 * and webhook health after a connection test, so the values must not change.
 */
public enum StepKey {
    TOKEN("token"),
    ACCOUNT_PROBE("account-probe"),
    WEBHOOK_REGISTRATION("webhook-registration"),
    SIGNATURE_VERIFICATION("signature-verification");

    private final String value;

    StepKey(String value) {
        this.value = value;
    }

    @JsonValue
    public String value() {
        return value;
    }

    @JsonCreator
    public static StepKey from(String raw) {
        if (raw != null) {
            for (StepKey candidate : values()) {
                if (candidate.value.equalsIgnoreCase(raw)) {
                    return candidate;
                }
            }
        }
        throw new IllegalArgumentException("Unknown step key: " + raw);
    }
}

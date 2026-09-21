package io.smartmoney.api.bankintegration;

/**
 * Configuration the admin interface edits. Credentials are deliberately absent:
 * the client key and client secret are held as environment variables or in a
 * secret manager and are never sent to the browser.
 */
public record BankConnectionSettings(
        String bankId,
        BankEnvironment environment,
        int apiTimeoutSeconds,
        int retryAttempts,
        int retryDelaySeconds,
        boolean signatureVerification,
        boolean automaticRetry) {

    public static BankConnectionSettings defaults(String bankId) {
        return new BankConnectionSettings(bankId, BankEnvironment.SANDBOX, 30, 3, 5, true, true);
    }

    public BankConnectionSettings withBankId(String id) {
        return new BankConnectionSettings(
                id, environment, apiTimeoutSeconds, retryAttempts, retryDelaySeconds,
                signatureVerification, automaticRetry);
    }
}

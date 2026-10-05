package io.smartmoney.api.bankintegration.stanbic;

import io.smartmoney.api.bankintegration.BankEnvironment;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Values come from application.yml, which reads them from environment variables.
 * The client key and client secret are never written to source control and are
 * never returned to the browser.
 */
@ConfigurationProperties(prefix = "smartmoney.stanbic")
public record StanbicProperties(
        BankEnvironment environment,
        String portalUrl,
        String tokenUrl,
        String apiBaseUrl,
        String clientKey,
        String clientSecret,
        String accountNumber,
        String accountEnquiryPath,
        String registrationPath,
        String apiKey,
        String channel,
        String profileApprover,
        String oauthScope,
        int apiTimeoutSeconds,
        int retryAttempts,
        int retryDelaySeconds,
        String webhookPath,
        boolean signatureVerification,
        String signatureHeader,
        String signatureSecret) {

    static final String HOST = "https://api.connect.stanbicbank.co.ke";
    static final String SANDBOX_BASE = HOST + "/api/sandbox";
    static final String PRODUCTION_BASE = HOST + "/api/prod";

    public boolean production() {
        return environment == BankEnvironment.PRODUCTION;
    }

    /** STANBIC_API_BASE_URL when set, otherwise the sandbox or production base for STANBIC_ENV. */
    @Override
    public String apiBaseUrl() {
        return isPresent(apiBaseUrl) ? apiBaseUrl.trim() : (production() ? PRODUCTION_BASE : SANDBOX_BASE);
    }

    /**
     * STANBIC_TOKEN_URL when set, otherwise the base followed by /auth/oauth2/token,
     * which is where the sandbox specification puts it. Confirm the production
     * value with Stanbic before going live and set STANBIC_TOKEN_URL if it differs.
     */
    @Override
    public String tokenUrl() {
        if (isPresent(tokenUrl)) {
            return tokenUrl.trim();
        }
        String base = apiBaseUrl();
        return (base.endsWith("/") ? base.substring(0, base.length() - 1) : base) + "/auth/oauth2/token";
    }

    public boolean credentialsConfigured() {
        return isPresent(tokenUrl()) && isPresent(clientKey) && isPresent(clientSecret);
    }

    public boolean apiConfigured() {
        return isPresent(apiBaseUrl());
    }

    /** Registration endpoint: the API base plus the register path. */
    public String registrationUrl() {
        String base = apiBaseUrl();
        String path = registrationPath == null || registrationPath.isBlank()
                ? "/registerurl/"
                : registrationPath;
        if (!path.startsWith("/")) {
            path = "/" + path;
        }
        String trimmed = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
        return trimmed + path;
    }

    /**
     * The registerurl request carries an ApiKey field. The developer portal only
     * issues the OAuth client key and secret, so when no separate ApiKey has been
     * supplied we send the client key and say so, rather than refusing to try.
     */
    public String effectiveApiKey() {
        return isPresent(apiKey) ? apiKey : (clientKey == null ? "" : clientKey);
    }

    /** Where the value sent as ApiKey came from, for the admin screen. */
    public String apiKeySource() {
        if (isPresent(apiKey)) {
            return "separate ApiKey";
        }
        return isPresent(clientKey) ? "client key fallback" : "missing";
    }

    public boolean registrationConfigured() {
        return credentialsConfigured() && isPresent(effectiveApiKey());
    }

    /** Tail of a value, for confirming which credential is loaded without exposing it. */
    public static String tail(String value) {
        if (!isPresent(value)) {
            return "";
        }
        return value.length() <= 4 ? "****" : "****" + value.substring(value.length() - 4);
    }

    public String maskedAccountNumber() {
        if (!isPresent(accountNumber)) {
            return "not configured";
        }
        if (accountNumber.length() <= 4) {
            return accountNumber;
        }
        return "*".repeat(accountNumber.length() - 4)
                + accountNumber.substring(accountNumber.length() - 4);
    }

    public static boolean isPresent(String value) {
        return value != null && !value.isBlank();
    }
}

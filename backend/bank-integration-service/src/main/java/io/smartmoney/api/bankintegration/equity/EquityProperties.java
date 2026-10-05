package io.smartmoney.api.bankintegration.equity;

import io.smartmoney.api.bankintegration.BankEnvironment;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Equity Bank Jenga API configuration.
 *
 * Jenga authenticates a merchant with an Api-Key header plus the merchant code
 * and consumer secret in the body, and pushes Instant Payment Notifications
 * (IPN) to a callback URL protected with Basic Auth: the username and password
 * registered with that URL on Jenga HQ. Source:
 * https://developer.jengahq.io/guides/get-started/developer-quickstart and
 * https://developer.jengahq.io/guides/jenga-pgw/instant-payment-notifications
 */
@ConfigurationProperties(prefix = "smartmoney.equity")
public record EquityProperties(
        BankEnvironment environment,
        String tokenUrl,
        String merchantCode,
        String apiKey,
        String consumerSecret,
        /** The Equity account payments settle into, used when an IPN names no account. */
        String accountNumber,
        String webhookPath,
        /** Basic Auth credentials registered with the IPN callback URL on Jenga HQ. */
        String ipnUsername,
        String ipnPassword,
        boolean signatureVerification,
        int apiTimeoutSeconds) {

    static final String UAT_TOKEN_URL = "https://uat.finserve.africa/authentication/api/v3/authenticate/merchant";
    static final String PRODUCTION_TOKEN_URL = "https://api.finserve.africa/authentication/api/v3/authenticate/merchant";

    /** EQUITY_TOKEN_URL when set, otherwise Jenga's UAT or production endpoint for EQUITY_ENV. */
    @Override
    public String tokenUrl() {
        if (isPresent(tokenUrl)) {
            return tokenUrl.trim();
        }
        return environment == BankEnvironment.PRODUCTION ? PRODUCTION_TOKEN_URL : UAT_TOKEN_URL;
    }

    public boolean credentialsConfigured() {
        return isPresent(merchantCode) && isPresent(apiKey) && isPresent(consumerSecret);
    }

    public boolean ipnCredentialsConfigured() {
        return isPresent(ipnUsername) && isPresent(ipnPassword);
    }

    public String maskedAccountNumber() {
        if (!isPresent(accountNumber)) {
            return null;
        }
        return accountNumber.length() <= 4
                ? accountNumber
                : "*".repeat(accountNumber.length() - 4) + accountNumber.substring(accountNumber.length() - 4);
    }

    public static boolean isPresent(String value) {
        return value != null && !value.isBlank();
    }
}

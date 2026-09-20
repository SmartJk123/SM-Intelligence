package io.smartmoney.api.bankintegration.ncba;

import io.smartmoney.api.bankintegration.BankEnvironment;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * NCBA account level push notification configuration.
 *
 * NCBA pushes to us and we never call them. There is no OAuth token to fetch
 * and no registration API to call: the endpoint address, the secret key, the
 * username, the password and the account number are sent to the bank in
 * writing, and NCBA configures them on their side. That is why this connector
 * has no token service.
 */
@ConfigurationProperties(prefix = "smartmoney.ncba")
public record NcbaProperties(
        BankEnvironment environment,
        String portalUrl,
        String webhookPath,
        String secretKey,
        String username,
        String password,
        String accountNumber,
        boolean signatureVerification,
        int apiTimeoutSeconds) {

    /** The credentials that appear on the request letter to NCBA. */
    public boolean credentialsConfigured() {
        return isPresent(secretKey) && isPresent(username) && isPresent(password);
    }

    /** True when notifications can actually be verified rather than refused. */
    public boolean signatureVerificationReady() {
        return signatureVerification && isPresent(secretKey);
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

    public static String tail(String value) {
        if (!isPresent(value)) {
            return "";
        }
        return value.length() <= 4 ? "****" : "****" + value.substring(value.length() - 4);
    }

    public static boolean isPresent(String value) {
        return value != null && !value.isBlank();
    }
}

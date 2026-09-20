package io.smartmoney.api.bankintegration.kcb;

import io.smartmoney.api.bankintegration.BankEnvironment;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * KCB BUNI configuration.
 *
 * Note the shape of KCB's platform: the OAuth token endpoint lives on the
 * accounts host, separate from the API host, and the instant payment
 * notification carries a SHA256withRSA signature that we verify with KCB's
 * public key.
 */
@ConfigurationProperties(prefix = "smartmoney.kcb")
public record KcbProperties(
        BankEnvironment environment,
        String portalUrl,
        String tokenUrl,
        String revokeUrl,
        String apiBaseUrl,
        String clientKey,
        String clientSecret,
        /** basic: credentials in the Authorization header. body: client_id and client_secret form fields. */
        String authStyle,
        String webhookPath,
        boolean signatureVerification,
        String signatureHeader,
        String publicKey,
        int apiTimeoutSeconds) {

    public boolean credentialsConfigured() {
        return isPresent(tokenUrl) && isPresent(clientKey) && isPresent(clientSecret);
    }

    public boolean signatureVerificationReady() {
        return signatureVerification && isPresent(publicKey);
    }

    public boolean usesBasicAuth() {
        return authStyle == null || authStyle.isBlank() || "basic".equalsIgnoreCase(authStyle);
    }

    public static boolean isPresent(String value) {
        return value != null && !value.isBlank();
    }
}

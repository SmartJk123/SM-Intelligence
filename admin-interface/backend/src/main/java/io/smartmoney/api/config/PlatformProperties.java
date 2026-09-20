package io.smartmoney.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Platform level settings. publicBaseUrl is the address the bank can reach, so
 * it is what appears in the callback URL shown in the admin interface.
 */
@ConfigurationProperties(prefix = "smartmoney")
public record PlatformProperties(String publicBaseUrl, Security security) {

    public record Security(boolean permitAll) {
    }

    public String callbackUrl(String bankId) {
        String base = publicBaseUrl == null || publicBaseUrl.isBlank()
                ? "http://localhost:8080"
                : publicBaseUrl;
        return trimTrailingSlash(base) + "/api/v1/webhooks/" + bankId;
    }

    /**
     * Whether the configured address is a public HTTPS address rather than
     * localhost. This is a configuration check only. It does not prove that the
     * host resolves or that anything is listening on it, so check
     * CallbackReachability before telling a bank that an address is ready. A
     * quick tunnel is issued a new hostname every time it starts.
     */
    public boolean callbackIsPublic() {
        String base = publicBaseUrl == null ? "" : publicBaseUrl.toLowerCase();
        return base.startsWith("https://") && !base.contains("localhost");
    }

    private static String trimTrailingSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}

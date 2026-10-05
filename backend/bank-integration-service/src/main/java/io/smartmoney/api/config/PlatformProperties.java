package io.smartmoney.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Platform level settings. publicBaseUrl is the address the bank can reach, so
 * it is what appears in the callback URL shown in the admin interface.
 *
 * A mutable class rather than a record, so the admin interface can change
 * publicBaseUrl on the running service (see PublicBaseUrlService, which also
 * stores it so it survives a restart). The get/set pairs are for Spring's
 * JavaBean binder; the rest of the code keeps the no-prefix accessors.
 */
@ConfigurationProperties(prefix = "smartmoney")
public class PlatformProperties {

    private volatile String publicBaseUrl;
    private Security security = new Security();

    public PlatformProperties() {
    }

    public PlatformProperties(String publicBaseUrl, Security security) {
        this.publicBaseUrl = publicBaseUrl;
        this.security = security == null ? new Security() : security;
    }

    public String getPublicBaseUrl() { return publicBaseUrl; }
    public void setPublicBaseUrl(String publicBaseUrl) { this.publicBaseUrl = publicBaseUrl; }
    public String publicBaseUrl() { return publicBaseUrl; }

    public Security getSecurity() { return security; }
    public void setSecurity(Security security) { this.security = security; }
    public Security security() { return security; }

    public static class Security {
        private boolean permitAll;
        /** identity-service's JWT_SECRET, used to verify admin and customer tokens. */
        private String jwtSecret;

        public Security() {
        }

        public Security(boolean permitAll) {
            this.permitAll = permitAll;
        }

        public boolean isPermitAll() { return permitAll; }
        public void setPermitAll(boolean permitAll) { this.permitAll = permitAll; }
        public boolean permitAll() { return permitAll; }

        public String getJwtSecret() { return jwtSecret; }
        public void setJwtSecret(String jwtSecret) { this.jwtSecret = jwtSecret; }
        public String jwtSecret() { return jwtSecret; }
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

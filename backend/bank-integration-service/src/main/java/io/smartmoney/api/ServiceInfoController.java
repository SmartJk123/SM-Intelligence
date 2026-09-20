package io.smartmoney.api;

import io.smartmoney.api.bankintegration.stanbic.StanbicProperties;
import io.smartmoney.api.config.PlatformProperties;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

/**
 * Human readable landing page listing the addresses that have to be given to
 * the bank, plus the OAuth redirect target.
 */
@RestController
public class ServiceInfoController {

    private final PlatformProperties platform;
    private final StanbicProperties stanbic;

    public ServiceInfoController(PlatformProperties platform, StanbicProperties stanbic) {
        this.platform = platform;
        this.stanbic = stanbic;
    }

    @GetMapping(value = "/", produces = MediaType.TEXT_PLAIN_VALUE)
    public String index() {
        String base = baseUrl();
        return """
                SmartMoney Intelligence API

                Status            running
                Environment       %s
                Stanbic account   %s
                Credentials set   %s

                URLs to give Stanbic
                  Notification URL  %s
                  Redirect URL      %s/oauth/stanbic/callback

                For your own checking
                  Health            %s/actuator/health
                  Admin API         %s/api/v1/admin/bank-integrations
                  Webhook probe     %s/api/v1/webhooks/stanbic

                When the notification URL is not publicly reachable, start a tunnel and
                set PUBLIC_BASE_URL to the tunnel address, then restart the service.
                """.formatted(
                stanbic.environment() == null ? "Sandbox" : stanbic.environment().value(),
                stanbic.maskedAccountNumber(),
                stanbic.credentialsConfigured() ? "yes" : "no",
                platform.callbackUrl("stanbic"),
                base,
                base,
                base,
                base);
    }

    @GetMapping(value = "/oauth/stanbic/callback", produces = MediaType.TEXT_PLAIN_VALUE)
    public String oauthCallback(
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String error) {

        if (error != null) {
            return "Stanbic returned an error: " + error
                    + "\n\nThis endpoint exists so the address can be registered as a redirect URL. "
                    + "SmartMoney authenticates server to server, so it does not depend on this redirect.";
        }
        return """
                SmartMoney Intelligence OAuth redirect endpoint

                Received at %s
                Authorization code present: %s
                State present: %s

                This address exists so it can be registered as a redirect URL with Stanbic.
                SmartMoney authenticates server to server with the client credentials flow,
                so no user consent redirect is required for the integration itself.
                """.formatted(Instant.now(), code != null, state != null);
    }

    private String baseUrl() {
        String base = platform.publicBaseUrl();
        if (base == null || base.isBlank()) {
            return "http://localhost:8080";
        }
        return base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
    }
}

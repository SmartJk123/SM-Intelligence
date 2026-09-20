package io.smartmoney.api.bankintegration.stanbic;

import io.smartmoney.api.bankintegration.BankConnectionSettings;
import io.smartmoney.api.bankintegration.BankConnectionTest;
import io.smartmoney.api.bankintegration.BankConnector;
import io.smartmoney.api.bankintegration.CallbackReachability;
import io.smartmoney.api.bankintegration.ConnectionStep;
import io.smartmoney.api.bankintegration.StepKey;
import io.smartmoney.api.config.PlatformProperties;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Service
public class StanbicConnector implements BankConnector {

    private static final String BANK_ID = "stanbic";

    private final StanbicProperties props;
    private final PlatformProperties platform;
    private final StanbicTokenService tokens;
    private final CallbackReachability reachability;
    private final RestClient http;

    public StanbicConnector(
            StanbicProperties props,
            PlatformProperties platform,
            StanbicTokenService tokens,
            CallbackReachability reachability) {
        this.props = props;
        this.platform = platform;
        this.tokens = tokens;
        this.reachability = reachability;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(10));
        factory.setReadTimeout(Duration.ofSeconds(Math.max(5, props.apiTimeoutSeconds())));
        this.http = RestClient.builder().requestFactory(factory).build();
    }

    @Override
    public String bankId() {
        return BANK_ID;
    }

    @Override
    public String displayName() {
        return "Stanbic Bank Kenya";
    }

    @Override
    public BankConnectionTest testConnection(BankConnectionSettings settings) {
        long started = System.nanoTime();
        List<ConnectionStep> steps = new ArrayList<>();
        String environment = settings.environment() == null
                ? "Sandbox"
                : settings.environment().value();

        String token = null;
        try {
            token = tokens.accessToken();
            steps.add(ConnectionStep.ok(StepKey.TOKEN, "Token request",
                    "Access token issued for the " + environment
                            + " environment and cached for one hour."));
        } catch (Exception error) {
            steps.add(ConnectionStep.fail(StepKey.TOKEN, "Token request", error.getMessage()));
        }

        steps.add(probeAccount(token, started));
        steps.add(webhookRegistrationStep());
        steps.add(signatureStep(settings));

        return BankConnectionTest.of(steps, elapsedMs(started));
    }

    private ConnectionStep probeAccount(String token, long started) {
        if (token == null) {
            return ConnectionStep.fail(StepKey.ACCOUNT_PROBE, "Account probe",
                    "Skipped because no access token was obtained.");
        }
        if (!props.apiConfigured()) {
            return ConnectionStep.warn(StepKey.ACCOUNT_PROBE, "Account probe",
                    "STANBIC_API_BASE_URL is not set, so the account enquiry was not attempted.");
        }

        String path = props.accountEnquiryPath() == null || props.accountEnquiryPath().isBlank()
                ? "/accounts/" + props.accountNumber()
                : props.accountEnquiryPath().replace("{accountNumber}", props.accountNumber());
        String url = trimTrailingSlash(props.apiBaseUrl()) + path;

        try {
            http.get()
                    .uri(url)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .retrieve()
                    .toBodilessEntity();
            return ConnectionStep.ok(StepKey.ACCOUNT_PROBE, "Account probe",
                    "Account " + props.maskedAccountNumber() + " responded in " + elapsedMs(started) + " ms.");
        } catch (RestClientResponseException error) {
            if (error.getStatusCode().value() == 401) {
                tokens.invalidate();
                return ConnectionStep.fail(StepKey.ACCOUNT_PROBE, "Account probe",
                        "The gateway rejected the access token.");
            }
            return ConnectionStep.warn(StepKey.ACCOUNT_PROBE, "Account probe",
                    "The gateway returned HTTP " + error.getStatusCode().value() + " for "
                            + url + ". Confirm the account enquiry path in the API product page.");
        } catch (RestClientException error) {
            return ConnectionStep.fail(StepKey.ACCOUNT_PROBE, "Account probe",
                    "Could not reach " + url + ": " + error.getMessage());
        }
    }

    private ConnectionStep webhookRegistrationStep() {
        String callback = platform.callbackUrl(BANK_ID);
        CallbackReachability.State state = reachability.state(BANK_ID);

        if (state == CallbackReachability.State.RESOLVABLE) {
            return ConnectionStep.ok(StepKey.WEBHOOK_REGISTRATION, "Webhook endpoint registration",
                    "Callback URL " + callback + " resolves. Confirm with Stanbic that this address is "
                            + "registered for credit and debit alerts.");
        }
        if (state == CallbackReachability.State.UNRESOLVABLE) {
            return ConnectionStep.fail(StepKey.WEBHOOK_REGISTRATION, "Webhook endpoint registration",
                    "Callback URL " + callback + " does not resolve, so Stanbic cannot deliver to it. A quick "
                            + "tunnel is issued a new hostname every time it starts and the previous address "
                            + "stops resolving. Start the tunnel again, put the new address in PUBLIC_BASE_URL "
                            + "and restart the service, then register the new address.");
        }
        return ConnectionStep.warn(StepKey.WEBHOOK_REGISTRATION, "Webhook endpoint registration",
                "Callback URL is " + callback + ". Stanbic cannot reach localhost, so start a tunnel and set "
                        + "PUBLIC_BASE_URL before registering this URL with them.");
    }

    private ConnectionStep signatureStep(BankConnectionSettings settings) {
        if (!settings.signatureVerification()) {
            return ConnectionStep.warn(StepKey.SIGNATURE_VERIFICATION, "Signature verification",
                    "Signature verification is disabled, so callback authenticity cannot be confirmed.");
        }
        if (!StanbicProperties.isPresent(props.signatureSecret())) {
            return ConnectionStep.warn(StepKey.SIGNATURE_VERIFICATION, "Signature verification",
                    "Enabled, but the signature header or server-side secret is not set, so callbacks will be rejected until configured.");
        }
        return ConnectionStep.ok(StepKey.SIGNATURE_VERIFICATION, "Signature verification",
                "Callbacks are verified with HMAC-SHA256 using the configured signing secret.");
    }

    private static String trimTrailingSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private static long elapsedMs(long startedNanos) {
        return (System.nanoTime() - startedNanos) / 1_000_000;
    }
}

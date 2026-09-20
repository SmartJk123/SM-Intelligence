package io.smartmoney.api.bankintegration.stanbic;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.smartmoney.api.config.PlatformProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.time.Duration;
import java.util.UUID;

/**
 * Registers the SmartMoney callback URL with Stanbic so that real-time credit
 * and debit alerts are pushed to us.
 *
 * Contract taken from "Register Your Payment Result URL for Real-time Credit
 * Alerts - Sandbox" version 1.0.2: POST to /registerurl with ReferenceId,
 * ApiKey, CallBackUrl, NotificationType, ProfileApprover and Channel.
 */
@Service
public class StanbicRegistrationService {

    private static final Logger log = LoggerFactory.getLogger(StanbicRegistrationService.class);
    private static final String BANK_ID = "stanbic";

    private final StanbicProperties props;
    private final PlatformProperties platform;
    private final StanbicTokenService tokens;
    private final RestClient http;

    public StanbicRegistrationService(
            StanbicProperties props, PlatformProperties platform, StanbicTokenService tokens) {
        this.props = props;
        this.platform = platform;
        this.tokens = tokens;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(10));
        factory.setReadTimeout(Duration.ofSeconds(Math.max(15, props.apiTimeoutSeconds())));
        this.http = RestClient.builder().requestFactory(factory).build();
    }

    /** @param notificationType CREDIT or DEBIT */
    public RegistrationOutcome register(String notificationType) {
        String type = notificationType == null || notificationType.isBlank()
                ? "CREDIT"
                : notificationType.toUpperCase();
        String callbackUrl = platform.callbackUrl(BANK_ID);
        String reference = "SM-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        if (!props.registrationConfigured()) {
            return RegistrationOutcome.failed(type, reference, callbackUrl,
                    "Set STANBIC_CLIENT_KEY and STANBIC_CLIENT_SECRET before registering. "
                            + "STANBIC_API_KEY is optional and falls back to the client key.");
        }

        String token;
        try {
            token = tokens.accessToken();
        } catch (Exception error) {
            return RegistrationOutcome.failed(type, reference, callbackUrl,
                    "Could not obtain an access token: " + error.getMessage()
                            + hintFor(error.getMessage()));
        }

        RegisterUrlRequest body = new RegisterUrlRequest(
                reference,
                props.effectiveApiKey(),
                callbackUrl,
                type,
                props.profileApprover() == null ? "SmartMoney Intelligence" : props.profileApprover(),
                props.channel() == null ? "APGW" : props.channel());

        try {
            RegisterUrlResponse response = http.post()
                    .uri(props.registrationUrl())
                    .contentType(MediaType.APPLICATION_JSON)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .body(body)
                    .retrieve()
                    .body(RegisterUrlResponse.class);

            if (response == null) {
                return RegistrationOutcome.failed(type, reference, callbackUrl,
                        "Stanbic returned an empty response.");
            }
            boolean accepted = "00".equals(response.responseCode());
            log.info("Stanbic callback registration for {} returned {} {}",
                    type, response.responseCode(), response.responseMessage());
            return new RegistrationOutcome(
                    type,
                    accepted,
                    response.responseCode(),
                    response.responseMessage(),
                    response.referenceId() == null ? reference : response.referenceId(),
                    callbackUrl,
                    accepted ? null : "Stanbic rejected the registration.");
        } catch (RestClientResponseException error) {
            String errorBody = error.getResponseBodyAsString();
            return RegistrationOutcome.failed(type, reference, callbackUrl,
                    "Stanbic returned HTTP " + error.getStatusCode().value() + ": "
                            + summarise(errorBody) + hintFor(errorBody));
        } catch (RestClientException error) {
            return RegistrationOutcome.failed(type, reference, callbackUrl,
                    "Could not reach " + props.registrationUrl() + ": " + error.getMessage());
        }
    }

    /**
     * The ApiKey field is documented only by example, so when Stanbic complains
     * about it we say plainly what to do instead of leaving a raw error.
     */
    private static String hintFor(String responseBody) {
        String body = responseBody == null ? "" : responseBody.toLowerCase();
        if (body.contains("apikey") || body.contains("api key")) {
            return " This response mentions the ApiKey field. That value is not issued on the "
                    + "developer portal, so it may need to be requested from the Integrations Team "
                    + "at kilele@stanbic.com. The service is currently sending the client key in "
                    + "that field.";
        }
        if (body.contains("unauthor") || body.contains("forbidden")) {
            return " This looks like an authorisation failure. Confirm the application is approved "
                    + "to consume the registerurl product.";
        }
        if (body.contains("api not found") || body.contains("not found")) {
            return " This is a gateway routing error rather than a credentials error, so the URL is "
                    + "the likely cause. Copy the exact value from the portal product page.";
        }
        return "";
    }

    private static String summarise(String body) {
        if (body == null || body.isBlank()) {
            return "no response body";
        }
        String trimmed = body.strip().replaceAll("\\s+", " ");
        return trimmed.length() > 240 ? trimmed.substring(0, 240) + "..." : trimmed;
    }

    public record RegisterUrlRequest(
            @JsonProperty("ReferenceId") String referenceId,
            @JsonProperty("ApiKey") String apiKey,
            @JsonProperty("CallBackUrl") String callBackUrl,
            @JsonProperty("NotificationType") String notificationType,
            @JsonProperty("ProfileApprover") String profileApprover,
            @JsonProperty("Channel") String channel) {
    }

    public record RegisterUrlResponse(
            @JsonProperty("ResponseCode") String responseCode,
            @JsonProperty("ResponseMessage") String responseMessage,
            @JsonProperty("ReferenceId") String referenceId) {
    }

    /** Result shown in the admin interface. */
    public record RegistrationOutcome(
            String notificationType,
            boolean accepted,
            String responseCode,
            String responseMessage,
            String referenceId,
            String callbackUrl,
            String error) {

        static RegistrationOutcome failed(
                String type, String reference, String callbackUrl, String error) {
            return new RegistrationOutcome(type, false, null, null, reference, callbackUrl, error);
        }
    }
}

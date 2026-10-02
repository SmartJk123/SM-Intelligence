package io.smartmoney.api.bankintegration.stanbic;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.smartmoney.api.bankintegration.TokenSnapshot;
import io.smartmoney.api.bankintegration.TokenStateProvider;
import io.smartmoney.api.bankintegration.TokenStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;

/**
 * Obtains and caches the Stanbic OAuth access token.
 *
 * The developer portal issues the token with the client key and client secret,
 * and the token is valid for one hour, so it is refreshed shortly before expiry.
 */
@Service
public class StanbicTokenService implements TokenStateProvider {

    private static final Logger log = LoggerFactory.getLogger(StanbicTokenService.class);
    private static final Duration EARLY_REFRESH = Duration.ofSeconds(120);
    private static final long DEFAULT_LIFETIME_SECONDS = 3600L;
    private static final String HTML_HINT =
            "The token URL returned an HTML page instead of a token. That usually means the value is a "
                    + "portal web address rather than the API token endpoint. Open the API product page, "
                    + "select the API inside it, and copy the Token URL shown there. It should look like "
                    + "https://api.connect.stanbicbank.co.ke/api/sandbox/auth/oauth2/token";

    private final StanbicProperties props;
    private final ObjectMapper mapper;
    private final RestClient http;

    private String cachedToken;
    private Instant expiresAt = Instant.EPOCH;
    private Instant refreshedAt;

    public StanbicTokenService(StanbicProperties props, ObjectMapper mapper) {
        this.props = props;
        this.mapper = mapper;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(10));
        factory.setReadTimeout(Duration.ofSeconds(Math.max(5, props.apiTimeoutSeconds())));
        this.http = RestClient.builder().requestFactory(factory).build();
    }

    public synchronized String accessToken() {
        if (!props.credentialsConfigured()) {
            throw new IllegalStateException(
                    "Stanbic credentials are not configured. Set STANBIC_TOKEN_URL, "
                            + "STANBIC_CLIENT_KEY and STANBIC_CLIENT_SECRET.");
        }
        if (cachedToken != null && Instant.now().isBefore(expiresAt.minus(EARLY_REFRESH))) {
            return cachedToken;
        }

        String form = "grant_type=client_credentials"
                + "&client_id=" + encode(props.clientKey())
                + "&client_secret=" + encode(props.clientSecret())
                + (StanbicProperties.isPresent(props.oauthScope())
                        ? "&scope=" + encode(props.oauthScope())
                        : "");

        String raw;
        try {
            raw = http.post()
                    .uri(props.tokenUrl())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(String.class);
        } catch (RestClientResponseException error) {
            throw new IllegalStateException("Stanbic token endpoint returned HTTP "
                    + error.getStatusCode().value() + ". "
                    + explain(error.getResponseBodyAsString()), error);
        } catch (RestClientException error) {
            throw new IllegalStateException(
                    "Stanbic token endpoint could not be reached: " + error.getMessage(), error);
        }

        TokenResponse response = parse(raw);

        long lifetime = response.expiresIn() == null ? DEFAULT_LIFETIME_SECONDS : response.expiresIn();
        this.cachedToken = response.accessToken();
        this.expiresAt = Instant.now().plusSeconds(lifetime);
        this.refreshedAt = Instant.now();
        log.info("Obtained a Stanbic access token, valid for {} seconds", lifetime);
        return cachedToken;
    }

    public synchronized void invalidate() {
        this.cachedToken = null;
        this.expiresAt = Instant.EPOCH;
    }

    @Override
    public TokenSnapshot tokenSnapshot() {
        if (cachedToken == null) {
            return TokenSnapshot.unknown();
        }
        long minutes = Duration.between(Instant.now(), expiresAt).toMinutes();
        TokenStatus status;
        if (minutes <= 0) {
            status = TokenStatus.EXPIRED;
        } else if (minutes <= 5) {
            status = TokenStatus.EXPIRING;
        } else {
            status = TokenStatus.VALID;
        }
        return new TokenSnapshot(status, (int) Math.max(0, minutes), refreshedAt);
    }

    private static String encode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    /**
     * Reads the token response without assuming a content type, so an HTML page
     * coming back from the wrong address produces a clear message instead of a
     * message converter failure.
     */
    private TokenResponse parse(String raw) {
        String body = raw == null ? "" : raw.strip();
        if (body.isEmpty()) {
            throw new IllegalStateException("The token endpoint returned an empty response.");
        }
        if (body.startsWith("<")) {
            throw new IllegalStateException(HTML_HINT);
        }
        try {
            JsonNode node = mapper.readTree(body);
            JsonNode token = node.get("access_token");
            if (token == null || token.asText().isBlank()) {
                throw new IllegalStateException(
                        "The token endpoint did not return an access_token. Response: " + summarise(body));
            }
            JsonNode expires = node.get("expires_in");
            Long lifetime = expires != null && expires.isNumber() ? expires.asLong() : null;
            JsonNode type = node.get("token_type");
            return new TokenResponse(token.asText(), lifetime, type == null ? null : type.asText());
        } catch (IllegalStateException error) {
            throw error;
        } catch (Exception error) {
            throw new IllegalStateException(
                    "The token endpoint returned a response that is not JSON: " + summarise(body));
        }
    }

    private static String explain(String body) {
        if (body == null || body.isBlank()) {
            return "No response body.";
        }
        return body.strip().startsWith("<") ? HTML_HINT : summarise(body);
    }

    private static String summarise(String body) {
        if (body == null || body.isBlank()) {
            return "No response body.";
        }
        String trimmed = body.strip().replaceAll("\\s+", " ");
        return trimmed.length() > 240 ? trimmed.substring(0, 240) + "..." : trimmed;
    }

    private record TokenResponse(
            @JsonProperty("access_token") String accessToken,
            @JsonProperty("expires_in") Long expiresIn,
            @JsonProperty("token_type") String tokenType) {
    }
}

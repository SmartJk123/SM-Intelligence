package io.smartmoney.api.bankintegration.kcb;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.smartmoney.api.bankintegration.TokenSnapshot;
import io.smartmoney.api.bankintegration.TokenStateProvider;
import io.smartmoney.api.bankintegration.TokenStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
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
import java.util.Base64;

/**
 * Obtains and caches a KCB BUNI access token using the client credentials grant.
 *
 * The token endpoint is https://accounts.buni.kcbgroup.com/oauth2/token, which
 * is a different host from the API gateway. BUNI lists several grant types for
 * an application; server to server access uses Client Credentials.
 */
@Service
public class KcbTokenService implements TokenStateProvider {

    private static final Logger log = LoggerFactory.getLogger(KcbTokenService.class);
    private static final Duration EARLY_REFRESH = Duration.ofSeconds(120);
    private static final long DEFAULT_LIFETIME_SECONDS = 3600L;
    private static final String HTML_HINT =
            "The KCB token URL returned an HTML page instead of a token. Check that the value is the "
                    + "OAuth token endpoint from BUNI, which should look like "
                    + "https://accounts.buni.kcbgroup.com/oauth2/token";

    private final KcbProperties props;
    private final ObjectMapper mapper;
    private final RestClient http;

    private String cachedToken;
    private Instant expiresAt = Instant.EPOCH;
    private Instant refreshedAt;

    public KcbTokenService(KcbProperties props, ObjectMapper mapper) {
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
                    "KCB credentials are not configured. Generate a sandbox Key and Secret for the "
                            + "application in BUNI, then set KCB_CLIENT_KEY and KCB_CLIENT_SECRET. "
                            + "The portal currently reports that no key and secret have been generated.");
        }
        if (cachedToken != null && Instant.now().isBefore(expiresAt.minus(EARLY_REFRESH))) {
            return cachedToken;
        }

        String form = "grant_type=client_credentials"
                + (props.usesBasicAuth()
                        ? ""
                        : "&client_id=" + encode(props.clientKey())
                                + "&client_secret=" + encode(props.clientSecret()));

        RestClient.RequestBodySpec request = http.post()
                .uri(props.tokenUrl())
                .accept(MediaType.APPLICATION_JSON, MediaType.ALL)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED);
        if (props.usesBasicAuth()) {
            request = request.header(HttpHeaders.AUTHORIZATION,
                    "Basic " + Base64.getEncoder().encodeToString(
                            (props.clientKey() + ":" + props.clientSecret())
                                    .getBytes(StandardCharsets.UTF_8)));
        }

        String raw;
        try {
            byte[] bytes = request.body(form).retrieve().body(byte[].class);
            raw = bytes == null ? "" : new String(bytes, StandardCharsets.UTF_8);
        } catch (RestClientResponseException error) {
            int status = error.getStatusCode().value();
            throw new IllegalStateException("KCB token endpoint returned HTTP "
                    + status + ". "
                    + explain(status, error.getResponseBodyAsString()), error);
        } catch (RestClientException error) {
            throw new IllegalStateException(
                    "KCB token endpoint could not be reached: " + error.getMessage(), error);
        }

        TokenResponse response = parse(raw);
        long lifetime = response.expiresIn() == null ? DEFAULT_LIFETIME_SECONDS : response.expiresIn();
        this.cachedToken = response.accessToken();
        this.expiresAt = Instant.now().plusSeconds(lifetime);
        this.refreshedAt = Instant.now();
        log.info("Obtained a KCB access token, valid for {} seconds", lifetime);
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
        TokenStatus status = minutes <= 0
                ? TokenStatus.EXPIRED
                : minutes <= 5 ? TokenStatus.EXPIRING : TokenStatus.VALID;
        return new TokenSnapshot(status, (int) Math.max(0, minutes), refreshedAt);
    }

    private TokenResponse parse(String raw) {
        String body = raw == null ? "" : raw.strip();
        if (body.isEmpty()) {
            throw new IllegalStateException("The KCB token endpoint returned an empty response.");
        }
        if (body.startsWith("<")) {
            throw new IllegalStateException(HTML_HINT);
        }
        try {
            JsonNode node = mapper.readTree(body);
            JsonNode token = node.get("access_token");
            if (token == null || token.asText().isBlank()) {
                throw new IllegalStateException(
                        "The KCB token endpoint did not return an access_token. Response: " + summarise(body));
            }
            JsonNode expires = node.get("expires_in");
            Long lifetime = expires != null && expires.isNumber() ? expires.asLong() : null;
            return new TokenResponse(token.asText(), lifetime);
        } catch (IllegalStateException error) {
            throw error;
        } catch (Exception error) {
            throw new IllegalStateException(
                    "The KCB token endpoint returned a response that is not JSON: " + summarise(body));
        }
    }

    private static String explain(int status, String body) {
        String detail;
        if (body == null || body.isBlank()) {
            detail = "No response body.";
        } else {
            detail = body.strip().startsWith("<") ? HTML_HINT : summarise(body);
        }
        // A 401 is the expected outcome of a missing, wrong or partial key pair, so
        // say what to check instead of leaving the reader with a status code.
        if (status == 401 || status == 403) {
            return detail + " KCB rejected the credentials. The Key and Secret are wrong, were not "
                    + "generated for this environment, or were pasted incompletely. Generate the "
                    + "sandbox Key and Secret for the application in BUNI and paste both values exactly.";
        }
        return detail;
    }

    private static String encode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    private static String summarise(String body) {
        String trimmed = body.strip().replaceAll("\\s+", " ");
        return trimmed.length() > 240 ? trimmed.substring(0, 240) + "..." : trimmed;
    }

    private record TokenResponse(String accessToken, Long expiresIn) {
    }
}

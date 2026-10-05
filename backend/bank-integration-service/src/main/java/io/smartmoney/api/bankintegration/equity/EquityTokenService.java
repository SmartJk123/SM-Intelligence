package io.smartmoney.api.bankintegration.equity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.smartmoney.api.bankintegration.TokenSnapshot;
import io.smartmoney.api.bankintegration.TokenStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Map;

/**
 * Obtains and caches a Jenga merchant access token.
 *
 *   POST {tokenUrl}
 *   Api-Key: {EQUITY_API_KEY}
 *   {"merchantCode": "...", "consumerSecret": "..."}
 *
 * The response carries accessToken, refreshToken, expiresIn, issuedAt and
 * tokenType. expiresIn is read as either a number of seconds or a timestamp,
 * since the documentation names the field without fixing its format.
 */
@Service
public class EquityTokenService {

    private static final Logger log = LoggerFactory.getLogger(EquityTokenService.class);
    private static final Duration EARLY_REFRESH = Duration.ofSeconds(120);
    private static final long DEFAULT_LIFETIME_SECONDS = 3600L;

    private final EquityProperties props;
    private final ObjectMapper mapper;
    private final RestClient http;

    private String cachedToken;
    private Instant expiresAt = Instant.EPOCH;
    private Instant refreshedAt;

    public EquityTokenService(EquityProperties props, ObjectMapper mapper) {
        this.props = props;
        this.mapper = mapper;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(10));
        factory.setReadTimeout(Duration.ofSeconds(Math.max(5, props.apiTimeoutSeconds())));
        this.http = RestClient.builder().requestFactory(factory).build();
    }

    public synchronized String accessToken() {
        if (!props.credentialsConfigured()) {
            throw new IllegalStateException("Equity credentials are not configured. Copy the merchant code, "
                    + "API key and consumer secret from Jenga HQ into EQUITY_MERCHANT_CODE, EQUITY_API_KEY "
                    + "and EQUITY_CONSUMER_SECRET.");
        }
        if (cachedToken != null && Instant.now().isBefore(expiresAt.minus(EARLY_REFRESH))) {
            return cachedToken;
        }

        String raw;
        try {
            byte[] bytes = http.post()
                    .uri(props.tokenUrl())
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .header("Api-Key", props.apiKey())
                    .body(Map.of("merchantCode", props.merchantCode(), "consumerSecret", props.consumerSecret()))
                    .retrieve()
                    .body(byte[].class);
            raw = bytes == null ? "" : new String(bytes, StandardCharsets.UTF_8);
        } catch (RestClientResponseException error) {
            int status = error.getStatusCode().value();
            throw new IllegalStateException("Jenga authentication returned HTTP " + status + ". "
                    + (status == 401 || status == 403
                            ? "Check the API key, merchant code and consumer secret, and that they are for the "
                                    + (props.environment() == null ? "UAT" : props.environment().value()) + " environment."
                            : summarise(error.getResponseBodyAsString())), error);
        } catch (RestClientException error) {
            throw new IllegalStateException("Jenga authentication could not be reached: " + error.getMessage(), error);
        }

        JsonNode body;
        try {
            body = mapper.readTree(raw);
        } catch (Exception error) {
            throw new IllegalStateException("Jenga authentication returned something other than JSON: " + summarise(raw));
        }
        String token = body.path("accessToken").asText("");
        if (token.isBlank()) {
            throw new IllegalStateException("Jenga authentication did not return an accessToken. Response: " + summarise(raw));
        }
        this.cachedToken = token;
        this.expiresAt = expiry(body.path("expiresIn"));
        this.refreshedAt = Instant.now();
        log.info("Obtained a Jenga access token, valid until {}", expiresAt);
        return cachedToken;
    }

    public synchronized TokenSnapshot tokenSnapshot() {
        if (cachedToken == null) {
            return TokenSnapshot.unknown();
        }
        long minutes = Duration.between(Instant.now(), expiresAt).toMinutes();
        TokenStatus status = minutes <= 0
                ? TokenStatus.EXPIRED
                : minutes <= 5 ? TokenStatus.EXPIRING : TokenStatus.VALID;
        return new TokenSnapshot(status, (int) Math.max(0, minutes), refreshedAt);
    }

    /** expiresIn as seconds, epoch seconds or milliseconds, or an ISO timestamp. */
    static Instant expiry(JsonNode expiresIn) {
        Instant now = Instant.now();
        if (expiresIn == null || expiresIn.isMissingNode() || expiresIn.isNull()) {
            return now.plusSeconds(DEFAULT_LIFETIME_SECONDS);
        }
        if (expiresIn.isNumber() || expiresIn.asText().matches("\\d+")) {
            long value = expiresIn.asLong();
            if (value > 1_000_000_000_000L) {
                return Instant.ofEpochMilli(value);
            }
            if (value > 1_000_000_000L) {
                return Instant.ofEpochSecond(value);
            }
            return now.plusSeconds(value > 0 ? value : DEFAULT_LIFETIME_SECONDS);
        }
        try {
            return OffsetDateTime.parse(expiresIn.asText()).toInstant();
        } catch (Exception notIso) {
            try {
                return Instant.parse(expiresIn.asText());
            } catch (Exception unreadable) {
                return now.plusSeconds(DEFAULT_LIFETIME_SECONDS);
            }
        }
    }

    private static String summarise(String body) {
        if (body == null || body.isBlank()) {
            return "no response body";
        }
        String trimmed = body.strip().replaceAll("\\s+", " ");
        return trimmed.length() > 240 ? trimmed.substring(0, 240) + "..." : trimmed;
    }
}

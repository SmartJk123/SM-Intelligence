package io.smartmoney.api.bankintegration.equity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.smartmoney.api.bankintegration.SignaturePolicy;
import io.smartmoney.api.bankintegration.WebhookEventEntity;
import io.smartmoney.api.bankintegration.WebhookIngestionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;

/**
 * Equity (Jenga) Instant Payment Notification endpoint.
 *
 * Jenga authenticates the callback with Basic Auth, using the username and
 * password registered with the callback URL on Jenga HQ. It sends successful
 * and failed payments alike; both are recorded, and only a successful one is
 * delivered to a customer (see NormalizedTransactionService).
 */
@RestController
@RequestMapping("/api/v1/webhooks")
public class EquityWebhookController {

    private static final Logger log = LoggerFactory.getLogger(EquityWebhookController.class);
    private static final String BANK_ID = "equity";

    private final WebhookIngestionService events;
    private final EquityProperties props;
    private final SignaturePolicy policy;
    private final ObjectMapper mapper;

    public EquityWebhookController(WebhookIngestionService events, EquityProperties props, SignaturePolicy policy,
                                   ObjectMapper mapper) {
        this.events = events;
        this.props = props;
        this.policy = policy;
        this.mapper = mapper;
    }

    @GetMapping(value = "/equity", produces = MediaType.TEXT_PLAIN_VALUE)
    public String probe() {
        return """
                SmartMoney Intelligence notification endpoint
                Provider : Equity Bank Kenya (Jenga)

                POST Instant Payment Notifications to this URL as JSON, with Basic Auth.
                A GET returns this message so the address can be verified.

                Checked at %s
                """.formatted(Instant.now());
    }

    @PostMapping(path = "/equity", consumes = MediaType.ALL_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> receive(
            @RequestBody(required = false) String rawBody,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {

        Boolean authenticated = authenticate(authorization);
        if (Boolean.FALSE.equals(authenticated)) {
            log.warn("Equity notification refused: Basic Auth credentials missing or wrong");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .header(HttpHeaders.WWW_AUTHENTICATE, "Basic realm=\"SmartMoney\"")
                    .body(Map.of("status", "rejected", "reason", "Invalid or missing Basic Auth credentials"));
        }

        String reference = reference(rawBody);
        String externalId = reference != null ? reference : payloadHash(rawBody);
        WebhookEventEntity event = events.record(BANK_ID, externalId, authenticated, rawBody);
        events.process(event.getId());
        return ResponseEntity.ok(Map.of("status", "received", "reference", externalId));
    }

    /** null when checking is switched off, so the event is stored as not checked rather than verified. */
    private Boolean authenticate(String authorization) {
        if (!policy.verifies(BANK_ID, props.signatureVerification())) {
            return null;
        }
        if (!props.ipnCredentialsConfigured() || authorization == null || !authorization.startsWith("Basic ")) {
            return false;
        }
        String expected = props.ipnUsername() + ":" + props.ipnPassword();
        try {
            byte[] provided = Base64.getDecoder().decode(authorization.substring(6).trim());
            return MessageDigest.isEqual(provided, expected.getBytes(StandardCharsets.UTF_8));
        } catch (IllegalArgumentException notBase64) {
            return false;
        }
    }

    /** transaction.reference, else bank.reference. */
    private String reference(String rawBody) {
        if (rawBody == null || rawBody.isBlank()) {
            return null;
        }
        try {
            JsonNode root = mapper.readTree(rawBody);
            for (String path : new String[]{"/transaction/reference", "/bank/reference"}) {
                String value = root.at(path).asText("");
                if (!value.isBlank()) {
                    return value;
                }
            }
        } catch (Exception notJson) {
            // Recorded by its hash; normalisation reports the body as unreadable.
        }
        return null;
    }

    private static String payloadHash(String rawBody) {
        try {
            return "PAYLOAD-" + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest((rawBody == null ? "" : rawBody).getBytes(StandardCharsets.UTF_8)));
        } catch (Exception error) {
            throw new IllegalStateException("Could not identify the notification payload", error);
        }
    }
}

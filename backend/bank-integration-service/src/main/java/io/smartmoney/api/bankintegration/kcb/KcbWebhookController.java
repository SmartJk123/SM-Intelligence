package io.smartmoney.api.bankintegration.kcb;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.smartmoney.api.bankintegration.WebhookEventEntity;
import io.smartmoney.api.bankintegration.WebhookIngestionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;

/**
 * KCB instant payment notification endpoint.
 *
 * BUNI specifies that we verify the Signature header with the KCB public key and
 * acknowledge with a small JSON body. The acknowledgement shape is fixed by the
 * specification, so it is returned exactly as documented.
 */
@RestController
@RequestMapping("/api/v1/webhooks")
public class KcbWebhookController {

    private static final Logger log = LoggerFactory.getLogger(KcbWebhookController.class);
    private static final String BANK_ID = "kcb";
    private static final String[] TRANSACTION_ID_FIELDS = {
            "transactionID", "transactionId", "transaction_id",
            "transactionReference", "transactionRef", "reference", "id"
    };

    private final WebhookIngestionService events;
    private final KcbSignatureVerifier signatures;
    private final ObjectMapper mapper;

    public KcbWebhookController(
            WebhookIngestionService events, KcbSignatureVerifier signatures, ObjectMapper mapper) {
        this.events = events;
        this.signatures = signatures;
        this.mapper = mapper;
    }

    @GetMapping(value = "/kcb", produces = MediaType.TEXT_PLAIN_VALUE)
    public String probe() {
        return """
                SmartMoney Intelligence notification endpoint
                Provider : KCB Bank Kenya

                POST instant payment notifications to this URL.
                A GET returns this message so the address can be verified.

                Checked at %s
                """.formatted(Instant.now());
    }

    @PostMapping(path = "/kcb", consumes = MediaType.ALL_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<KcbAcknowledgement> receive(
            @RequestBody(required = false) String rawBody,
            @RequestHeader Map<String, String> headers) {

        Boolean signatureValid = signatures.verify(rawBody, headers);
        if (Boolean.FALSE.equals(signatureValid)) {
            log.warn("KCB notification rejected: the Signature header did not verify");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new KcbAcknowledgement(null, "401", "Invalid or unconfigured signature"));
        }

        String transactionId = transactionId(rawBody);
        String externalEventId = transactionId == null ? payloadHash(rawBody) : transactionId;
        WebhookEventEntity event = events.record(BANK_ID, externalEventId, signatureValid, rawBody);
        events.process(event.getId());

        // The acknowledgement body is fixed by the BUNI specification.
        return ResponseEntity.ok(new KcbAcknowledgement(
                externalEventId,
                "0",
                "Notification received successfully"));
    }

    private static String payloadHash(String rawBody) {
        try {
            return "PAYLOAD-" + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest((rawBody == null ? "" : rawBody).getBytes(StandardCharsets.UTF_8)));
        } catch (Exception error) {
            throw new IllegalStateException("Could not identify the notification payload", error);
        }
    }

    private String transactionId(String rawBody) {
        if (rawBody == null || rawBody.isBlank()) {
            return null;
        }
        try {
            JsonNode root = mapper.readTree(rawBody);
            for (String field : TRANSACTION_ID_FIELDS) {
                JsonNode value = root.get(field);
                if (value != null && !value.isNull() && !value.asText().isBlank()) {
                    return value.asText();
                }
            }
        } catch (Exception ignored) {
            // Not JSON, or a shape we do not recognise. The stored event id is used instead.
        }
        return null;
    }

    /** Response body required by the KCB instant payment notification API. */
    public record KcbAcknowledgement(String transactionID, String statusCode, String statusMessage) {
    }
}

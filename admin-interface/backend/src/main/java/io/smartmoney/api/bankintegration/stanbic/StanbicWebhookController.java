package io.smartmoney.api.bankintegration.stanbic;

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
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Endpoint the bank posts payment notifications to, plus a readable GET so the
 * address can be verified in a browser by anyone checking it.
 */
@RestController
@RequestMapping("/api/v1/webhooks")
public class StanbicWebhookController {

    private static final Logger log = LoggerFactory.getLogger(StanbicWebhookController.class);
    private static final String BANK_ID = "stanbic";

    private final WebhookIngestionService events;
    private final StanbicSignatureVerifier signatures;

    public StanbicWebhookController(
            WebhookIngestionService events, StanbicSignatureVerifier signatures) {
        this.events = events;
        this.signatures = signatures;
    }

    @GetMapping(value = "/stanbic", produces = MediaType.TEXT_PLAIN_VALUE)
    public String probe() {
        return """
                SmartMoney Intelligence notification endpoint
                Provider : Stanbic Bank Kenya

                POST payment notifications to this URL.
                A GET returns this message so the address can be verified.

                Checked at %s
                """.formatted(Instant.now());
    }

    @PostMapping(path = "/stanbic", consumes = MediaType.ALL_VALUE)
    public ResponseEntity<Map<String, Object>> receive(
            @RequestBody(required = false) String rawBody,
            @RequestHeader Map<String, String> headers) {

        Boolean signatureValid = signatures.verify(rawBody, headers);
        String externalEventId = signatures.eventId(rawBody);

        if (Boolean.FALSE.equals(signatureValid)) {
            log.warn("Rejected a Stanbic notification with an invalid signature");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("status", "rejected", "reason", "Invalid or unconfigured signature"));
        }

        WebhookEventEntity event = events.record(BANK_ID, externalEventId, signatureValid, rawBody);
        events.process(event.getId());

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "received");
        body.put("eventId", event.getId());
        body.put("signatureVerified", signatureValid);
        return ResponseEntity.ok(body);
    }
}

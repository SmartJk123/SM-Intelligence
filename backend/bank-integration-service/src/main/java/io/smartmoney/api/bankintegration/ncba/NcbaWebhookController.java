package io.smartmoney.api.bankintegration.ncba;

import io.smartmoney.api.bankintegration.WebhookEventEntity;
import io.smartmoney.api.bankintegration.WebhookIngestionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;

/**
 * NCBA account level push notification endpoint.
 *
 * NCBA posts an XML body and reads a value back out of the Result element. Any
 * value containing the string OK counts as delivered, and anything else is
 * requeued on their side, so a refused notification is answered with HTTP 200
 * and a FAIL result rather than an error status. An envelope that cannot be
 * parsed at all is answered the same way, because a retry is harmless while a
 * silent drop is not.
 */
@RestController
@RequestMapping("/api/v1/webhooks")
public class NcbaWebhookController {

    private static final Logger log = LoggerFactory.getLogger(NcbaWebhookController.class);

    private static final String BANK_ID = "ncba";
    private static final String ACCEPTED = "OK";
    private static final String DUPLICATE = "OK: Duplicate Notification";

    /**
     * A notification is a few hundred bytes. Anything far larger is refused
     * before it is parsed, so an oversized body cannot be used to exhaust the
     * service. The container level limit belongs in the deployment.
     */
    private static final int MAX_BODY_CHARS = 512 * 1024;

    private static final String ENVELOPE = """
            <?xml version="1.0" encoding="UTF-8"?>
            <soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/">
              <soapenv:Header/>
              <soapenv:Body>
                <NCBAPaymentNotificationResult>
                  <Result>%s</Result>
                </NCBAPaymentNotificationResult>
              </soapenv:Body>
            </soapenv:Envelope>
            """;

    private final WebhookIngestionService events;
    private final NcbaSignatureVerifier signatures;

    public NcbaWebhookController(
            WebhookIngestionService events, NcbaSignatureVerifier signatures) {
        this.events = events;
        this.signatures = signatures;
    }

    @GetMapping(value = "/ncba", produces = MediaType.TEXT_PLAIN_VALUE)
    public String probe() {
        return """
                SmartMoney Intelligence notification endpoint
                Provider : NCBA Bank Kenya

                POST account level push notifications to this URL as XML.
                A GET returns this message so the address can be verified.

                Checked at %s
                """.formatted(Instant.now());
    }

    @PostMapping(path = "/ncba", consumes = MediaType.ALL_VALUE)
    public ResponseEntity<String> receive(@RequestBody(required = false) String rawBody) {
        if (rawBody != null && rawBody.length() > MAX_BODY_CHARS) {
            log.warn("Refused an NCBA notification of {} characters", rawBody.length());
            return xml("FAIL: The notification body is larger than this endpoint accepts");
        }

        NcbaNotification notification = NcbaNotification.parse(rawBody);
        NcbaSignatureVerifier.Verdict verdict = signatures.verify(notification);
        if (!verdict.accepted()) {
            log.warn("Refused an NCBA notification: {}", verdict.reason());
            return xml("FAIL: " + verdict.reason());
        }

        String reference = notification.reference();
        if (reference != null && events.alreadyRecorded(BANK_ID, reference)) {
            log.info("NCBA notification {} was already stored, answered as a duplicate", reference);
            return xml(DUPLICATE);
        }

        String externalEventId = reference == null ? payloadHash(rawBody) : reference;
        WebhookEventEntity event = events.record(
                BANK_ID, externalEventId, verdict.signatureValid(), rawBody);
        events.process(event.getId());

        log.info("Accepted NCBA notification {}", notification.describe());
        return xml(ACCEPTED);
    }

    private static ResponseEntity<String> xml(String result) {
        return ResponseEntity.status(HttpStatus.OK)
                .contentType(MediaType.parseMediaType("text/xml; charset=UTF-8"))
                .body(ENVELOPE.formatted(escape(result)));
    }

    private static String escape(String value) {
        return value == null ? "" : value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }

    /** Used as the stored reference when a notification carries no TransID. */
    private static String payloadHash(String rawBody) {
        try {
            return "PAYLOAD-" + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest((rawBody == null ? "" : rawBody).getBytes(StandardCharsets.UTF_8)));
        } catch (Exception error) {
            throw new IllegalStateException("Could not identify the notification payload", error);
        }
    }
}

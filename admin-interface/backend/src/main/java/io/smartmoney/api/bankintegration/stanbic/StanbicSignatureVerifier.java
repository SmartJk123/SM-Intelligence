package io.smartmoney.api.bankintegration.stanbic;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.smartmoney.api.bankintegration.SignaturePolicy;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;

/**
 * Verifies incoming notifications.
 *
 * Stanbic has not yet told us how the callback is authenticated, so this class
 * supports the common case, an HMAC-SHA256 signature over the raw body, and
 * returns null when verification is not configured. Confirm the real scheme
 * with the Integrations Team and adjust {@link #verify}.
 */
@Component
public class StanbicSignatureVerifier {

    private static final String[] EVENT_ID_FIELDS = {
            "eventId", "event_id", "transactionReference", "transactionRef", "reference", "id"
    };

    private final StanbicProperties props;
    private final ObjectMapper mapper;
    private final SignaturePolicy policy;

    public StanbicSignatureVerifier(
            StanbicProperties props, ObjectMapper mapper, SignaturePolicy policy) {
        this.props = props;
        this.mapper = mapper;
        this.policy = policy;
    }

    /**
     * @return true when the signature matches, false when it does not, and null
     *         when verification is disabled so the caller can record the event
     *         without claiming it was verified.
     */
    public Boolean verify(String rawBody, Map<String, String> headers) {
        if (!policy.verifies("stanbic", props.signatureVerification())) {
            return null;
        }
        if (!StanbicProperties.isPresent(props.signatureHeader())
                || !StanbicProperties.isPresent(props.signatureSecret())) {
            return false;
        }

        String provided = headerValue(headers, props.signatureHeader());
        if (provided == null) {
            return false;
        }

        String expected = hmacSha256(props.signatureSecret(), rawBody == null ? "" : rawBody);
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                provided.trim().toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Best effort event identifier. Falls back to a hash of the body so that a
     * repeated delivery of the same notification is still detected.
     */
    public String eventId(String rawBody) {
        if (rawBody == null || rawBody.isBlank()) {
            return null;
        }
        try {
            JsonNode root = mapper.readTree(rawBody);
            for (String field : EVENT_ID_FIELDS) {
                JsonNode value = root.get(field);
                if (value != null && !value.isNull() && !value.asText().isBlank()) {
                    return value.asText();
                }
            }
        } catch (Exception ignored) {
            // Not JSON, or not a shape we recognise. Fall through to the hash.
        }
        return sha256Hex(rawBody);
    }

    private static String headerValue(Map<String, String> headers, String name) {
        if (headers == null || name == null) {
            return null;
        }
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(name)) {
                return entry.getValue();
            }
        }
        return null;
    }

    private static String hmacSha256(String secret, String body) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception error) {
            throw new IllegalStateException("Unable to compute the signature", error);
        }
    }

    private static String sha256Hex(String body) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(body.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception error) {
            throw new IllegalStateException("Unable to hash the payload", error);
        }
    }
}

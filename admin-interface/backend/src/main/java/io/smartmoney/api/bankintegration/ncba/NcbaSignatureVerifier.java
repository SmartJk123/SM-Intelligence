package io.smartmoney.api.bankintegration.ncba;

import io.smartmoney.api.bankintegration.SignaturePolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Verifies an NCBA account level push notification.
 *
 * The specification defines the check in two parts:
 *
 * 1. The User and Password elements must be the values we gave NCBA, which is
 *    the only authentication the notification carries.
 * 2. HashVal is the SHA256 of the secret key followed by TransType, TransID,
 *    TransTime, TransAmount, AccountNr, Narrative, PhoneNr, CustomerName and
 *    Status, in that order. The digest is written as a lowercase hex string and
 *    then base64 encoded. The double encoding is unusual, but it is what all
 *    three of their code samples do, so it is reproduced exactly.
 *
 * Verification fails closed. With verification enabled and no secret key loaded,
 * every notification is refused rather than stored as if it were genuine.
 */
@Component
public class NcbaSignatureVerifier {

    private static final Logger log = LoggerFactory.getLogger(NcbaSignatureVerifier.class);

    private static final String BANK_ID = "ncba";

    private final NcbaProperties props;
    private final SignaturePolicy policy;

    public NcbaSignatureVerifier(NcbaProperties props, SignaturePolicy policy) {
        this.props = props;
        this.policy = policy;
    }

    /**
     * @param accepted     whether the notification may be stored and acted on
     * @param signatureValid true when the hash was checked and matched, null when
     *                       checking is disabled, so the event is never described
     *                       as verified when nothing was verified
     * @param reason       why a notification was refused, otherwise an empty string
     */
    public record Verdict(boolean accepted, Boolean signatureValid, String reason) {
    }

    public Verdict verify(NcbaNotification notification) {
        if (notification == null) {
            return new Verdict(false, null, "The body is not XML the service can read");
        }

        if (NcbaProperties.isPresent(props.username())
                && !props.username().equals(notification.user())) {
            return new Verdict(false, null, "The User element does not match the configured username");
        }
        if (NcbaProperties.isPresent(props.password())
                && !props.password().equals(notification.password())) {
            return new Verdict(false, null, "The Password element does not match the configured password");
        }

        if (!policy.verifies(BANK_ID, props.signatureVerification())) {
            return new Verdict(true, null, "");
        }
        if (!NcbaProperties.isPresent(props.secretKey())) {
            return new Verdict(false, null,
                    "Signature verification is enabled but no secret key is configured");
        }
        if (!NcbaProperties.isPresent(notification.hashVal())) {
            return new Verdict(false, false, "The HashVal element is missing");
        }

        String expected = hash(props.secretKey(), notification);
        String provided = strip(notification.hashVal());
        if (!MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                provided.getBytes(StandardCharsets.UTF_8))) {
            log.warn("NCBA notification rejected: HashVal did not verify for {}",
                    notification.reference());
            return new Verdict(false, false, "HashVal did not match the values sent");
        }
        return new Verdict(true, true, "");
    }

    /**
     * The HashVal the specification defines for this notification.
     *
     * @param secretKey the secret key given to NCBA, which is not transmitted
     */
    public static String hash(String secretKey, NcbaNotification notification) {
        String concatenated = nullToEmpty(secretKey)
                + nullToEmpty(notification.transType())
                + nullToEmpty(notification.transId())
                + nullToEmpty(notification.transTime())
                + nullToEmpty(notification.transAmount())
                + nullToEmpty(notification.accountNr())
                + nullToEmpty(notification.narrative())
                + nullToEmpty(notification.phoneNr())
                + nullToEmpty(notification.customerName())
                + nullToEmpty(notification.status());
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(concatenated.getBytes(StandardCharsets.UTF_8));
            String hex = HexFormat.of().formatHex(digest);
            return Base64.getEncoder().encodeToString(hex.getBytes(StandardCharsets.US_ASCII));
        } catch (Exception error) {
            throw new IllegalStateException("SHA-256 is not available", error);
        }
    }

    private static String strip(String value) {
        return value == null ? "" : value.replaceAll("\\s+", "");
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}

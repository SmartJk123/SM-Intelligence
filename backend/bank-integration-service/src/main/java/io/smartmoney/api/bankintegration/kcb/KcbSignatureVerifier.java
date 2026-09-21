package io.smartmoney.api.bankintegration.kcb;

import io.smartmoney.api.bankintegration.SignaturePolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Map;

/**
 * Verifies the KCB instant payment notification signature.
 *
 * BUNI specifies a Signature header carrying a SHA256withRSA signature of the
 * request body, signed with the KCB private key and verified with the KCB public
 * key. The public key is configured as a PEM block, a path to a .pem file, or a
 * base64 encoded X.509 key.
 */
@Component
public class KcbSignatureVerifier {

    private static final Logger log = LoggerFactory.getLogger(KcbSignatureVerifier.class);

    private final KcbProperties props;
    private final SignaturePolicy policy;
    private volatile PublicKey cachedKey;
    private volatile boolean keyLoadAttempted;

    public KcbSignatureVerifier(KcbProperties props, SignaturePolicy policy) {
        this.props = props;
        this.policy = policy;
    }

    /**
     * @return true when the signature is valid, false when it is not, and null
     *         when verification is disabled, so the caller can record the event
     *         without claiming it was verified.
     */
    public Boolean verify(String rawBody, Map<String, String> headers) {
        if (!policy.verifies("kcb", props.signatureVerification())) {
            return null;
        }
        if (!KcbProperties.isPresent(props.publicKey())) {
            return false;
        }
        String provided = headerValue(headers, props.signatureHeader());
        if (provided == null || provided.isBlank()) {
            return false;
        }
        try {
            PublicKey key = publicKey();
            if (key == null) {
                return false;
            }
            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initVerify(key);
            signature.update((rawBody == null ? "" : rawBody).getBytes(StandardCharsets.UTF_8));
            return signature.verify(decodeSignature(provided));
        } catch (Exception error) {
            log.warn("KCB signature check failed: {}", error.getMessage());
            return false;
        }
    }

    private byte[] decodeSignature(String value) {
        String cleaned = value.trim();
        try {
            return Base64.getDecoder().decode(cleaned);
        } catch (IllegalArgumentException error) {
            return Base64.getUrlDecoder().decode(cleaned);
        }
    }

    private PublicKey publicKey() {
        if (cachedKey != null || keyLoadAttempted) {
            return cachedKey;
        }
        synchronized (this) {
            if (cachedKey != null || keyLoadAttempted) {
                return cachedKey;
            }
            keyLoadAttempted = true;
            try {
                String configured = props.publicKey().trim();
                String material = configured;

                if (!configured.contains("BEGIN") && !configured.contains("\n")
                        && configured.length() < 260 && Files.exists(Path.of(configured))) {
                    material = Files.readString(Path.of(configured));
                }

                String base64 = material
                        .replace("-----BEGIN PUBLIC KEY-----", "")
                        .replace("-----END PUBLIC KEY-----", "")
                        .replaceAll("\\s+", "");

                byte[] der = Base64.getDecoder().decode(base64);
                cachedKey = KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(der));
                log.info("Loaded the KCB public key for signature verification");
            } catch (Exception error) {
                log.warn("Could not load the KCB public key: {}", error.getMessage());
                cachedKey = null;
            }
            return cachedKey;
        }
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
}

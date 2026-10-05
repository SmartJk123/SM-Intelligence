package io.smartmoney.api.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;

/**
 * Checks an admin token issued by identity-service.
 *
 * identity-service signs its tokens with HMAC, keyed by the UTF-8 bytes of
 * JWT_SECRET (jjwt picks HS256, HS384 or HS512 from the key length). This
 * service is given the same JWT_SECRET, recomputes the signature and accepts
 * the token only when it matches and has not expired. The role it carries is
 * then checked by the security rules, so a valid customer token is refused
 * with 403 rather than treated as no token at all.
 *
 * The role is read from the token, so a demotion or suspension made in
 * identity-service reaches this service when the token expires (24 hours by
 * default), not on the next request as it does in identity-service itself.
 */
public class AdminTokenVerifier {

    public static final String ADMIN_ROLE = "PLATFORM_ADMIN";

    private static final Map<String, String> ALGORITHMS = Map.of(
            "HS256", "HmacSHA256",
            "HS384", "HmacSHA384",
            "HS512", "HmacSHA512");

    private final byte[] secret;
    private final ObjectMapper mapper;

    public AdminTokenVerifier(String secret, ObjectMapper mapper) {
        this.secret = secret == null ? new byte[0] : secret.getBytes(StandardCharsets.UTF_8);
        this.mapper = mapper;
    }

    public boolean configured() {
        return secret.length >= 32;
    }

    /** A token whose signature and expiry checked out. */
    public record Verified(String userId, String role) {
    }

    /** @return the token's user and role when it is a valid, current identity-service token. */
    public Optional<Verified> verify(String token) {
        if (!configured() || token == null) {
            return Optional.empty();
        }
        String[] parts = token.split("\\.");
        if (parts.length != 3) {
            return Optional.empty();
        }
        try {
            JsonNode header = mapper.readTree(Base64.getUrlDecoder().decode(parts[0]));
            String algorithm = ALGORITHMS.get(header.path("alg").asText(""));
            if (algorithm == null) {
                return Optional.empty();
            }
            Mac mac = Mac.getInstance(algorithm);
            mac.init(new SecretKeySpec(secret, algorithm));
            byte[] expected = mac.doFinal((parts[0] + "." + parts[1]).getBytes(StandardCharsets.US_ASCII));
            byte[] provided = Base64.getUrlDecoder().decode(parts[2]);
            if (!MessageDigest.isEqual(expected, provided)) {
                return Optional.empty();
            }

            JsonNode claims = mapper.readTree(Base64.getUrlDecoder().decode(parts[1]));
            long expiresAt = claims.path("exp").asLong(0);
            if (expiresAt * 1000 <= System.currentTimeMillis()) {
                return Optional.empty();
            }
            String subject = claims.path("sub").asText("");
            String role = claims.path("role").asText("");
            return subject.isBlank() || role.isBlank() ? Optional.empty() : Optional.of(new Verified(subject, role));
        } catch (Exception malformed) {
            return Optional.empty();
        }
    }
}

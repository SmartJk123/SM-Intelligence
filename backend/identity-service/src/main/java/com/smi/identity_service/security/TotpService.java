package com.smi.identity_service.security;

import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Standard RFC 6238 TOTP (Time-Based One-Time Password) service.
 * Supports Base32 secret generation, 6-digit TOTP validation with +/- 1 time-step drift,
 * and emergency recovery backup codes.
 */
@Service
public class TotpService {

    private static final String BASE32_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
    private static final int TIME_STEP_SECONDS = 30;
    private static final int DIGITS = 6;
    private static final int MODULUS = 1_000_000;
    private static final String ISSUER = "SM-Intelligence";

    private final SecureRandom random = new SecureRandom();

    /**
     * Generates a 160-bit (20 byte) random secret key encoded as an RFC 4648 Base32 string.
     */
    public String generateSecret() {
        byte[] buffer = new byte[20];
        random.nextBytes(buffer);
        return encodeBase32(buffer);
    }

    /**
     * Constructs a standard otpauth:// URI suitable for authenticator apps (Google Authenticator, Authy, 1Password).
     */
    public String buildOtpAuthUri(String accountEmail, String base32Secret) {
        String encodedIssuer = URLEncoder.encode(ISSUER, StandardCharsets.UTF_8).replace("+", "%20");
        String encodedAccount = URLEncoder.encode(accountEmail, StandardCharsets.UTF_8).replace("+", "%20");
        return String.format(
                "otpauth://totp/%s:%s?secret=%s&issuer=%s&algorithm=SHA1&digits=%d&period=%d",
                encodedIssuer,
                encodedAccount,
                base32Secret,
                encodedIssuer,
                DIGITS,
                TIME_STEP_SECONDS
        );
    }

    /**
     * Generates 8 random 10-character backup recovery codes.
     */
    public List<String> generateBackupCodes() {
        List<String> codes = new ArrayList<>(8);
        String chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        for (int i = 0; i < 8; i++) {
            StringBuilder code = new StringBuilder(10);
            for (int j = 0; j < 10; j++) {
                code.append(chars.charAt(random.nextInt(chars.length())));
            }
            codes.add(code.substring(0, 5) + "-" + code.substring(5));
        }
        return codes;
    }

    /**
     * Hashes backup recovery codes with SHA-256 and serializes them as comma-separated hex strings.
     */
    public String hashBackupCodes(List<String> plainBackupCodes) {
        return plainBackupCodes.stream()
                .map(this::hashSingleCode)
                .collect(Collectors.joining(","));
    }

    /**
     * Verifies if a given code is a valid TOTP code or an unused emergency backup code.
     */
    public boolean verifyTotp(String base32Secret, String rawCode) {
        if (base32Secret == null || rawCode == null) {
            return false;
        }

        String code = rawCode.trim().replaceAll("\\s+", "");
        if (code.length() != DIGITS || !code.chars().allMatch(Character::isDigit)) {
            return false;
        }

        long currentSeconds = System.currentTimeMillis() / 1000L;
        long currentWindow = currentSeconds / TIME_STEP_SECONDS;

        // Allow +/- 1 time-step (window size 3) to accommodate clock drift
        for (long window = currentWindow - 1; window <= currentWindow + 1; window++) {
            String expected = generateCodeForWindow(base32Secret, window);
            if (expected != null && MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), code.getBytes(StandardCharsets.UTF_8))) {
                return true;
            }
        }

        return false;
    }

    /**
     * Generates the current 6-digit TOTP code for a secret at the current system time.
     */
    public String generateCurrentCode(String base32Secret) {
        long currentSeconds = System.currentTimeMillis() / 1000L;
        long currentWindow = currentSeconds / TIME_STEP_SECONDS;
        return generateCodeForWindow(base32Secret, currentWindow);
    }

    /**
     * Verifies and consumes a backup code. Returns updated hashed codes string on success,
     * or null if the code did not match any stored backup code.
     */
    public String verifyAndConsumeBackupCode(String rawBackupCode, String storedHashes) {
        if (rawBackupCode == null || storedHashes == null || storedHashes.isBlank()) {
            return null;
        }

        String inputHash = hashSingleCode(rawBackupCode.trim());
        List<String> hashes = new ArrayList<>(Arrays.asList(storedHashes.split(",")));

        int matchIndex = -1;
        for (int i = 0; i < hashes.size(); i++) {
            if (MessageDigest.isEqual(hashes.get(i).trim().getBytes(StandardCharsets.UTF_8), inputHash.getBytes(StandardCharsets.UTF_8))) {
                matchIndex = i;
                break;
            }
        }

        if (matchIndex != -1) {
            hashes.remove(matchIndex);
            return String.join(",", hashes);
        }

        return null;
    }

    private String generateCodeForWindow(String base32Secret, long window) {
        try {
            byte[] keyBytes = decodeBase32(base32Secret);
            byte[] data = ByteBuffer.allocate(8).putLong(window).array();

            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(keyBytes, "RAW"));
            byte[] hash = mac.doFinal(data);

            int offset = hash[hash.length - 1] & 0x0F;
            int binary = ((hash[offset] & 0x7F) << 24)
                    | ((hash[offset + 1] & 0xFF) << 16)
                    | ((hash[offset + 2] & 0xFF) << 8)
                    | (hash[offset + 3] & 0xFF);

            int otp = binary % MODULUS;
            return String.format("%06d", otp);
        } catch (Exception e) {
            return null;
        }
    }

    public String hashSingleCode(String plain) {
        try {
            String normalized = plain.toUpperCase().replace("-", "").trim();
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(normalized.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    public static String encodeBase32(byte[] data) {
        StringBuilder result = new StringBuilder();
        int buffer = 0;
        int bitsLeft = 0;
        for (byte b : data) {
            buffer = (buffer << 8) | (b & 0xFF);
            bitsLeft += 8;
            while (bitsLeft >= 5) {
                int index = (buffer >> (bitsLeft - 5)) & 0x1F;
                result.append(BASE32_ALPHABET.charAt(index));
                bitsLeft -= 5;
            }
        }
        if (bitsLeft > 0) {
            int index = (buffer << (5 - bitsLeft)) & 0x1F;
            result.append(BASE32_ALPHABET.charAt(index));
        }
        return result.toString();
    }

    public static byte[] decodeBase32(String base32) {
        String clean = base32.toUpperCase().replaceAll("[^A-Z2-7]", "");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int buffer = 0;
        int bitsLeft = 0;
        for (int i = 0; i < clean.length(); i++) {
            char c = clean.charAt(i);
            int val = BASE32_ALPHABET.indexOf(c);
            if (val < 0) continue;
            buffer = (buffer << 5) | val;
            bitsLeft += 5;
            if (bitsLeft >= 8) {
                out.write((buffer >> (bitsLeft - 8)) & 0xFF);
                bitsLeft -= 8;
            }
        }
        return out.toByteArray();
    }
}

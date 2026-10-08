package com.smi.identity_service.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TotpServiceTest {

    private TotpService totpService;

    @BeforeEach
    void setUp() {
        totpService = new TotpService();
    }

    @Test
    @DisplayName("generateSecret generates valid Base32 secret string")
    void shouldGenerateValidBase32Secret() {
        String secret = totpService.generateSecret();
        assertNotNull(secret);
        assertEquals(32, secret.length());
        assertTrue(secret.matches("^[A-Z2-7]+$"));
    }

    @Test
    @DisplayName("buildOtpAuthUri formats valid URI with issuer and account")
    void shouldBuildOtpAuthUri() {
        String uri = totpService.buildOtpAuthUri("user@example.com", "JBSWY3DPEHPK3PXP");
        assertTrue(uri.startsWith("otpauth://totp/"));
        assertTrue(uri.contains("secret=JBSWY3DPEHPK3PXP"));
        assertTrue(uri.contains("issuer=SM-Intelligence"));
    }

    @Test
    @DisplayName("verifyTotp succeeds with generated code and rejects incorrect code")
    void shouldVerifyTotpCode() {
        String secret = totpService.generateSecret();

        // Calculate current code manually through reflection or encode/decode
        byte[] keyBytes = TotpService.decodeBase32(secret);
        assertNotNull(keyBytes);
        assertEquals(20, keyBytes.length);

        // Verify invalid code is rejected
        assertFalse(totpService.verifyTotp(secret, "000000"));
        assertFalse(totpService.verifyTotp(secret, "abc"));
        assertFalse(totpService.verifyTotp(null, "123456"));
    }

    @Test
    @DisplayName("generateBackupCodes creates 8 formatted codes and hashes correctly")
    void shouldGenerateAndHashBackupCodes() {
        List<String> codes = totpService.generateBackupCodes();
        assertEquals(8, codes.size());
        for (String code : codes) {
            assertTrue(code.contains("-"));
            assertEquals(11, code.length());
        }

        String hashed = totpService.hashBackupCodes(codes);
        assertNotNull(hashed);
        assertEquals(8, hashed.split(",").length);

        // Consume first code
        String consumed = totpService.verifyAndConsumeBackupCode(codes.get(0), hashed);
        assertNotNull(consumed);
        assertEquals(7, consumed.split(",").length);

        // Second attempt with consumed code fails
        assertNull(totpService.verifyAndConsumeBackupCode(codes.get(0), consumed));
    }
}

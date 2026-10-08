package com.smi.identity_service.service;

import com.smi.identity_service.domain.User;
import com.smi.identity_service.dto.AuthResponse;
import com.smi.identity_service.dto.MfaSetupResponse;
import com.smi.identity_service.exception.InvalidCredentialsException;
import com.smi.identity_service.repository.UserRepository;
import com.smi.identity_service.security.JwtService;
import com.smi.identity_service.security.TotpService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MfaServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private TotpService totpService;

    @Mock
    private JwtService jwtService;

    @Mock
    private RefreshTokenService refreshTokenService;

    @Mock
    private PasswordEncoder passwordEncoder;

    private MfaService mfaService;

    @BeforeEach
    void setUp() {
        mfaService = new MfaService(
                userRepository,
                totpService,
                jwtService,
                refreshTokenService,
                passwordEncoder
        );
    }

    @Test
    @DisplayName("setupMfa generates secret and backup codes and updates user")
    void shouldSetupMfa() {
        UUID userId = UUID.randomUUID();
        User user = new User(userId, "Jane Doe", "jane@example.com", "hash");

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(totpService.generateSecret()).thenReturn("JBSWY3DPEHPK3PXP");
        when(totpService.generateBackupCodes()).thenReturn(List.of("CODE-1", "CODE-2"));
        when(totpService.hashBackupCodes(anyList())).thenReturn("hash1,hash2");
        when(totpService.buildOtpAuthUri("jane@example.com", "JBSWY3DPEHPK3PXP"))
                .thenReturn("otpauth://totp/SM-Intelligence:jane@example.com?secret=JBSWY3DPEHPK3PXP");

        MfaSetupResponse response = mfaService.setupMfa(userId);

        assertEquals("JBSWY3DPEHPK3PXP", response.secret());
        assertEquals(2, response.backupCodes().size());
        assertEquals("JBSWY3DPEHPK3PXP", user.getMfaSecret());
        assertEquals("hash1,hash2", user.getMfaBackupCodes());
        verify(userRepository).save(user);
    }

    @Test
    @DisplayName("enableMfa enables MFA when TOTP code is valid")
    void shouldEnableMfaOnValidCode() {
        UUID userId = UUID.randomUUID();
        User user = new User(userId, "Jane Doe", "jane@example.com", "hash");
        user.setMfaSecret("JBSWY3DPEHPK3PXP");

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(totpService.verifyTotp("JBSWY3DPEHPK3PXP", "123456")).thenReturn(true);

        mfaService.enableMfa(userId, "123456");

        assertTrue(user.getMfaEnabled());
        verify(userRepository).save(user);
    }

    @Test
    @DisplayName("enableMfa throws InvalidCredentialsException on invalid code")
    void shouldRejectInvalidMfaCode() {
        UUID userId = UUID.randomUUID();
        User user = new User(userId, "Jane Doe", "jane@example.com", "hash");
        user.setMfaSecret("JBSWY3DPEHPK3PXP");

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(totpService.verifyTotp("JBSWY3DPEHPK3PXP", "wrong")).thenReturn(false);

        assertThrows(InvalidCredentialsException.class, () -> mfaService.enableMfa(userId, "wrong"));
        assertFalse(user.getMfaEnabled());
    }

    @Test
    @DisplayName("verifyMfaLogin issues access and refresh tokens when code is valid")
    void shouldVerifyMfaLoginSuccessfully() {
        UUID userId = UUID.randomUUID();
        User user = new User(userId, "Jane Doe", "jane@example.com", "hash");
        user.setMfaEnabled(true);
        user.setMfaSecret("SECRET");

        when(jwtService.isMfaPendingToken("mfa.token")).thenReturn(true);
        when(jwtService.extractUserId("mfa.token")).thenReturn(userId);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(totpService.verifyTotp("SECRET", "123456")).thenReturn(true);
        when(jwtService.generateToken(user)).thenReturn("access.jwt.token");
        when(refreshTokenService.createRefreshToken(user)).thenReturn("new-refresh-token");

        AuthResponse authResponse = mfaService.verifyMfaLogin("mfa.token", "123456");

        assertNotNull(authResponse);
        assertEquals("access.jwt.token", authResponse.getToken());
        assertEquals("new-refresh-token", authResponse.getRefreshToken());
        assertEquals(userId, authResponse.getUserId());
    }

    @Test
    @DisplayName("disableMfa clears secret and disables MFA when password and code match")
    void shouldDisableMfaSuccessfully() {
        UUID userId = UUID.randomUUID();
        User user = new User(userId, "Jane Doe", "jane@example.com", "hash");
        user.setMfaEnabled(true);
        user.setMfaSecret("SECRET");

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("Password123!", "hash")).thenReturn(true);
        when(totpService.verifyTotp("SECRET", "123456")).thenReturn(true);

        mfaService.disableMfa(userId, "Password123!", "123456");

        assertFalse(user.getMfaEnabled());
        assertNull(user.getMfaSecret());
        assertNull(user.getMfaBackupCodes());
        verify(userRepository).save(user);
    }
}

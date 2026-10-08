package com.smi.identity_service.service;

import com.smi.identity_service.domain.User;
import com.smi.identity_service.dto.AuthResponse;
import com.smi.identity_service.dto.MfaSetupResponse;
import com.smi.identity_service.exception.AccountSuspendedException;
import com.smi.identity_service.exception.InvalidCredentialsException;
import com.smi.identity_service.exception.UserNotFoundException;
import com.smi.identity_service.repository.UserRepository;
import com.smi.identity_service.security.JwtService;
import com.smi.identity_service.security.TotpService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Service managing Multi-Factor Authentication (TOTP 2FA) setup, activation,
 * login challenge verification, and deactivation.
 */
@Service
@Transactional
public class MfaService {

    private final UserRepository userRepository;
    private final TotpService totpService;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final PasswordEncoder passwordEncoder;

    public MfaService(
            UserRepository userRepository,
            TotpService totpService,
            JwtService jwtService,
            RefreshTokenService refreshTokenService,
            PasswordEncoder passwordEncoder
    ) {
        this.userRepository = userRepository;
        this.totpService = totpService;
        this.jwtService = jwtService;
        this.refreshTokenService = refreshTokenService;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Initiates MFA setup by generating a Base32 secret and 8 backup recovery codes.
     * MFA remains disabled until confirmed with {@link #enableMfa(UUID, String)}.
     */
    public MfaSetupResponse setupMfa(UUID userId) {
        User user = getActiveUser(userId);

        String secret = totpService.generateSecret();
        List<String> backupCodes = totpService.generateBackupCodes();
        String hashedBackupCodes = totpService.hashBackupCodes(backupCodes);

        user.setMfaSecret(secret);
        user.setMfaBackupCodes(hashedBackupCodes);
        userRepository.save(user);

        String otpauthUri = totpService.buildOtpAuthUri(user.getEmailAddress(), secret);
        return new MfaSetupResponse(secret, otpauthUri, backupCodes);
    }

    /**
     * Confirms and activates MFA after verifying that the user successfully entered a TOTP code.
     */
    public void enableMfa(UUID userId, String code) {
        User user = getActiveUser(userId);

        if (user.getMfaSecret() == null || user.getMfaSecret().isBlank()) {
            throw new IllegalArgumentException("MFA setup must be initiated first");
        }

        if (!totpService.verifyTotp(user.getMfaSecret(), code)) {
            throw new InvalidCredentialsException("Invalid verification code");
        }

        user.setMfaEnabled(true);
        userRepository.save(user);
    }

    /**
     * Verifies the second factor (TOTP code or recovery code) during two-stage login
     * and exchanges the ephemeral MFA challenge token for a full session token pair.
     */
    public AuthResponse verifyMfaLogin(String mfaToken, String codeOrBackup) {
        if (mfaToken == null || mfaToken.isBlank() || !jwtService.isMfaPendingToken(mfaToken)) {
            throw new InvalidCredentialsException("Invalid or expired MFA session");
        }

        UUID userId = jwtService.extractUserId(mfaToken);
        User user = getActiveUser(userId);

        if (!Boolean.TRUE.equals(user.getMfaEnabled())) {
            throw new InvalidCredentialsException("MFA is not enabled for this user");
        }

        boolean totpValid = totpService.verifyTotp(user.getMfaSecret(), codeOrBackup);
        if (!totpValid) {
            String updatedBackupCodes = totpService.verifyAndConsumeBackupCode(codeOrBackup, user.getMfaBackupCodes());
            if (updatedBackupCodes == null) {
                throw new InvalidCredentialsException("Invalid verification code or recovery code");
            }
            user.setMfaBackupCodes(updatedBackupCodes);
            userRepository.save(user);
        }

        String accessToken = jwtService.generateToken(user);
        String refreshToken = refreshTokenService.createRefreshToken(user);

        return new AuthResponse(
                accessToken,
                jwtService.getExpirationMs(),
                user.getId(),
                user.getName(),
                user.getEmailAddress(),
                user.getPhoneNumber(),
                user.getAccountType(),
                user.getRole(),
                refreshToken
        );
    }

    /**
     * Disables MFA requiring confirmation of both account password and an authenticator/backup code.
     */
    public void disableMfa(UUID userId, String password, String codeOrBackup) {
        User user = getActiveUser(userId);

        if (!Boolean.TRUE.equals(user.getMfaEnabled())) {
            return;
        }

        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new InvalidCredentialsException("Invalid password");
        }

        boolean totpValid = totpService.verifyTotp(user.getMfaSecret(), codeOrBackup);
        if (!totpValid) {
            String updated = totpService.verifyAndConsumeBackupCode(codeOrBackup, user.getMfaBackupCodes());
            if (updated == null) {
                throw new InvalidCredentialsException("Invalid verification code or recovery code");
            }
        }

        user.setMfaEnabled(false);
        user.setMfaSecret(null);
        user.setMfaBackupCodes(null);
        userRepository.save(user);
    }

    private User getActiveUser(UUID userId) {
        User user = userRepository.findById(userId)
                .filter(u -> u.getDeletedAt() == null)
                .orElseThrow(() -> new UserNotFoundException("User not found"));

        if (user.isSuspended()) {
            throw new AccountSuspendedException();
        }

        return user;
    }
}

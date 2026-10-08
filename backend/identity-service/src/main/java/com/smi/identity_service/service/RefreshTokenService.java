package com.smi.identity_service.service;

import com.smi.identity_service.domain.RefreshToken;
import com.smi.identity_service.domain.User;
import com.smi.identity_service.dto.AuthResponse;
import com.smi.identity_service.exception.AccountSuspendedException;
import com.smi.identity_service.exception.InvalidCredentialsException;
import com.smi.identity_service.repository.RefreshTokenRepository;
import com.smi.identity_service.repository.UserRepository;
import com.smi.identity_service.security.JwtService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Manages refresh tokens, cryptographic hashing, rotation, and session revocation.
 */
@Service
@Transactional
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final long refreshExpirationDays;
    private final SecureRandom random = new SecureRandom();

    public RefreshTokenService(
            RefreshTokenRepository refreshTokenRepository,
            UserRepository userRepository,
            JwtService jwtService,
            @Value("${jwt.refresh-expiration-days:30}") long refreshExpirationDays
    ) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.userRepository = userRepository;
        this.jwtService = jwtService;
        this.refreshExpirationDays = refreshExpirationDays;
    }

    /**
     * Issues a cryptographically secure refresh token for a user, stores its SHA-256 hash,
     * and returns the plaintext token to the client.
     */
    public String createRefreshToken(User user) {
        byte[] secret = new byte[32];
        random.nextBytes(secret);
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(secret);

        OffsetDateTime expiresAt = OffsetDateTime.now().plusDays(refreshExpirationDays);
        RefreshToken entity = new RefreshToken(user.getId(), hash(rawToken), expiresAt);
        refreshTokenRepository.save(entity);

        return rawToken;
    }

    /**
     * Rotates a refresh token: invalidates the old token and issues a fresh access token
     * paired with a new refresh token.
     */
    public AuthResponse rotateRefreshToken(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            throw new InvalidCredentialsException("Refresh token is required");
        }

        String tokenHash = hash(rawRefreshToken.trim());
        RefreshToken token = refreshTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new InvalidCredentialsException("Invalid, expired, or revoked refresh token"));

        OffsetDateTime now = OffsetDateTime.now();
        if (!token.isUsable(now)) {
            throw new InvalidCredentialsException("Invalid, expired, or revoked refresh token");
        }

        User user = userRepository.findById(token.getUserId())
                .filter(u -> u.getDeletedAt() == null)
                .orElseThrow(() -> new InvalidCredentialsException("User account not found"));

        if (user.isSuspended()) {
            throw new AccountSuspendedException();
        }

        // Revoke the old token (Single-use refresh token rotation)
        token.setRevokedAt(now);
        refreshTokenRepository.save(token);

        // Generate new access token and new rotated refresh token
        String newAccessToken = jwtService.generateToken(user);
        String newRefreshToken = createRefreshToken(user);

        return new AuthResponse(
                newAccessToken,
                jwtService.getExpirationMs(),
                user.getId(),
                user.getName(),
                user.getEmailAddress(),
                user.getPhoneNumber(),
                user.getAccountType(),
                user.getRole(),
                newRefreshToken
        );
    }

    /**
     * Revokes a specific refresh token (e.g. during single device logout).
     */
    public void revokeToken(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            return;
        }
        String tokenHash = hash(rawRefreshToken.trim());
        refreshTokenRepository.findByTokenHash(tokenHash).ifPresent(token -> {
            if (token.getRevokedAt() == null) {
                token.setRevokedAt(OffsetDateTime.now());
                refreshTokenRepository.save(token);
            }
        });
    }

    /**
     * Revokes all active refresh tokens for a user (e.g. password change or global logout).
     */
    public void revokeAllForUser(UUID userId) {
        OffsetDateTime now = OffsetDateTime.now();
        refreshTokenRepository.findByUserIdAndRevokedAtIsNull(userId).forEach(token -> {
            token.setRevokedAt(now);
            refreshTokenRepository.save(token);
        });
    }

    private static String hash(String raw) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm missing", e);
        }
    }
}

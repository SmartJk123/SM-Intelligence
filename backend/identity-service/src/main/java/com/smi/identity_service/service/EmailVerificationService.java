package com.smi.identity_service.service;

import com.smi.identity_service.domain.EmailVerificationToken;
import com.smi.identity_service.domain.User;
import com.smi.identity_service.exception.AccountSuspendedException;
import com.smi.identity_service.exception.InvalidEmailVerificationTokenException;
import com.smi.identity_service.repository.EmailVerificationTokenRepository;
import com.smi.identity_service.repository.UserRepository;
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
 * Service managing user email verification flows, one-time verification tokens,
 * cryptographic hashing, and delivery.
 */
@Service
@Transactional
public class EmailVerificationService {

    private static final long RESEND_AFTER_SECONDS = 60;

    private final UserRepository userRepository;
    private final EmailVerificationTokenRepository tokenRepository;
    private final EmailVerificationMailer mailer;
    private final String verificationBaseUrl;
    private final long validHours;
    private final SecureRandom random = new SecureRandom();

    public EmailVerificationService(
            UserRepository userRepository,
            EmailVerificationTokenRepository tokenRepository,
            EmailVerificationMailer mailer,
            @Value("${app.email-verification.url:http://localhost:4200/verify-email}") String verificationBaseUrl,
            @Value("${app.email-verification.valid-hours:24}") long validHours
    ) {
        this.userRepository = userRepository;
        this.tokenRepository = tokenRepository;
        this.mailer = mailer;
        this.verificationBaseUrl = verificationBaseUrl;
        this.validHours = validHours;
    }

    /**
     * Creates and stores a secure verification token, then sends the verification link.
     */
    public String sendVerificationEmail(User user) {
        if (user == null || user.getDeletedAt() != null || user.isSuspended()) {
            return null;
        }

        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        String tokenHash = hash(rawToken);
        OffsetDateTime expiresAt = OffsetDateTime.now().plusHours(validHours);
        EmailVerificationToken entity = new EmailVerificationToken(user.getId(), tokenHash, expiresAt);
        tokenRepository.save(entity);

        String delimiter = verificationBaseUrl.contains("?") ? "&token=" : "?token=";
        String link = verificationBaseUrl + delimiter + rawToken;

        String to = user.getEmailAddress();
        String name = user.getName();
        Thread.ofVirtual().start(() -> mailer.sendVerificationLink(to, name, link, validHours));

        return rawToken;
    }

    /**
     * Confirms user email ownership given a raw verification token.
     */
    public User verifyEmail(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new InvalidEmailVerificationTokenException("Verification token is required");
        }

        String tokenHash = hash(rawToken.trim());
        EmailVerificationToken token = tokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(InvalidEmailVerificationTokenException::new);

        OffsetDateTime now = OffsetDateTime.now();
        if (!token.isUsable(now)) {
            throw new InvalidEmailVerificationTokenException();
        }

        User user = userRepository.findById(token.getUserId())
                .filter(u -> u.getDeletedAt() == null)
                .orElseThrow(InvalidEmailVerificationTokenException::new);

        if (user.isSuspended()) {
            throw new AccountSuspendedException();
        }

        user.setIsEmailVerified(true);
        userRepository.save(user);

        token.setUsedAt(now);
        tokenRepository.save(token);

        return user;
    }

    /**
     * Resends email verification if the user exists, is active, and is unverified.
     * Rate limited by RESEND_AFTER_SECONDS.
     */
    public void requestVerification(String emailAddress) {
        if (emailAddress == null || emailAddress.isBlank()) {
            return;
        }

        User user = userRepository.findByEmailAddressAndDeletedAtIsNull(emailAddress.trim().toLowerCase())
                .orElse(null);

        if (user == null || user.isSuspended() || Boolean.TRUE.equals(user.getIsEmailVerified())) {
            return;
        }

        OffsetDateTime now = OffsetDateTime.now();
        var openTokens = tokenRepository.findByUserIdAndUsedAtIsNull(user.getId());
        if (openTokens.stream().anyMatch(t -> t.getCreatedAt().isAfter(now.minusSeconds(RESEND_AFTER_SECONDS)))) {
            return;
        }

        sendVerificationEmail(user);
    }

    /**
     * Resends email verification for an authenticated user.
     */
    public void requestVerificationForUser(UUID userId) {
        if (userId == null) {
            return;
        }

        User user = userRepository.findById(userId)
                .filter(u -> u.getDeletedAt() == null)
                .orElse(null);

        if (user == null || user.isSuspended() || Boolean.TRUE.equals(user.getIsEmailVerified())) {
            return;
        }

        OffsetDateTime now = OffsetDateTime.now();
        var openTokens = tokenRepository.findByUserIdAndUsedAtIsNull(user.getId());
        if (openTokens.stream().anyMatch(t -> t.getCreatedAt().isAfter(now.minusSeconds(RESEND_AFTER_SECONDS)))) {
            return;
        }

        sendVerificationEmail(user);
    }

    private String hash(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }
}

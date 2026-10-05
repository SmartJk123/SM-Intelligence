package com.smi.identity_service.service;

import com.smi.identity_service.domain.PasswordResetToken;
import com.smi.identity_service.domain.User;
import com.smi.identity_service.exception.InvalidResetTokenException;
import com.smi.identity_service.repository.PasswordResetTokenRepository;
import com.smi.identity_service.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Forgot-password flow: a user asks for a link by email, then uses it once to
 * choose a new password.
 *
 * The request answers the same way whether or not the address has an account,
 * and the email is sent off the request thread, so neither the reply nor its
 * timing reveals who is registered.
 */
@Service
@Transactional
public class PasswordResetService {

    /** A new link for the same account is not sent more often than this. */
    private static final long RESEND_AFTER_SECONDS = 60;

    private final UserRepository users;
    private final PasswordResetTokenRepository tokens;
    private final PasswordEncoder passwordEncoder;
    private final PasswordResetMailer mailer;
    private final String resetUrl;
    private final long validMinutes;
    private final SecureRandom random = new SecureRandom();

    public PasswordResetService(UserRepository users, PasswordResetTokenRepository tokens,
                                PasswordEncoder passwordEncoder, PasswordResetMailer mailer,
                                @Value("${app.password-reset.url:http://localhost:4200/reset-password}") String resetUrl,
                                @Value("${app.password-reset.valid-minutes:60}") long validMinutes) {
        this.users = users;
        this.tokens = tokens;
        this.passwordEncoder = passwordEncoder;
        this.mailer = mailer;
        this.resetUrl = resetUrl;
        this.validMinutes = validMinutes;
    }

    /** Emails a reset link when the address belongs to an active account. Silent otherwise. */
    public void requestReset(String emailAddress) {
        if (emailAddress == null || emailAddress.isBlank()) {
            return;
        }
        User user = users.findByEmailAddressAndDeletedAtIsNull(emailAddress.trim().toLowerCase()).orElse(null);
        if (user == null || user.isSuspended()) {
            return;
        }
        OffsetDateTime now = OffsetDateTime.now();
        var open = tokens.findByUserIdAndUsedAtIsNull(user.getId());
        if (open.stream().anyMatch(token -> token.getCreatedAt().isAfter(now.minusSeconds(RESEND_AFTER_SECONDS)))) {
            return;
        }
        // Only the newest link works.
        open.forEach(token -> token.setUsedAt(now));

        byte[] secret = new byte[32];
        random.nextBytes(secret);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(secret);
        tokens.save(new PasswordResetToken(user.getId(), hash(token), now.plusMinutes(validMinutes)));

        String link = resetUrl + (resetUrl.contains("?") ? "&" : "?") + "token=" + token;
        String to = user.getEmailAddress();
        String name = user.getName();
        Thread.ofVirtual().start(() -> mailer.sendResetLink(to, name, link, validMinutes));
    }

    /** Sets the new password and uses up the link. */
    public void resetPassword(String token, String newPassword) {
        OffsetDateTime now = OffsetDateTime.now();
        PasswordResetToken reset = token == null || token.isBlank() ? null
                : tokens.findByTokenHash(hash(token.trim())).orElse(null);
        if (reset == null || !reset.isUsable(now)) {
            throw new InvalidResetTokenException();
        }
        User user = users.findById(reset.getUserId())
                .filter(candidate -> candidate.getDeletedAt() == null && !candidate.isSuspended())
                .orElseThrow(InvalidResetTokenException::new);

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setUpdatedAt(now);
        users.save(user);
        tokens.findByUserIdAndUsedAtIsNull(user.getId()).forEach(open -> open.setUsedAt(now));
    }

    private static String hash(String token) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}

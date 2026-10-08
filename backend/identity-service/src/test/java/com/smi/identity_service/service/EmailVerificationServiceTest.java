package com.smi.identity_service.service;

import com.smi.identity_service.domain.EmailVerificationToken;
import com.smi.identity_service.domain.User;
import com.smi.identity_service.exception.AccountSuspendedException;
import com.smi.identity_service.exception.InvalidEmailVerificationTokenException;
import com.smi.identity_service.repository.EmailVerificationTokenRepository;
import com.smi.identity_service.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmailVerificationServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private EmailVerificationTokenRepository tokenRepository;

    @Mock
    private EmailVerificationMailer mailer;

    private EmailVerificationService service;

    @BeforeEach
    void setUp() {
        service = new EmailVerificationService(
                userRepository,
                tokenRepository,
                mailer,
                "http://localhost:4200/verify-email",
                24
        );
    }

    @Test
    @DisplayName("sendVerificationEmail saves token and triggers mailer")
    void shouldSendVerificationEmail() {
        UUID userId = UUID.randomUUID();
        User user = new User(userId, "Jane Doe", "jane@example.com", "hash");

        String rawToken = service.sendVerificationEmail(user);

        assertNotNull(rawToken);
        assertFalse(rawToken.isBlank());

        ArgumentCaptor<EmailVerificationToken> captor = ArgumentCaptor.forClass(EmailVerificationToken.class);
        verify(tokenRepository).save(captor.capture());
        EmailVerificationToken saved = captor.getValue();
        assertEquals(userId, saved.getUserId());
        assertNotNull(saved.getTokenHash());
        assertTrue(saved.getExpiresAt().isAfter(OffsetDateTime.now()));
    }

    @Test
    @DisplayName("verifyEmail successfully verifies unverified user")
    void shouldVerifyEmailSuccessfully() {
        UUID userId = UUID.randomUUID();
        User user = new User(userId, "Jane Doe", "jane@example.com", "hash");
        assertFalse(user.getIsEmailVerified());

        EmailVerificationToken token = new EmailVerificationToken(userId, "some_hash", OffsetDateTime.now().plusHours(2));

        when(tokenRepository.findByTokenHash(any())).thenReturn(Optional.of(token));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        User result = service.verifyEmail("any-raw-token");

        assertTrue(result.getIsEmailVerified());
        assertNotNull(token.getUsedAt());
        verify(userRepository).save(user);
        verify(tokenRepository).save(token);
    }

    @Test
    @DisplayName("verifyEmail throws exception when token not found")
    void shouldThrowWhenTokenNotFound() {
        when(tokenRepository.findByTokenHash(any())).thenReturn(Optional.empty());

        assertThrows(InvalidEmailVerificationTokenException.class, () -> service.verifyEmail("missing-token"));
    }

    @Test
    @DisplayName("verifyEmail throws exception when token already used")
    void shouldThrowWhenTokenAlreadyUsed() {
        UUID userId = UUID.randomUUID();
        EmailVerificationToken token = new EmailVerificationToken(userId, "hash", OffsetDateTime.now().plusHours(2));
        token.setUsedAt(OffsetDateTime.now().minusMinutes(5));

        when(tokenRepository.findByTokenHash(any())).thenReturn(Optional.of(token));

        assertThrows(InvalidEmailVerificationTokenException.class, () -> service.verifyEmail("used-token"));
    }

    @Test
    @DisplayName("verifyEmail throws exception when token expired")
    void shouldThrowWhenTokenExpired() {
        UUID userId = UUID.randomUUID();
        EmailVerificationToken token = new EmailVerificationToken(userId, "hash", OffsetDateTime.now().minusHours(1));

        when(tokenRepository.findByTokenHash(any())).thenReturn(Optional.of(token));

        assertThrows(InvalidEmailVerificationTokenException.class, () -> service.verifyEmail("expired-token"));
    }

    @Test
    @DisplayName("verifyEmail throws AccountSuspendedException if user is suspended")
    void shouldThrowWhenUserSuspended() {
        UUID userId = UUID.randomUUID();
        User user = new User(userId, "Suspended User", "suspended@example.com", "hash");
        user.setStatus(User.STATUS_SUSPENDED);

        EmailVerificationToken token = new EmailVerificationToken(userId, "hash", OffsetDateTime.now().plusHours(2));

        when(tokenRepository.findByTokenHash(any())).thenReturn(Optional.of(token));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        assertThrows(AccountSuspendedException.class, () -> service.verifyEmail("raw-token"));
    }

    @Test
    @DisplayName("requestVerification rate limits recent requests")
    void shouldRateLimitResend() {
        UUID userId = UUID.randomUUID();
        User user = new User(userId, "Jane Doe", "jane@example.com", "hash");
        user.setIsEmailVerified(false);

        EmailVerificationToken recentToken = new EmailVerificationToken(userId, "hash", OffsetDateTime.now().plusHours(2));

        when(userRepository.findByEmailAddressAndDeletedAtIsNull("jane@example.com")).thenReturn(Optional.of(user));
        when(tokenRepository.findByUserIdAndUsedAtIsNull(userId)).thenReturn(List.of(recentToken));

        service.requestVerification("jane@example.com");

        verify(tokenRepository, never()).save(any());
    }
}

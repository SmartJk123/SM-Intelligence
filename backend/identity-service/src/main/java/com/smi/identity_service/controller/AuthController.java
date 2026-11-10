package com.smi.identity_service.controller;

import com.smi.identity_service.activity.ActivityLog;
import com.smi.identity_service.domain.User;
import com.smi.identity_service.dto.AuthResponse;
import com.smi.identity_service.dto.ChangePasswordRequest;
import com.smi.identity_service.dto.ForgotPasswordRequest;
import com.smi.identity_service.dto.IntrospectTokenRequest;
import com.smi.identity_service.dto.LoginRequest;
import com.smi.identity_service.dto.MfaDisableRequest;
import com.smi.identity_service.dto.MfaSetupResponse;
import com.smi.identity_service.dto.MfaVerifyRequest;
import com.smi.identity_service.dto.RefreshTokenRequest;
import com.smi.identity_service.dto.RegisterRequest;
import com.smi.identity_service.dto.ResendVerificationRequest;
import com.smi.identity_service.dto.ResetPasswordRequest;
import com.smi.identity_service.dto.TokenIntrospectionResponse;
import com.smi.identity_service.dto.UpdateProfileRequest;
import com.smi.identity_service.dto.UserProfileResponse;
import com.smi.identity_service.dto.VerifyEmailRequest;
import com.smi.identity_service.exception.AccountSuspendedException;
import com.smi.identity_service.exception.InvalidCredentialsException;
import com.smi.identity_service.exception.StatusException;
import com.smi.identity_service.security.AdminAccounts;
import com.smi.identity_service.security.JwtService;
import com.smi.identity_service.service.RequestDevice;
import com.smi.identity_service.settings.PlatformSettings;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import java.time.OffsetDateTime;
import java.util.List;
import com.smi.identity_service.service.EmailService;
import com.smi.identity_service.service.EmailVerificationService;
import com.smi.identity_service.service.MfaService;
import com.smi.identity_service.service.PasswordResetService;
import com.smi.identity_service.service.RefreshTokenService;
import com.smi.identity_service.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserService userService;
    private final JwtService jwtService;
    private final PasswordResetService passwordReset;
    private final RefreshTokenService refreshTokenService;
    private final EmailVerificationService emailVerificationService;
    private final MfaService mfaService;
    private final EmailService emailService;
    private final ActivityLog activity;
    private final PlatformSettings settings;
    private final AdminAccounts adminAccounts;

    /** One signed-in device of the current user, for the Security and Profile pages. */
    public record SessionView(UUID id, String device, String userAgent, String ipAddress, OffsetDateTime signedInAt,
                              boolean current) {
    }

    public AuthController(UserService userService,
                          JwtService jwtService,
                          PasswordResetService passwordReset,
                          RefreshTokenService refreshTokenService,
                          EmailVerificationService emailVerificationService,
                          MfaService mfaService,
                          EmailService emailService,
                          ActivityLog activity,
                          PlatformSettings settings,
                          AdminAccounts adminAccounts) {
        this.userService = userService;
        this.jwtService = jwtService;
        this.passwordReset = passwordReset;
        this.refreshTokenService = refreshTokenService;
        this.emailVerificationService = emailVerificationService;
        this.mfaService = mfaService;
        this.emailService = emailService;
        this.activity = activity;
        this.settings = settings;
        this.adminAccounts = adminAccounts;
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        if (settings.flag("system", "maintenanceMode")) {
            throw new StatusException(HttpStatus.SERVICE_UNAVAILABLE, "MAINTENANCE",
                    "SmartMoney is down for maintenance. Please try again later.");
        }
        User user = userService.registerUser(request);
        RefreshTokenService.Issued session = refreshTokenService.issue(user);
        String token = session.accessToken();
        String refreshToken = session.refreshToken();
        emailVerificationService.sendVerificationEmail(user);
        onSignUp(user);

        AuthResponse response = new AuthResponse(
                token,
                jwtService.getExpirationMs(),
                user.getId(),
                user.getName(),
                user.getEmailAddress(),
                user.getPhoneNumber(),
                user.getAccountType(),
                user.getRole(),
                refreshToken
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        refuseWhileLockedOut(request.getEmailAddress());
        boolean firstSignIn = userService.findByEmail(request.getEmailAddress())
                .map(existing -> existing.getLastLoginAt() == null)
                .orElse(false);
        User user;
        try {
            user = userService.authenticate(request);
        } catch (InvalidCredentialsException error) {
            activity.audit(ActivityLog.Actor.unknown(request.getEmailAddress()), "SIGN_IN_FAILED", "USER", null,
                    request.getEmailAddress(), "Wrong email address or password", ActivityLog.FAILURE);
            throw error;
        } catch (AccountSuspendedException error) {
            activity.audit(ActivityLog.Actor.unknown(request.getEmailAddress()), "SIGN_IN_BLOCKED", "USER", null,
                    request.getEmailAddress(), "Suspended account tried to sign in", ActivityLog.FAILURE);
            activity.notifyAdmins("Security", "WARNING", "Suspended account tried to sign in",
                    request.getEmailAddress() + " tried to sign in while suspended.");
            throw error;
        }
        refuseByPolicy(user);
        onSignIn(user, firstSignIn);
        if (Boolean.TRUE.equals(user.getMfaEnabled())) {
            String mfaChallengeToken = jwtService.generateMfaChallengeToken(user);
            return ResponseEntity.ok(AuthResponse.mfaChallenge(mfaChallengeToken));
        }

        RefreshTokenService.Issued session = refreshTokenService.issue(user);
        String token = session.accessToken();
        String refreshToken = session.refreshToken();

        AuthResponse response = new AuthResponse(
                token,
                jwtService.getExpirationMs(),
                user.getId(),
                user.getName(),
                user.getEmailAddress(),
                user.getPhoneNumber(),
                user.getAccountType(),
                user.getRole(),
                refreshToken
        );

        return ResponseEntity.ok(response);
    }

    @GetMapping("/me")
    public ResponseEntity<UserProfileResponse> getCurrentUser(
            @RequestHeader(value = "Authorization", required = false) String authHeader
    ) {
        User user = resolveAuthenticatedUser(authHeader);
        return ResponseEntity.ok(UserProfileResponse.fromUser(user));
    }

    @PutMapping("/me")
    public ResponseEntity<UserProfileResponse> updateProfile(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @Valid @RequestBody UpdateProfileRequest request
    ) {
        User user = resolveAuthenticatedUser(authHeader);
        User updated = userService.updateSelfProfile(user.getId(), request);
        return ResponseEntity.ok(UserProfileResponse.fromUser(updated));
    }

    @PatchMapping("/me")
    public ResponseEntity<UserProfileResponse> patchProfile(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @Valid @RequestBody UpdateProfileRequest request
    ) {
        return updateProfile(authHeader, request);
    }

    @PostMapping("/change-password")
    public ResponseEntity<Map<String, String>> changePassword(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @Valid @RequestBody ChangePasswordRequest request
    ) {
        User user = resolveAuthenticatedUser(authHeader);
        userService.changePassword(user.getId(), request);
        // Every other device is signed out; the one that changed the password stays signed in.
        refreshTokenService.revokeAllExcept(user.getId(), jwtService.extractSessionId(authHeader.substring(7)));
        activity.audit(ActivityLog.Actor.of(user), "PASSWORD_CHANGED", user, "Other sessions signed out");
        return ResponseEntity.ok(Map.of("message", "Password changed successfully"));
    }

    /** The current user's signed-in devices, newest first. */
    @GetMapping("/sessions")
    public List<SessionView> sessions(@RequestHeader(value = "Authorization", required = false) String authHeader) {
        User user = resolveAuthenticatedUser(authHeader);
        UUID current = jwtService.extractSessionId(authHeader.substring(7));
        return refreshTokenService.activeSessions(user.getId()).stream()
                .map(session -> new SessionView(session.getId(), RequestDevice.describe(session.getUserAgent()),
                        session.getUserAgent(), session.getIpAddress(), session.getCreatedAt(),
                        session.getId().equals(current)))
                .toList();
    }

    /** Signs one of the current user's devices out. */
    @DeleteMapping("/sessions/{id}")
    public ResponseEntity<Void> revokeSession(@RequestHeader(value = "Authorization", required = false) String authHeader,
                                              @PathVariable UUID id) {
        User user = resolveAuthenticatedUser(authHeader);
        if (!refreshTokenService.revokeSession(user.getId(), id)) {
            return ResponseEntity.notFound().build();
        }
        activity.audit(ActivityLog.Actor.of(user), "SESSION_REVOKED", user, "Signed out another device");
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        AuthResponse response = refreshTokenService.rotateRefreshToken(request.refreshToken());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/logout")
    public ResponseEntity<Map<String, String>> logout(
            @RequestBody(required = false) RefreshTokenRequest request,
            @RequestHeader(value = "Authorization", required = false) String authHeader
    ) {
        if (request != null && request.refreshToken() != null && !request.refreshToken().isBlank()) {
            refreshTokenService.revokeToken(request.refreshToken());
        }
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            if (jwtService.isTokenValid(token)) {
                try {
                    UUID userId = jwtService.extractUserId(token);
                    UUID sessionId = jwtService.extractSessionId(token);
                    // Signing out ends this device only; older tokens without a session end them all.
                    if (sessionId != null) {
                        refreshTokenService.revokeSession(userId, sessionId);
                    } else {
                        refreshTokenService.revokeAllForUser(userId);
                    }
                    userService.findById(userId).ifPresent(user ->
                            activity.audit(ActivityLog.Actor.of(user), "SIGN_OUT", user, null));
                } catch (Exception ignored) {
                }
            }
        }
        return ResponseEntity.ok(Map.of("message", "Logged out successfully"));
    }

    /** Too many wrong passwords for this address recently: refuse before checking the password again. */
    private void refuseWhileLockedOut(String email) {
        int max = settings.number("security", "maxFailedAttempts");
        if (max <= 0 || email == null) {
            return;
        }
        int minutes = Math.max(1, settings.number("security", "lockoutMinutes"));
        long failures = activity.countRecent("SIGN_IN_FAILED", email.trim().toLowerCase(),
                OffsetDateTime.now().minusMinutes(minutes));
        if (failures >= max) {
            activity.audit(ActivityLog.Actor.unknown(email), "SIGN_IN_BLOCKED", "USER", null, email,
                    "Locked out after " + failures + " failed attempts", ActivityLog.FAILURE);
            throw new StatusException(HttpStatus.TOO_MANY_REQUESTS, "TOO_MANY_ATTEMPTS",
                    "Too many failed sign-in attempts. Try again in " + minutes + " minutes or reset your password.");
        }
    }

    /** Maintenance keeps customers out; the staff domain rule keeps staff to approved addresses. */
    private void refuseByPolicy(User user) {
        if (!user.isPlatformAdmin()) {
            if (settings.flag("system", "maintenanceMode")) {
                throw new StatusException(HttpStatus.SERVICE_UNAVAILABLE, "MAINTENANCE",
                        "SmartMoney is down for maintenance. Please try again later.");
            }
            return;
        }
        var domains = settings.list("security", "adminEmailDomains");
        String email = user.getEmailAddress();
        String domain = email.substring(email.indexOf('@') + 1);
        boolean allowed = domains.isEmpty() || adminAccounts.isAdminEmail(email)
                || domains.stream().map(d -> d.startsWith("@") ? d.substring(1) : d).anyMatch(domain::equalsIgnoreCase);
        if (!allowed) {
            activity.audit(ActivityLog.Actor.of(user), "SIGN_IN_BLOCKED", user, "Email domain not allowed for staff");
            throw new StatusException(HttpStatus.FORBIDDEN, "DOMAIN_NOT_ALLOWED",
                    "Staff sign in is limited to approved email domains.");
        }
    }

    /** Audit, notify the administrators and send the welcome email, which goes out off the request thread. */
    private void onSignUp(User user) {
        activity.audit(ActivityLog.Actor.of(user), "SIGN_UP", user,
                "ORGANIZATION".equals(user.getAccountType()) ? "Organisation account" : "Individual account");
        activity.notifyAdmins("Customer", "INFO", "New sign up",
                user.getName() + " (" + user.getEmailAddress() + ") created "
                        + ("ORGANIZATION".equals(user.getAccountType())
                        ? "an organisation account" + (user.getOrganizationName() == null ? "" : " for " + user.getOrganizationName())
                        : "an individual account") + ".");
        String email = user.getEmailAddress();
        String name = user.getName();
        String accountType = user.getAccountType();
        Thread.ofVirtual().start(() -> emailService.sendWelcome(email, name, accountType));
    }

    private void onSignIn(User user, boolean firstSignIn) {
        activity.audit(ActivityLog.Actor.of(user), "SIGN_IN", user, firstSignIn ? "First sign in" : null);
        if (user.isPlatformAdmin()) {
            activity.notifyAdmins("Security", "INFO", "Administrator signed in",
                    user.getEmailAddress() + " signed in to the platform.");
        } else if (firstSignIn) {
            activity.notifyAdmins("Customer", "INFO", "New customer signed in",
                    user.getName() + " (" + user.getEmailAddress() + ") signed in for the first time.");
        }
    }

    private User resolveAuthenticatedUser(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new InvalidCredentialsException("Missing or invalid Authorization header");
        }

        String token = authHeader.substring(7);
        if (!jwtService.isTokenValid(token)) {
            throw new InvalidCredentialsException("Token is expired or invalid");
        }

        UUID userId = jwtService.extractUserId(token);
        User user = userService.findById(userId)
                .filter(candidate -> candidate.getDeletedAt() == null)
                .orElseThrow(() -> new InvalidCredentialsException("User associated with token not found"));
        // A signed-out or revoked session stops working at once, not when the token expires.
        UUID sessionId = jwtService.extractSessionId(token);
        if (sessionId != null && !refreshTokenService.isSessionActive(sessionId)) {
            throw new InvalidCredentialsException("This session was signed out");
        }
        // A suspension takes effect on the next request, not when the token expires.
        if (user.isSuspended()) {
            throw new AccountSuspendedException();
        }
        return user;
    }

    /** Always 202 with the same message, so the answer never reveals whether the address has an account. */
    @PostMapping("/forgot-password")
    public ResponseEntity<Map<String, String>> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        passwordReset.requestReset(request.emailAddress());
        return ResponseEntity.accepted().body(Map.of("message",
                "If an account exists for that email address, a reset link is on its way."));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        passwordReset.resetPassword(request.token(), request.password());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/introspect")
    public ResponseEntity<TokenIntrospectionResponse> introspect(
            @RequestBody(required = false) IntrospectTokenRequest request,
            @RequestHeader(value = "Authorization", required = false) String authHeader
    ) {
        String token = request != null && request.token() != null && !request.token().isBlank()
                ? request.token()
                : authHeader;
        TokenIntrospectionResponse response = userService.introspectToken(token);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/validate")
    public ResponseEntity<TokenIntrospectionResponse> validate(
            @RequestHeader(value = "Authorization", required = false) String authHeader
    ) {
        TokenIntrospectionResponse response = userService.introspectToken(authHeader);
        if (!response.active()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
        }
        return ResponseEntity.ok(response);
    }

    @PostMapping("/verify-email")
    public ResponseEntity<Map<String, String>> verifyEmail(@Valid @RequestBody VerifyEmailRequest request) {
        emailVerificationService.verifyEmail(request.token());
        return ResponseEntity.ok(Map.of("message", "Email verified successfully"));
    }

    @GetMapping("/verify-email")
    public ResponseEntity<Map<String, String>> verifyEmailByGet(@RequestParam("token") String token) {
        emailVerificationService.verifyEmail(token);
        return ResponseEntity.ok(Map.of("message", "Email verified successfully"));
    }

    @PostMapping("/verify-email/resend")
    public ResponseEntity<Map<String, String>> resendVerification(
            @RequestBody(required = false) ResendVerificationRequest request,
            @RequestHeader(value = "Authorization", required = false) String authHeader
    ) {
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            if (jwtService.isTokenValid(token)) {
                try {
                    UUID userId = jwtService.extractUserId(token);
                    emailVerificationService.requestVerificationForUser(userId);
                } catch (Exception ignored) {
                }
            }
        } else if (request != null && request.emailAddress() != null && !request.emailAddress().isBlank()) {
            emailVerificationService.requestVerification(request.emailAddress());
        }

        return ResponseEntity.ok(Map.of("message",
                "If an unverified account exists, a verification link has been sent."));
    }

    @PostMapping("/mfa/setup")
    public ResponseEntity<MfaSetupResponse> setupMfa(
            @RequestHeader(value = "Authorization", required = false) String authHeader
    ) {
        User user = resolveAuthenticatedUser(authHeader);
        MfaSetupResponse response = mfaService.setupMfa(user.getId());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/mfa/enable")
    public ResponseEntity<Map<String, String>> enableMfa(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @Valid @RequestBody MfaVerifyRequest request
    ) {
        User user = resolveAuthenticatedUser(authHeader);
        mfaService.enableMfa(user.getId(), request.code());
        return ResponseEntity.ok(Map.of("message", "MFA enabled successfully"));
    }

    @PostMapping("/mfa/verify")
    public ResponseEntity<AuthResponse> verifyMfa(@Valid @RequestBody MfaVerifyRequest request) {
        AuthResponse response = mfaService.verifyMfaLogin(request.mfaToken(), request.code());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/mfa/disable")
    public ResponseEntity<Map<String, String>> disableMfa(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @Valid @RequestBody MfaDisableRequest request
    ) {
        User user = resolveAuthenticatedUser(authHeader);
        mfaService.disableMfa(user.getId(), request.password(), request.code());
        return ResponseEntity.ok(Map.of("message", "MFA disabled successfully"));
    }
}

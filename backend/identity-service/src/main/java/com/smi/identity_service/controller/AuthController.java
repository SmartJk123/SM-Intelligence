package com.smi.identity_service.controller;

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
import com.smi.identity_service.security.JwtService;
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

    public AuthController(UserService userService,
                          JwtService jwtService,
                          PasswordResetService passwordReset,
                          RefreshTokenService refreshTokenService,
                          EmailVerificationService emailVerificationService,
                          MfaService mfaService) {
        this.userService = userService;
        this.jwtService = jwtService;
        this.passwordReset = passwordReset;
        this.refreshTokenService = refreshTokenService;
        this.emailVerificationService = emailVerificationService;
        this.mfaService = mfaService;
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        User user = userService.registerUser(request);
        String token = jwtService.generateToken(user);
        String refreshToken = refreshTokenService.createRefreshToken(user);
        emailVerificationService.sendVerificationEmail(user);

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
        User user = userService.authenticate(request);
        if (Boolean.TRUE.equals(user.getMfaEnabled())) {
            String mfaChallengeToken = jwtService.generateMfaChallengeToken(user);
            return ResponseEntity.ok(AuthResponse.mfaChallenge(mfaChallengeToken));
        }

        String token = jwtService.generateToken(user);
        String refreshToken = refreshTokenService.createRefreshToken(user);

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
        refreshTokenService.revokeAllForUser(user.getId());
        return ResponseEntity.ok(Map.of("message", "Password changed successfully"));
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
                    refreshTokenService.revokeAllForUser(userId);
                } catch (Exception ignored) {
                }
            }
        }
        return ResponseEntity.ok(Map.of("message", "Logged out successfully"));
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

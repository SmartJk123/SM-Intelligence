package com.smi.identity_service.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smi.identity_service.domain.User;
import com.smi.identity_service.dto.LoginRequest;
import com.smi.identity_service.dto.RegisterRequest;
import com.smi.identity_service.exception.GlobalExceptionHandler;
import com.smi.identity_service.exception.InvalidCredentialsException;
import com.smi.identity_service.exception.InvalidEmailVerificationTokenException;
import com.smi.identity_service.exception.UserAlreadyExistsException;
import com.smi.identity_service.security.JwtService;
import com.smi.identity_service.service.EmailVerificationService;
import com.smi.identity_service.service.MfaService;
import com.smi.identity_service.service.RefreshTokenService;
import com.smi.identity_service.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private UserService userService;

    @Mock
    private JwtService jwtService;

    @Mock
    private RefreshTokenService refreshTokenService;

    @Mock
    private EmailVerificationService emailVerificationService;

    @Mock
    private MfaService mfaService;

    @InjectMocks
    private AuthController authController;

    @BeforeEach
    void setUp() {
        this.mockMvc = MockMvcBuilders.standaloneSetup(authController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("POST /api/auth/register - Success returns 201 with JWT token")
    void shouldRegisterUserSuccessfully() throws Exception {
        UUID userId = UUID.randomUUID();
        User mockUser = new User(userId, "Jane Doe", "jane@example.com", "hashed_pwd");
        mockUser.setAccountType("INDIVIDUAL");

        when(userService.registerUser(any(RegisterRequest.class))).thenReturn(mockUser);
        when(jwtService.generateToken(mockUser)).thenReturn("mocked.jwt.token");
        when(jwtService.getExpirationMs()).thenReturn(86400000L);

        RegisterRequest request = new RegisterRequest("Jane Doe", "jane@example.com", "password123", "+254712345678");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").value("mocked.jwt.token"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.name").value("Jane Doe"))
                .andExpect(jsonPath("$.emailAddress").value("jane@example.com"))
                .andExpect(jsonPath("$.accountType").value("INDIVIDUAL"));
    }

    @Test
    @DisplayName("POST /api/auth/register - Duplicate email returns 409 Conflict")
    void shouldReturnConflictOnDuplicateEmail() throws Exception {
        when(userService.registerUser(any(RegisterRequest.class)))
                .thenThrow(new UserAlreadyExistsException("User already exists"));

        RegisterRequest request = new RegisterRequest("Jane Doe", "jane@example.com", "password123", null);

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    @DisplayName("POST /api/auth/login - Success returns 200 with JWT token")
    void shouldLoginSuccessfully() throws Exception {
        UUID userId = UUID.randomUUID();
        User mockUser = new User(userId, "Jane Doe", "jane@example.com", "hashed_pwd");

        when(userService.authenticate(any(LoginRequest.class))).thenReturn(mockUser);
        when(jwtService.generateToken(mockUser)).thenReturn("login.jwt.token");
        when(jwtService.getExpirationMs()).thenReturn(86400000L);

        LoginRequest request = new LoginRequest("jane@example.com", "password123");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("login.jwt.token"))
                .andExpect(jsonPath("$.emailAddress").value("jane@example.com"));
    }

    @Test
    @DisplayName("POST /api/auth/login - Bad credentials returns 401 Unauthorized")
    void shouldReturnUnauthorizedOnBadCredentials() throws Exception {
        when(userService.authenticate(any(LoginRequest.class)))
                .thenThrow(new InvalidCredentialsException("Invalid email address or password"));

        LoginRequest request = new LoginRequest("jane@example.com", "wrongPassword");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    @DisplayName("GET /api/auth/me - Success returns user profile with valid Bearer token")
    void shouldReturnUserProfileWithValidToken() throws Exception {
        UUID userId = UUID.randomUUID();
        User mockUser = new User(userId, "Jane Doe", "jane@example.com", "hashed_pwd");

        when(jwtService.isTokenValid("valid.jwt.token")).thenReturn(true);
        when(jwtService.extractUserId("valid.jwt.token")).thenReturn(userId);
        when(userService.findById(userId)).thenReturn(Optional.of(mockUser));

        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", "Bearer valid.jwt.token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Jane Doe"))
                .andExpect(jsonPath("$.emailAddress").value("jane@example.com"));
    }

    @Test
    @DisplayName("GET /api/auth/me - Missing Authorization header returns 401 Unauthorized")
    void shouldReturnUnauthorizedWhenMissingAuthHeader() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    @DisplayName("POST /api/auth/register - Supports mobile app payload with 'email' and 'phoneNumber'")
    void shouldRegisterWithMobileAppPayload() throws Exception {
        UUID userId = UUID.randomUUID();
        User mockUser = new User(userId, "Frank Mwangi", "frank@example.com", "+254712345678", "hashed_pwd");
        mockUser.setAccountType("INDIVIDUAL");

        when(userService.registerUser(any(RegisterRequest.class))).thenReturn(mockUser);
        when(jwtService.generateToken(mockUser)).thenReturn("mobile.jwt.token");
        when(jwtService.getExpirationMs()).thenReturn(86400000L);

        String mobileJson = """
            {
                "name": "Frank Mwangi",
                "email": "frank@example.com",
                "phoneNumber": "+254712345678",
                "password": "password123"
            }
        """;

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mobileJson))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(userId.toString()))
                .andExpect(jsonPath("$.userId").value(userId.toString()))
                .andExpect(jsonPath("$.name").value("Frank Mwangi"))
                .andExpect(jsonPath("$.email").value("frank@example.com"))
                .andExpect(jsonPath("$.emailAddress").value("frank@example.com"))
                .andExpect(jsonPath("$.phoneNumber").value("+254712345678"))
                .andExpect(jsonPath("$.token").value("mobile.jwt.token"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"));
    }

    @Test
    @DisplayName("POST /api/auth/login - Supports mobile app payload with 'email'")
    void shouldLoginWithMobileAppPayload() throws Exception {
        UUID userId = UUID.randomUUID();
        User mockUser = new User(userId, "Frank Mwangi", "frank@example.com", "+254712345678", "hashed_pwd");

        when(userService.authenticate(any(LoginRequest.class))).thenReturn(mockUser);
        when(jwtService.generateToken(mockUser)).thenReturn("mobile.login.token");
        when(jwtService.getExpirationMs()).thenReturn(86400000L);

        String mobileJson = """
            {
                "email": "frank@example.com",
                "password": "password123"
            }
        """;

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mobileJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(userId.toString()))
                .andExpect(jsonPath("$.userId").value(userId.toString()))
                .andExpect(jsonPath("$.email").value("frank@example.com"))
                .andExpect(jsonPath("$.emailAddress").value("frank@example.com"))
                .andExpect(jsonPath("$.phoneNumber").value("+254712345678"))
                .andExpect(jsonPath("$.token").value("mobile.login.token"));
    }

    @Test
    @DisplayName("PUT /api/auth/me - Success updates profile")
    void shouldUpdateProfileSuccessfully() throws Exception {
        UUID userId = UUID.randomUUID();
        User mockUser = new User(userId, "Jane Doe", "jane@example.com", "hashed_pwd");
        User updatedUser = new User(userId, "Jane Updated", "jane@example.com", "hashed_pwd");
        updatedUser.setPhoneNumber("+254799887766");
        updatedUser.setOrganizationName("New Org Ltd");

        when(jwtService.isTokenValid("valid.token")).thenReturn(true);
        when(jwtService.extractUserId("valid.token")).thenReturn(userId);
        when(userService.findById(userId)).thenReturn(Optional.of(mockUser));
        when(userService.updateSelfProfile(eq(userId), any())).thenReturn(updatedUser);

        String json = """
            {
                "name": "Jane Updated",
                "phoneNumber": "+254799887766",
                "organizationName": "New Org Ltd"
            }
        """;

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/auth/me")
                        .header("Authorization", "Bearer valid.token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(userId.toString()))
                .andExpect(jsonPath("$.name").value("Jane Updated"))
                .andExpect(jsonPath("$.phoneNumber").value("+254799887766"))
                .andExpect(jsonPath("$.organizationName").value("New Org Ltd"));
    }

    @Test
    @DisplayName("PATCH /api/auth/me - Success updates partial profile")
    void shouldPatchProfileSuccessfully() throws Exception {
        UUID userId = UUID.randomUUID();
        User mockUser = new User(userId, "Jane Doe", "jane@example.com", "hashed_pwd");
        User updatedUser = new User(userId, "Jane Doe", "jane@example.com", "hashed_pwd");
        updatedUser.setIndustry("Technology");

        when(jwtService.isTokenValid("valid.token")).thenReturn(true);
        when(jwtService.extractUserId("valid.token")).thenReturn(userId);
        when(userService.findById(userId)).thenReturn(Optional.of(mockUser));
        when(userService.updateSelfProfile(eq(userId), any())).thenReturn(updatedUser);

        String json = """
            {
                "industry": "Technology"
            }
        """;

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch("/api/auth/me")
                        .header("Authorization", "Bearer valid.token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.industry").value("Technology"));
    }

    @Test
    @DisplayName("POST /api/auth/change-password - Success changes password")
    void shouldChangePasswordSuccessfully() throws Exception {
        UUID userId = UUID.randomUUID();
        User mockUser = new User(userId, "Jane Doe", "jane@example.com", "hashed_pwd");

        when(jwtService.isTokenValid("valid.token")).thenReturn(true);
        when(jwtService.extractUserId("valid.token")).thenReturn(userId);
        when(userService.findById(userId)).thenReturn(Optional.of(mockUser));
        doNothing().when(userService).changePassword(eq(userId), any());

        String json = """
            {
                "currentPassword": "oldPassword123!",
                "newPassword": "newSecurePassword456!"
            }
        """;

        mockMvc.perform(post("/api/auth/change-password")
                        .header("Authorization", "Bearer valid.token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Password changed successfully"));
    }

    @Test
    @DisplayName("POST /api/auth/change-password - Refused without auth header")
    void shouldRefusePasswordChangeWithoutAuth() throws Exception {
        String json = """
            {
                "currentPassword": "oldPassword123!",
                "newPassword": "newSecurePassword456!"
            }
        """;

        mockMvc.perform(post("/api/auth/change-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /api/auth/introspect - Returns active response for valid token")
    void shouldIntrospectValidToken() throws Exception {
        UUID userId = UUID.randomUUID();
        com.smi.identity_service.dto.TokenIntrospectionResponse mockResp =
                com.smi.identity_service.dto.TokenIntrospectionResponse.active(
                        userId, "jane@example.com", "USER", "ACTIVE", "INDIVIDUAL", 1700000000L);

        when(userService.introspectToken("valid.jwt.token")).thenReturn(mockResp);

        String json = """
            {
                "token": "valid.jwt.token"
            }
        """;

        mockMvc.perform(post("/api/auth/introspect")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.userId").value(userId.toString()))
                .andExpect(jsonPath("$.email").value("jane@example.com"))
                .andExpect(jsonPath("$.role").value("USER"));
    }

    @Test
    @DisplayName("POST /api/auth/introspect - Reads from Authorization header if body omitted")
    void shouldIntrospectFromAuthHeader() throws Exception {
        com.smi.identity_service.dto.TokenIntrospectionResponse mockResp =
                com.smi.identity_service.dto.TokenIntrospectionResponse.inactive();

        when(userService.introspectToken("Bearer invalid.jwt")).thenReturn(mockResp);

        mockMvc.perform(post("/api/auth/introspect")
                        .header("Authorization", "Bearer invalid.jwt"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
    }

    @Test
    @DisplayName("GET /api/auth/validate - Returns 200 OK for active token")
    void shouldValidateActiveToken() throws Exception {
        UUID userId = UUID.randomUUID();
        com.smi.identity_service.dto.TokenIntrospectionResponse mockResp =
                com.smi.identity_service.dto.TokenIntrospectionResponse.active(
                        userId, "jane@example.com", "USER", "ACTIVE", "INDIVIDUAL", 1700000000L);

        when(userService.introspectToken("Bearer valid.jwt")).thenReturn(mockResp);

        mockMvc.perform(get("/api/auth/validate")
                        .header("Authorization", "Bearer valid.jwt"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.userId").value(userId.toString()));
    }

    @Test
    @DisplayName("GET /api/auth/validate - Returns 401 Unauthorized for inactive token")
    void shouldRefuseInactiveTokenOnValidate() throws Exception {
        com.smi.identity_service.dto.TokenIntrospectionResponse mockResp =
                com.smi.identity_service.dto.TokenIntrospectionResponse.inactive();

        when(userService.introspectToken("Bearer bad.jwt")).thenReturn(mockResp);

        mockMvc.perform(get("/api/auth/validate")
                        .header("Authorization", "Bearer bad.jwt"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.active").value(false));
    }

    @Test
    @DisplayName("POST /api/auth/refresh - Successfully rotates refresh token")
    void shouldRotateRefreshTokenSuccessfully() throws Exception {
        UUID userId = UUID.randomUUID();
        com.smi.identity_service.dto.AuthResponse authResponse = new com.smi.identity_service.dto.AuthResponse(
                "new.access.token",
                86400L,
                userId,
                "Jane Doe",
                "jane@example.com",
                null,
                "INDIVIDUAL",
                "USER",
                "new-refresh-token"
        );

        when(refreshTokenService.rotateRefreshToken("valid-refresh-token")).thenReturn(authResponse);

        com.smi.identity_service.dto.RefreshTokenRequest request =
                new com.smi.identity_service.dto.RefreshTokenRequest("valid-refresh-token");

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("new.access.token"))
                .andExpect(jsonPath("$.refreshToken").value("new-refresh-token"))
                .andExpect(jsonPath("$.userId").value(userId.toString()));
    }

    @Test
    @DisplayName("POST /api/auth/refresh - Invalid token returns 401 Unauthorized")
    void shouldReturnUnauthorizedWhenRefreshTokenInvalid() throws Exception {
        when(refreshTokenService.rotateRefreshToken("invalid-refresh-token"))
                .thenThrow(new InvalidCredentialsException("Invalid, expired, or revoked refresh token"));

        com.smi.identity_service.dto.RefreshTokenRequest request =
                new com.smi.identity_service.dto.RefreshTokenRequest("invalid-refresh-token");

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    @DisplayName("POST /api/auth/logout - Revokes token and returns 200 OK")
    void shouldLogoutSuccessfully() throws Exception {
        UUID userId = UUID.randomUUID();
        when(jwtService.isTokenValid("valid.jwt")).thenReturn(true);
        when(jwtService.extractUserId("valid.jwt")).thenReturn(userId);

        com.smi.identity_service.dto.RefreshTokenRequest request =
                new com.smi.identity_service.dto.RefreshTokenRequest("valid-refresh-token");

        mockMvc.perform(post("/api/auth/logout")
                        .header("Authorization", "Bearer valid.jwt")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Logged out successfully"));
    }

    @Test
    @DisplayName("POST /api/auth/verify-email - Valid token confirms email")
    void shouldVerifyEmailSuccessfully() throws Exception {
        UUID userId = UUID.randomUUID();
        User verifiedUser = new User(userId, "Jane Doe", "jane@example.com", "hash");
        verifiedUser.setIsEmailVerified(true);
        when(emailVerificationService.verifyEmail("valid-token")).thenReturn(verifiedUser);

        com.smi.identity_service.dto.VerifyEmailRequest request =
                new com.smi.identity_service.dto.VerifyEmailRequest("valid-token");

        mockMvc.perform(post("/api/auth/verify-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Email verified successfully"));
    }

    @Test
    @DisplayName("POST /api/auth/verify-email - Invalid token returns 400 Bad Request")
    void shouldReturnBadRequestOnInvalidVerificationToken() throws Exception {
        when(emailVerificationService.verifyEmail("invalid-token"))
                .thenThrow(new InvalidEmailVerificationTokenException());

        com.smi.identity_service.dto.VerifyEmailRequest request =
                new com.smi.identity_service.dto.VerifyEmailRequest("invalid-token");

        mockMvc.perform(post("/api/auth/verify-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_EMAIL_VERIFICATION_TOKEN"));
    }

    @Test
    @DisplayName("GET /api/auth/verify-email - Valid token confirms email via GET query param")
    void shouldVerifyEmailViaGet() throws Exception {
        UUID userId = UUID.randomUUID();
        User verifiedUser = new User(userId, "Jane Doe", "jane@example.com", "hash");
        verifiedUser.setIsEmailVerified(true);
        when(emailVerificationService.verifyEmail("valid-token")).thenReturn(verifiedUser);

        mockMvc.perform(get("/api/auth/verify-email")
                        .param("token", "valid-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Email verified successfully"));
    }

    @Test
    @DisplayName("POST /api/auth/verify-email/resend - Sends verification link and returns 200 OK")
    void shouldResendVerificationEmail() throws Exception {
        com.smi.identity_service.dto.ResendVerificationRequest request =
                new com.smi.identity_service.dto.ResendVerificationRequest("jane@example.com");

        mockMvc.perform(post("/api/auth/verify-email/resend")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("If an unverified account exists, a verification link has been sent."));
    }

    @Test
    @DisplayName("POST /api/auth/login - Returns MFA challenge when user has MFA enabled")
    void shouldReturnMfaChallengeOnLoginWhenMfaEnabled() throws Exception {
        UUID userId = UUID.randomUUID();
        User mfaUser = new User(userId, "Jane Doe", "jane@example.com", "hashed_pwd");
        mfaUser.setMfaEnabled(true);

        when(userService.authenticate(any(LoginRequest.class))).thenReturn(mfaUser);
        when(jwtService.generateMfaChallengeToken(mfaUser)).thenReturn("mfa.challenge.token");

        LoginRequest request = new LoginRequest("jane@example.com", "password123");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mfaRequired").value(true))
                .andExpect(jsonPath("$.mfaToken").value("mfa.challenge.token"));
    }

    @Test
    @DisplayName("POST /api/auth/mfa/setup - Initiates MFA setup and returns secret + backup codes")
    void shouldInitiateMfaSetup() throws Exception {
        UUID userId = UUID.randomUUID();
        User mockUser = new User(userId, "Jane Doe", "jane@example.com", "hashed_pwd");

        when(jwtService.isTokenValid("valid.jwt.token")).thenReturn(true);
        when(jwtService.extractUserId("valid.jwt.token")).thenReturn(userId);
        when(userService.findById(userId)).thenReturn(Optional.of(mockUser));

        com.smi.identity_service.dto.MfaSetupResponse setupResp =
                new com.smi.identity_service.dto.MfaSetupResponse("JBSWY3DPEHPK3PXP", "otpauth://totp/...", java.util.List.of("ABCD-1234"));
        when(mfaService.setupMfa(userId)).thenReturn(setupResp);

        mockMvc.perform(post("/api/auth/mfa/setup")
                        .header("Authorization", "Bearer valid.jwt.token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.secret").value("JBSWY3DPEHPK3PXP"))
                .andExpect(jsonPath("$.backupCodes[0]").value("ABCD-1234"));
    }

    @Test
    @DisplayName("POST /api/auth/mfa/enable - Confirms code and activates MFA")
    void shouldEnableMfa() throws Exception {
        UUID userId = UUID.randomUUID();
        User mockUser = new User(userId, "Jane Doe", "jane@example.com", "hashed_pwd");

        when(jwtService.isTokenValid("valid.jwt.token")).thenReturn(true);
        when(jwtService.extractUserId("valid.jwt.token")).thenReturn(userId);
        when(userService.findById(userId)).thenReturn(Optional.of(mockUser));

        com.smi.identity_service.dto.MfaVerifyRequest request =
                new com.smi.identity_service.dto.MfaVerifyRequest("123456", null);

        mockMvc.perform(post("/api/auth/mfa/enable")
                        .header("Authorization", "Bearer valid.jwt.token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("MFA enabled successfully"));
    }

    @Test
    @DisplayName("POST /api/auth/mfa/verify - Completes two-stage login and issues session")
    void shouldVerifyMfaLogin() throws Exception {
        UUID userId = UUID.randomUUID();
        com.smi.identity_service.dto.AuthResponse authResponse = new com.smi.identity_service.dto.AuthResponse(
                "session.jwt.token",
                86400L,
                userId,
                "Jane Doe",
                "jane@example.com",
                null,
                "INDIVIDUAL",
                "USER",
                "refresh-token-xyz"
        );

        when(mfaService.verifyMfaLogin("mfa.challenge.token", "123456")).thenReturn(authResponse);

        com.smi.identity_service.dto.MfaVerifyRequest request =
                new com.smi.identity_service.dto.MfaVerifyRequest("123456", "mfa.challenge.token");

        mockMvc.perform(post("/api/auth/mfa/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("session.jwt.token"))
                .andExpect(jsonPath("$.refreshToken").value("refresh-token-xyz"));
    }

    @Test
    @DisplayName("POST /api/auth/mfa/disable - Valid password and code disables MFA")
    void shouldDisableMfa() throws Exception {
        UUID userId = UUID.randomUUID();
        User mockUser = new User(userId, "Jane Doe", "jane@example.com", "hashed_pwd");

        when(jwtService.isTokenValid("valid.jwt.token")).thenReturn(true);
        when(jwtService.extractUserId("valid.jwt.token")).thenReturn(userId);
        when(userService.findById(userId)).thenReturn(Optional.of(mockUser));

        com.smi.identity_service.dto.MfaDisableRequest request =
                new com.smi.identity_service.dto.MfaDisableRequest("Password123!", "123456");

        mockMvc.perform(post("/api/auth/mfa/disable")
                        .header("Authorization", "Bearer valid.jwt.token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("MFA disabled successfully"));
    }
}

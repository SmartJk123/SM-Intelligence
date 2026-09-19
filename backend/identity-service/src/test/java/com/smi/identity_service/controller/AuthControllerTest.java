package com.smi.identity_service.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smi.identity_service.domain.User;
import com.smi.identity_service.dto.LoginRequest;
import com.smi.identity_service.dto.RegisterRequest;
import com.smi.identity_service.exception.GlobalExceptionHandler;
import com.smi.identity_service.exception.InvalidCredentialsException;
import com.smi.identity_service.exception.UserAlreadyExistsException;
import com.smi.identity_service.security.JwtService;
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
}

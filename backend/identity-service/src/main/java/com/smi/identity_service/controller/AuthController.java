package com.smi.identity_service.controller;

import com.smi.identity_service.domain.User;
import com.smi.identity_service.dto.AuthResponse;
import com.smi.identity_service.dto.LoginRequest;
import com.smi.identity_service.dto.RegisterRequest;
import com.smi.identity_service.dto.UserProfileResponse;
import com.smi.identity_service.exception.InvalidCredentialsException;
import com.smi.identity_service.security.JwtService;
import com.smi.identity_service.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserService userService;
    private final JwtService jwtService;

    public AuthController(UserService userService, JwtService jwtService) {
        this.userService = userService;
        this.jwtService = jwtService;
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        User user = userService.registerUser(request);
        String token = jwtService.generateToken(user);

        AuthResponse response = new AuthResponse(
                token,
                jwtService.getExpirationMs(),
                user.getId(),
                user.getName(),
                user.getEmailAddress(),
                user.getAccountType()
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        User user = userService.authenticate(request);
        String token = jwtService.generateToken(user);

        AuthResponse response = new AuthResponse(
                token,
                jwtService.getExpirationMs(),
                user.getId(),
                user.getName(),
                user.getEmailAddress(),
                user.getAccountType()
        );

        return ResponseEntity.ok(response);
    }

    @GetMapping("/me")
    public ResponseEntity<UserProfileResponse> getCurrentUser(
            @RequestHeader(value = "Authorization", required = false) String authHeader
    ) {
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

        return ResponseEntity.ok(UserProfileResponse.fromUser(user));
    }
}

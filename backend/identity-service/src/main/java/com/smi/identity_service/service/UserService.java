package com.smi.identity_service.service;

import com.smi.identity_service.domain.User;
import com.smi.identity_service.dto.ChangePasswordRequest;
import com.smi.identity_service.dto.LoginRequest;
import com.smi.identity_service.dto.RegisterRequest;
import com.smi.identity_service.dto.TokenIntrospectionResponse;
import com.smi.identity_service.dto.UpdateProfileRequest;
import com.smi.identity_service.dto.UpdateUserRequest;
import com.smi.identity_service.exception.AccountSuspendedException;
import com.smi.identity_service.exception.InvalidCredentialsException;
import com.smi.identity_service.exception.UserAlreadyExistsException;
import com.smi.identity_service.exception.UserNotFoundException;
import com.smi.identity_service.repository.UserRepository;
import com.smi.identity_service.security.AdminAccounts;
import com.smi.identity_service.security.JwtService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Service class orchestrating User lifecycle, registration, and authentication logic.
 */
@Service
@Transactional
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AdminAccounts adminAccounts;
    private final JwtService jwtService;

    public UserService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       AdminAccounts adminAccounts,
                       JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.adminAccounts = adminAccounts;
        this.jwtService = jwtService;
    }

    public User registerUser(RegisterRequest request) {
        String email = request.getEmailAddress().trim().toLowerCase();
        if (userRepository.findByEmailAddressAndDeletedAtIsNull(email).isPresent()) {
            throw new UserAlreadyExistsException("A user with email '" + email + "' already exists");
        }

        String validatedPhoneNumber = normalizeAndValidatePhoneNumber(request.getPhoneNumber());
        String passwordHash = passwordEncoder.encode(request.getPassword());

        User user = new User(request.getName().trim(), email, validatedPhoneNumber, passwordHash);
        if (request.getAccountType() != null && !request.getAccountType().isBlank()) {
            user.setAccountType(request.getAccountType().trim().toUpperCase());
        }
        if (request.getOrganizationName() != null && !request.getOrganizationName().isBlank()) {
            user.setOrganizationName(request.getOrganizationName().trim());
        }
        if (adminAccounts.isAdminEmail(email)) {
            user.setRole(User.ROLE_PLATFORM_ADMIN);
        }

        return userRepository.save(user);
    }

    /**
     * Checks the credentials first, so a wrong password never reveals whether the
     * account is suspended, then refuses a suspended account and records the sign in.
     */
    public User authenticate(LoginRequest request) {
        String email = request.getEmailAddress().trim().toLowerCase();
        User user = userRepository.findByEmailAddressAndDeletedAtIsNull(email)
                .orElseThrow(() -> new InvalidCredentialsException("Invalid email address or password"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new InvalidCredentialsException("Invalid email address or password");
        }
        if (user.isSuspended()) {
            throw new AccountSuspendedException();
        }

        user.setLastLoginAt(OffsetDateTime.now());
        return userRepository.save(user);
    }

    public User createUser(String name, String emailAddress, String rawPassword) {
        return createUser(name, emailAddress, null, rawPassword);
    }

    public User createUser(String name, String emailAddress, String phoneNumber, String rawPassword) {
        String validatedPhoneNumber = normalizeAndValidatePhoneNumber(phoneNumber);
        String passwordHash = passwordEncoder.encode(rawPassword);

        User user = new User(name, emailAddress.trim().toLowerCase(), validatedPhoneNumber, passwordHash);
        return userRepository.save(user);
    }

    public User updatePhoneNumber(UUID userId, String newPhoneNumber) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found with id: " + userId));

        String validatedPhoneNumber = normalizeAndValidatePhoneNumber(newPhoneNumber);
        user.setPhoneNumber(validatedPhoneNumber);
        user.setUpdatedAt(OffsetDateTime.now());

        return userRepository.save(user);
    }

    public User removePhoneNumber(UUID userId) {
        return updatePhoneNumber(userId, null);
    }

    public User softDeleteUser(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found with id: " + userId));

        user.setDeletedAt(OffsetDateTime.now());
        user.setUpdatedAt(OffsetDateTime.now());

        return userRepository.save(user);
    }

    @Transactional(readOnly = true)
    public Optional<User> findByEmail(String emailAddress) {
        if (emailAddress == null || emailAddress.trim().isEmpty()) {
            return Optional.empty();
        }
        return userRepository.findByEmailAddressAndDeletedAtIsNull(emailAddress.trim().toLowerCase());
    }

    @Transactional(readOnly = true)
    public Optional<User> findByPhoneNumber(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.trim().isEmpty()) {
            return Optional.empty();
        }
        return userRepository.findByPhoneNumber(phoneNumber.trim());
    }

    @Transactional(readOnly = true)
    public boolean existsByPhoneNumber(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.trim().isEmpty()) {
            return false;
        }
        return userRepository.existsByPhoneNumber(phoneNumber.trim());
    }

    @Transactional(readOnly = true)
    public Optional<User> findById(UUID userId) {
        return userRepository.findById(userId);
    }

    @Transactional(readOnly = true)
    public List<User> listActiveUsers() {
        return userRepository.findByDeletedAtIsNullOrderByCreatedAtDesc();
    }

    @Transactional(readOnly = true)
    public User getActiveUser(UUID userId) {
        return userRepository.findById(userId)
                .filter(user -> user.getDeletedAt() == null)
                .orElseThrow(() -> new UserNotFoundException("User not found with id: " + userId));
    }

    /**
     * Suspends or restores an account. An administrator cannot suspend their own
     * account, so the admin interface can never lock out the person using it.
     */
    public User changeStatus(UUID userId, String status, UUID actingAdminId) {
        String normalized = status == null ? "" : status.trim().toUpperCase();
        if (!User.STATUS_ACTIVE.equals(normalized) && !User.STATUS_SUSPENDED.equals(normalized)) {
            throw new IllegalArgumentException("Status must be ACTIVE or SUSPENDED");
        }
        if (User.STATUS_SUSPENDED.equals(normalized) && userId.equals(actingAdminId)) {
            throw new IllegalArgumentException("You cannot suspend your own account");
        }

        User user = getActiveUser(userId);
        user.setStatus(normalized);
        user.setUpdatedAt(OffsetDateTime.now());
        return userRepository.save(user);
    }

    /**
     * Updates the profile fields an administrator may correct. The email address,
     * account type, role and password are deliberately not editable here.
     */
    public User updateProfile(UUID userId, UpdateUserRequest request) {
        User user = getActiveUser(userId);
        user.setName(request.getName().trim());
        user.setPhoneNumber(normalizeAndValidatePhoneNumber(request.getPhoneNumber()));
        user.setOrganizationName(blankToNull(request.getOrganizationName()));
        user.setBusinessType(blankToNull(request.getBusinessType()));
        user.setIndustry(blankToNull(request.getIndustry()));
        user.setUpdatedAt(OffsetDateTime.now());
        return userRepository.save(user);
    }

    /**
     * Updates an authenticated user's own profile fields.
     */
    public User updateSelfProfile(UUID userId, UpdateProfileRequest request) {
        User user = getActiveUser(userId);
        if (user.isSuspended()) {
            throw new AccountSuspendedException();
        }
        if (request.getName() != null && !request.getName().isBlank()) {
            user.setName(request.getName().trim());
        }
        if (request.getPhoneNumber() != null) {
            user.setPhoneNumber(normalizeAndValidatePhoneNumber(request.getPhoneNumber()));
        }
        if (request.getOrganizationName() != null) {
            user.setOrganizationName(blankToNull(request.getOrganizationName()));
        }
        if (request.getBusinessType() != null) {
            user.setBusinessType(blankToNull(request.getBusinessType()));
        }
        if (request.getIndustry() != null) {
            user.setIndustry(blankToNull(request.getIndustry()));
        }
        if (request.getTimezone() != null && !request.getTimezone().isBlank()) {
            user.setTimezone(request.getTimezone().trim());
        }
        if (request.getLocale() != null && !request.getLocale().isBlank()) {
            user.setLocale(request.getLocale().trim());
        }
        if (request.getReportingCurrency() != null && !request.getReportingCurrency().isBlank()) {
            String curr = request.getReportingCurrency().trim().toUpperCase();
            if (curr.length() != 3) {
                throw new IllegalArgumentException("Reporting currency must be a 3-letter ISO code");
            }
            user.setReportingCurrency(curr);
        }
        user.setUpdatedAt(OffsetDateTime.now());
        return userRepository.save(user);
    }

    /**
     * Changes an authenticated user's password, verifying their existing password first.
     */
    public void changePassword(UUID userId, ChangePasswordRequest request) {
        User user = getActiveUser(userId);
        if (user.isSuspended()) {
            throw new AccountSuspendedException();
        }
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new InvalidCredentialsException("Current password does not match");
        }
        if (passwordEncoder.matches(request.newPassword(), user.getPasswordHash())) {
            throw new IllegalArgumentException("New password must be different from current password");
        }
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        user.setUpdatedAt(OffsetDateTime.now());
        userRepository.save(user);
    }

    /**
     * Introspects an access token according to RFC 7662 standards.
     * Verifies cryptographic signature, expiration, and ensures user is active.
     */
    @Transactional(readOnly = true)
    public TokenIntrospectionResponse introspectToken(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return TokenIntrospectionResponse.inactive();
        }
        String token = rawToken.startsWith("Bearer ") ? rawToken.substring(7).trim() : rawToken.trim();
        Optional<io.jsonwebtoken.Claims> claimsOpt = jwtService.extractClaims(token);
        if (claimsOpt.isEmpty()) {
            return TokenIntrospectionResponse.inactive();
        }
        var claims = claimsOpt.get();
        UUID userId;
        try {
            userId = UUID.fromString(claims.getSubject());
        } catch (Exception e) {
            return TokenIntrospectionResponse.inactive();
        }

        Optional<User> userOpt = userRepository.findById(userId);
        if (userOpt.isEmpty()) {
            return TokenIntrospectionResponse.inactive();
        }
        User user = userOpt.get();
        if (user.getDeletedAt() != null || user.isSuspended()) {
            return TokenIntrospectionResponse.inactive();
        }

        long expSeconds = claims.getExpiration() != null ? claims.getExpiration().getTime() / 1000 : 0;
        return TokenIntrospectionResponse.active(
                user.getId(),
                user.getEmailAddress(),
                user.getRole(),
                user.getStatus(),
                user.getAccountType(),
                expSeconds
        );
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String normalizeAndValidatePhoneNumber(String phoneNumber) {
        if (phoneNumber == null) {
            return null;
        }
        String trimmed = phoneNumber.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        if (trimmed.length() < 7 || trimmed.length() > 20) {
            throw new IllegalArgumentException("Phone number must be between 7 and 20 characters");
        }
        return trimmed;
    }
}

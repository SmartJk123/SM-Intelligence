package com.smi.identity_service.service;

import com.smi.identity_service.domain.User;
import com.smi.identity_service.dto.LoginRequest;
import com.smi.identity_service.dto.RegisterRequest;
import com.smi.identity_service.exception.InvalidCredentialsException;
import com.smi.identity_service.exception.UserAlreadyExistsException;
import com.smi.identity_service.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
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

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
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

        return userRepository.save(user);
    }

    @Transactional(readOnly = true)
    public User authenticate(LoginRequest request) {
        String email = request.getEmailAddress().trim().toLowerCase();
        User user = userRepository.findByEmailAddressAndDeletedAtIsNull(email)
                .orElseThrow(() -> new InvalidCredentialsException("Invalid email address or password"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new InvalidCredentialsException("Invalid email address or password");
        }

        return user;
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

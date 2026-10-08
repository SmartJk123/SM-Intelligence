package com.smi.identity_service.service;

import com.smi.identity_service.domain.User;
import com.smi.identity_service.repository.UserRepository;
import com.smi.identity_service.security.AdminAccounts;
import com.smi.identity_service.security.JwtService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private AdminAccounts adminAccounts;

    @Mock
    private JwtService jwtService;

    @InjectMocks
    private UserService userService;

    @Test
    @DisplayName("Should create user with hashed password without phone number")
    void shouldCreateUserWithHashedPassword() {
        String name = "Jane Doe";
        String email = "jane@example.com";
        String rawPassword = "rawPassword123";
        String hashedPassword = "hashed_secret_123";

        User savedUser = new User(name, email, hashedPassword);

        when(passwordEncoder.encode(rawPassword)).thenReturn(hashedPassword);
        when(userRepository.save(any(User.class))).thenReturn(savedUser);

        User actualUser = userService.createUser(name, email, rawPassword);

        assertNotNull(actualUser);
        assertEquals(email, actualUser.getEmailAddress());
        assertNull(actualUser.getPhoneNumber());

        verify(passwordEncoder, times(1)).encode(rawPassword);

        // The raw password must never reach the store, only its hash. The other
        // test in this class already asserted this; without it the variable was
        // declared and then ignored, which is the warning this replaces.
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        assertEquals(hashedPassword, userCaptor.getValue().getPasswordHash());
    }

    @Test
    @DisplayName("Should create user with hashed password and phone number")
    void shouldCreateUserWithHashedPasswordAndPhoneNumber() {
        String name = "John Doe";
        String email = "john@example.com";
        String phoneNumber = "+254712345678";
        String rawPassword = "secretPassword!";
        String hashedPassword = "hashed_secret_xyz";

        User savedUser = new User(name, email, phoneNumber, hashedPassword);

        when(passwordEncoder.encode(rawPassword)).thenReturn(hashedPassword);
        when(userRepository.save(any(User.class))).thenReturn(savedUser);

        User actualUser = userService.createUser(name, email, phoneNumber, rawPassword);

        assertNotNull(actualUser);
        assertEquals("john@example.com", actualUser.getEmailAddress());
        assertEquals("+254712345678", actualUser.getPhoneNumber());

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        assertEquals("+254712345678", userCaptor.getValue().getPhoneNumber());
        assertEquals(hashedPassword, userCaptor.getValue().getPasswordHash());
    }

    @Test
    @DisplayName("Should reject user creation when phone number is too short")
    void shouldRejectCreationWhenPhoneNumberTooShort() {
        assertThrows(IllegalArgumentException.class, () ->
                userService.createUser("John", "john@example.com", "12345", "password")
        );
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("Should reject user creation when phone number is too long")
    void shouldRejectCreationWhenPhoneNumberTooLong() {
        String overlyLongPhone = "123456789012345678901"; // 21 chars
        assertThrows(IllegalArgumentException.class, () ->
                userService.createUser("John", "john@example.com", overlyLongPhone, "password")
        );
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("Should update user's phone number successfully")
    void shouldUpdatePhoneNumberSuccessfully() {
        UUID userId = UUID.randomUUID();
        User existingUser = new User(userId, "Jane Doe", "jane@example.com", "existing_hash");
        String newPhone = "+254700112233";

        when(userRepository.findById(userId)).thenReturn(Optional.of(existingUser));
        when(userRepository.save(existingUser)).thenReturn(existingUser);

        User updatedUser = userService.updatePhoneNumber(userId, newPhone);

        assertNotNull(updatedUser);
        assertEquals("+254700112233", updatedUser.getPhoneNumber());
        verify(userRepository, times(1)).save(existingUser);
    }

    @Test
    @DisplayName("Should soft delete user successfully")
    void shouldSoftDeleteUserSuccessfully() {
        UUID userId = UUID.randomUUID();
        User existingUser = new User(userId, "Jane Doe", "jane@example.com", "existing_hash");

        when(userRepository.findById(userId)).thenReturn(Optional.of(existingUser));
        when(userRepository.save(existingUser)).thenReturn(existingUser);

        User deletedUser = userService.softDeleteUser(userId);

        assertNotNull(deletedUser);
        assertNotNull(deletedUser.getDeletedAt());
        verify(userRepository, times(1)).save(existingUser);
    }

    @Test
    @DisplayName("Should update self profile successfully")
    void shouldUpdateSelfProfileSuccessfully() {
        UUID userId = UUID.randomUUID();
        User existingUser = new User(userId, "Jane Doe", "jane@example.com", "existing_hash");
        when(userRepository.findById(userId)).thenReturn(Optional.of(existingUser));
        when(userRepository.save(existingUser)).thenReturn(existingUser);

        com.smi.identity_service.dto.UpdateProfileRequest request = new com.smi.identity_service.dto.UpdateProfileRequest();
        request.setName("Jane Smith");
        request.setPhoneNumber("+254711223344");
        request.setOrganizationName("Acme Ltd");
        request.setBusinessType("LLC");
        request.setIndustry("Fintech");
        request.setTimezone("Africa/Nairobi");
        request.setLocale("en-KE");
        request.setReportingCurrency("KES");

        User updated = userService.updateSelfProfile(userId, request);

        assertEquals("Jane Smith", updated.getName());
        assertEquals("+254711223344", updated.getPhoneNumber());
        assertEquals("Acme Ltd", updated.getOrganizationName());
        assertEquals("LLC", updated.getBusinessType());
        assertEquals("Fintech", updated.getIndustry());
        assertEquals("Africa/Nairobi", updated.getTimezone());
        assertEquals("en-KE", updated.getLocale());
        assertEquals("KES", updated.getReportingCurrency());
        verify(userRepository).save(existingUser);
    }

    @Test
    @DisplayName("Should reject self profile update if user is suspended")
    void shouldRejectSelfProfileUpdateIfSuspended() {
        UUID userId = UUID.randomUUID();
        User existingUser = new User(userId, "Jane Doe", "jane@example.com", "existing_hash");
        existingUser.setStatus(User.STATUS_SUSPENDED);
        when(userRepository.findById(userId)).thenReturn(Optional.of(existingUser));

        com.smi.identity_service.dto.UpdateProfileRequest request = new com.smi.identity_service.dto.UpdateProfileRequest();
        request.setName("Jane Smith");

        assertThrows(com.smi.identity_service.exception.AccountSuspendedException.class,
                () -> userService.updateSelfProfile(userId, request));
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should reject self profile update with invalid currency code")
    void shouldRejectSelfProfileWithInvalidCurrency() {
        UUID userId = UUID.randomUUID();
        User existingUser = new User(userId, "Jane Doe", "jane@example.com", "existing_hash");
        when(userRepository.findById(userId)).thenReturn(Optional.of(existingUser));

        com.smi.identity_service.dto.UpdateProfileRequest request = new com.smi.identity_service.dto.UpdateProfileRequest();
        request.setReportingCurrency("INVALID");

        assertThrows(IllegalArgumentException.class,
                () -> userService.updateSelfProfile(userId, request));
    }

    @Test
    @DisplayName("Should change password successfully when current password matches")
    void shouldChangePasswordSuccessfully() {
        UUID userId = UUID.randomUUID();
        User existingUser = new User(userId, "Jane Doe", "jane@example.com", "current_hashed_pw");
        when(userRepository.findById(userId)).thenReturn(Optional.of(existingUser));
        when(passwordEncoder.matches("oldPassword123!", "current_hashed_pw")).thenReturn(true);
        when(passwordEncoder.matches("newPassword456!", "current_hashed_pw")).thenReturn(false);
        when(passwordEncoder.encode("newPassword456!")).thenReturn("new_hashed_pw");

        com.smi.identity_service.dto.ChangePasswordRequest request =
                new com.smi.identity_service.dto.ChangePasswordRequest("oldPassword123!", "newPassword456!");

        userService.changePassword(userId, request);

        assertEquals("new_hashed_pw", existingUser.getPasswordHash());
        verify(userRepository).save(existingUser);
    }

    @Test
    @DisplayName("Should reject password change when current password is wrong")
    void shouldRejectPasswordChangeWhenCurrentPasswordIsWrong() {
        UUID userId = UUID.randomUUID();
        User existingUser = new User(userId, "Jane Doe", "jane@example.com", "current_hashed_pw");
        when(userRepository.findById(userId)).thenReturn(Optional.of(existingUser));
        when(passwordEncoder.matches("wrongCurrentPw", "current_hashed_pw")).thenReturn(false);

        com.smi.identity_service.dto.ChangePasswordRequest request =
                new com.smi.identity_service.dto.ChangePasswordRequest("wrongCurrentPw", "newPassword456!");

        assertThrows(com.smi.identity_service.exception.InvalidCredentialsException.class,
                () -> userService.changePassword(userId, request));
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should reject password change when new password equals current password")
    void shouldRejectPasswordChangeWhenNewPasswordMatchesCurrent() {
        UUID userId = UUID.randomUUID();
        User existingUser = new User(userId, "Jane Doe", "jane@example.com", "current_hashed_pw");
        when(userRepository.findById(userId)).thenReturn(Optional.of(existingUser));
        when(passwordEncoder.matches("samePassword123!", "current_hashed_pw")).thenReturn(true);

        com.smi.identity_service.dto.ChangePasswordRequest request =
                new com.smi.identity_service.dto.ChangePasswordRequest("samePassword123!", "samePassword123!");

        assertThrows(IllegalArgumentException.class,
                () -> userService.changePassword(userId, request));
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should return active introspection for valid token and active user")
    void shouldReturnActiveIntrospection() {
        UUID userId = UUID.randomUUID();
        User existingUser = new User(userId, "Jane Doe", "jane@example.com", "hash");
        existingUser.setRole("USER");
        existingUser.setStatus("ACTIVE");
        existingUser.setAccountType("INDIVIDUAL");

        io.jsonwebtoken.Claims mockClaims = mock(io.jsonwebtoken.Claims.class);
        when(mockClaims.getSubject()).thenReturn(userId.toString());
        when(mockClaims.getExpiration()).thenReturn(new java.util.Date(System.currentTimeMillis() + 60000));

        when(jwtService.extractClaims("valid-jwt")).thenReturn(Optional.of(mockClaims));
        when(userRepository.findById(userId)).thenReturn(Optional.of(existingUser));

        com.smi.identity_service.dto.TokenIntrospectionResponse response =
                userService.introspectToken("Bearer valid-jwt");

        assertTrue(response.active());
        assertEquals(userId, response.userId());
        assertEquals("jane@example.com", response.email());
        assertEquals("USER", response.role());
        assertEquals("ACTIVE", response.status());
        assertEquals("INDIVIDUAL", response.accountType());
    }

    @Test
    @DisplayName("Should return inactive introspection for invalid or expired token")
    void shouldReturnInactiveIntrospectionForInvalidToken() {
        when(jwtService.extractClaims("bad-jwt")).thenReturn(Optional.empty());

        com.smi.identity_service.dto.TokenIntrospectionResponse response =
                userService.introspectToken("bad-jwt");

        assertFalse(response.active());
        assertNull(response.userId());
    }

    @Test
    @DisplayName("Should return inactive introspection when user is suspended")
    void shouldReturnInactiveIntrospectionWhenUserSuspended() {
        UUID userId = UUID.randomUUID();
        User suspendedUser = new User(userId, "Jane Doe", "jane@example.com", "hash");
        suspendedUser.setStatus(User.STATUS_SUSPENDED);

        io.jsonwebtoken.Claims mockClaims = mock(io.jsonwebtoken.Claims.class);
        when(mockClaims.getSubject()).thenReturn(userId.toString());

        when(jwtService.extractClaims("valid-jwt")).thenReturn(Optional.of(mockClaims));
        when(userRepository.findById(userId)).thenReturn(Optional.of(suspendedUser));

        com.smi.identity_service.dto.TokenIntrospectionResponse response =
                userService.introspectToken("valid-jwt");

        assertFalse(response.active());
    }
}

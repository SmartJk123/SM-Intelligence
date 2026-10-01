package com.smi.identity_service.service;

import com.smi.identity_service.domain.User;
import com.smi.identity_service.repository.UserRepository;
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
        assertEquals("jane@example.com", actualUser.getEmailAddress());
        assertNull(actualUser.getPhoneNumber());

        verify(passwordEncoder, times(1)).encode(rawPassword);
        verify(userRepository, times(1)).save(any(User.class));
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
}

package com.smi.identity_service.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class UserTest {

    @Test
    @DisplayName("Default constructor should create empty User with default schema values")
    void testDefaultConstructor() {
        User user = new User();
        assertNull(user.getId());
        assertNull(user.getName());
        assertNull(user.getEmailAddress());
        assertNull(user.getPasswordHash());
        assertNull(user.getPhoneNumber());
        assertEquals("INDIVIDUAL", user.getAccountType());
        assertEquals("KES", user.getReportingCurrency());
        assertEquals("Africa/Nairobi", user.getTimezone());
        assertEquals("en-KE", user.getLocale());
        assertFalse(user.getIsEmailVerified());
        assertFalse(user.getMfaEnabled());
        assertNotNull(user.getCreatedAt());
        assertNull(user.getDeletedAt());
    }

    @Test
    @DisplayName("Parameterized constructor should populate fields correctly")
    void testParameterizedConstructor() {
        User user = new User("Jane Doe", "jane@example.com", "+254712345678", "hash123");
        assertEquals("Jane Doe", user.getName());
        assertEquals("jane@example.com", user.getEmailAddress());
        assertEquals("+254712345678", user.getPhoneNumber());
        assertEquals("hash123", user.getPasswordHash());
    }

    @Test
    @DisplayName("Equals and hashCode should evaluate based on id or email address")
    void testEqualsAndHashCode() {
        UUID id = UUID.randomUUID();
        User user1 = new User(id, "Jane", "jane@example.com", "hash");
        User user2 = new User(id, "Jane Updated", "jane@example.com", "hash2");
        User user3 = new User(UUID.randomUUID(), "Other", "other@example.com", "hash");

        assertEquals(user1, user2);
        assertNotEquals(user1, user3);
        assertEquals(user1.hashCode(), user2.hashCode());
    }
}

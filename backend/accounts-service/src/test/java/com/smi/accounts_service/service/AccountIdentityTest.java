package com.smi.accounts_service.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AccountIdentityTest {

    @Test
    void isInternalService_returnsFalse_whenConfiguredTokenIsNull() {
        AccountIdentity identity = new AccountIdentity("http://localhost:8081", null);
        assertFalse(identity.isInternalService("some-token"));
        assertFalse(identity.isInternalService(null));
    }

    @Test
    void isInternalService_returnsFalse_whenConfiguredTokenIsBlank() {
        AccountIdentity identity = new AccountIdentity("http://localhost:8081", "   ");
        assertFalse(identity.isInternalService("some-token"));
        assertFalse(identity.isInternalService("   "));
        assertFalse(identity.isInternalService(""));
    }

    @Test
    void isInternalService_returnsTrue_whenProvidedTokenMatchesConfiguredToken() {
        AccountIdentity identity = new AccountIdentity("http://localhost:8081", "secret-token");
        assertTrue(identity.isInternalService("secret-token"));
        assertFalse(identity.isInternalService("wrong-token"));
        assertFalse(identity.isInternalService(null));
        assertFalse(identity.isInternalService(""));
    }
}

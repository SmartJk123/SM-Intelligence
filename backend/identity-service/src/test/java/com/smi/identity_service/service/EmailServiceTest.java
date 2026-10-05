package com.smi.identity_service.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmailServiceTest {

    @Mock
    private PasswordResetMailer mailer;

    private EmailService service() {
        return new EmailService(mailer, "https://app.example/login");
    }

    @Test
    @DisplayName("Owner email carries the set-password link and sign-in address, and no password")
    void ownerEmailContainsWhatTheRecipientNeeds() {
        when(mailer.send(eq("a@b.co"), anyString(), anyString())).thenReturn(true);

        boolean sent = service().sendOrganizationOwnerSetup("a@b.co", "Ann", "Acme",
                "https://app.example/reset-password?token=xyz");

        assertTrue(sent);
        ArgumentCaptor<String> text = ArgumentCaptor.forClass(String.class);
        verify(mailer).send(eq("a@b.co"), anyString(), text.capture());
        assertTrue(text.getValue().contains("https://app.example/reset-password?token=xyz"));
        assertTrue(text.getValue().contains("https://app.example/login"));
        assertFalse(text.getValue().toLowerCase().contains("temporary password"));
    }

    @Test
    @DisplayName("A mail failure is reported as false, never thrown")
    void mailFailureReturnsFalse() {
        when(mailer.send(anyString(), anyString(), anyString())).thenReturn(false);

        assertFalse(service().sendMemberSetup("a@b.co", "Ann", "Acme", "ADMIN", "https://link"));
        assertFalse(service().sendExistingUserAddedToOrganization("a@b.co", "Ann", "Acme", "ADMIN"));
    }
}

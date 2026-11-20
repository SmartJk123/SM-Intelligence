package com.smi.identity_service.service;

import com.smi.identity_service.activity.ActivityLog;
import com.smi.identity_service.domain.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CustomerInviteResendTest {

    @Mock private UserService users;
    @Mock private OrganizationService organizations;
    @Mock private EmailService email;
    @Mock private PasswordResetService passwordResets;
    @Mock private ActivityLog activity;

    @InjectMocks private CustomerInviteService invites;

    private final UUID adminId = UUID.randomUUID();
    private final User customer = new User(UUID.randomUUID(), "Jacktone", "jacktone@example.com", "hash");

    @Test
    @DisplayName("resends the welcome email with a new temporary password and setup link, and audits it")
    void resendsTheInviteWithANewTemporaryPassword() {
        ArgumentCaptor<String> password = ArgumentCaptor.forClass(String.class);
        when(users.resetTemporaryPassword(eq(customer.getId()), password.capture())).thenReturn(customer);
        when(passwordResets.setupLink(customer)).thenReturn("http://localhost:4200/reset-password?token=abc");
        when(email.sendCustomerInvite(anyString(), anyString(), anyString(), anyString(), isNull())).thenReturn(true);
        when(users.findById(adminId)).thenReturn(Optional.empty());

        CustomerInviteService.Result result = invites.resend(customer.getId(), adminId);

        assertTrue(result.emailSent());
        assertEquals(12, password.getValue().length());
        verify(email).sendCustomerInvite(eq("jacktone@example.com"), eq("Jacktone"), eq(password.getValue()),
                eq("http://localhost:4200/reset-password?token=abc"), isNull());
        verify(activity).audit(any(), eq("CUSTOMER_INVITE_RESENT"), eq(customer), eq("Invite emailed again"));
    }

    @Test
    @DisplayName("records that the email was not sent when the mail server is unreachable")
    void reportsAFailedSend() {
        when(users.resetTemporaryPassword(eq(customer.getId()), anyString())).thenReturn(customer);
        when(passwordResets.setupLink(customer)).thenReturn("link");
        when(email.sendCustomerInvite(anyString(), anyString(), anyString(), anyString(), isNull())).thenReturn(false);
        when(users.findById(adminId)).thenReturn(Optional.empty());

        assertFalse(invites.resend(customer.getId(), adminId).emailSent());
        verify(activity).audit(any(), eq("CUSTOMER_INVITE_RESENT"), eq(customer), eq("Invite email not sent"));
    }

    @Test
    @DisplayName("refuses a customer who has already signed in, and sends nothing")
    void refusesACustomerWhoHasSignedIn() {
        when(users.resetTemporaryPassword(eq(customer.getId()), anyString()))
                .thenThrow(new IllegalArgumentException("This customer has already signed in."));

        assertThrows(IllegalArgumentException.class, () -> invites.resend(customer.getId(), adminId));
        verifyNoInteractions(email);
    }
}

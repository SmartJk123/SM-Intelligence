package com.smi.identity_service.service;

import com.smi.identity_service.activity.EmailTemplates;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class EmailServiceTest {

    /** A mailer that keeps what it was asked to send instead of talking to SMTP. */
    private static final class CapturingMailer extends PasswordResetMailer {
        record Sent(String to, String subject, String text, String templateKey, String[] secrets) {
        }

        final List<Sent> sent = new ArrayList<>();
        boolean accept = true;

        CapturingMailer() {
            super("", 587, "", "", "");
            setTemplates(EmailTemplates.builtIn());
        }

        @Override
        public boolean send(String to, String subject, String text, String templateKey, String... secrets) {
            sent.add(new Sent(to, subject, text, templateKey, secrets));
            return accept;
        }
    }

    private final CapturingMailer mailer = new CapturingMailer();
    private final EmailService service = new EmailService(mailer, "https://app.example/login");

    @Test
    @DisplayName("Owner email carries the set-password link and sign-in address, and no password")
    void ownerEmailContainsWhatTheRecipientNeeds() {
        boolean sent = service.sendOrganizationOwnerSetup("a@b.co", "Ann", "Acme",
                "https://app.example/reset-password?token=xyz");

        assertTrue(sent);
        CapturingMailer.Sent email = mailer.sent.getFirst();
        assertEquals("a@b.co", email.to());
        assertTrue(email.subject().contains("Acme"));
        assertTrue(email.text().contains("https://app.example/reset-password?token=xyz"));
        assertTrue(email.text().contains("https://app.example/login"));
        assertFalse(email.text().toLowerCase().contains("temporary password"));
        assertArrayEquals(new String[] {"https://app.example/reset-password?token=xyz"}, email.secrets());
    }

    @Test
    @DisplayName("Customer invite carries the temporary password and link, both marked secret for the outbox")
    void customerInviteMarksSecrets() {
        service.sendCustomerInvite("c@d.co", "Cy", "Temp-Pass-123", "https://app.example/reset-password?token=abc",
                "Acme");

        CapturingMailer.Sent email = mailer.sent.getFirst();
        assertEquals(EmailTemplates.CUSTOMER_INVITE, email.templateKey());
        assertTrue(email.text().contains("Temp-Pass-123"));
        assertTrue(email.text().contains("as part of Acme"));
        assertTrue(List.of(email.secrets()).contains("Temp-Pass-123"));
        assertTrue(List.of(email.secrets()).contains("https://app.example/reset-password?token=abc"));
        assertFalse(email.text().contains("{{"), "every placeholder is filled in");
    }

    @Test
    @DisplayName("Welcome email names the account type and the sign-in page")
    void welcomeEmail() {
        service.sendWelcome("e@f.co", "Eve", "ORGANIZATION");

        CapturingMailer.Sent email = mailer.sent.getFirst();
        assertEquals(EmailTemplates.WELCOME, email.templateKey());
        assertTrue(email.text().contains("Eve"));
        assertTrue(email.text().contains("organisation account"));
        assertTrue(email.text().contains("https://app.example/login"));
    }

    @Test
    @DisplayName("A mail failure is reported as false, never thrown")
    void mailFailureReturnsFalse() {
        mailer.accept = false;

        assertFalse(service.sendMemberSetup("a@b.co", "Ann", "Acme", "ADMIN", "https://link"));
        assertFalse(service.sendExistingUserAddedToOrganization("a@b.co", "Ann", "Acme", "ADMIN"));
    }
}

package com.smi.identity_service.service;

import com.smi.identity_service.activity.EmailTemplates;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * Sends the account emails (welcome, invites, organisation setup) through the
 * same SMTP settings as the password reset emails (SMTP_HOST and the rest, see
 * PasswordResetMailer). The wording comes from EmailTemplates, which an
 * administrator can edit, and every email is kept in the admin Outbox.
 *
 * Every method returns whether the email was sent instead of throwing, because
 * a mail outage must not undo an account or membership already saved; the
 * caller reports emailSent=false and the admin can follow up.
 */
@Service
public class EmailService {

    private final PasswordResetMailer mailer;
    private final String loginUrl;

    public EmailService(PasswordResetMailer mailer,
                        @Value("${app.frontend.login-url:http://localhost:4200/login}") String loginUrl) {
        this.mailer = mailer;
        this.loginUrl = loginUrl;
    }

    /** Sent after a customer signs up on the web app. accountType is INDIVIDUAL or ORGANIZATION. */
    public boolean sendWelcome(String toEmail, String toName, String accountType) {
        return mailer.sendTemplate(toEmail, EmailTemplates.WELCOME, values(
                "name", toName,
                "email", toEmail,
                "accountType", "ORGANIZATION".equalsIgnoreCase(accountType) ? "organisation" : "individual",
                "loginUrl", loginUrl));
    }

    /**
     * Sent when an administrator adds a customer. Carries a temporary password and a
     * one-time link to choose their own; both are hidden in the outbox copy.
     */
    public boolean sendCustomerInvite(String toEmail, String toName, String temporaryPassword, String setupLink,
                                      String organisationName) {
        return mailer.sendTemplate(toEmail, EmailTemplates.CUSTOMER_INVITE, values(
                "name", toName,
                "email", toEmail,
                "temporaryPassword", temporaryPassword,
                "setupLink", setupLink,
                "loginUrl", loginUrl,
                "organisationLine", organisationName == null ? "" : " as part of " + organisationName));
    }

    public boolean sendOrganizationOwnerSetup(String toEmail, String toName, String organizationName,
                                              String setupLink) {
        return mailer.sendTemplate(toEmail, EmailTemplates.ORGANISATION_OWNER_SETUP, values(
                "name", toName,
                "email", toEmail,
                "organisation", organizationName,
                "setupLink", setupLink,
                "loginUrl", loginUrl));
    }

    public boolean sendMemberSetup(String toEmail, String toName, String organizationName, String role,
                                   String setupLink) {
        return mailer.sendTemplate(toEmail, EmailTemplates.MEMBER_SETUP, values(
                "name", toName,
                "email", toEmail,
                "organisation", organizationName,
                "role", role,
                "setupLink", setupLink,
                "loginUrl", loginUrl));
    }

    public boolean sendExistingUserAddedToOrganization(String toEmail, String toName, String organizationName,
                                                        String role) {
        return mailer.sendTemplate(toEmail, EmailTemplates.EXISTING_MEMBER_ADDED, values(
                "name", toName,
                "organisation", organizationName,
                "role", role,
                "loginUrl", loginUrl));
    }

    private static Map<String, String> values(String... pairs) {
        Map<String, String> values = new HashMap<>();
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            values.put(pairs[i], pairs[i + 1]);
        }
        return values;
    }
}

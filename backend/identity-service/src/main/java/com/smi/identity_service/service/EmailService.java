package com.smi.identity_service.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Sends organisation invitation emails, through the same SMTP settings as the
 * password reset emails (SMTP_HOST and the rest, see PasswordResetMailer).
 *
 * A new account gets a one-time link to choose its own password, never a
 * password in the email. Every method returns whether the email was sent
 * instead of throwing, because a mail outage must not undo an organisation or
 * membership already saved; the caller reports emailSent=false and the admin
 * can follow up.
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

    public boolean sendOrganizationOwnerSetup(String toEmail, String toName, String organizationName,
                                              String setupLink) {
        return mailer.send(toEmail, "You're set up as the owner of " + organizationName + " on SmartMoney",
                "Hi " + toName + ",\n\n"
                        + "An account has been created for you as the owner of \"" + organizationName
                        + "\" on SmartMoney.\n\n"
                        + setupInstructions(toEmail, setupLink));
    }

    public boolean sendMemberSetup(String toEmail, String toName, String organizationName, String role,
                                   String setupLink) {
        return mailer.send(toEmail, "You've been added to " + organizationName + " on SmartMoney",
                "Hi " + toName + ",\n\n"
                        + "An account has been created for you as " + role + " on \"" + organizationName
                        + "\" on SmartMoney.\n\n"
                        + setupInstructions(toEmail, setupLink));
    }

    public boolean sendExistingUserAddedToOrganization(String toEmail, String toName, String organizationName,
                                                        String role) {
        return mailer.send(toEmail, "You've been added to " + organizationName + " on SmartMoney",
                "Hi " + toName + ",\n\n"
                        + "Your existing SmartMoney account has been added to \"" + organizationName + "\" as "
                        + role + ".\n\n"
                        + "Sign in at " + loginUrl + " with your usual password.\n");
    }

    private String setupInstructions(String toEmail, String setupLink) {
        return "Choose your password with this link (it works once and expires in 7 days):\n\n"
                + setupLink + "\n\n"
                + "Then sign in at " + loginUrl + " with " + toEmail + ".\n"
                + "If the link has expired, use \"Forgot password?\" on the sign-in page.\n";
    }
}

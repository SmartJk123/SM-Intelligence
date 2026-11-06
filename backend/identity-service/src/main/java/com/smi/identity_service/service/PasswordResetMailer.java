package com.smi.identity_service.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.smi.identity_service.activity.ActivityLog;
import com.smi.identity_service.activity.EmailTemplates;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Properties;

/**
 * Emails password reset links over SMTP, configured by SMTP_HOST, SMTP_PORT,
 * SMTP_USERNAME, SMTP_PASSWORD and MAIL_FROM. Without SMTP_HOST nothing is
 * sent and a warning is logged, so the service still starts on a machine with
 * no mail account set up.
 */
@Component
public class PasswordResetMailer {

    private static final Logger log = LoggerFactory.getLogger(PasswordResetMailer.class);

    private final JavaMailSenderImpl sender;
    private final String from;

    public PasswordResetMailer(
            @Value("${app.mail.host:}") String host,
            @Value("${app.mail.port:587}") int port,
            @Value("${app.mail.username:}") String username,
            @Value("${app.mail.password:}") String password,
            @Value("${app.mail.from:}") String from) {
        this.from = from.isBlank() ? username : from;
        if (host.isBlank()) {
            this.sender = null;
            log.warn("SMTP_HOST is not set, so password reset emails cannot be sent");
            return;
        }
        JavaMailSenderImpl mail = new JavaMailSenderImpl();
        mail.setHost(host);
        mail.setPort(port);
        mail.setUsername(username.isBlank() ? null : username);
        mail.setPassword(password.isBlank() ? null : password);
        Properties properties = mail.getJavaMailProperties();
        properties.put("mail.smtp.auth", String.valueOf(!username.isBlank()));
        // Port 465 is TLS from the first byte; every other port upgrades with STARTTLS.
        if (port == 465) {
            properties.put("mail.smtp.ssl.enable", "true");
        } else {
            properties.put("mail.smtp.starttls.enable", "true");
            properties.put("mail.smtp.starttls.required", "true");
        }
        properties.put("mail.smtp.connectiontimeout", "10000");
        properties.put("mail.smtp.timeout", "10000");
        properties.put("mail.smtp.writetimeout", "10000");
        this.sender = mail;
    }

    private ActivityLog activity;
    private EmailTemplates templates = EmailTemplates.builtIn();

    /** Optional so the mailer can still be built on its own, as the health monitor tests do. */
    @Autowired(required = false)
    public void setActivityLog(ActivityLog activity) {
        this.activity = activity;
    }

    @Autowired(required = false)
    public void setTemplates(EmailTemplates templates) {
        this.templates = templates;
    }

    public boolean isConfigured() {
        return sender != null;
    }

    /** Sends the link. Failures are logged, never thrown: the caller's answer must not reveal them. */
    public void sendResetLink(String to, String name, String link, long validMinutes) {
        sendTemplate(to, EmailTemplates.PASSWORD_RESET,
                Map.of("name", name, "link", link, "validMinutes", String.valueOf(validMinutes)));
    }

    /** Fills an email template in and sends it; its secret values are hidden in the outbox copy. */
    public boolean sendTemplate(String to, String templateKey, Map<String, String> values) {
        EmailTemplates.Rendered email = templates.render(templateKey, values);
        return send(to, email.subject(), email.body(), templateKey, email.secretValues());
    }

    /**
     * Sends a plain text email with the SMTP settings above.
     *
     * @return true when the server accepted it; false when SMTP is not set up or
     *         sending failed. Never throws, so a mail outage cannot undo work the
     *         caller has already saved.
     */
    public boolean send(String to, String subject, String text) {
        return send(to, subject, text, null);
    }

    /**
     * As send(to, subject, text), and keeps a copy in the admin Outbox under
     * templateKey. Each of secrets is stored as [hidden] in that copy.
     */
    public boolean send(String to, String subject, String text, String templateKey, String... secrets) {
        if (sender == null) {
            log.warn("SMTP_HOST is not set, so the email \"{}\" was not sent", subject);
            record(to, subject, text, templateKey, "NOT_CONFIGURED", "SMTP is not set up", secrets);
            return false;
        }
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(to);
        message.setSubject(subject);
        message.setText(text);
        try {
            sender.send(message);
            log.info("Sent the email \"{}\"", subject);
            record(to, subject, text, templateKey, "SENT", null, secrets);
            return true;
        } catch (MailException error) {
            log.warn("Could not send the email \"{}\": {}", subject, error.getMessage());
            record(to, subject, text, templateKey, "FAILED", error.getMessage(), secrets);
            return false;
        }
    }

    private void record(String to, String subject, String text, String templateKey, String status, String error,
                        String... secrets) {
        if (activity != null) {
            activity.recordEmail(to, subject, text, templateKey, status, error, secrets);
        }
    }
}

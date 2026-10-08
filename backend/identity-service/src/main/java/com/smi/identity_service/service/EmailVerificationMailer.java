package com.smi.identity_service.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Component;

import java.util.Properties;

/**
 * Emails verification links over SMTP, configured by SMTP_HOST, SMTP_PORT,
 * SMTP_USERNAME, SMTP_PASSWORD and MAIL_FROM. Without SMTP_HOST nothing is
 * sent and a warning is logged, allowing the service to run offline/locally.
 */
@Component
public class EmailVerificationMailer {

    private static final Logger log = LoggerFactory.getLogger(EmailVerificationMailer.class);

    private final JavaMailSenderImpl sender;
    private final String from;

    public EmailVerificationMailer(
            @Value("${app.mail.host:}") String host,
            @Value("${app.mail.port:587}") int port,
            @Value("${app.mail.username:}") String username,
            @Value("${app.mail.password:}") String password,
            @Value("${app.mail.from:}") String from) {
        this.from = from.isBlank() ? username : from;
        if (host.isBlank()) {
            this.sender = null;
            log.warn("SMTP_HOST is not set, so email verification messages cannot be sent");
            return;
        }
        JavaMailSenderImpl mail = new JavaMailSenderImpl();
        mail.setHost(host);
        mail.setPort(port);
        mail.setUsername(username.isBlank() ? null : username);
        mail.setPassword(password.isBlank() ? null : password);
        Properties properties = mail.getJavaMailProperties();
        properties.put("mail.smtp.auth", String.valueOf(!username.isBlank()));
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

    public boolean isConfigured() {
        return sender != null;
    }

    public void sendVerificationLink(String to, String name, String link, long validHours) {
        send(to, "Verify your SM-Intelligence account", "Hello " + name + ",\n\n"
                + "Welcome to SM-Intelligence! Please confirm your email address by opening the following link:\n\n"
                + link + "\n\n"
                + "This link is valid for " + validHours + " hours.\n"
                + "If you did not sign up for SM-Intelligence, you can safely ignore this email.\n");
    }

    public boolean send(String to, String subject, String text) {
        if (sender == null) {
            log.warn("SMTP_HOST is not set, so the email \"{}\" was not sent", subject);
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
            return true;
        } catch (MailException error) {
            log.warn("Could not send the email \"{}\": {}", subject, error.getMessage());
            return false;
        }
    }
}

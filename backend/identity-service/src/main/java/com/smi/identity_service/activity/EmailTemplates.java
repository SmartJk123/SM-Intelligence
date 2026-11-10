package com.smi.identity_service.activity;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The text of every email the platform sends. Each template has built-in wording;
 * an administrator can replace the subject and body from the admin portal and
 * reset it back. Placeholders are written {{name}}.
 */
@Service
public class EmailTemplates {

    private static final Logger log = LoggerFactory.getLogger(EmailTemplates.class);

    public static final String WELCOME = "WELCOME";
    public static final String CUSTOMER_INVITE = "CUSTOMER_INVITE";
    public static final String PASSWORD_RESET = "PASSWORD_RESET";
    public static final String ORGANISATION_OWNER_SETUP = "ORGANISATION_OWNER_SETUP";
    public static final String MEMBER_SETUP = "MEMBER_SETUP";
    public static final String EXISTING_MEMBER_ADDED = "EXISTING_MEMBER_ADDED";
    public static final String STAFF_INVITE = "STAFF_INVITE";
    public static final String STAFF_ACCESS_GRANTED = "STAFF_ACCESS_GRANTED";

    /** A built-in template. secrets are placeholders whose values never reach the outbox. */
    public record Definition(String key, String name, String description, String subject, String body,
                             List<String> placeholders, List<String> secrets) {
    }

    /** A template as the admin portal shows it. */
    public record View(String key, String name, String description, String subject, String body,
                       List<String> placeholders, boolean customised, OffsetDateTime updatedAt, String updatedBy) {
    }

    public record Rendered(String key, String subject, String body, String[] secretValues) {
    }

    private static final Map<String, Definition> BUILT_IN = new LinkedHashMap<>();

    static {
        add(new Definition(WELCOME, "Welcome", "Sent when a customer creates an account on the web app.",
                "Welcome to SmartMoney Intelligence",
                """
                Hi {{name}},

                Welcome to SmartMoney Intelligence. Your {{accountType}} account is ready.

                Sign in at {{loginUrl}} with {{email}} to add your bank accounts and see all your money in one place.

                If you did not create this account, reply to this email and we will look into it.
                """,
                List.of("name", "email", "accountType", "loginUrl"), List.of()));
        add(new Definition(CUSTOMER_INVITE, "Customer invite",
                "Sent when an administrator adds a customer. Carries a temporary password.",
                "Your SmartMoney Intelligence account is ready",
                """
                Hi {{name}},

                An administrator has created a SmartMoney Intelligence account for you{{organisationLine}}.

                Sign in at {{loginUrl}}
                Email: {{email}}
                Temporary password: {{temporaryPassword}}

                Please change this password straight away. You can choose your own password with this link
                (it works once and expires in 7 days):

                {{setupLink}}

                If the link has expired, use "Forgot password?" on the sign-in page.
                """,
                List.of("name", "email", "temporaryPassword", "setupLink", "loginUrl", "organisationLine"),
                List.of("temporaryPassword", "setupLink")));
        add(new Definition(PASSWORD_RESET, "Password reset", "Sent when someone uses Forgot password.",
                "Reset your SM-Intelligence password",
                """
                Hello {{name}},

                We received a request to reset the password for your SM-Intelligence account.
                Open this link to choose a new password:

                {{link}}

                The link works once and expires in {{validMinutes}} minutes.
                If you did not ask for this, you can ignore this email; your password has not changed.
                """,
                List.of("name", "link", "validMinutes"), List.of("link")));
        add(new Definition(ORGANISATION_OWNER_SETUP, "Organisation owner setup",
                "Sent when an organisation is created with a new owner.",
                "You're set up as the owner of {{organisation}} on SmartMoney",
                """
                Hi {{name}},

                An account has been created for you as the owner of "{{organisation}}" on SmartMoney.

                Choose your password with this link (it works once and expires in 7 days):

                {{setupLink}}

                Then sign in at {{loginUrl}} with {{email}}.
                If the link has expired, use "Forgot password?" on the sign-in page.
                """,
                List.of("name", "email", "organisation", "setupLink", "loginUrl"), List.of("setupLink")));
        add(new Definition(MEMBER_SETUP, "Organisation member setup",
                "Sent when a new person is invited into an organisation.",
                "You've been added to {{organisation}} on SmartMoney",
                """
                Hi {{name}},

                An account has been created for you as {{role}} on "{{organisation}}" on SmartMoney.

                Choose your password with this link (it works once and expires in 7 days):

                {{setupLink}}

                Then sign in at {{loginUrl}} with {{email}}.
                If the link has expired, use "Forgot password?" on the sign-in page.
                """,
                List.of("name", "email", "organisation", "role", "setupLink", "loginUrl"), List.of("setupLink")));
        add(new Definition(EXISTING_MEMBER_ADDED, "Added to an organisation",
                "Sent when someone who already has an account is added to an organisation.",
                "You've been added to {{organisation}} on SmartMoney",
                """
                Hi {{name}},

                Your existing SmartMoney account has been added to "{{organisation}}" as {{role}}.

                Sign in at {{loginUrl}} with your usual password.
                """,
                List.of("name", "organisation", "role", "loginUrl"), List.of()));
        add(new Definition(STAFF_INVITE, "Staff invite",
                "Sent when an administrator adds a new staff member. Carries a temporary password.",
                "You have been given access to the SmartMoney admin portal",
                """
                Hi {{name}},

                You have been added to the SmartMoney Intelligence admin portal as {{role}}.

                Sign in at {{adminLoginUrl}}
                Email: {{email}}
                Temporary password: {{temporaryPassword}}

                Please choose your own password straight away with this link
                (it works once and expires in 7 days):

                {{setupLink}}
                """,
                List.of("name", "email", "role", "temporaryPassword", "setupLink", "adminLoginUrl"),
                List.of("temporaryPassword", "setupLink")));
        add(new Definition(STAFF_ACCESS_GRANTED, "Staff access granted",
                "Sent when an existing account is given admin access.",
                "You now have access to the SmartMoney admin portal",
                """
                Hi {{name}},

                Your SmartMoney account has been given access to the admin portal as {{role}}.

                Sign in at {{adminLoginUrl}} with your usual email address and password.
                """,
                List.of("name", "role", "adminLoginUrl"), List.of()));
    }

    private static void add(Definition definition) {
        BUILT_IN.put(definition.key(), definition);
    }

    private final EmailTemplateOverrideRepository overrides;

    @Autowired
    public EmailTemplates(EmailTemplateOverrideRepository overrides) {
        this.overrides = overrides;
    }

    /** Built-in wording only, for unit tests and code that runs without a database. */
    public static EmailTemplates builtIn() {
        return new EmailTemplates(null);
    }

    public List<View> list() {
        return BUILT_IN.values().stream().map(this::view).toList();
    }

    public View get(String key) {
        return view(definition(key));
    }

    public View update(String key, String subject, String body, String updatedBy) {
        definition(key);
        if (subject == null || subject.isBlank() || body == null || body.isBlank()) {
            throw new IllegalArgumentException("A template needs a subject and a body");
        }
        EmailTemplateOverride override = overrides.findById(key).orElseGet(() -> new EmailTemplateOverride(key));
        override.update(subject.trim(), body, updatedBy);
        overrides.save(override);
        return get(key);
    }

    public View reset(String key) {
        definition(key);
        overrides.deleteById(key);
        return get(key);
    }

    /** Fills the template in. Placeholders without a value become empty. */
    public Rendered render(String key, Map<String, String> values) {
        Definition definition = definition(key);
        Optional<EmailTemplateOverride> override = override(key);
        String subject = fill(override.map(EmailTemplateOverride::getSubject).orElse(definition.subject()), values);
        String body = fill(override.map(EmailTemplateOverride::getBody).orElse(definition.body()), values);
        String[] secrets = definition.secrets().stream().map(values::get).toArray(String[]::new);
        return new Rendered(key, subject, body, secrets);
    }

    private static String fill(String text, Map<String, String> values) {
        String result = text;
        for (Map.Entry<String, String> entry : values.entrySet()) {
            result = result.replace("{{" + entry.getKey() + "}}", entry.getValue() == null ? "" : entry.getValue());
        }
        return result.replaceAll("\\{\\{[a-zA-Z]+}}", "");
    }

    private View view(Definition definition) {
        Optional<EmailTemplateOverride> override = override(definition.key());
        return new View(definition.key(), definition.name(), definition.description(),
                override.map(EmailTemplateOverride::getSubject).orElse(definition.subject()),
                override.map(EmailTemplateOverride::getBody).orElse(definition.body()),
                definition.placeholders(), override.isPresent(),
                override.map(EmailTemplateOverride::getUpdatedAt).orElse(null),
                override.map(EmailTemplateOverride::getUpdatedBy).orElse(null));
    }

    private Optional<EmailTemplateOverride> override(String key) {
        if (overrides == null) {
            return Optional.empty();
        }
        try {
            return overrides.findById(key);
        } catch (RuntimeException error) {
            // Unreadable table: the built-in wording still lets the email go out.
            log.warn("Could not read the {} email template, using the built-in text: {}", key, error.getMessage());
            return Optional.empty();
        }
    }

    private static Definition definition(String key) {
        Definition definition = BUILT_IN.get(key);
        if (definition == null) {
            throw new IllegalArgumentException("Unknown email template: " + key);
        }
        return definition;
    }
}

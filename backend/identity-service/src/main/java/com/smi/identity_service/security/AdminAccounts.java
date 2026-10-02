package com.smi.identity_service.security;

import com.smi.identity_service.domain.User;
import com.smi.identity_service.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Which addresses are platform administrators.
 *
 * The list comes from ADMIN_EMAILS (comma separated). At start up every
 * existing account on the list is given the PLATFORM_ADMIN role, and an
 * account registered later with a listed address gets it on registration.
 * Removing an address from the list does not demote the account: change the
 * role column for that, so an empty or mistyped variable can never lock every
 * administrator out.
 */
@Component
public class AdminAccounts implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminAccounts.class);

    private final Set<String> emails;
    private final UserRepository userRepository;

    public AdminAccounts(@Value("${app.admin.emails:}") String emails, UserRepository userRepository) {
        this.emails = Arrays.stream(emails.split(","))
                .map(email -> email.trim().toLowerCase())
                .filter(email -> !email.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
        this.userRepository = userRepository;
    }

    public boolean isAdminEmail(String emailAddress) {
        return emailAddress != null && emails.contains(emailAddress.trim().toLowerCase());
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (emails.isEmpty()) {
            log.warn("ADMIN_EMAILS is empty, so no account is granted admin access at start up");
            return;
        }
        for (String email : emails) {
            userRepository.findByEmailAddressAndDeletedAtIsNull(email).ifPresentOrElse(user -> {
                if (!user.isPlatformAdmin()) {
                    user.setRole(User.ROLE_PLATFORM_ADMIN);
                    user.setUpdatedAt(OffsetDateTime.now());
                    userRepository.save(user);
                    log.info("Granted platform admin to {}", email);
                }
            }, () -> log.info("Admin address {} has no account yet; it becomes admin when it registers", email));
        }
    }
}

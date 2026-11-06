package com.smi.identity_service.service;

import com.smi.identity_service.activity.ActivityLog;
import com.smi.identity_service.domain.Organization;
import com.smi.identity_service.domain.User;
import com.smi.identity_service.dto.CreateCustomerRequest;
import com.smi.identity_service.dto.UserSummaryResponse;
import com.smi.identity_service.exception.UserAlreadyExistsException;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.UUID;

/**
 * Adds a customer from the admin portal: creates the account (and, for an
 * organisation account, the organisation or the membership), then emails a
 * temporary password with a link to choose their own.
 *
 * Not @Transactional at this level, like OrganizationInviteService: each step
 * commits on its own and the email goes out after, outside any transaction.
 */
@Service
public class CustomerInviteService {

    /** No look-alike characters (0/O, 1/l/I), so the password can be typed from the email. */
    private static final String PASSWORD_CHARS = "abcdefghijkmnpqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    public record Result(UserSummaryResponse user, boolean emailSent, UUID organizationId, String organizationName) {
    }

    private final UserService users;
    private final OrganizationService organizations;
    private final EmailService email;
    private final PasswordResetService passwordResets;
    private final ActivityLog activity;

    public CustomerInviteService(UserService users, OrganizationService organizations, EmailService email,
                                 PasswordResetService passwordResets, ActivityLog activity) {
        this.users = users;
        this.organizations = organizations;
        this.email = email;
        this.passwordResets = passwordResets;
        this.activity = activity;
    }

    public Result create(CreateCustomerRequest request, UUID adminId) {
        boolean organisation = "ORGANIZATION".equals(request.accountType());
        boolean existingOrg = request.organizationId() != null;
        boolean newOrg = request.newOrganizationName() != null && !request.newOrganizationName().isBlank();
        if (organisation && existingOrg == newOrg) {
            // Checked before anything is written, so a bad request leaves nothing behind.
            throw new IllegalArgumentException("Choose an existing organisation or name a new one");
        }

        if (users.findByEmail(request.email()).isPresent()) {
            throw new UserAlreadyExistsException("A user with email '" + request.email().trim().toLowerCase()
                    + "' already exists");
        }
        // Looked up (or created) before the account, so a wrong organisation id leaves no account behind.
        Organization org = null;
        if (organisation) {
            org = existingOrg
                    ? organizations.getOrganization(request.organizationId())
                    : organizations.createOrganizationRow(request.newOrganizationName().trim(), request.businessType());
        }

        String temporaryPassword = temporaryPassword();
        User user = users.createCustomer(request.name(), request.email(), request.phoneNumber(), temporaryPassword,
                request.accountType(), org == null ? null : org.getName());

        if (organisation) {
            String role = request.role() == null || request.role().isBlank()
                    ? (existingOrg ? "MEMBER" : "OWNER")
                    : request.role();
            organizations.addMember(org.getId(), user, role, adminId);
        }

        boolean sent = email.sendCustomerInvite(user.getEmailAddress(), user.getName(), temporaryPassword,
                passwordResets.setupLink(user), org == null ? null : org.getName());

        ActivityLog.Actor admin = users.findById(adminId).map(ActivityLog.Actor::of).orElse(ActivityLog.Actor.system());
        activity.audit(admin, "CUSTOMER_CREATED", user,
                (organisation ? "Organisation account" + " in " + org.getName() : "Individual account")
                        + (sent ? ", invite emailed" : ", invite email not sent"));
        activity.notifyAdmins("Customer", "INFO", "Customer added",
                user.getName() + " (" + user.getEmailAddress() + ") was added by "
                        + (admin.email() == null ? "an administrator" : admin.email()) + ".");

        return new Result(UserSummaryResponse.fromUser(user), sent, org == null ? null : org.getId(),
                org == null ? null : org.getName());
    }

    private static String temporaryPassword() {
        StringBuilder password = new StringBuilder(12);
        for (int i = 0; i < 12; i++) {
            password.append(PASSWORD_CHARS.charAt(RANDOM.nextInt(PASSWORD_CHARS.length())));
        }
        return password.toString();
    }
}

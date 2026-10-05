package com.smi.identity_service.service;

import com.smi.identity_service.domain.Organization;
import com.smi.identity_service.domain.OrganizationMember;
import com.smi.identity_service.domain.User;
import com.smi.identity_service.dto.CreateOrganizationRequest;
import com.smi.identity_service.dto.InviteMemberRequest;
import com.smi.identity_service.dto.OrganizationMemberResponse;
import com.smi.identity_service.dto.OrganizationResponse;
import com.smi.identity_service.exception.UserAlreadyMemberException;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.Optional;
import java.util.UUID;

/**
 * Orchestrates organization + membership creation together with the account
 * creation and email side effects they need.
 *
 * Deliberately NOT @Transactional at this level: it calls into
 * {@link OrganizationService} and {@link UserService} (each transactional on
 * their own), then sends email afterwards. If this class instead owned one
 * big transactional method that called a private "helper" internally, that
 * self-invocation would bypass Spring's proxy and silently turn @Transactional
 * into a no-op, so a failure mid-sequence could leave orphaned rows instead of
 * rolling back. Splitting persistence and orchestration into separate beans
 * avoids that trap, and keeps the (potentially slow) email call outside of any
 * database transaction.
 */
@Service
public class OrganizationInviteService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String PASSWORD_CHARS =
            "abcdefghijkmnopqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ23456789!@#$%";

    private final OrganizationService organizationService;
    private final UserService userService;
    private final EmailService emailService;
    private final PasswordResetService passwordResets;

    public OrganizationInviteService(OrganizationService organizationService, UserService userService,
                                      EmailService emailService, PasswordResetService passwordResets) {
        this.organizationService = organizationService;
        this.userService = userService;
        this.emailService = emailService;
        this.passwordResets = passwordResets;
    }

    public OrganizationResponse createOrganizationWithOwner(CreateOrganizationRequest request) {
        boolean hasOwnerName = request.getOwnerName() != null && !request.getOwnerName().isBlank();
        boolean hasOwnerEmail = request.getOwnerEmail() != null && !request.getOwnerEmail().isBlank();
        if (hasOwnerName != hasOwnerEmail) {
            // Checked before anything is written, so a half-filled owner leaves no organisation behind.
            throw new IllegalArgumentException("Give both the owner name and the owner email, or neither");
        }

        Organization organization = organizationService.createOrganizationRow(
                request.getOrganizationName(), request.getBusinessType());

        if (!hasOwnerEmail) {
            // Saved on its own. No account is created and no email is sent, so emailSent is
            // null (not applicable). The owner is invited later through inviteMember.
            return OrganizationResponse.fromOrganization(organization, 0, null);
        }

        boolean emailSent;
        Optional<User> existing = userService.findByEmail(request.getOwnerEmail());
        if (existing.isPresent()) {
            User user = existing.get();
            organizationService.addMember(organization.getId(), user, "OWNER", null);
            emailSent = emailService.sendExistingUserAddedToOrganization(
                    user.getEmailAddress(), user.getName(), organization.getName(), "OWNER");
        } else {
            User user = userService.createUser(request.getOwnerName().trim(), request.getOwnerEmail(), unusablePassword());
            organizationService.addMember(organization.getId(), user, "OWNER", null);
            emailSent = emailService.sendOrganizationOwnerSetup(
                    user.getEmailAddress(), user.getName(), organization.getName(), passwordResets.setupLink(user));
        }

        int memberCount = (int) organizationService.countActiveMembers(organization.getId());
        return OrganizationResponse.fromOrganization(organization, memberCount, emailSent);
    }

    public OrganizationMemberResponse inviteMember(UUID organizationId, InviteMemberRequest request) {
        Organization organization = organizationService.getOrganization(organizationId);

        Optional<User> existing = userService.findByEmail(request.getEmail());
        User user;
        boolean emailSent;
        if (existing.isPresent()) {
            user = existing.get();
            if (organizationService.isAlreadyMember(organizationId, user.getId())) {
                throw new UserAlreadyMemberException(
                        "User with email '" + request.getEmail() + "' is already a member of this organisation");
            }
            emailSent = emailService.sendExistingUserAddedToOrganization(
                    user.getEmailAddress(), user.getName(), organization.getName(), request.getRole());
        } else {
            user = userService.createUser(request.getName().trim(), request.getEmail(), unusablePassword());
            emailSent = emailService.sendMemberSetup(user.getEmailAddress(), user.getName(), organization.getName(),
                    request.getRole(), passwordResets.setupLink(user));
        }

        OrganizationMember member = organizationService.addMember(organizationId, user, request.getRole(), null);
        return OrganizationMemberResponse.fromMember(member, emailSent);
    }

    /**
     * A random password nobody is told. The new member chooses their own through
     * the emailed setup link, so no password ever travels by email.
     */
    private String unusablePassword() {
        StringBuilder password = new StringBuilder(32);
        for (int i = 0; i < 32; i++) {
            password.append(PASSWORD_CHARS.charAt(RANDOM.nextInt(PASSWORD_CHARS.length())));
        }
        return password.toString();
    }
}

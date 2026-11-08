package com.smi.identity_service.staff;

import com.smi.identity_service.activity.ActivityLog;
import com.smi.identity_service.domain.User;
import com.smi.identity_service.exception.StatusException;
import com.smi.identity_service.exception.UserAlreadyExistsException;
import com.smi.identity_service.exception.UserNotFoundException;
import com.smi.identity_service.repository.UserRepository;
import com.smi.identity_service.security.AdminAccounts;
import com.smi.identity_service.service.PasswordResetMailer;
import com.smi.identity_service.service.PasswordResetService;
import com.smi.identity_service.service.RefreshTokenService;
import com.smi.identity_service.activity.EmailTemplates;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Staff of the admin portal: administrators with a role. An address in
 * ADMIN_EMAILS (or an administrator without a role) has every permission;
 * staff added here get exactly their role's permissions.
 */
@Service
public class StaffService {

    private static final String PASSWORD_CHARS = "abcdefghijkmnpqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    public record Access(boolean fullAccess, UUID roleId, String roleName, List<String> permissions) {
    }

    public record StaffMember(UUID id, String name, String email, UUID roleId, String roleName, boolean fullAccess,
                              boolean fromAdminEmails, String status, OffsetDateTime lastLoginAt,
                              OffsetDateTime createdAt) {
    }

    public record RoleView(UUID id, String name, String description, List<String> permissions, long staffCount,
                           OffsetDateTime updatedAt) {
    }

    public record AddResult(StaffMember staff, boolean emailSent, boolean existingAccount) {
    }

    private final UserRepository users;
    private final StaffRoleRepository roles;
    private final AdminAccounts adminAccounts;
    private final PasswordEncoder passwordEncoder;
    private final PasswordResetService passwordResets;
    private final PasswordResetMailer mailer;
    private final RefreshTokenService sessions;
    private final ActivityLog activity;
    private final String adminLoginUrl;

    public StaffService(UserRepository users, StaffRoleRepository roles, AdminAccounts adminAccounts,
                        PasswordEncoder passwordEncoder, PasswordResetService passwordResets,
                        PasswordResetMailer mailer, RefreshTokenService sessions, ActivityLog activity,
                        @Value("${app.admin.portal-login-url:http://localhost:4300/admin/login}") String adminLoginUrl) {
        this.users = users;
        this.roles = roles;
        this.adminAccounts = adminAccounts;
        this.passwordEncoder = passwordEncoder;
        this.passwordResets = passwordResets;
        this.mailer = mailer;
        this.sessions = sessions;
        this.activity = activity;
        this.adminLoginUrl = adminLoginUrl;
    }

    // ---- What a signed-in administrator may do ----

    @Transactional(readOnly = true)
    public Access access(UUID userId) {
        User user = users.findById(userId).orElse(null);
        if (user == null || !user.isPlatformAdmin()) {
            return new Access(false, null, null, List.of());
        }
        StaffRole role = user.getStaffRoleId() == null || adminAccounts.isAdminEmail(user.getEmailAddress())
                ? null
                : roles.findById(user.getStaffRoleId()).orElse(null);
        if (role == null) {
            return new Access(true, null, "Full access", StaffPermissions.keys());
        }
        return new Access(false, role.getId(), role.getName(), role.permissionList());
    }

    public boolean can(UUID userId, String permission) {
        Access access = access(userId);
        return access.fullAccess() || access.permissions().contains(permission);
    }

    public void require(UUID userId, String permission) {
        if (userId == null || !can(userId, permission)) {
            throw new StatusException(HttpStatus.FORBIDDEN, "PERMISSION_DENIED",
                    "Your role does not include " + StaffPermissions.ALL.getOrDefault(permission, permission) + ".");
        }
    }

    // ---- Staff ----

    @Transactional(readOnly = true)
    public List<StaffMember> listStaff() {
        Map<UUID, StaffRole> byId = new HashMap<>();
        roles.findAll().forEach(role -> byId.put(role.getId(), role));
        return users.findByDeletedAtIsNullOrderByCreatedAtDesc().stream()
                .filter(User::isPlatformAdmin)
                .map(user -> member(user, byId))
                .sorted(Comparator.comparing(StaffMember::name, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    /**
     * Gives an email address admin access with a role. A new address gets an account
     * and an email with a temporary password; an existing customer account is promoted
     * and told by email to sign in to the admin portal with its usual password.
     */
    @Transactional
    public AddResult addStaff(String name, String email, UUID roleId, UUID actingAdmin) {
        String address = email == null ? "" : email.trim().toLowerCase();
        if (address.isEmpty() || !address.contains("@")) {
            throw new IllegalArgumentException("Enter the staff member's email address");
        }
        StaffRole role = role(roleId);
        User existing = users.findByEmailAddressAndDeletedAtIsNull(address).orElse(null);
        boolean sent;
        User user;
        if (existing != null) {
            if (existing.isPlatformAdmin()) {
                throw new UserAlreadyExistsException(address + " already has admin access");
            }
            existing.setRole(User.ROLE_PLATFORM_ADMIN);
            existing.setStaffRoleId(role.getId());
            existing.setUpdatedAt(OffsetDateTime.now());
            user = users.save(existing);
            sent = mailer.sendTemplate(address, EmailTemplates.STAFF_ACCESS_GRANTED, values(
                    "name", user.getName(), "role", role.getName(), "adminLoginUrl", adminLoginUrl));
        } else {
            if (name == null || name.trim().length() < 2) {
                throw new IllegalArgumentException("Enter the staff member's name");
            }
            String temporaryPassword = temporaryPassword();
            user = new User(name.trim(), address, null, passwordEncoder.encode(temporaryPassword));
            user.setRole(User.ROLE_PLATFORM_ADMIN);
            user.setStaffRoleId(role.getId());
            user = users.save(user);
            sent = mailer.sendTemplate(address, EmailTemplates.STAFF_INVITE, values(
                    "name", user.getName(), "email", address, "role", role.getName(),
                    "temporaryPassword", temporaryPassword, "setupLink", passwordResets.setupLink(user),
                    "adminLoginUrl", adminLoginUrl));
        }
        audit(actingAdmin, "STAFF_ADDED", user, "Role: " + role.getName() + (sent ? ", email sent" : ", email not sent"));
        activity.notifyAdmins("Security", "INFO", "Staff member added",
                address + " now has admin access as " + role.getName() + ".");
        return new AddResult(member(user, Map.of(role.getId(), role)), sent, existing != null);
    }

    @Transactional
    public StaffMember changeRole(UUID staffId, UUID roleId, UUID actingAdmin) {
        User user = staff(staffId);
        if (user.getId().equals(actingAdmin)) {
            throw new IllegalArgumentException("You cannot change your own role");
        }
        if (adminAccounts.isAdminEmail(user.getEmailAddress())) {
            throw new IllegalArgumentException(user.getEmailAddress()
                    + " is listed in ADMIN_EMAILS and always has full access");
        }
        StaffRole role = role(roleId);
        user.setStaffRoleId(role.getId());
        user.setUpdatedAt(OffsetDateTime.now());
        users.save(user);
        audit(actingAdmin, "STAFF_ROLE_CHANGED", user, "Role: " + role.getName());
        return member(user, Map.of(role.getId(), role));
    }

    /** Takes admin access away: the account becomes a customer account and its sessions end. */
    @Transactional
    public void removeStaff(UUID staffId, UUID actingAdmin) {
        User user = staff(staffId);
        if (user.getId().equals(actingAdmin)) {
            throw new IllegalArgumentException("You cannot remove your own admin access");
        }
        if (adminAccounts.isAdminEmail(user.getEmailAddress())) {
            throw new IllegalArgumentException(user.getEmailAddress()
                    + " is listed in ADMIN_EMAILS; remove it there and restart identity-service");
        }
        user.setRole(User.ROLE_USER);
        user.setStaffRoleId(null);
        user.setUpdatedAt(OffsetDateTime.now());
        users.save(user);
        sessions.revokeAllExcept(user.getId(), null);
        audit(actingAdmin, "STAFF_REMOVED", user, "Admin access removed");
        activity.notifyAdmins("Security", "WARNING", "Staff access removed",
                user.getEmailAddress() + " no longer has admin access.");
    }

    // ---- Roles ----

    @Transactional(readOnly = true)
    public List<RoleView> listRoles() {
        List<User> admins = users.findByDeletedAtIsNullOrderByCreatedAtDesc().stream().filter(User::isPlatformAdmin).toList();
        return roles.findAll().stream()
                .sorted(Comparator.comparing(StaffRole::getName, String.CASE_INSENSITIVE_ORDER))
                .map(role -> view(role, admins.stream().filter(u -> role.getId().equals(u.getStaffRoleId())).count()))
                .toList();
    }

    @Transactional
    public RoleView saveRole(UUID id, String name, String description, List<String> permissions, UUID actingAdmin) {
        String cleanName = name == null ? "" : name.trim();
        if (cleanName.length() < 2 || cleanName.length() > 60) {
            throw new IllegalArgumentException("A role name needs 2 to 60 characters");
        }
        List<String> cleanPermissions = new LinkedHashSet<>(permissions == null ? List.<String>of() : permissions)
                .stream().toList();
        for (String permission : cleanPermissions) {
            if (!StaffPermissions.ALL.containsKey(permission)) {
                throw new IllegalArgumentException("Unknown permission: " + permission);
            }
        }
        if (cleanPermissions.isEmpty()) {
            throw new IllegalArgumentException("Give the role at least one permission");
        }
        roles.findByNameIgnoreCase(cleanName)
                .filter(other -> !other.getId().equals(id))
                .ifPresent(other -> {
                    throw new UserAlreadyExistsException("A role named " + cleanName + " already exists");
                });
        StaffRole role;
        if (id == null) {
            role = new StaffRole(cleanName, description == null ? null : description.trim(), cleanPermissions);
        } else {
            role = role(id);
            role.update(cleanName, description == null ? null : description.trim(), cleanPermissions);
        }
        StaffRole saved = roles.save(role);
        activity.audit(actor(actingAdmin), id == null ? "STAFF_ROLE_CREATED" : "STAFF_ROLE_UPDATED", "STAFF_ROLE",
                saved.getId().toString(), saved.getName(), String.join(", ", cleanPermissions), ActivityLog.SUCCESS);
        return view(saved, users.findByDeletedAtIsNullOrderByCreatedAtDesc().stream()
                .filter(u -> u.isPlatformAdmin() && saved.getId().equals(u.getStaffRoleId())).count());
    }

    /** Refused while staff hold the role, so nobody silently ends up with full access. */
    @Transactional
    public void deleteRole(UUID id, UUID actingAdmin) {
        StaffRole role = role(id);
        long holders = users.findByDeletedAtIsNullOrderByCreatedAtDesc().stream()
                .filter(u -> u.isPlatformAdmin() && id.equals(u.getStaffRoleId())).count();
        if (holders > 0) {
            throw new IllegalArgumentException("Move the " + holders + " staff member" + (holders == 1 ? "" : "s")
                    + " with this role to another role first");
        }
        roles.delete(role);
        activity.audit(actor(actingAdmin), "STAFF_ROLE_DELETED", "STAFF_ROLE", id.toString(), role.getName(), null,
                ActivityLog.SUCCESS);
    }

    // ---- Helpers ----

    private StaffMember member(User user, Map<UUID, StaffRole> byId) {
        boolean fromList = adminAccounts.isAdminEmail(user.getEmailAddress());
        StaffRole role = fromList || user.getStaffRoleId() == null ? null : byId.get(user.getStaffRoleId());
        return new StaffMember(user.getId(), user.getName(), user.getEmailAddress(),
                role == null ? null : role.getId(), role == null ? "Full access" : role.getName(),
                role == null, fromList, user.getStatus(), user.getLastLoginAt(), user.getCreatedAt());
    }

    private RoleView view(StaffRole role, long count) {
        return new RoleView(role.getId(), role.getName(), role.getDescription(), role.permissionList(), count,
                role.getUpdatedAt());
    }

    private StaffRole role(UUID id) {
        if (id == null) {
            throw new IllegalArgumentException("Choose a role");
        }
        return roles.findById(id).orElseThrow(() -> new IllegalArgumentException("That role no longer exists"));
    }

    private User staff(UUID id) {
        return users.findById(id)
                .filter(user -> user.getDeletedAt() == null && user.isPlatformAdmin())
                .orElseThrow(() -> new UserNotFoundException("No staff member with id " + id));
    }

    private ActivityLog.Actor actor(UUID adminId) {
        return adminId == null ? ActivityLog.Actor.system()
                : users.findById(adminId).map(ActivityLog.Actor::of).orElse(ActivityLog.Actor.system());
    }

    private void audit(UUID actingAdmin, String action, User target, String detail) {
        activity.audit(actor(actingAdmin), action, target, detail);
    }

    private static String temporaryPassword() {
        StringBuilder password = new StringBuilder(12);
        for (int i = 0; i < 12; i++) {
            password.append(PASSWORD_CHARS.charAt(RANDOM.nextInt(PASSWORD_CHARS.length())));
        }
        return password.toString();
    }

    private static Map<String, String> values(String... pairs) {
        Map<String, String> values = new HashMap<>();
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            values.put(pairs[i], pairs[i + 1]);
        }
        return values;
    }
}

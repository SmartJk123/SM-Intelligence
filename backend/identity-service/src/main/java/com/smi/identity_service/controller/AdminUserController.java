package com.smi.identity_service.controller;

import com.smi.identity_service.activity.ActivityLog;
import com.smi.identity_service.domain.User;
import com.smi.identity_service.dto.CreateCustomerRequest;
import com.smi.identity_service.dto.UpdateUserRequest;
import com.smi.identity_service.dto.UpdateUserStatusRequest;
import com.smi.identity_service.dto.UserSummaryResponse;
import com.smi.identity_service.service.CustomerInviteService;
import com.smi.identity_service.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * User management for the admin interface. These are the same rows the
 * customer-facing web app registers and signs in against, so a change made
 * here applies to that user on their next request.
 *
 * Every endpoint requires an active PLATFORM_ADMIN token (see SecurityConfig),
 * and every change is written to the audit log.
 */
@RestController
@RequestMapping("/api/v1/admin/users")
public class AdminUserController {

    private final UserService userService;
    private final CustomerInviteService customerInvites;
    private final ActivityLog activity;

    public AdminUserController(UserService userService, CustomerInviteService customerInvites,
                               ActivityLog activity) {
        this.userService = userService;
        this.customerInvites = customerInvites;
        this.activity = activity;
    }

    @GetMapping
    public ResponseEntity<List<UserSummaryResponse>> listUsers() {
        List<UserSummaryResponse> response = userService.listActiveUsers().stream()
                .map(UserSummaryResponse::fromUser)
                .toList();
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<UserSummaryResponse> getUser(@PathVariable UUID id) {
        return ResponseEntity.ok(UserSummaryResponse.fromUser(userService.getActiveUser(id)));
    }

    /**
     * Adds a customer (individual or organisation) and emails them a temporary
     * password with a link to choose their own.
     */
    @PostMapping
    public ResponseEntity<CustomerInviteService.Result> createCustomer(@Valid @RequestBody CreateCustomerRequest request,
                                                                       @AuthenticationPrincipal UUID adminId) {
        return ResponseEntity.status(HttpStatus.CREATED).body(customerInvites.create(request, adminId));
    }

    /** Suspend or restore access. Refuses to suspend the calling admin. */
    @PatchMapping("/{id}/status")
    public ResponseEntity<UserSummaryResponse> changeStatus(@PathVariable UUID id,
                                                            @Valid @RequestBody UpdateUserStatusRequest request,
                                                            @AuthenticationPrincipal UUID adminId) {
        User user = userService.changeStatus(id, request.getStatus(), adminId);
        activity.audit(admin(adminId), user.isSuspended() ? "USER_SUSPENDED" : "USER_RESTORED", user, null);
        activity.notifyAdmins("Customer", user.isSuspended() ? "WARNING" : "INFO",
                user.isSuspended() ? "Customer suspended" : "Customer restored",
                user.getName() + " (" + user.getEmailAddress() + ") was " + (user.isSuspended() ? "suspended" : "restored")
                        + " by " + (admin(adminId).email() == null ? "an administrator" : admin(adminId).email()) + ".");
        return ResponseEntity.ok(UserSummaryResponse.fromUser(user));
    }

    @PutMapping("/{id}")
    public ResponseEntity<UserSummaryResponse> updateUser(@PathVariable UUID id,
                                                          @Valid @RequestBody UpdateUserRequest request,
                                                          @AuthenticationPrincipal UUID adminId) {
        User user = userService.updateProfile(id, request);
        activity.audit(admin(adminId), "USER_UPDATED", user, "Profile edited by an administrator");
        return ResponseEntity.ok(UserSummaryResponse.fromUser(user));
    }

    private ActivityLog.Actor admin(UUID adminId) {
        return adminId == null
                ? ActivityLog.Actor.system()
                : userService.findById(adminId).map(ActivityLog.Actor::of).orElse(ActivityLog.Actor.system());
    }
}

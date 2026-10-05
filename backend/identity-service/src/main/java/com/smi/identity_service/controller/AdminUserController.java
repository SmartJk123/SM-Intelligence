package com.smi.identity_service.controller;

import com.smi.identity_service.dto.UpdateUserRequest;
import com.smi.identity_service.dto.UpdateUserStatusRequest;
import com.smi.identity_service.dto.UserSummaryResponse;
import com.smi.identity_service.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
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
 * Every endpoint requires an active PLATFORM_ADMIN token (see SecurityConfig).
 */
@RestController
@RequestMapping("/api/v1/admin/users")
public class AdminUserController {

    private final UserService userService;

    public AdminUserController(UserService userService) {
        this.userService = userService;
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

    /** Suspend or restore access. Refuses to suspend the calling admin. */
    @PatchMapping("/{id}/status")
    public ResponseEntity<UserSummaryResponse> changeStatus(@PathVariable UUID id,
                                                            @Valid @RequestBody UpdateUserStatusRequest request,
                                                            @AuthenticationPrincipal UUID adminId) {
        return ResponseEntity.ok(UserSummaryResponse.fromUser(
                userService.changeStatus(id, request.getStatus(), adminId)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<UserSummaryResponse> updateUser(@PathVariable UUID id,
                                                          @Valid @RequestBody UpdateUserRequest request) {
        return ResponseEntity.ok(UserSummaryResponse.fromUser(userService.updateProfile(id, request)));
    }
}

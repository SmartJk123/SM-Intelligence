package com.smi.identity_service.controller;

import com.smi.identity_service.dto.UserSummaryResponse;
import com.smi.identity_service.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Read-only user listing for the admin interface, so a signup on the
 * customer-facing app is visible to admins without a separate sync step.
 *
 * SECURITY GAP: admin-interface has no real authentication against this service yet,
 * so this endpoint is left open (see SecurityConfig). It only reads data, but
 * must be locked down once admin-interface has real admin auth.
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
}

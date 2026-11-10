package com.smi.identity_service.staff;

import com.smi.identity_service.activity.ActivityLog;
import com.smi.identity_service.repository.UserRepository;
import com.smi.identity_service.settings.PlatformSettings;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Settings, staff and roles for the admin portal. Reading and changing staff and
 * roles needs the "settings" permission (see StaffAccessInterceptor); /staff/me is
 * open to every administrator so the portal knows what to show.
 */
@RestController
@RequestMapping("/api/v1/admin")
public class AdminStaffController {

    public record AddStaffRequest(String name, String email, UUID roleId) {
    }

    public record ChangeRoleRequest(UUID roleId) {
    }

    public record RoleRequest(String name, String description, List<String> permissions) {
    }

    public record Permission(String key, String label) {
    }

    private final StaffService staff;
    private final PlatformSettings settings;
    private final UserRepository users;
    private final ActivityLog activity;

    public AdminStaffController(StaffService staff, PlatformSettings settings, UserRepository users,
                                ActivityLog activity) {
        this.staff = staff;
        this.settings = settings;
        this.users = users;
        this.activity = activity;
    }

    /** The signed-in administrator's role and permissions, and the full permission list. */
    @GetMapping("/staff/me")
    public Map<String, Object> me(@AuthenticationPrincipal UUID adminId) {
        StaffService.Access access = staff.access(adminId);
        return Map.of(
                "fullAccess", access.fullAccess(),
                "roleName", access.roleName() == null ? "" : access.roleName(),
                "permissions", access.permissions(),
                "allPermissions", StaffPermissions.ALL.entrySet().stream()
                        .map(entry -> new Permission(entry.getKey(), entry.getValue())).toList());
    }

    @GetMapping("/staff")
    public List<StaffService.StaffMember> listStaff() {
        return staff.listStaff();
    }

    @PostMapping("/staff")
    public ResponseEntity<StaffService.AddResult> addStaff(@RequestBody AddStaffRequest request,
                                                           @AuthenticationPrincipal UUID adminId) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(staff.addStaff(request.name(), request.email(), request.roleId(), adminId));
    }

    @PatchMapping("/staff/{id}")
    public StaffService.StaffMember changeRole(@PathVariable UUID id, @RequestBody ChangeRoleRequest request,
                                               @AuthenticationPrincipal UUID adminId) {
        return staff.changeRole(id, request.roleId(), adminId);
    }

    @DeleteMapping("/staff/{id}")
    public ResponseEntity<Void> removeStaff(@PathVariable UUID id, @AuthenticationPrincipal UUID adminId) {
        staff.removeStaff(id, adminId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/staff-roles")
    public List<StaffService.RoleView> roles() {
        return staff.listRoles();
    }

    @PostMapping("/staff-roles")
    public ResponseEntity<StaffService.RoleView> createRole(@RequestBody RoleRequest request,
                                                            @AuthenticationPrincipal UUID adminId) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(staff.saveRole(null, request.name(), request.description(), request.permissions(), adminId));
    }

    @PutMapping("/staff-roles/{id}")
    public StaffService.RoleView updateRole(@PathVariable UUID id, @RequestBody RoleRequest request,
                                            @AuthenticationPrincipal UUID adminId) {
        return staff.saveRole(id, request.name(), request.description(), request.permissions(), adminId);
    }

    @DeleteMapping("/staff-roles/{id}")
    public ResponseEntity<Void> deleteRole(@PathVariable UUID id, @AuthenticationPrincipal UUID adminId) {
        staff.deleteRole(id, adminId);
        return ResponseEntity.noContent().build();
    }

    // ---- Settings ----

    @GetMapping("/settings")
    public Map<String, PlatformSettings.Section> allSettings() {
        Map<String, PlatformSettings.Section> all = new java.util.LinkedHashMap<>();
        PlatformSettings.SECTIONS.forEach(section -> all.put(section, settings.get(section)));
        return all;
    }

    @GetMapping("/settings/{section}")
    public PlatformSettings.Section settings(@PathVariable String section) {
        return settings.get(section);
    }

    @PutMapping("/settings/{section}")
    public PlatformSettings.Section saveSettings(@PathVariable String section, @RequestBody Map<String, Object> values,
                                                 @AuthenticationPrincipal UUID adminId) {
        ActivityLog.Actor actor = adminId == null ? ActivityLog.Actor.system()
                : users.findById(adminId).map(ActivityLog.Actor::of).orElse(ActivityLog.Actor.system());
        PlatformSettings.Section saved = settings.save(section, values, actor.email());
        activity.audit(actor, "SETTINGS_UPDATED", "SETTINGS", section, section + " settings",
                String.join(", ", values.keySet()), ActivityLog.SUCCESS);
        return saved;
    }
}

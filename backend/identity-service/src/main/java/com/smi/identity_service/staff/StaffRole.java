package com.smi.identity_service.staff;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/** A named set of admin portal permissions given to staff (see StaffPermissions). */
@Entity
@Table(name = "staff_roles")
public class StaffRole {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "name", nullable = false, unique = true)
    private String name;

    @Column(name = "description", length = 1000)
    private String description;

    @Column(name = "permissions", nullable = false, length = 2000)
    private String permissions;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    protected StaffRole() {
    }

    public StaffRole(String name, String description, List<String> permissions) {
        update(name, description, permissions);
    }

    public void update(String name, String description, List<String> permissions) {
        this.name = name;
        this.description = description;
        this.permissions = String.join(",", permissions);
        this.updatedAt = OffsetDateTime.now();
    }

    public List<String> permissionList() {
        return permissions == null || permissions.isBlank()
                ? List.of()
                : Arrays.stream(permissions.split(",")).map(String::trim).filter(p -> !p.isEmpty()).toList();
    }

    public UUID getId() { return id; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}

package com.smi.identity_service.staff;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface StaffRoleRepository extends JpaRepository<StaffRole, UUID> {
    Optional<StaffRole> findByNameIgnoreCase(String name);
}

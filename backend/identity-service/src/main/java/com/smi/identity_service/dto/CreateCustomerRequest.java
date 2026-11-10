package com.smi.identity_service.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * A customer added from the admin portal. For an ORGANIZATION account give either
 * organizationId (an existing organisation) or newOrganizationName (created here).
 */
public record CreateCustomerRequest(
        @NotBlank(message = "Name is required")
        @Size(min = 2, max = 100, message = "Name must be between 2 and 100 characters")
        String name,

        @NotBlank(message = "Email address is required")
        @Email(message = "Email address must be valid")
        String email,

        String phoneNumber,

        @NotBlank(message = "Account type is required")
        @Pattern(regexp = "^(INDIVIDUAL|ORGANIZATION)$", message = "Account type must be INDIVIDUAL or ORGANIZATION")
        String accountType,

        UUID organizationId,

        @Size(max = 150, message = "Organisation name must be at most 150 characters")
        String newOrganizationName,

        String businessType,

        /** Role inside the organisation: OWNER, ADMIN, MEMBER or VIEWER. Defaults to OWNER for a new one. */
        @Pattern(regexp = "^(OWNER|ADMIN|MEMBER|VIEWER)?$", message = "Role must be OWNER, ADMIN, MEMBER or VIEWER")
        String role) {
}

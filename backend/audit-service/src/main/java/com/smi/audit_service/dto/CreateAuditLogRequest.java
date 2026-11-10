package com.smi.audit_service.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.util.UUID;

public record CreateAuditLogRequest(
        @NotBlank(message = "sourceService is required")
        String sourceService,

        @NotBlank(message = "tableName is required")
        String tableName,

        @NotNull(message = "recordId is required")
        UUID recordId,

        @NotBlank(message = "action is required")
        @Pattern(regexp = "^(INSERT|UPDATE|DELETE)$", message = "action must be INSERT, UPDATE, or DELETE")
        String action,

        String oldData,

        String newData,

        UUID changedBy
) {
}

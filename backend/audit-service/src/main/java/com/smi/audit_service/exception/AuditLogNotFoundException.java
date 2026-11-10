package com.smi.audit_service.exception;

import java.util.UUID;

public class AuditLogNotFoundException extends RuntimeException {
    public AuditLogNotFoundException(UUID id) {
        super("Audit log entry not found with id: " + id);
    }
}

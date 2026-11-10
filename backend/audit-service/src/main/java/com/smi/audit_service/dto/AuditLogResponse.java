package com.smi.audit_service.dto;

import com.smi.audit_service.domain.AuditLog;

import java.time.Instant;
import java.util.UUID;

public record AuditLogResponse(
        UUID id,
        String sourceService,
        String tableName,
        UUID recordId,
        String action,
        String oldData,
        String newData,
        UUID changedBy,
        Instant changedAt
) {
    public static AuditLogResponse from(AuditLog log) {
        return new AuditLogResponse(
                log.getId(),
                log.getSourceService(),
                log.getTableName(),
                log.getRecordId(),
                log.getAction(),
                log.getOldData(),
                log.getNewData(),
                log.getChangedBy(),
                log.getChangedAt()
        );
    }
}

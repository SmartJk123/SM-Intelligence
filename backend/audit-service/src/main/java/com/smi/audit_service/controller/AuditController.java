package com.smi.audit_service.controller;

import com.smi.audit_service.dto.AuditLogResponse;
import com.smi.audit_service.dto.CreateAuditLogRequest;
import com.smi.audit_service.security.AuditIdentity;
import com.smi.audit_service.service.AuditService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/audit")
public class AuditController {

    private final AuditService auditService;
    private final AuditIdentity identity;

    public AuditController(AuditService auditService, AuditIdentity identity) {
        this.auditService = auditService;
        this.identity = identity;
    }

    @PostMapping
    public ResponseEntity<AuditLogResponse> recordAuditLog(
            @RequestHeader(value = "X-Internal-Token", required = false) String internalToken,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = "X-User-Id", required = false) String xUserId,
            @Valid @RequestBody CreateAuditLogRequest request) {

        UUID actorId = request.changedBy();
        if (actorId == null) {
            actorId = identity.resolveActor(authorization, xUserId, null);
            if (actorId != null) {
                request = new CreateAuditLogRequest(
                        request.sourceService(),
                        request.tableName(),
                        request.recordId(),
                        request.action(),
                        request.oldData(),
                        request.newData(),
                        actorId
                );
            }
        }

        AuditLogResponse created = auditService.recordAuditLog(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping
    public ResponseEntity<Page<AuditLogResponse>> getAuditLogs(
            @RequestParam(required = false) String sourceService,
            @RequestParam(required = false) String tableName,
            @RequestParam(required = false) UUID recordId,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) UUID changedBy,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant toDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Page<AuditLogResponse> logs = auditService.getAuditLogs(
                sourceService, tableName, recordId, action, changedBy, fromDate, toDate, page, size);
        return ResponseEntity.ok(logs);
    }

    @GetMapping("/{id}")
    public ResponseEntity<AuditLogResponse> getAuditLogById(@PathVariable UUID id) {
        return ResponseEntity.ok(auditService.getAuditLogById(id));
    }

    @GetMapping("/history/{tableName}/{recordId}")
    public ResponseEntity<List<AuditLogResponse>> getEntityHistory(
            @PathVariable String tableName,
            @PathVariable UUID recordId) {
        return ResponseEntity.ok(auditService.getEntityHistory(tableName, recordId));
    }
}

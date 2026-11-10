package com.smi.audit_service.service;

import com.smi.audit_service.domain.AuditLog;
import com.smi.audit_service.dto.AuditLogResponse;
import com.smi.audit_service.dto.CreateAuditLogRequest;
import com.smi.audit_service.exception.AuditLogNotFoundException;
import com.smi.audit_service.repository.AuditLogRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class AuditService {

    private final AuditLogRepository auditLogRepository;

    public AuditService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    @Transactional
    public AuditLogResponse recordAuditLog(CreateAuditLogRequest request) {
        AuditLog auditLog = new AuditLog(
                request.sourceService(),
                request.tableName(),
                request.recordId(),
                request.action().toUpperCase(),
                request.oldData(),
                request.newData(),
                request.changedBy()
        );

        AuditLog saved = auditLogRepository.save(auditLog);
        return AuditLogResponse.from(saved);
    }

    public Page<AuditLogResponse> getAuditLogs(
            String sourceService,
            String tableName,
            UUID recordId,
            String action,
            UUID changedBy,
            Instant fromDate,
            Instant toDate,
            int page,
            int size) {

        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100),
                Sort.by(Sort.Direction.DESC, "changedAt"));

        Specification<AuditLog> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (sourceService != null && !sourceService.isBlank()) {
                predicates.add(cb.equal(root.get("sourceService"), sourceService.trim()));
            }
            if (tableName != null && !tableName.isBlank()) {
                predicates.add(cb.equal(root.get("tableName"), tableName.trim()));
            }
            if (recordId != null) {
                predicates.add(cb.equal(root.get("recordId"), recordId));
            }
            if (action != null && !action.isBlank()) {
                predicates.add(cb.equal(root.get("action"), action.trim().toUpperCase()));
            }
            if (changedBy != null) {
                predicates.add(cb.equal(root.get("changedBy"), changedBy));
            }
            if (fromDate != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("changedAt"), fromDate));
            }
            if (toDate != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("changedAt"), toDate));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        return auditLogRepository.findAll(spec, pageable).map(AuditLogResponse::from);
    }

    public AuditLogResponse getAuditLogById(UUID id) {
        return auditLogRepository.findById(id)
                .map(AuditLogResponse::from)
                .orElseThrow(() -> new AuditLogNotFoundException(id));
    }

    public List<AuditLogResponse> getEntityHistory(String tableName, UUID recordId) {
        return auditLogRepository.findByTableNameAndRecordIdOrderByChangedAtDesc(tableName, recordId)
                .stream()
                .map(AuditLogResponse::from)
                .toList();
    }
}
